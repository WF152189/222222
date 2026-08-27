package com.example.svf.report;

public record ReportSearchRequest(
        String startDate,
        String startTime,
        String endDate,
        String endTime,
        String businessId
) {
}
