package com.docpipeline.document;

import com.docpipeline.document.dto.DocumentResponse;
import com.docpipeline.document.dto.PresignedUrlRequest;
import com.docpipeline.document.dto.PresignedUrlResponse;
import com.docpipeline.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponse> requestPresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request,
            @AuthenticationPrincipal User user) {
        PresignedUrlResponse response = documentService.requestUploadUrl(
                request.fileName(), request.contentType(), user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/confirm-upload")
    public ResponseEntity<DocumentResponse> confirmUpload(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        DocumentResponse response = documentService.confirmUpload(id, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DocumentResponse> documents = documentService.listDocuments(user.getId(), pageable);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        DocumentResponse response = documentService.getDocument(id, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/download-url")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        String url = documentService.getDownloadUrl(id, user.getId());
        return ResponseEntity.ok(Map.of("url", url));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        documentService.deleteDocument(id, user.getId());
    }
}
