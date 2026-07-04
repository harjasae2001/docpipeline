package com.docpipeline.document;

import com.docpipeline.config.AppProperties;
import com.docpipeline.document.dto.DocumentResponse;
import com.docpipeline.document.dto.PresignedUrlResponse;
import com.docpipeline.exception.DocumentNotFoundException;
import com.docpipeline.monitoring.CustomMetrics;
import com.docpipeline.storage.S3StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final S3StorageService s3StorageService;
    private final AppProperties appProperties;
    private final CustomMetrics customMetrics;

    public DocumentService(DocumentRepository documentRepository,
                           S3StorageService s3StorageService,
                           AppProperties appProperties,
                           CustomMetrics customMetrics) {
        this.documentRepository = documentRepository;
        this.s3StorageService = s3StorageService;
        this.appProperties = appProperties;
        this.customMetrics = customMetrics;
    }

    @Transactional
    public PresignedUrlResponse requestUploadUrl(String fileName, String contentType, UUID userId) {
        String s3Key = String.format("users/%s/%s/%s", userId, UUID.randomUUID(), fileName);

        Document document = new Document();
        document.setUserId(userId);
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setS3Key(s3Key);
        document.setStatus(DocumentStatus.PENDING_UPLOAD);

        document = documentRepository.save(document);
        log.info("Created document record {} for user {} with s3Key {}", document.getId(), userId, s3Key);

        String uploadUrl = s3StorageService.generatePresignedPutUrl(
                s3Key,
                contentType,
                appProperties.getAws().getS3().getPresignedUrlExpiration()
        );

        return new PresignedUrlResponse(document.getId(), uploadUrl, s3Key);
    }

    @Transactional
    public DocumentResponse confirmUpload(UUID documentId, UUID userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        if (!s3StorageService.doesObjectExist(document.getS3Key())) {
            throw new IllegalArgumentException("File has not been uploaded to S3 yet");
        }

        long fileSize = s3StorageService.getObjectSize(document.getS3Key());
        document.setFileSize(fileSize);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setUploadedAt(LocalDateTime.now());

        document = documentRepository.save(document);
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
        return s3StorageService.generatePresignedGetUrl(
                document.getS3Key(),
                appProperties.getAws().getS3().getPresignedUrlExpiration()
        );
    }

    @Transactional
    public void deleteDocument(UUID documentId, UUID userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        s3StorageService.deleteObject(document.getS3Key());
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
                document.getUploadedAt(),
                document.getProcessedAt(),
                document.getCreatedAt()
        );
    }
}
