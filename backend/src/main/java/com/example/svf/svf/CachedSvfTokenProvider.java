package com.example.svf.svf;

import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfUserContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * ユーザー単位で SVF Cloud アクセストークンをキャッシュする {@link SvfTokenProvider} 実装。
 *
 * <p>SVF Cloud のトークンは実行ユーザーごとに発行されるため、キャッシュキーは
 * userId と userName の組み合わせです。キャッシュされたトークンが期限に近づいたら
 * 自動的に再取得します。</p>
 *
 * <p>キャッシュは Caffeine による上限・期限付きキャッシュで、
 * 使われなくなったユーザーのトークンがメモリに蓄積し続けることを防ぎます：</p>
 * <ul>
 *   <li>maximumSize: エントリ数の上限（超過時は使用頻度の低いものから退去）</li>
 *   <li>expireAfterWrite: 書き込みからトークン有効期限経過後は未使用エントリを自動削除</li>
 * </ul>
 *
 * <p>トークン取得は OAuth 2.0 の JWT bearer grant 方式で行います：</p>
 * <ol>
 *   <li>{@link SvfJwtAssertionFactory} で実行ユーザーの JWT assertion を生成</li>
 *   <li>clientId / secret の Basic 認証ヘッダー付きで {@code POST /oauth2/token} を呼び出し</li>
 *   <li>レスポンスの token / expiration をキャッシュに保存</li>
 * </ol>
 */
@Component
public class CachedSvfTokenProvider implements SvfTokenProvider {
    /** トークンの期限切れ判定に使うバッファ秒数。期限のこの秒数前になったら再取得する。 */
    private static final long EXPIRATION_BUFFER_SECONDS = 60;
    /** OAuth 2.0 JWT bearer grant の grant_type 値（RFC 7523）。 */
    private static final String JWT_BEARER_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    /** キャッシュエントリ数の上限。超過時は使用頻度の低いエントリから退去される。 */
    private static final long MAX_CACHE_SIZE = 1000;
    /**
     * エポックミリ秒とエポック秒の境界値。
     * この値以上ならエポックミリ秒（13桁の例: 1442046911540）、
     * 未満ならエポック秒（10桁の例: 1442046911）と判定できる。
     */
    private static final long EPOCH_MILLIS_THRESHOLD = 100_000_000_000L;

    private final SvfCloudProperties properties;
    private final SvfJwtAssertionFactory assertionFactory;
    private final RestClient restClient;
    /** 期限判定に使用する時刻源（テストでは固定時刻に差し替え可能）。 */
    private final Clock clock;
    /** ユーザーごとのトークンキャッシュ。キーは "userId\u0000userName"。 */
    private final Cache<String, SvfAccessToken> tokenCache;

    /** 本番用: 時刻源はシステム時計（UTC）。コンストラクタが複数あるため DI 対象を明示する。 */
    @Autowired
    public CachedSvfTokenProvider(
            SvfCloudProperties properties,
            SvfJwtAssertionFactory assertionFactory,
            RestClient.Builder builder) {
        this(properties, assertionFactory, builder, Clock.systemUTC());
    }

    /** テスト用: 時刻源を差し替えるコンストラクタ。 */
    CachedSvfTokenProvider(
            SvfCloudProperties properties,
            SvfJwtAssertionFactory assertionFactory,
            RestClient.Builder builder,
            Clock clock) {
        this.properties = properties;
        this.assertionFactory = assertionFactory;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.clock = clock;
        this.tokenCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                // トークン有効期限を過ぎた未使用エントリは自動的に退去させる
                .expireAfterWrite(Duration.ofSeconds(properties.resolvedTokenExpirationSeconds()))
                .build();
    }

