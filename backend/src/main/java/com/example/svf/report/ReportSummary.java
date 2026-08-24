package com.example.svf.report;

import java.time.LocalDate;

public record ReportSummary(
        String id,
        String name,
        LocalDate reportDate,
        String reportNumber,
        boolean pdfConverted
) {
}
