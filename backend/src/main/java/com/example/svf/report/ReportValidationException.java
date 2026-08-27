package com.example.svf.report;

import java.util.List;

public class ReportValidationException extends RuntimeException {
    private final List<ValidationErrorDetail> errors;

    public ReportValidationException(List<ValidationErrorDetail> errors) {
        super("Report search request validation failed");
        this.errors = List.copyOf(errors);
    }

    public List<ValidationErrorDetail> getErrors() {
        return errors;
    }
}
