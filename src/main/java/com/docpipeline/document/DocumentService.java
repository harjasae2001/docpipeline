package com.docpipeline.document;

import com.docpipeline.config.AppProperties;
import com.docpipeline.document.dto.DocumentResponse;
import com.docpipeline.document.dto.PresignedUrlResponse;
import com.docpipeline.exception.DocumentNotFoundException;
import com.docpipeline.monitoring.CustomMetrics;
import com.docpipeline.processing.ProcessingQueueService;
import com.docpipeline.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "application/pdf", "text/csv", "image/jpeg", "image/png");

    private final StorageService storageService;
    private final AppProperties appProperties;
    private final CustomMetrics customMetrics;
    private final ProcessingQueueService processingQueueService;

    public DocumentService(DocumentRepository documentRepository,
                           StorageService storageService,
                           AppProperties appProperties,
                           CustomMetrics customMetrics,
                           ProcessingQueueService processingQueueService) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.appProperties = appProperties;
        this.customMetrics = customMetrics;
        this.processingQueueService = processingQueueService;
    }

    @Transactional
    public PresignedUrlResponse requestUploadUrl(String fileName, String contentType, UUID userId) {
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
        String safeName = sanitizeFileName(fileName);
        String storageKey = String.format("users/%s/%s/%s", userId, UUID.randomUUID(), safeName);

        Document document = new Document();
        document.setUserId(userId);
        document.setFileName(safeName);
        document.setContentType(contentType);
        document.setStorageBucket(appProperties.getStorage().getBucketName());
        document.setStorageKey(storageKey);
        document.setStatus(DocumentStatus.PENDING_UPLOAD);

        document = documentRepository.save(document);
        log.info("Created document record {} for user {} with storage key {}", document.getId(), userId, storageKey);

        String uploadUrl = storageService.generatePresignedPutUrl(
                storageKey,
                contentType,
                appProperties.getStorage().getPresignedUrlExpiration()
        );

        return new PresignedUrlResponse(document.getId(), uploadUrl, storageKey);
    }

    @Transactional
    public DocumentResponse confirmUpload(UUID documentId, UUID userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        if (!storageService.doesObjectExist(document.getStorageKey())) {
            throw new IllegalArgumentException("File has not been uploaded yet");
        }

        long fileSize = storageService.getObjectSize(document.getStorageKey());
        if (fileSize > 50L * 1024 * 1024) {
            throw new IllegalArgumentException("File exceeds the 50 MB limit");
        }
        document.setFileSize(fileSize);
        
        if (document.getStatus() == DocumentStatus.PENDING_UPLOAD) {
            document.setStatus(DocumentStatus.UPLOADED);
        }
        if (document.getUploadedAt() == null) {
            document.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }

        document = documentRepository.save(document);
        processingQueueService.enqueueIfAbsent(document);
        customMetrics.recordUpload();
        log.info("Upload confirmed for document {}", documentId);

        return toDocumentResponse(document);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listDocuments(UUID userId, Pageable pageable) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toDocumentResponse);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId, UUID userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));
        return toDocumentResponse(document);
    }

    @Transactional(readOnly = true)
    public String getDownloadUrl(UUID documentId, UUID userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));
        return storageService.generatePresignedGetUrl(
                document.getStorageKey(),
                appProperties.getStorage().getPresignedUrlExpiration()
        );
    }

    @Transactional
    public void deleteDocument(UUID documentId, UUID userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        storageService.deleteObject(document.getStorageKey());
        storageService.deleteObject(String.format("reports/%s/%s/report.json", userId, documentId));
        document.setStatus(DocumentStatus.ARCHIVED);
        documentRepository.save(document);
        log.info("Document {} deleted and archived", documentId);
    }

    private DocumentResponse toDocumentResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                document.getFileSize(),
                document.getStatus().name(),
                document.getExtractedText(),
                document.getMetadata(),
                toLocalDateTime(document.getUploadedAt()),
                toLocalDateTime(document.getProcessedAt()),
                toLocalDateTime(document.getCreatedAt()),
                toLocalDateTime(document.getUpdatedAt()),
                document.getLastError()
        );
    }

    private java.time.LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private String sanitizeFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.replace('\\', '/');
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (leaf.isBlank() || leaf.equals(".") || leaf.equals("..")) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return leaf.length() > 500 ? leaf.substring(0, 500) : leaf;
    }
}
