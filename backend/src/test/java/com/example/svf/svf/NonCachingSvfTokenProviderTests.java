package com.example.svf.svf;

import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NonCachingSvfTokenProviderTests {
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");
    private static final SvfUserContext USER = new SvfUserContext("user@example.com", "Test User");

    private MockRestServiceServer mockServer;
    private NonCachingSvfTokenProvider provider;

    @BeforeEach
    void setUp() {
        SvfCloudProperties properties = new SvfCloudProperties(
                "real", "http://svf.test", "client-id", "client-secret",
                null, null, null, 300L, 3600L,
                0L, false, false, 10L);
        SvfJwtAssertionFactory assertionFactory = user -> "test-assertion";
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        provider = new NonCachingSvfTokenProvider(
                properties, assertionFactory, builder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void requestsNewTokenOnEveryCall() {
        String basic = Base64.getEncoder().encodeToString(
                "client-id:client-secret".getBytes(StandardCharsets.UTF_8));
        long expiration = NOW.plusSeconds(3600).toEpochMilli();

        mockServer.expect(twice(), requestTo("http://svf.test/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + basic))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"),
                        org.hamcrest.Matchers.containsString("assertion=test-assertion"))))
                .andRespond(withSuccess(
                        "{\"token\":\"new-token\",\"expiration\":" + expiration + "}",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.getAccessToken(USER)).isEqualTo("new-token");
        assertThat(provider.getAccessToken(USER)).isEqualTo("new-token");
        mockServer.verify();
    }

    @Test
    void rejectsExpiredToken() {
        mockServer.expect(requestTo("http://svf.test/oauth2/token"))
                .andRespond(withSuccess(
                        "{\"token\":\"expired-token\",\"expiration\":" + NOW.minusSeconds(1).toEpochMilli() + "}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getAccessToken(USER))
                .isInstanceOf(SvfCloudException.class)
                .hasMessageContaining("expired");
        mockServer.verify();
    }

    @Test
    void rejectsInvalidResponse() {
        mockServer.expect(requestTo("http://svf.test/oauth2/token"))
                .andRespond(withSuccess("{\"token\":\"\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getAccessToken(USER))
                .isInstanceOf(SvfCloudException.class)
                .hasMessageContaining("invalid");
        mockServer.verify();
    }
}
