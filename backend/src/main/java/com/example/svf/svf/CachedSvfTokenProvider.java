package com.example.svf.svf;

import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfUserContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
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

    private final SvfCloudProperties properties;
    private final SvfJwtAssertionFactory assertionFactory;
    private final RestClient restClient;
    /** ユーザーごとのトークンキャッシュ。キーは "userId\u0000userName"。 */
    private final Cache<String, SvfAccessToken> tokenCache;

    public CachedSvfTokenProvider(
            SvfCloudProperties properties,
            SvfJwtAssertionFactory assertionFactory,
            RestClient.Builder builder) {
        this.properties = properties;
        this.assertionFactory = assertionFactory;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
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
     * 期限が近ければ）SVF Cloud に要求して更新します。二重チェックロックで
     * 同じユーザーの同時要求時にトークン取得が重複しないようにしています。
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
        if (cached != null && !cached.isExpiringSoon()) {
            return cached.getToken();
        }
        // キャッシュミスまたは期限間近の場合のみ同期ブロックに入って取得する
        synchronized (tokenCache) {
            // 二重チェック: 待っている間に他スレッドが更新した可能性があるため再確認
            cached = tokenCache.getIfPresent(cacheKey);
            if (cached != null && !cached.isExpiringSoon()) {
                return cached.getToken();
            }
            SvfAccessToken refreshed = retrieveAccessToken(user);
            tokenCache.put(cacheKey, refreshed);
            return refreshed.getToken();
        }
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
            return new SvfAccessToken(response.get("token").toString(), Long.parseLong(response.get("expiration").toString()));
        } catch (RestClientResponseException ex) {
            throw new SvfCloudException("SVF Cloud token request failed: " + ex.getStatusCode(), ex);
        }
    }

    /**
     * キャッシュ用のトークン情報。
     * 不変クラス: アクセストークン文字列と有効期限（エポック秒）を保持する。
     */
    private static final class SvfAccessToken {
        private final String token;
        private final long expirationEpochSeconds;

        SvfAccessToken(String token, long expirationEpochSeconds) {
            this.token = token;
            this.expirationEpochSeconds = expirationEpochSeconds;
        }

        /** アクセストークン文字列を返します。 */
        String getToken() {
            return token;
        }

        /** 有効期限（エポック秒）を返します。 */
        long getExpirationEpochSeconds() {
            return expirationEpochSeconds;
        }

        /**
         * トークンが期限間近（バッファ秒数以内）かどうかを判定します。
         */
        boolean isExpiringSoon() {
            return Instant.now().getEpochSecond() >= expirationEpochSeconds - EXPIRATION_BUFFER_SECONDS;
        }
    }
}
