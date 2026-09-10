package com.docpipeline.processing;

import com.docpipeline.config.AppProperties;
import com.docpipeline.document.Document;
import com.docpipeline.document.DocumentRepository;
import com.docpipeline.document.DocumentStatus;
import com.docpipeline.monitoring.CustomMetrics;
import com.docpipeline.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.DocumentLocation;
import software.amazon.awssdk.services.textract.model.FeatureType;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.JobStatus;
import software.amazon.awssdk.services.textract.model.S3Object;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisResponse;

import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Profile("worker")
@Slf4j
public class TextractService {
    private final TextractClient textractClient;
    private final S3Client stagingClient;
    private final StorageService storageService;
    private final DocumentRepository documentRepository;
    private final ProcessingJobRepository jobRepository;
    private final MetadataExtractor metadataExtractor;
    private final CustomMetrics metrics;
    private final AppProperties properties;

    public TextractService(TextractClient textractClient,
                           @Qualifier("textractS3Client") S3Client stagingClient,
                           StorageService storageService,
                           DocumentRepository documentRepository,
                           ProcessingJobRepository jobRepository,
                           MetadataExtractor metadataExtractor,
                           CustomMetrics metrics,
                           AppProperties properties) {
        this.textractClient = textractClient;
        this.stagingClient = stagingClient;
        this.storageService = storageService;
        this.documentRepository = documentRepository;
        this.jobRepository = jobRepository;
        this.metadataExtractor = metadataExtractor;
        this.metrics = metrics;
        this.properties = properties;
    }

    @Transactional
    public void start(Document document) {
        String stagingKey = "textract/" + document.getId() + "/" + document.getFileName();
        long size = storageService.getObjectSize(document.getStorageKey());
        try (InputStream input = storageService.openObject(document.getStorageKey())) {
            PutObjectRequest.Builder put = PutObjectRequest.builder()
                    .bucket(stagingBucket()).key(stagingKey).contentType(document.getContentType());
            if (properties.getAws().getTextractKmsKeyId() != null
                    && !properties.getAws().getTextractKmsKeyId().isBlank()) {
                put.serverSideEncryption("aws:kms")
                        .ssekmsKeyId(properties.getAws().getTextractKmsKeyId());
            }
            stagingClient.putObject(put.build(), RequestBody.fromInputStream(input, size));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not stage document for Textract", exception);
        }

        DocumentLocation location = DocumentLocation.builder()
                .s3Object(S3Object.builder().bucket(stagingBucket()).name(stagingKey).build()).build();
        StartDocumentAnalysisResponse response = textractClient.startDocumentAnalysis(
                StartDocumentAnalysisRequest.builder()
                        .documentLocation(location)
                        .featureTypes(FeatureType.TABLES, FeatureType.FORMS)
                        .clientRequestToken(document.getId().toString())
                        .jobTag(document.getId().toString())
                        .build());
        document.setTextractStagingKey(stagingKey);
        document.setTextractJobId(response.jobId());
        document.setStatus(DocumentStatus.PROCESSING);
        document.setLastError(null);
        documentRepository.save(document);
    }

    @Scheduled(fixedDelayString = "${app.processing.textract-poll-delay-ms:30000}")
    @Transactional
    public void pollPendingJobs() {
        for (Document document : documentRepository.findByStatus(DocumentStatus.PROCESSING)) {
            if (document.getTextractJobId() == null) {
                continue;
            }
            try {
                processResult(document);
            } catch (Exception exception) {
                log.error("Could not poll Textract job for document {}", document.getId(), exception);
            }
        }
    }

    private void processResult(Document document) {
        GetDocumentAnalysisResponse first = textractClient.getDocumentAnalysis(
                GetDocumentAnalysisRequest.builder().jobId(document.getTextractJobId()).build());
        if (first.jobStatus() == JobStatus.IN_PROGRESS) {
            return;
        }
        if (first.jobStatus() == JobStatus.FAILED || first.jobStatus() == JobStatus.PARTIAL_SUCCESS) {
            fail(document, "Textract job ended with status " + first.jobStatusAsString());
            return;
        }

        List<Block> blocks = new ArrayList<>(first.blocks());
        String nextToken = first.nextToken();
        while (nextToken != null && !nextToken.isBlank()) {
            GetDocumentAnalysisResponse page = textractClient.getDocumentAnalysis(
                    GetDocumentAnalysisRequest.builder()
                            .jobId(document.getTextractJobId()).nextToken(nextToken).build());
            blocks.addAll(page.blocks());
            nextToken = page.nextToken();
        }

        String extractedText = metadataExtractor.extractText(blocks);
        Map<String, String> pairs = metadataExtractor.extractKeyValuePairs(blocks);
        document.setExtractedText(extractedText);
        document.setMetadata(metadataExtractor.toJsonMetadata(
                extractedText, pairs, metadataExtractor.calculateAverageConfidence(blocks)));
        document.setStatus(DocumentStatus.COMPLETED);
        document.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        document.setLastError(null);
        documentRepository.save(document);
        jobRepository.findByDocumentId(document.getId()).ifPresent(job -> {
            job.setStatus(ProcessingJobStatus.COMPLETED);
            job.setLastError(null);
            jobRepository.save(job);
        });
        deleteStaging(document);
        metrics.recordProcessingSuccess(Duration.between(
                document.getUploadedAt() == null ? document.getCreatedAt() : document.getUploadedAt(),
                document.getProcessedAt()));
    }

    private void fail(Document document, String error) {
        document.setStatus(DocumentStatus.FAILED);
        document.setLastError(error);
        documentRepository.save(document);
        jobRepository.findByDocumentId(document.getId()).ifPresent(job -> {
            job.setStatus(ProcessingJobStatus.FAILED);
            job.setLastError(error);
            jobRepository.save(job);
        });
        try {
            deleteStaging(document);
        } catch (Exception cleanupException) {
            log.warn("Could not delete failed Textract staging object for document {}", document.getId(), cleanupException);
        }
        metrics.recordProcessingFailure();
    }

    private void deleteStaging(Document document) {
        if (document.getTextractStagingKey() == null) {
            return;
        }
        stagingClient.deleteObject(DeleteObjectRequest.builder()
                .bucket(stagingBucket()).key(document.getTextractStagingKey()).build());
        document.setTextractStagingKey(null);
        documentRepository.save(document);
    }

    private String stagingBucket() {
        String bucket = properties.getAws().getTextractStagingBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("AWS_TEXTRACT_STAGING_BUCKET is required for worker mode");
        }
        return bucket;
    }
}
