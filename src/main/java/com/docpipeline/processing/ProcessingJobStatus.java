package com.docpipeline.processing;

public enum ProcessingJobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    RETRYING,
    FAILED,
    DEAD_LETTER
}
