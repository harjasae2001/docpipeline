package com.docpipeline.processing;

import com.docpipeline.config.AppProperties;
import com.docpipeline.document.Document;
import com.docpipeline.document.DocumentRepository;
import com.docpipeline.document.DocumentStatus;
import com.docpipeline.monitoring.CustomMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("worker")
@Slf4j
public class DocumentProcessingWorker {
    private final ProcessingQueueService queueService;
    private final ProcessingJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final CsvDocumentProcessor csvProcessor;
    private final TextractService textractService;
    private final AppProperties properties;
    private final CustomMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    public DocumentProcessingWorker(ProcessingQueueService queueService,
                                    ProcessingJobRepository jobRepository,
                                    DocumentRepository documentRepository,
                                    CsvDocumentProcessor csvProcessor,
                                    TextractService textractService,
                                    AppProperties properties,
                                    CustomMetrics metrics,
                                    TransactionTemplate transactionTemplate) {
        this.queueService = queueService;
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
        this.csvProcessor = csvProcessor;
        this.textractService = textractService;
        this.properties = properties;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${app.processing.queue-poll-delay-ms:5000}")
    public void poll() {
        List<ReceivedQueueMessage> messages = queueService.read(
                properties.getProcessing().getVisibilityTimeoutSeconds(),
                properties.getProcessing().getBatchSize());
        messages.forEach(message -> transactionTemplate.executeWithoutResult(status -> handle(message)));
    }

    void handle(ReceivedQueueMessage received) {
        QueueMessage message = received.payload();
        Document document = documentRepository.findById(message.documentId()).orElse(null);
        ProcessingJob job = jobRepository.findByDocumentId(message.documentId()).orElse(null);
        if (document == null || job == null || document.getStatus() == DocumentStatus.ARCHIVED) {
            queueService.archive(received.messageId());
            return;
        }
        if ((document.getStatus() == DocumentStatus.PROCESSING && document.getTextractJobId() != null)
                || document.getStatus() == DocumentStatus.COMPLETED) {
            queueService.delete(received.messageId());
            return;
        }

        try {
            int claimed = documentRepository.transitionStatus(document.getId(),
                    List.of(DocumentStatus.UPLOADED, DocumentStatus.PENDING_UPLOAD), DocumentStatus.PROCESSING);
            if (claimed == 0 && document.getStatus() != DocumentStatus.PROCESSING) {
                queueService.archive(received.messageId());
                return;
            }
            document.setStatus(DocumentStatus.PROCESSING);
            job.setStatus(ProcessingJobStatus.PROCESSING);
            jobRepository.save(job);
            if ("text/csv".equals(document.getContentType())) {
                csvProcessor.process(document);
                job.setStatus(ProcessingJobStatus.COMPLETED);
                jobRepository.save(job);
            } else {
                textractService.start(document);
            }
            queueService.delete(received.messageId());
        } catch (IllegalArgumentException exception) {
            permanentFailure(received, document, job, exception.getMessage());
        } catch (Exception exception) {
            transientFailure(received, document, job, exception);
        }
    }

    private void transientFailure(ReceivedQueueMessage received, Document document,
                                  ProcessingJob job, Exception exception) {
        String error = safeMessage(exception);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setLastError(error);
        documentRepository.save(document);
        if (job.getAttemptCount() + 1 >= properties.getProcessing().getMaxAttempts()) {
            permanentFailure(received, document, job, error);
            return;
        }
        int baseDelay = Math.min(300, (int) Math.pow(2, job.getAttemptCount() + 1) * 5);
        int delay = Math.min(300, baseDelay + ThreadLocalRandom.current().nextInt(1, 6));
        queueService.retry(document, job, delay, error);
        queueService.delete(received.messageId());
        log.warn("Retrying document {} in {} seconds: {}", document.getId(), delay, error);
    }

    private void permanentFailure(ReceivedQueueMessage received, Document document,
                                  ProcessingJob job, String error) {
        document.setStatus(DocumentStatus.FAILED);
        document.setLastError(error);
        documentRepository.save(document);
        queueService.deadLetter(received.payload(), job, error);
        queueService.archive(received.messageId());
        metrics.recordProcessingFailure();
        log.error("Document {} moved to the processing DLQ: {}", document.getId(), error);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(2000, message.length()));
    }
}
