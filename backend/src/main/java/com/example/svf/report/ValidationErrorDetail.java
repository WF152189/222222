package com.example.svf.report;

public record ValidationErrorDetail(
        String code,
        String messageId,
        String message,
        String field
) {
}
