package com.docpipeline.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CustomMetrics {

    private final Counter documentsUploaded;
    private final Counter documentsProcessedSuccess;
    private final Counter documentsProcessedFailure;
    private final Timer textractDuration;

    public CustomMetrics(MeterRegistry registry) {
        this.documentsUploaded = Counter.builder("docpipeline.documents.uploaded")
                .description("Total number of documents uploaded")
                .register(registry);

        this.documentsProcessedSuccess = Counter.builder("docpipeline.documents.processed.success")
                .description("Total number of documents processed successfully")
                .register(registry);

        this.documentsProcessedFailure = Counter.builder("docpipeline.documents.processed.failure")
                .description("Total number of documents that failed processing")
                .register(registry);

        this.textractDuration = Timer.builder("docpipeline.textract.duration")
                .description("Time taken for Textract processing")
                .register(registry);
    }

    public void recordUpload() {
        documentsUploaded.increment();
    }

    public void recordProcessingSuccess(Duration duration) {
        documentsProcessedSuccess.increment();
        textractDuration.record(duration);
    }

    public void recordProcessingFailure() {
        documentsProcessedFailure.increment();
    }
}
