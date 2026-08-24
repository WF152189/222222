package com.example.svf.report;

import java.time.LocalDate;
import java.util.List;

public record ReportDetail(
        String id,
        String name,
        LocalDate reportDate,
        String reportNumber,
        String customerName,
        List<ReportLine> lines
) {
}
