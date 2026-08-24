package com.example.svf.svf.model;

public record SvfRenderResult(
        byte[] pdf,
        String artifactId,
        String actionId,
        String ticket,
        String artifactName,
        String contentType
) {
}
