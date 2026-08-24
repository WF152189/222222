package com.example.svf.report;

import com.example.svf.auth.AuthenticatedUser;
import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfUserContext;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    public static final String SVF_USER_ID_HEADER = "X-SVF-User-Id";
    public static final String SVF_USER_NAME_HEADER = "X-SVF-User-Name";

    private final ReportService reportService;
    private final SvfCloudProperties svfCloudProperties;

    public ReportController(ReportService reportService, SvfCloudProperties svfCloudProperties) {
        this.reportService = reportService;
        this.svfCloudProperties = svfCloudProperties;
    }

    @GetMapping
    public List<ReportSummary> searchReports() {
        return reportService.findAll();
    }

    @GetMapping(value = "/{reportId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> showPdf(
            @PathVariable("reportId") String reportId,
            @RequestHeader(value = SVF_USER_ID_HEADER, required = false) String userId,
            @RequestHeader(value = SVF_USER_NAME_HEADER, required = false) String userName,
            Authentication authentication) {
        byte[] pdf = reportService.convertToPdf(reportId, resolveUser(authentication, userId, userName));
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(reportId + ".pdf", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }

    private SvfUserContext resolveUser(Authentication authentication, String userId, String userName) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return new SvfUserContext(user.userId(), user.userName());
        }
        String resolvedUserId = userId == null || userId.isBlank() ? svfCloudProperties.userId() : userId;
        String resolvedUserName = userName == null || userName.isBlank() ? svfCloudProperties.userName() : userName;
        return new SvfUserContext(resolvedUserId, resolvedUserName);
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ReportNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("message", ex.getMessage()));
    }
}
