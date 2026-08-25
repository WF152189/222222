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

/**
 * SVF Cloud 認証用 JWT assertion を生成する既定実装。
 *
 * <p>assertion は {@code ヘッダー.ペイロード.署名} の3部構成で、
 * SVF Cloud が要求する RS256（SHA256withRSA）方式で署名します。</p>
 *
 * <p>実行モードによる違い：</p>
 * <ul>
 *   <li><b>real モード</b>: 設定された PEM 秘密鍵（PKCS#8）で実際に RS256 署名する。
 *       SVF Cloud 本番環境はこの署名を検証する。</li>
 *   <li><b>mock モード</b>: 署名部に固定文字列 "mock-signature" を入れる。
 *       内蔵 mock サーバーは署名を検証しないため、鍵なしで動作確認できる。</li>
 * </ul>
 */
@Component
public class DefaultSvfJwtAssertionFactory implements SvfJwtAssertionFactory {
    private final SvfCloudProperties properties;

    public DefaultSvfJwtAssertionFactory(SvfCloudProperties properties) {
        this.properties = properties;
    }

    /**
     * 実行ユーザー向けの JWT assertion を組み立てます。
     *
     * <p>処理手順:</p>
     * <ol>
     *   <li>ユーザーが未指定なら設定ファイルのデフォルトユーザーにフォールバック</li>
     *   <li>ヘッダー（{"alg":"RS256"}）とクレームをそれぞれ Base64URL エンコード</li>
     *   <li>real モードなら RSA 秘密鍵で署名、mock モードならダミー署名</li>
     * </ol>
     *
     * @param user 実行ユーザー情報（null または空ならデフォルトユーザーを使用）
     * @return 署名済み JWT 文字列
     */
    @Override
    public String createAssertion(SvfUserContext user) {
        SvfUserContext resolvedUser = resolveUser(user);
        // JWT ヘッダー: 署名アルゴリズムは RS256（SVF Cloud の要求仕様）
        String header = base64Url("{\"alg\":\"RS256\"}");
        String payload = base64Url(createClaims(resolvedUser));
        // 署名対象は「ヘッダー.ペイロード」
        String signingInput = header + "." + payload;
        if (properties.isRealMode()) {
            return signingInput + "." + sign(signingInput);
        }
        // mock モード: mock サーバーは署名を検証しないため固定値で代用
        return signingInput + ".mock-signature";
    }

    /**
     * JWT のペイロード（クレーム部）の JSON 文字列を組み立てます。
     *
     * <ul>
     *   <li>iss: clientId（SVF Cloud に登録したアプリケーションID）</li>
     *   <li>sub: 実行ユーザーID（SVF Cloud の実行履歴に記録される主体）</li>
     *   <li>exp: 有効期限（エポック秒。文字列形式で送信）</li>
     *   <li>userName: 実行ユーザー表示名</li>
     *   <li>timeZone / locale: SVF Cloud が出力に使う地域設定</li>
     * </ul>
     */
    private String createClaims(SvfUserContext user) {
        long exp = Instant.now().plusSeconds(properties.resolvedJwtExpirationSeconds()).getEpochSecond();
        return "{\"iss\":\"" + escapeJson(properties.clientId())
                + "\",\"sub\":\"" + escapeJson(user.getUserId())
                + "\",\"exp\":\"" + exp
                + "\",\"userName\":\"" + escapeJson(user.getUserName())
                + "\",\"timeZone\":\"Asia/Tokyo\",\"locale\":\"ja\"}";
    }

    /**
     * ユーザー未指定の場合は設定ファイル（svf.cloud.user-id / user-name）の
     * デフォルトユーザーへフォールバックします。
     */
    private SvfUserContext resolveUser(SvfUserContext user) {
        if (user != null && !user.isBlank()) {
            return user;
        }
        return new SvfUserContext(properties.userId(), properties.userName());
    }

    /**
     * 「ヘッダー.ペイロード」を RSA 秘密鍵（SHA256withRSA）で署名し、
     * Base64URL（パディングなし）文字列で返します。
     *
     * @throws SvfCloudException 鍵の読み込みまたは署名処理に失敗した場合
     */
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

    /**
     * 設定されたパスから PEM 形式（PKCS#8）の RSA 秘密鍵を読み込みます。
     * PEM のヘッダー/フッターと改行を取り除き、Base64 デコードして秘密鍵を復元します。
     *
     * @throws SvfCloudException キーパス未設定（real モードでは必須）や読み込み失敗の場合
     */
    private PrivateKey loadPrivateKey() throws Exception {
        String path = properties.jwtPrivateKeyPath();
        if (path == null || path.isBlank()) {
            throw new SvfCloudException("svf.cloud.jwt-private-key-path is required in real mode");
        }
        String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        // PEM のマーカー行と空白を除去して Base64 本体だけを取り出す
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    /**
     * 文字列を Base64URL（パディングなし）にエンコードします。JWT 各部の標準形式です。
     */
    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * JSON 文字列値として安全に埋め込めるよう、バックスラッシュと二重引用符をエスケープします。
     */
    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
