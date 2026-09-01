package com.example.svf.mock;

import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfUserContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockTokenStore {
    private final SvfCloudProperties properties;
    private final Map<String, TokenRecord> accessTokens = new ConcurrentHashMap<>();

    public MockTokenStore(SvfCloudProperties properties) {
        this.properties = properties;
    }

    public TokenIssueResult issue(SvfUserContext user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiration = Instant.now().plusSeconds(properties.resolvedTokenExpirationSeconds());
        accessTokens.put(token, new TokenRecord(expiration, user));
        // SVF Cloud 公式レスポンス例（13桁の 1442046911540 等）に合わせてエポックミリ秒を返す。
        // これによりクライアント側の期限判定ロジックを本番相当の値形式で検証できる。
        return new TokenIssueResult(token, expiration.toEpochMilli());
    }

    public void revoke(String token) {
        accessTokens.remove(token);
    }

    public Optional<SvfUserContext> extractValidBearerUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authorization.substring("Bearer ".length());
        TokenRecord tokenRecord = accessTokens.get(token);
        if (tokenRecord == null || tokenRecord.expiration().isBefore(Instant.now())) {
            accessTokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(tokenRecord.user());
    }

    public record TokenIssueResult(String token, long expiration) {
    }

    private record TokenRecord(Instant expiration, SvfUserContext user) {
    }
}
