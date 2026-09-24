package com.example.svf.svf;

import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * SVF Cloud アクセストークンを呼び出しのたびに新規取得する実装。
 *
 * <p>トークンをキャッシュしないため、アプリケーションの複数台構成でも
 * キャッシュの共有や無効化の同期を必要としません。</p>
 */
@Primary
@Component
public class NonCachingSvfTokenProvider implements SvfTokenProvider {
    private static final String JWT_BEARER_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final long EPOCH_MILLIS_THRESHOLD = 100_000_000_000L;

    private final SvfCloudProperties properties;
    private final SvfJwtAssertionFactory assertionFactory;
    private final RestClient restClient;
    private final Clock clock;

    @Autowired
    public NonCachingSvfTokenProvider(
            SvfCloudProperties properties,
            SvfJwtAssertionFactory assertionFactory,
            RestClient.Builder builder) {
        this(properties, assertionFactory, builder, Clock.systemUTC());
    }

    NonCachingSvfTokenProvider(
            SvfCloudProperties properties,
            SvfJwtAssertionFactory assertionFactory,
            RestClient.Builder builder,
            Clock clock) {
        this.properties = properties;
        this.assertionFactory = assertionFactory;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.clock = clock;
    }

    /**
     * 指定ユーザーのアクセストークンをSVF Cloudから毎回取得します。
     */
    @Override
    public String getAccessToken(SvfUserContext user) {
        String basic = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.secret()).getBytes(StandardCharsets.UTF_8));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", JWT_BEARER_GRANT_TYPE);
        body.add("assertion", assertionFactory.createAssertion(user));

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.get("token") == null || response.get("expiration") == null) {
                throw new SvfCloudException("SVF Cloud token response is invalid");
            }

            String token = response.get("token").toString();
            if (token.isBlank()) {
                throw new SvfCloudException("SVF Cloud token response is invalid");
            }

            Instant expiration = parseExpiration(response.get("expiration").toString());
            if (!Instant.now(clock).isBefore(expiration)) {
                throw new SvfCloudException("SVF Cloud returned an expired access token");
            }
            return token;
        } catch (RestClientResponseException ex) {
            throw new SvfCloudException("SVF Cloud token request failed: " + ex.getStatusCode(), ex);
        } catch (NumberFormatException ex) {
            throw new SvfCloudException("SVF Cloud token response has an invalid expiration", ex);
        }
    }

    private Instant parseExpiration(String expirationValue) {
        long value = Long.parseLong(expirationValue);
        return value >= EPOCH_MILLIS_THRESHOLD
                ? Instant.ofEpochMilli(value)
                : Instant.ofEpochSecond(value);
    }
}
