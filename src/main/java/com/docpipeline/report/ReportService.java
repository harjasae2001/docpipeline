package com.docpipeline.report;

import com.docpipeline.config.AppProperties;
import com.docpipeline.document.Document;
import com.docpipeline.document.DocumentRepository;
import com.docpipeline.document.DocumentStatus;
import com.docpipeline.exception.DocumentNotFoundException;
import com.docpipeline.storage.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ReportService {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ReportService(DocumentRepository documentRepository,
                         StorageService storageService,
                         AppProperties appProperties,
                         ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
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
            String reportKey = String.format("reports/%s/%s/report.json", userId, documentId);
            storageService.putString(reportKey, "application/json", reportJson);
            log.info("Report generated and uploaded for document {} at {}", documentId, reportKey);

            return storageService.generatePresignedGetUrl(
                    reportKey,
                    appProperties.getStorage().getPresignedUrlExpiration()
            );
        } catch (Exception e) {
            log.error("Failed to generate report for document {}", documentId, e);
            throw new RuntimeException("Failed to generate report", e);
        }
    }

    public String getReportDownloadUrl(UUID documentId, UUID userId) {
        documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        String reportKey = String.format("reports/%s/%s/report.json", userId, documentId);

        if (!storageService.doesObjectExist(reportKey)) {
            throw new DocumentNotFoundException("Report not found for document: " + documentId);
        }

        return storageService.generatePresignedGetUrl(
                reportKey,
                appProperties.getStorage().getPresignedUrlExpiration()
        );
    }
}
