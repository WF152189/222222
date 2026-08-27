package com.example.svf.report;

import java.util.List;

public record ReportSearchResponse(
        List<ReportSearchItem> reports,
        int totalCount
) {
}
