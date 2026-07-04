package com.docpipeline.document.dto;

import jakarta.validation.constraints.NotBlank;

public record PresignedUrlRequest(
        @NotBlank(message = "File name is required")
        String fileName,
        @NotBlank(message = "Content type is required")
        String contentType
) {}
