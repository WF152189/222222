package com.example.svf.report;

public record ReportSearchItem(
        String reportId,
        String reportName,
        String createdDate,
        String createdBy,
        String outputStatus
) {
}
