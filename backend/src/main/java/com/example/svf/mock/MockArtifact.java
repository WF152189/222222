package com.example.svf.mock;

import java.time.Instant;

public record MockArtifact(
        String id,
        String actionId,
        String ticket,
        String name,
        String sourceType,
        String path,
        byte[] content,
        Instant createdAt,
        String userId,
        String userName
) {
}
