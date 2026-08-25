package com.example.svf.svf;

import com.example.svf.svf.model.SvfUserContext;

/**
 * SVF Cloud 用アクセストークンの提供者インターフェース。
 *
 * <p>実行ユーザー（{@link SvfUserContext}）ごとにアクセストークンを取得します。
 * SVF Cloud はトークンをユーザー単位で発行するため、ユーザーごとに
 * 別々のトークンが必要になります。</p>
 *
 * @see CachedSvfTokenProvider
 */
public interface SvfTokenProvider {
    /**
     * 指定ユーザーの SVF Cloud アクセストークンを取得します。
     *
     * <p>実装はキャッシュや期限切れ判定を内部で行い、
     * 呼び出し側は常に有効なトークンを受け取れます。</p>
     *
     * @param user アクセストークンを要求する実行ユーザー
     * @return SVF Cloud WebAPI の Authorization ヘッダーに設定するアクセストークン
     * @throws SvfCloudException トークン取得に失敗した場合
     */
    String getAccessToken(SvfUserContext user);
}
