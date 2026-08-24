package com.example.svf.svf;

import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfUserContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

@Component
public class DefaultSvfJwtAssertionFactory implements SvfJwtAssertionFactory {
    private final SvfCloudProperties properties;

    public DefaultSvfJwtAssertionFactory(SvfCloudProperties properties) {
        this.properties = properties;
    }

    @Override
    public String createAssertion(SvfUserContext user) {
        SvfUserContext resolvedUser = resolveUser(user);
        String header = base64Url("{\"alg\":\"RS256\"}");
        String payload = base64Url(createClaims(resolvedUser));
        String signingInput = header + "." + payload;
        if (properties.isRealMode()) {
            return signingInput + "." + sign(signingInput);
        }
        return signingInput + ".mock-signature";
    }

    private String createClaims(SvfUserContext user) {
        long exp = Instant.now().plusSeconds(properties.resolvedJwtExpirationSeconds()).getEpochSecond();
        return "{\"iss\":\"" + escapeJson(properties.clientId())
                + "\",\"sub\":\"" + escapeJson(user.userId())
                + "\",\"exp\":\"" + exp
                + "\",\"userName\":\"" + escapeJson(user.userName())
                + "\",\"timeZone\":\"Asia/Tokyo\",\"locale\":\"ja\"}";
    }

    private SvfUserContext resolveUser(SvfUserContext user) {
        if (user != null && !user.isBlank()) {
            return user;
        }
        return new SvfUserContext(properties.userId(), properties.userName());
    }

    private String sign(String signingInput) {
        try {
            PrivateKey privateKey = loadPrivateKey();
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new SvfCloudException("Failed to create SVF Cloud JWT assertion", ex);
        }
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String path = properties.jwtPrivateKeyPath();
        if (path == null || path.isBlank()) {
            throw new SvfCloudException("svf.cloud.jwt-private-key-path is required in real mode");
        }
        String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