    /**
     * 指定ユーザーのアクセストークンを返します。
     *
     * <p>キャッシュに有効なトークンがあればそれを返し、なければ（または
     * 期限が近ければ）SVF Cloud に要求して更新します。{@code Cache.get(key, loader)}
     * によるキー単位のアトミックロードのため、同じユーザーの同時要求時にトークン取得は
     * 1 回にまとまり、かつ別ユーザーの取得は互いにブロックしません。
     * ロード中に例外が発生した場合はエントリはキャッシュに残りません。
     * 上限・期限切れエントリの退去は Caffeine が自動で行います。</p>
     *
     * @param user アクセストークンを要求する実行ユーザー
     * @return 有効なアクセストークン
     */
    @Override
    public String getAccessToken(SvfUserContext user) {
        // userId と userName を区切り文字付きで連結してキャッシュキーを作る
        String cacheKey = user.getUserId() + "\u0000" + user.getUserName();
        // まずロックなしで高速パス（キャッシュヒット）を確認
        SvfAccessToken cached = tokenCache.getIfPresent(cacheKey);
        if (cached != null && !cached.isExpiringSoon(clock)) {
            return cached.getToken();
        }
        // キャッシュミスまたは期限間近の場合のみキー単位のアトミックロードで取得する。
        // 期限間近エントリは先に削除してからロードする。削除せずにロードすると、
        // Cache.get は既存エントリをそのまま返して再取得が起きないため。
        // 削除→ロードの窓期間に並行呼び出しが再取得しても、得られるのは同じく新しいトークンであり、
        // ロード関数はキー単位で直列化されるため実効的なネットワーク呼び出しは最小限に収まる。
        tokenCache.invalidate(cacheKey);
        SvfAccessToken refreshed = tokenCache.get(cacheKey, key -> retrieveAccessToken(user));
        return refreshed.getToken();
    }

    /**
     * SVF Cloud のトークンエンドポイント（{@code POST /oauth2/token}）から
     * アクセストークンを新規取得します。
     *
     * @param user JWT assertion に含める実行ユーザー
     * @return 取得したトークンと有効期限のペア
     * @throws SvfCloudException 応答が不正、または HTTP エラーの場合
     */
    private SvfAccessToken retrieveAccessToken(SvfUserContext user) {
        // clientId:secret を Base64 エンコードした Basic 認証ヘッダーを構築
        String basic = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.secret()).getBytes(StandardCharsets.UTF_8));
        // フォーム本文: grant_type と実行ユーザーの JWT assertion
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
            // 応答には token と expiration の両方が必須
            if (response == null || response.get("token") == null || response.get("expiration") == null) {
                throw new SvfCloudException("SVF Cloud token response is invalid");
            }
            return new SvfAccessToken(
                    response.get("token").toString(),
                    parseExpiration(response.get("expiration").toString()));
        } catch (RestClientResponseException ex) {
            throw new SvfCloudException("SVF Cloud token request failed: " + ex.getStatusCode(), ex);
        }
    }

    /**
     * トークン応答の expiration 値をエポックミリ秒に変換します。
     *
     * <p>SVF Cloud の公式レスポンス例（例: {@code 1442046911540}）はエポックミリ秒形式です。
     * 一方、公式説明文中には秒数と読める記載もあり単位が確定していないため、
     * ここでは桁数に基づく境界判定で両形式を安全に受け入れます：</p>
     * <ul>
     *   <li>{@value #EPOCH_MILLIS_THRESHOLD} 以上 → エポックミリ秒（2001年以降を表すには桁数が足りない秒値と区別できる）</li>
     *   <li>未満 → エポック秒 → ミリ秒に変換</li>
     * </ul>
     *
     * <p>なお実環境の単位が判明した場合は、この判定を固定の単位解釈に置き換えること。</p>
     */
    private Instant parseExpiration(String expirationValue) {
        long value = Long.parseLong(expirationValue);
        if (value >= EPOCH_MILLIS_THRESHOLD) {
            return Instant.ofEpochMilli(value);
        }
        return Instant.ofEpochSecond(value);
    }

    /** テスト用: 指定ユーザーのキャッシュ済みトークンの有効期限を返します（未キャッシュなら null）。 */
    Instant cachedExpiration(SvfUserContext user) {
        SvfAccessToken cached = tokenCache.getIfPresent(user.getUserId() + "\u0000" + user.getUserName());
        return cached == null ? null : cached.getExpiration();
    }

    /**
     * キャッシュ用のトークン情報。
     * 不変クラス: アクセストークン文字列と有効期限を保持する。
     */
    private static final class SvfAccessToken {
        private final String token;
        private final Instant expiration;

        SvfAccessToken(String token, Instant expiration) {
            this.token = token;
            this.expiration = expiration;
        }

        /** アクセストークン文字列を返します。 */
        String getToken() {
            return token;
        }

        /** 有効期限を返します。 */
        Instant getExpiration() {
            return expiration;
        }

        /**
         * トークンが期限間近（バッファ秒数以内）かどうかを判定します。
         */
        boolean isExpiringSoon(Clock clock) {
            return !Instant.now(clock).isBefore(expiration.minusSeconds(EXPIRATION_BUFFER_SECONDS));
        }
    }
}
