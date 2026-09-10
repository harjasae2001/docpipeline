package com.docpipeline.processing;

import com.docpipeline.document.Document;
import com.docpipeline.document.DocumentRepository;
import com.docpipeline.document.DocumentStatus;
import com.docpipeline.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("worker")
@Slf4j
public class PendingUploadReconciler {
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final ProcessingQueueService queueService;

    public PendingUploadReconciler(DocumentRepository documentRepository,
                                   StorageService storageService,
                                   ProcessingQueueService queueService) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.queueService = queueService;
    }

    @Scheduled(fixedDelayString = "${app.processing.reconciliation-delay-ms:60000}")
    @Transactional
    public void reconcile() {
        for (Document document : documentRepository.findByStatus(DocumentStatus.PENDING_UPLOAD)) {
            try {
                if (storageService.doesObjectExist(document.getStorageKey())) {
                    document.setFileSize(storageService.getObjectSize(document.getStorageKey()));
                    document.setStatus(DocumentStatus.UPLOADED);
                    documentRepository.save(document);
                    queueService.enqueueIfAbsent(document);
                }
            } catch (Exception exception) {
                log.warn("Could not reconcile pending upload {}", document.getId(), exception);
            }
        }
    }
}
