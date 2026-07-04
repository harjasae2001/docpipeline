package com.docpipeline.processing;

import com.docpipeline.document.Document;
import com.docpipeline.document.DocumentRepository;
import com.docpipeline.document.DocumentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class EventBridgeListener {

    private final DocumentRepository documentRepository;
    private final TextractService textractService;
    private final ObjectMapper objectMapper;

    public EventBridgeListener(DocumentRepository documentRepository,
                               TextractService textractService,
                               ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.textractService = textractService;
        this.objectMapper = objectMapper;
    }

    @SqsListener("${app.aws.sqs.queue-name}")
    public void handleS3Event(String message) {
        log.info("Received SQS message for S3 event");
        try {
            JsonNode root = objectMapper.readTree(message);

            String bucketName;
            String objectKey;

            // Handle EventBridge event wrapping S3 notification
            if (root.has("detail")) {
                JsonNode detail = root.get("detail");
                JsonNode bucket = detail.path("bucket");
                JsonNode object = detail.path("object");
                bucketName = bucket.path("name").asText();
                objectKey = object.path("key").asText();
            } else if (root.has("Records")) {
                // Direct S3 event notification
                JsonNode record = root.get("Records").get(0);
                JsonNode s3 = record.get("s3");
                bucketName = s3.get("bucket").get("name").asText();
                objectKey = s3.get("object").get("key").asText();
            } else {
                log.warn("Unrecognized event format: {}", message);
                return;
            }

            log.info("S3 event - bucket: {}, key: {}", bucketName, objectKey);

            Optional<Document> documentOpt = documentRepository.findByS3Key(objectKey);
            if (documentOpt.isPresent()) {
                Document document = documentOpt.get();
                if (document.getStatus() == DocumentStatus.UPLOADED) {
                    log.info("Starting Textract processing for document {}", document.getId());
                    textractService.processDocument(document);
                } else {
                    log.debug("Document {} is in status {}, skipping processing", document.getId(), document.getStatus());
                }
            } else {
                log.warn("No document found for S3 key: {}", objectKey);
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse SQS message: {}", e.getMessage(), e);
        }
    }
}
