package com.example.svf.mock;

public record SvfApiError(
        String code,
        String message,
        String detail
) {
}
