package com.example.svf.svf.model;

public record SvfRenderRequest(
        String artifactName,
        String formPath,
        String csvData,
        SvfUserContext user,
        SvfRenderOptions options
) {
    public SvfRenderRequest {
        options = options == null ? SvfRenderOptions.pdfCsvDefault() : options;
    }
}
