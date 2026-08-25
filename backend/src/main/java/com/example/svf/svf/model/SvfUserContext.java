package com.example.svf.svf.model;

import java.util.Objects;

/**
 * 現在の業務ユーザー情報を表すモデル。
 *
 * <p>SVF Cloud では JWT assertion の {@code sub} / {@code userName} クレームに
 * 実行ユーザーの情報が書き込まれ、SVF Cloud 側の実行履歴（Activity History）や
 * 生成物メタデータにこのユーザー名が表示されます。</p>
 *
 * <p>また、アクセストークンのキャッシュキーとしても利用されるため、
 * ユーザーごとに異なるトークンが発行・キャッシュされます。</p>
 *
 * <p>不変クラス: 生成後にフィールドは変更されません。</p>
 */
public final class SvfUserContext {
    /** ユーザーID（SVF Cloud の sub クレームに設定される値。例: zhangsan@example.com） */
    private final String userId;
    /** ユーザー表示名（SVF Cloud の userName クレームに設定される値。例: 张三） */
    private final String userName;

    public SvfUserContext(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

    /** ユーザーID を返します。 */
    public String getUserId() {
        return userId;
    }

    /** ユーザー表示名を返します。 */
    public String getUserName() {
        return userName;
    }

    /**
     * ユーザー情報が未設定（null または空文字）かどうかを判定します。
     *
     * @return userId または userName のいずれかが空の場合は true
     */
    public boolean isBlank() {
        return isBlank(userId) || isBlank(userName);
    }

    /**
     * 文字列が null または空文字かどうかを判定する内部ユーティリティ。
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SvfUserContext other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(userName, other.userName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, userName);
    }

    @Override
    public String toString() {
        return "SvfUserContext[userId=" + userId + ", userName=" + userName + "]";
    }
}
