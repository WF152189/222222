package com.example.svf.svf;

import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link CachedSvfTokenProvider} の単体テスト。
 *
 * <p>{@link MockRestServiceServer} でトークンエンドポイントを再現し、
 * 固定時刻の {@link Clock} を注入することで expiration の単位解釈
 * （13桁のエポックミリ秒 / 10桁のエポック秒）、キャッシュ再利用、
 * 期限切れ時の再取得、取得失敗時の挙動を確認します。</p>
 */
class CachedSvfTokenProviderTests {
    /** テストの基準時刻。この時刻を元に expiration を組み立てる。 */
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final SvfUserContext USER = new SvfUserContext("user-1", "テスト太郎");

    private MockRestServiceServer mockServer;
    private CachedSvfTokenProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        SvfCloudProperties properties = new SvfCloudProperties(
                "mock", "http://svf.test", "client", "secret", "user-1", "テスト太郎",
                null, null, 3600L, 0L, false, false, 10L);
        SvfJwtAssertionFactory assertionFactory = user -> "test-assertion";
        provider = new CachedSvfTokenProvider(properties, assertionFactory, builder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * 公式レスポンス例のような13桁の値はエポックミリ秒として解釈され、
     * 有効なトークンは2回目の呼び出しでキャッシュから返されること（再取得なし）。
     */
    @Test
    void parsesThirteenDigitExpirationAsEpochMillisAndCachesToken() {
        long expirationMillis = NOW.plusSeconds(7200).toEpochMilli();
        expectTokenIssue("token-1", expirationMillis);

        assertThat(provider.getAccessToken(USER)).isEqualTo("token-1");
        assertThat(provider.getAccessToken(USER)).isEqualTo("token-1");

        // ミリ秒値がそのまま正しく解釈されていること（誤って秒扱いしていないこと）
        assertThat(provider.cachedExpiration(USER)).isEqualTo(Instant.ofEpochMilli(expirationMillis));
        // トークン取得は1回だけであること
        mockServer.verify();
    }

    /** 10桁の値はエポック秒として解釈されること。 */
    @Test
    void parsesTenDigitExpirationAsEpochSeconds() {
        long expirationSeconds = NOW.plusSeconds(7200).getEpochSecond();
        expectTokenIssue("token-1", expirationSeconds);

        assertThat(provider.getAccessToken(USER)).isEqualTo("token-1");
        assertThat(provider.cachedExpiration(USER)).isEqualTo(Instant.ofEpochSecond(expirationSeconds));
        mockServer.verify();
    }

    /**
     * トークンが既に失効している場合は、呼び出しのたびに再取得されること。
     */
    @Test
    void refreshesExpiredToken() {
        long expiredMillis = NOW.minusSeconds(120).toEpochMilli();
        expectTokenIssue("token-old", expiredMillis);
        expectTokenIssue("token-new", NOW.plusSeconds(7200).toEpochMilli());

        assertThat(provider.getAccessToken(USER)).isEqualTo("token-old");
        assertThat(provider.getAccessToken(USER)).isEqualTo("token-new");
        mockServer.verify();
    }

    /**
     * トークンが期限間近（バッファ60秒以内）の場合も再取得されること。
     */
    @Test
    void refreshesTokenThatIsExpiringSoon() {
        expectTokenIssue("token-soon", NOW.plusSeconds(30).toEpochMilli());
        expectTokenIssue("token-fresh", NOW.plusSeconds(7200).toEpochMilli());

        assertThat(provider.getAccessToken(USER)).isEqualTo("token-soon");
        assertThat(provider.getAccessToken(USER)).isEqualTo("token-fresh");
        mockServer.verify();
    }

    /**
     * トークン取得に失敗した場合は例外が伝播し、壊れたエントリがキャッシュに残らないこと。
     */
    @Test
    void doesNotCacheWhenTokenRequestFails() {
        mockServer.expect(method(HttpMethod.POST))
                .andExpect(requestTo("http://svf.test/oauth2/token"))
                .andRespond(withSuccess("{\"token\":\"token-bad\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getAccessToken(USER))
                .isInstanceOf(SvfCloudException.class)
                .hasMessageContaining("invalid");
        assertThat(provider.cachedExpiration(USER)).isNull();
        mockServer.verify();
    }

    /** トークン発行応答（1回分）の期待を登録します。 */
    private void expectTokenIssue(String token, long expiration) {
        mockServer.expect(method(HttpMethod.POST))
                .andExpect(requestTo("http://svf.test/oauth2/token"))
                .andRespond(withSuccess(
                        "{\"token\":\"" + token + "\",\"expiration\":" + expiration + "}",
                        MediaType.APPLICATION_JSON));
    }
}
