package com.example.svf.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String jwtSecret;
    private final long expirationSeconds;

    public JwtTokenService(
            ObjectMapper objectMapper,
            @Value("${app.security.jwt-secret}") String jwtSecret,
            @Value("${app.security.jwt-expiration-seconds:3600}") long expirationSeconds) {
        this.objectMapper = objectMapper;
        this.jwtSecret = jwtSecret;
        this.expirationSeconds = expirationSeconds;
    }

    public LoginResponse issueToken(AuthenticatedUser user) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + expirationSeconds;
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.userId());
        payload.put("username", user.username());
        payload.put("name", user.userName());
        payload.put("roles", List.of(user.role()));
        payload.put("iat", now);
        payload.put("exp", expiresAt);

        String encodedHeader = base64Url(toJson(header));
        String encodedPayload = base64Url(toJson(payload));
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = sign(signingInput);
        return new LoginResponse(signingInput + "." + signature, expiresAt, user);
    }

    public Optional<AuthenticatedUser> parseToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] segments = token.split("\\.");
        if (segments.length != 3) {
            return Optional.empty();
        }
        String signingInput = segments[0] + "." + segments[1];
        if (!constantTimeEquals(sign(signingInput), segments[2])) {
            return Optional.empty();
        }
        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {});
            long exp = Long.parseLong(String.valueOf(payload.get("exp")));
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            String userId = String.valueOf(payload.get("sub"));
            String username = String.valueOf(payload.get("username"));
            String userName = String.valueOf(payload.get("name"));
            String role = extractRole(payload.get("roles"));
            if (isBlank(userId) || isBlank(username) || isBlank(userName) || isBlank(role)) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(username, userId, userName, role));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String extractRole(Object rolesValue) {
        if (rolesValue instanceof List<?> roles && !roles.isEmpty()) {
            return String.valueOf(roles.get(0));
        }
        return "";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize JWT content", ex);
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigestSafeEquals.equals(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }

    private static class MessageDigestSafeEquals {
        static boolean equals(byte[] left, byte[] right) {
            if (left.length != right.length) {
                return false;
            }
            int result = 0;
            for (int i = 0; i < left.length; i++) {
                result |= left[i] ^ right[i];
            }
            return result == 0;
        }
    }
}
