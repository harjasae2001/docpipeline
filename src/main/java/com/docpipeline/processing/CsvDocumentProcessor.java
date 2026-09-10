package com.docpipeline.processing;

import com.docpipeline.document.Document;
import com.docpipeline.document.DocumentRepository;
import com.docpipeline.document.DocumentStatus;
import com.docpipeline.monitoring.CustomMetrics;
import com.docpipeline.storage.StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Profile("worker")
public class CsvDocumentProcessor {
    private final StorageService storageService;
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;
    private final CustomMetrics metrics;

    public CsvDocumentProcessor(StorageService storageService,
                                DocumentRepository documentRepository,
                                ObjectMapper objectMapper,
                                CustomMetrics metrics) {
        this.storageService = storageService;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Transactional
    public void process(Document document) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                storageService.openObject(document.getStorageKey()), StandardCharsets.UTF_8))) {
            CsvStats stats = readCsv(reader);
            complete(document, stats);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read CSV document", exception);
        }
    }

    private CsvStats readCsv(BufferedReader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        int rows = 0;
        int maxColumns = 0;
        int columns = 1;
        boolean quoted = false;
        boolean hasRecordContent = false;
        int current;

        while ((current = reader.read()) != -1) {
            char value = (char) current;
            text.append(value);
            if (value == '"') {
                reader.mark(1);
                int next = reader.read();
                if (quoted && next == '"') {
                    text.append('"');
                    hasRecordContent = true;
                } else {
                    quoted = !quoted;
                    if (next != -1) reader.reset();
                }
            } else if (value == ',' && !quoted) {
                columns++;
                hasRecordContent = true;
            } else if ((value == '\n' || value == '\r') && !quoted) {
                if (value == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '\n') text.append('\n');
                    else if (next != -1) reader.reset();
                }
                rows++;
                maxColumns = Math.max(maxColumns, columns);
                columns = 1;
                hasRecordContent = false;
            } else {
                hasRecordContent = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV contains an unterminated quoted field");
        }
        if (hasRecordContent) {
            rows++;
            maxColumns = Math.max(maxColumns, columns);
        }
        return new CsvStats(text.toString(), rows, maxColumns);
    }

    private void complete(Document document, CsvStats stats) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("processor", "csv");
        metadata.put("rowCount", stats.rows());
        metadata.put("maxColumnCount", stats.maxColumns());
        metadata.put("textLength", stats.text().length());
        try {
            document.setMetadata(objectMapper.writeValueAsString(metadata));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize CSV metadata", exception);
        }
        document.setExtractedText(stats.text());
        document.setStatus(DocumentStatus.COMPLETED);
        document.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        document.setLastError(null);
        documentRepository.save(document);
        metrics.recordProcessingSuccess(Duration.between(
                document.getUploadedAt() == null ? document.getCreatedAt() : document.getUploadedAt(),
                document.getProcessedAt()));
    }

    private record CsvStats(String text, int rows, int maxColumns) {}
}
