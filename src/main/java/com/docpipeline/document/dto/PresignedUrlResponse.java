package com.docpipeline.document.dto;

import java.util.UUID;

public record PresignedUrlResponse(
        UUID documentId,
        String uploadUrl,
        String s3Key,
        String storageKey
) {
    public PresignedUrlResponse(UUID documentId, String uploadUrl, String storageKey) {
        this(documentId, uploadUrl, storageKey, storageKey);
    }
}
