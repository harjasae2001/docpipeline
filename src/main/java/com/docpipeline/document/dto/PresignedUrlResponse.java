package com.docpipeline.document.dto;

import java.util.UUID;

public record PresignedUrlResponse(
        UUID documentId,
        String uploadUrl,
        String s3Key
) {}
