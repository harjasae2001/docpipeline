package com.docpipeline.report;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/{documentId}/generate")
    public ResponseEntity<Map<String, String>> generateReport(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal Jwt jwt) {
        String downloadUrl = reportService.generateReport(documentId, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
    }

    @GetMapping("/{documentId}/download-url")
    public ResponseEntity<Map<String, String>> getReportDownloadUrl(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal Jwt jwt) {
        String downloadUrl = reportService.getReportDownloadUrl(documentId, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
    }
}
