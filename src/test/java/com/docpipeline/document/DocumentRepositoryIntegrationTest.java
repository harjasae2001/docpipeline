package com.docpipeline.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
class DocumentRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("docpipeline")
            .withUsername("docpipeline")
            .withPassword("docpipeline");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DocumentRepository documentRepository;

    @Test
    void findsDocumentOnlyForItsOwner() {
        UUID ownerId = UUID.randomUUID();
        Document document = new Document();
        document.setUserId(ownerId);
        document.setFileName("invoice.pdf");
        document.setContentType("application/pdf");
        document.setS3Key("users/" + ownerId + "/invoice.pdf");
        Document saved = documentRepository.saveAndFlush(document);

        assertThat(documentRepository.findByIdAndUserId(saved.getId(), ownerId)).isPresent();
        assertThat(documentRepository.findByIdAndUserId(saved.getId(), UUID.randomUUID())).isEmpty();
    }
}
