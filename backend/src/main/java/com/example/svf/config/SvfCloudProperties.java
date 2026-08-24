package com.example.svf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "svf.cloud")
public record SvfCloudProperties(
        String mode,
        String baseUrl,
        String clientId,
        String secret,
        String userId,
        String userName,
        String jwtPrivateKeyPath,
        Long jwtExpirationSeconds,
        Long tokenExpirationSeconds,
        Long mockProcessingDelayMillis,
        Boolean mockForceError,
        Boolean mockRateLimitEnabled,
        Long mockRetryAfterSeconds
) {
    public boolean isRealMode() {
        return "real".equalsIgnoreCase(mode);
    }

    public long resolvedJwtExpirationSeconds() {
        return jwtExpirationSeconds == null || jwtExpirationSeconds <= 0 ? 300 : jwtExpirationSeconds;
    }

    public long resolvedTokenExpirationSeconds() {
        return tokenExpirationSeconds == null || tokenExpirationSeconds <= 0 ? 3600 : tokenExpirationSeconds;
    }

    public long resolvedMockProcessingDelayMillis() {
        return mockProcessingDelayMillis == null || mockProcessingDelayMillis < 0 ? 0 : mockProcessingDelayMillis;
    }

    public boolean resolvedMockForceError() {
        return Boolean.TRUE.equals(mockForceError);
    }

    public boolean resolvedMockRateLimitEnabled() {
        return Boolean.TRUE.equals(mockRateLimitEnabled);
    }

    public long resolvedMockRetryAfterSeconds() {
        return mockRetryAfterSeconds == null || mockRetryAfterSeconds <= 0 ? 10 : mockRetryAfterSeconds;
    }
}
