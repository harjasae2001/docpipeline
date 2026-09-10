package com.docpipeline.processing;

import java.util.UUID;

public record QueueMessage(
        UUID documentId,
        String storageBucket,
        String storageKey,
        String contentType,
        UUID correlationId,
        int attempt
) {}
