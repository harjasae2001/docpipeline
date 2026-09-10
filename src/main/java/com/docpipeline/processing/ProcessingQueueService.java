package com.docpipeline.processing;

import com.docpipeline.document.Document;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ProcessingQueueService {
    static final String PROCESSING_QUEUE = "document_processing";
    static final String DEAD_LETTER_QUEUE = "document_processing_dlq";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ProcessingJobRepository jobRepository;

    public ProcessingQueueService(JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper,
                                  ProcessingJobRepository jobRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public ProcessingJob enqueueIfAbsent(Document document) {
        return jobRepository.findByDocumentId(document.getId()).orElseGet(() -> {
            ProcessingJob job = new ProcessingJob();
            job.setDocumentId(document.getId());
            job.setCorrelationId(UUID.randomUUID());
            job.setStatus(ProcessingJobStatus.QUEUED);
            job.setAttemptCount(0);
            ProcessingJob saved = jobRepository.save(job);
            send(PROCESSING_QUEUE, payload(document, saved, 0), 0);
            return saved;
        });
    }

    public List<ReceivedQueueMessage> read(int visibilitySeconds, int batchSize) {
        return jdbcTemplate.query("select * from pgmq.read(?, ?, ?)",
                (rs, rowNum) -> mapReceived(rs), PROCESSING_QUEUE, visibilitySeconds, batchSize);
    }

    public void retry(Document document, ProcessingJob job, int delaySeconds, String error) {
        int attempt = job.getAttemptCount() + 1;
        job.setAttemptCount(attempt);
        job.setStatus(ProcessingJobStatus.RETRYING);
        job.setLastError(error);
        jobRepository.save(job);
        send(PROCESSING_QUEUE, payload(document, job, attempt), delaySeconds);
    }

    public void deadLetter(QueueMessage message, ProcessingJob job, String error) {
        job.setStatus(ProcessingJobStatus.DEAD_LETTER);
        job.setLastError(error);
        jobRepository.save(job);
        send(DEAD_LETTER_QUEUE, message, 0);
    }

    public void delete(long messageId) {
        jdbcTemplate.queryForObject("select pgmq.delete(?, ?)", Boolean.class, PROCESSING_QUEUE, messageId);
    }

    public void archive(long messageId) {
        jdbcTemplate.queryForObject("select pgmq.archive(?, ?)", Boolean.class, PROCESSING_QUEUE, messageId);
    }

    private void send(String queue, QueueMessage message, int delaySeconds) {
        try {
            String json = objectMapper.writeValueAsString(message);
            jdbcTemplate.queryForObject("select * from pgmq.send(?, cast(? as jsonb), ?)",
                    Long.class, queue, json, delaySeconds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize processing message", exception);
        }
    }

    private ReceivedQueueMessage mapReceived(ResultSet resultSet) throws SQLException {
        try {
            QueueMessage payload = objectMapper.readValue(resultSet.getString("message"), QueueMessage.class);
            return new ReceivedQueueMessage(
                    resultSet.getLong("msg_id"), resultSet.getInt("read_ct"), payload);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid queue payload", exception);
        }
    }

    private QueueMessage payload(Document document, ProcessingJob job, int attempt) {
        return new QueueMessage(document.getId(), document.getStorageBucket(), document.getStorageKey(),
                document.getContentType(), job.getCorrelationId(), attempt);
    }
}
