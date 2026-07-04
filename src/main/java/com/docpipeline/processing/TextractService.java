package com.docpipeline.processing;

import com.docpipeline.config.AppProperties;
import com.docpipeline.document.Document;
import com.docpipeline.document.DocumentRepository;
import com.docpipeline.document.DocumentStatus;
import com.docpipeline.monitoring.CustomMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TextractService {

    private final TextractClient textractClient;
    private final DocumentRepository documentRepository;
    private final MetadataExtractor metadataExtractor;
    private final CustomMetrics customMetrics;
    private final AppProperties appProperties;

    public TextractService(TextractClient textractClient,
                           DocumentRepository documentRepository,
                           MetadataExtractor metadataExtractor,
                           CustomMetrics customMetrics,
                           AppProperties appProperties) {
        this.textractClient = textractClient;
        this.documentRepository = documentRepository;
        this.metadataExtractor = metadataExtractor;
        this.customMetrics = customMetrics;
        this.appProperties = appProperties;
    }

    @Transactional
    public void processDocument(Document document) {
        try {
            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);

            S3Object s3Object = S3Object.builder()
                    .bucket(appProperties.getAws().getS3().getBucketName())
                    .name(document.getS3Key())
                    .build();

            DocumentLocation documentLocation = DocumentLocation.builder()
                    .s3Object(s3Object)
                    .build();

            StartDocumentAnalysisRequest analysisRequest = StartDocumentAnalysisRequest.builder()
                    .documentLocation(documentLocation)
                    .featureTypes(FeatureType.TABLES, FeatureType.FORMS)
                    .build();

            StartDocumentAnalysisResponse response = textractClient.startDocumentAnalysis(analysisRequest);
            document.setTextractJobId(response.jobId());
            documentRepository.save(document);

            log.info("Started Textract analysis for document {} with job ID {}", document.getId(), response.jobId());
        } catch (TextractException e) {
            log.error("Failed to start Textract processing for document {}", document.getId(), e);
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            customMetrics.recordProcessingFailure();
        }
    }

    @Transactional
    public void checkAndProcessResult(Document document) {
        if (document.getTextractJobId() == null) {
            log.warn("No Textract job ID for document {}", document.getId());
            return;
        }

        try {
            GetDocumentAnalysisRequest request = GetDocumentAnalysisRequest.builder()
                    .jobId(document.getTextractJobId())
                    .build();

            GetDocumentAnalysisResponse response = textractClient.getDocumentAnalysis(request);
            JobStatus jobStatus = response.jobStatus();

            if (jobStatus == JobStatus.SUCCEEDED) {
                List<Block> blocks = response.blocks();
                String extractedText = metadataExtractor.extractText(blocks);
                Map<String, String> kvPairs = metadataExtractor.extractKeyValuePairs(blocks);
                double confidence = metadataExtractor.calculateAverageConfidence(blocks);
                String metadata = metadataExtractor.toJsonMetadata(extractedText, kvPairs, confidence);

                document.setExtractedText(extractedText);
                document.setMetadata(metadata);
                document.setStatus(DocumentStatus.COMPLETED);
                document.setProcessedAt(LocalDateTime.now());
                documentRepository.save(document);

                Duration processingDuration = Duration.between(
                        document.getUploadedAt() != null ? document.getUploadedAt() : document.getCreatedAt(),
                        document.getProcessedAt()
                );
                customMetrics.recordProcessingSuccess(processingDuration);
                log.info("Document {} processed successfully", document.getId());

            } else if (jobStatus == JobStatus.FAILED) {
                document.setStatus(DocumentStatus.FAILED);
                documentRepository.save(document);
                customMetrics.recordProcessingFailure();
                log.error("Textract processing failed for document {}", document.getId());
            } else {
                log.debug("Textract job {} still in progress for document {}", document.getTextractJobId(), document.getId());
            }
        } catch (TextractException e) {
            log.error("Error checking Textract result for document {}", document.getId(), e);
        }
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void pollPendingJobs() {
        List<Document> processingDocs = documentRepository.findByStatus(DocumentStatus.PROCESSING);
        if (!processingDocs.isEmpty()) {
            log.debug("Polling {} documents in PROCESSING status", processingDocs.size());
            for (Document doc : processingDocs) {
                checkAndProcessResult(doc);
            }
        }
    }
}
