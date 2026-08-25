package com.example.svf.svf;

/**
 * SVF Cloud 関連のエラーを表す実行時例外。
 *
 * <p>SVF Cloud WebAPI との通信失敗（認証エラー、HTTP エラー、タイムアウト、
 * 応答形式不正など）はすべてこの例外に統一して投げられます。
 * 業務システム側は HTTP ステータスや RestClient の例外を直接扱う必要がありません。</p>
 */
public class SvfCloudException extends RuntimeException {
    /**
     * エラーメッセージのみを持つ例外を生成します。
     *
     * @param message エラー内容の説明
     */
    public SvfCloudException(String message) {
        super(message);
    }

    /**
     * 原因となった例外を保持する例外を生成します。
     *
     * @param message エラー内容の説明
     * @param cause   元となった例外（RestClientResponseException など）
     */
    public SvfCloudException(String message, Throwable cause) {
        super(message, cause);
    }
}
