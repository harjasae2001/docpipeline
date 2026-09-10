package com.docpipeline.processing;

import com.docpipeline.document.Document;
import com.docpipeline.document.DocumentRepository;
import com.docpipeline.document.DocumentStatus;
import com.docpipeline.monitoring.CustomMetrics;
import com.docpipeline.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CsvDocumentProcessorTest {
    private final StorageService storage = mock(StorageService.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final CustomMetrics metrics = mock(CustomMetrics.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CsvDocumentProcessor processor = new CsvDocumentProcessor(storage, documents, objectMapper, metrics);

    @Test
    void processesQuotedCommasEscapedQuotesAndMultilineFields() throws Exception {
        String csv = "name,notes\r\nAlice,\"hello, world\"\r\nBob,\"two\nlines and \"\"quotes\"\"\"";
        Document document = document();
        when(storage.openObject("documents/user/sample.csv"))
                .thenReturn(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        processor.process(document);

        JsonNode metadata = objectMapper.readTree(document.getMetadata());
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(document.getExtractedText()).isEqualTo(csv);
        assertThat(metadata.get("rowCount").asInt()).isEqualTo(3);
        assertThat(metadata.get("maxColumnCount").asInt()).isEqualTo(2);
        verify(documents).save(document);
    }

    @Test
    void rejectsUnterminatedQuotedFieldWithoutPersistingCompletion() {
        Document document = document();
        when(storage.openObject("documents/user/sample.csv"))
                .thenReturn(new ByteArrayInputStream("name,\"broken".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> processor.process(document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unterminated");
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
    }

    private Document document() {
        Document document = new Document();
        document.setStorageKey("documents/user/sample.csv");
        document.setStatus(DocumentStatus.PROCESSING);
        document.setCreatedAt(OffsetDateTime.now().minusSeconds(1));
        return document;
    }
}
