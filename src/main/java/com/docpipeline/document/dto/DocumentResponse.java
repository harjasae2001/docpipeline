package com.docpipeline.document.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String fileName,
        String contentType,
        Long fileSize,
        String status,
        String extractedText,
        String metadata,
        LocalDateTime uploadedAt,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {}
