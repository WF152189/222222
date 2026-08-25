package com.example.svf.svf;

import com.example.svf.svf.model.SvfUserContext;

/**
 * SVF Cloud 認証用 JWT assertion の生成インターフェース。
 *
 * <p>SVF Cloud は OAuth 2.0 の JWT bearer grant 方式を採用しており、
 * アクセストークン取得時にクライアントは RSA 署名済みの JWT assertion を
 * 送信して自らの身元と実行ユーザーを証明します。</p>
 *
 * @see DefaultSvfJwtAssertionFactory
 */
public interface SvfJwtAssertionFactory {
    /**
     * 指定ユーザー向けの JWT assertion を生成します。
     *
     * <p>assertion のクレームには実行ユーザーの userId（sub）と
     * userName が含まれ、SVF Cloud の実行履歴にこのユーザーが記録されます。</p>
     *
     * @param user 実行ユーザー情報
     * @return 署名済み JWT 文字列（ヘッダー.ペイロード.署名）
     * @throws SvfCloudException 秘密鍵の読み込みや署名に失敗した場合
     */
    String createAssertion(SvfUserContext user);
}
