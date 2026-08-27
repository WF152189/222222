package com.example.svf.report;

import com.example.svf.svf.SvfCloudException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ReportValidationException.class)
    public ResponseEntity<ReportSearchErrorResponse> handleReportValidationException(
            ReportValidationException ex) {
        return ResponseEntity.badRequest().body(new ReportSearchErrorResponse(
                "VALIDATION_ERROR",
                "入力内容を確認してください。",
                ex.getErrors(),
                resolveTraceId()));
    }

    @ExceptionHandler(SvfCloudException.class)
    public ResponseEntity<Map<String, String>> handleSvfCloudException(SvfCloudException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "code", "SVF_CLOUD_ERROR",
                        "message", ex.getMessage()));
    }

    private String resolveTraceId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            String traceId = servletAttributes.getRequest().getHeader("X-Trace-Id");
            if (traceId != null && !traceId.isBlank()) {
                return traceId;
            }
        }
        return UUID.randomUUID().toString();
    }
}
