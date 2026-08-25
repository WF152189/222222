package com.example.svf.svf.model;

import java.util.Objects;

/**
 * SVF Cloud への PDF レンダリング要求を表すモデル。
 *
 * <p>業務システム（帳票管理側）が SVF client モジュールに渡す唯一の入力オブジェクトです。
 * 認証・JWT 生成・multipart 組み立て・ポーリングなどの SVF Cloud プロトコル詳細は
 * このモデルの外側（client モジュール内部）に隠蔽されます。</p>
 *
 * <p>不変クラス: 生成後にフィールドは変更されません。</p>
 */
public final class SvfRenderRequest {
    /**
     * 生成物の名前。SVF Cloud へ送信する {@code name} パラメータ。
     * 現在のプロジェクトでは「帳票名-番号」形式を使用。
     */
    private final String artifactName;
    /**
     * 使用する帳票フォームのパス。SVF Cloud へ送信する {@code defaultForm} パラメータ。
     * 例: form/Practice/Report.xml
     */
    private final String formPath;
    /**
     * 帳票に流し込む印刷データ（CSV 形式の文字列）。
     * multipart の {@code data/report.csv} パートとして送信される。
     */
    private final String csvData;
    /**
     * 実行ユーザー情報。JWT の sub/userName に設定され、
     * SVF Cloud の実行履歴や生成 PDF に表示される。
     */
    private final SvfUserContext user;
    /**
     * レンダリングオプション。null で生成した場合は PDF/CSV のデフォルト値が適用される。
     */
    private final SvfRenderOptions options;

    /**
     * コンストラクタ: options が null の場合はデフォルト値
     * （PDF プリンタ / CSV ソース / 完了待ちあり / 60秒タイムアウト）を適用します。
     */
    public SvfRenderRequest(
            String artifactName,
            String formPath,
            String csvData,
            SvfUserContext user,
            SvfRenderOptions options) {
        this.artifactName = artifactName;
        this.formPath = formPath;
        this.csvData = csvData;
        this.user = user;
        this.options = options == null ? SvfRenderOptions.pdfCsvDefault() : options;
    }

    /** 生成物の名前を返します。 */
    public String getArtifactName() {
        return artifactName;
    }

    /** 帳票フォームのパスを返します。 */
    public String getFormPath() {
        return formPath;
    }

    /** 印刷データ（CSV 文字列）を返します。 */
    public String getCsvData() {
        return csvData;
    }

    /** 実行ユーザー情報を返します。 */
    public SvfUserContext getUser() {
        return user;
    }

    /** レンダリングオプションを返します（null にはならない）。 */
    public SvfRenderOptions getOptions() {
        return options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SvfRenderRequest other)) {
            return false;
        }
        return Objects.equals(artifactName, other.artifactName)
                && Objects.equals(formPath, other.formPath)
                && Objects.equals(csvData, other.csvData)
                && Objects.equals(user, other.user)
                && Objects.equals(options, other.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactName, formPath, csvData, user, options);
    }

    @Override
    public String toString() {
        return "SvfRenderRequest[artifactName=" + artifactName
                + ", formPath=" + formPath
                + ", user=" + user
                + ", options=" + options + "]";
    }
}
