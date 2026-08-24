package com.example.svf.svf;

import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfUserContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class CachedSvfTokenProvider implements SvfTokenProvider {
    private static final long EXPIRATION_BUFFER_SECONDS = 60;
    private static final String JWT_BEARER_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    private final SvfCloudProperties properties;
    private final SvfJwtAssertionFactory assertionFactory;
    private final RestClient restClient;
    private final ConcurrentMap<String, SvfAccessToken> tokenCache = new ConcurrentHashMap<>();

    public CachedSvfTokenProvider(
            SvfCloudProperties properties,
            SvfJwtAssertionFactory assertionFactory,
            RestClient.Builder builder) {
        this.properties = properties;
        this.assertionFactory = assertionFactory;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public String getAccessToken(SvfUserContext user) {
        String cacheKey = user.userId() + "\u0000" + user.userName();
        SvfAccessToken cached = tokenCache.get(cacheKey);
        if (cached != null && !cached.isExpiringSoon()) {
            return cached.token();
        }
        synchronized (tokenCache) {
            cached = tokenCache.get(cacheKey);
            if (cached != null && !cached.isExpiringSoon()) {
                return cached.token();
            }
            SvfAccessToken refreshed = retrieveAccessToken(user);
            tokenCache.put(cacheKey, refreshed);
            return refreshed.token();
        }
    }

    private SvfAccessToken retrieveAccessToken(SvfUserContext user) {
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
            return new SvfAccessToken(response.get("token").toString(), Long.parseLong(response.get("expiration").toString()));
        } catch (RestClientResponseException ex) {
            throw new SvfCloudException("SVF Cloud token request failed: " + ex.getStatusCode(), ex);
        }
    }

    private record SvfAccessToken(String token, long expirationEpochSeconds) {
        boolean isExpiringSoon() {
            return Instant.now().getEpochSecond() >= expirationEpochSeconds - EXPIRATION_BUFFER_SECONDS;
        }
    }
}
