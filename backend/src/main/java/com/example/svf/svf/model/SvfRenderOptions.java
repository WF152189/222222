package com.example.svf.svf.model;

public record SvfRenderOptions(
        int timeoutSeconds,
        boolean redirect,
        String printer,
        String source,
        boolean waitForCompletion,
        long pollIntervalMillis
) {
    public static SvfRenderOptions pdfCsvDefault() {
        return new SvfRenderOptions(60, false, "PDF", "CSV", true, 500);
    }

    public int resolvedTimeoutSeconds() {
        return timeoutSeconds <= 0 ? 60 : timeoutSeconds;
    }

    public long resolvedPollIntervalMillis() {
        return pollIntervalMillis <= 0 ? 500 : pollIntervalMillis;
    }

    public String resolvedPrinter() {
        return printer == null || printer.isBlank() ? "PDF" : printer;
    }

    public String resolvedSource() {
        return source == null || source.isBlank() ? "CSV" : source;
    }
}
