package com.example.svf.report;

import java.util.List;

public record ReportSearchErrorResponse(
        String code,
        String message,
        List<ValidationErrorDetail> errors,
        String traceId
) {
}
