package com.docpipeline.report;

import com.docpipeline.config.AppProperties;
import com.docpipeline.document.Document;
import com.docpipeline.document.DocumentRepository;
import com.docpipeline.document.DocumentStatus;
import com.docpipeline.exception.DocumentNotFoundException;
import com.docpipeline.storage.S3StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ReportService {

    private final DocumentRepository documentRepository;
    private final S3StorageService s3StorageService;
    private final S3Client s3Client;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ReportService(DocumentRepository documentRepository,
                         S3StorageService s3StorageService,
                         S3Client s3Client,
                         AppProperties appProperties,
                         ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.s3StorageService = s3StorageService;
        this.s3Client = s3Client;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public String generateReport(UUID documentId, UUID userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        if (document.getStatus() != DocumentStatus.COMPLETED) {
            throw new IllegalArgumentException("Document processing is not completed. Current status: " + document.getStatus());
        }

        try {
            Map<String, Object> report = new HashMap<>();
            report.put("reportId", UUID.randomUUID().toString());
            report.put("documentId", document.getId().toString());
            report.put("fileName", document.getFileName());
            report.put("contentType", document.getContentType());
            report.put("fileSize", document.getFileSize());
            report.put("status", document.getStatus().name());
            report.put("extractedText", document.getExtractedText());
            report.put("metadata", document.getMetadata());
            report.put("uploadedAt", document.getUploadedAt() != null ? document.getUploadedAt().toString() : null);
            report.put("processedAt", document.getProcessedAt() != null ? document.getProcessedAt().toString() : null);
            report.put("generatedAt", LocalDateTime.now().toString());

            String reportJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
            String reportS3Key = String.format("reports/%s/%s/report.json", userId, documentId);

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(appProperties.getAws().getS3().getBucketName())
                    .key(reportS3Key)
                    .contentType("application/json")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromString(reportJson));
            log.info("Report generated and uploaded for document {} at {}", documentId, reportS3Key);

            return s3StorageService.generatePresignedGetUrl(
                    reportS3Key,
                    appProperties.getAws().getS3().getPresignedUrlExpiration()
            );
        } catch (Exception e) {
            log.error("Failed to generate report for document {}", documentId, e);
            throw new RuntimeException("Failed to generate report", e);
        }
    }

    public String getReportDownloadUrl(UUID documentId, UUID userId) {
        documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        String reportS3Key = String.format("reports/%s/%s/report.json", userId, documentId);

        if (!s3StorageService.doesObjectExist(reportS3Key)) {
            throw new DocumentNotFoundException("Report not found for document: " + documentId);
        }

        return s3StorageService.generatePresignedGetUrl(
                reportS3Key,
                appProperties.getAws().getS3().getPresignedUrlExpiration()
        );
    }
}
