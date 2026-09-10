package com.docpipeline.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Page<Document> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Optional<Document> findByIdAndUserId(UUID id, UUID userId);
    Optional<Document> findByStorageKey(String storageKey);
    List<Document> findByStatus(DocumentStatus status);

    @Modifying
    @Query("update Document d set d.status = :next where d.id = :id and d.status in :allowed")
    int transitionStatus(@Param("id") UUID id,
                         @Param("allowed") List<DocumentStatus> allowed,
                         @Param("next") DocumentStatus next);
}
