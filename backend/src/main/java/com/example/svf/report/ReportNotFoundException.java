package com.example.svf.report;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(String reportId) {
        super("Report not found: " + reportId);
    }
}
