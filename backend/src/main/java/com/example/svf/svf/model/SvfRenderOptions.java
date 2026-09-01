package com.example.svf.svf.model;

import java.util.Objects;

/**
 * SVF Cloud へのレンダリング要求のオプションパラメータ。
 *
 * <p>SVF Cloud WebAPI の印刷ジョブ登録時に送信するパラメータと、
 * クライアント側のポーリング動作を制御します。</p>
 *
 * <p>不変クラス: 生成後にフィールドは変更されません。</p>
 */
public final class SvfRenderOptions {
    /**
     * 印刷ジョブのタイムアウト秒数（SVF Cloud へ送信する {@code timeout} パラメータ）。
     * 0 以下の場合は {@link #resolvedTimeoutSeconds()} で 60 秒に補正される。
     */
    private final int timeoutSeconds;
    /**
     * 印刷ジョブ完了後に SVF Cloud へリダイレクトするかどうか（{@code redirect} パラメータ）。
     */
    private final boolean redirect;
    /**
     * 出力先プリンタ名（{@code printer} パラメータ）。PDF 出力なら "PDF"。
     */
    private final String printer;
    /**
     * 印刷データソースの種類（{@code source} パラメータ）。CSV データなら "CSV"。
     */
    private final String source;
    /**
     * 印刷ジョブの完了までポーリングする際の間隔（ミリ秒）。
     * クライアントは常にジョブ完了までポーリングしてからダウンロードする。
     * 0 以下の場合は 500ms に補正される。
     */
    private final long pollIntervalMillis;

    public SvfRenderOptions(
            int timeoutSeconds,
            boolean redirect,
            String printer,
            String source,
            long pollIntervalMillis) {
        this.timeoutSeconds = timeoutSeconds;
        this.redirect = redirect;
        this.printer = printer;
        this.source = source;
        this.pollIntervalMillis = pollIntervalMillis;
    }

    /**
     * PDF/CSV 出力用のデフォルトオプションを生成します。
     * タイムアウト 60 秒、リダイレクトなし、プリンタ "PDF"、ソース "CSV"、
     * ポーリング間隔 500ms。
     */
    public static SvfRenderOptions pdfCsvDefault() {
        return new SvfRenderOptions(60, false, "PDF", "CSV", 500);
    }

    /** タイムアウト秒数を返します。 */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /** リダイレクト可否を返します。 */
    public boolean getRedirect() {
        return redirect;
    }

    /** プリンタ名を返します。 */
    public String getPrinter() {
        return printer;
    }

    /** データソースを返します。 */
    public String getSource() {
        return source;
    }

    /** ポーリング間隔（ミリ秒）を返します。 */
    public long getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    /**
     * 補正後のタイムアウト秒数を返します（未設定時は 60 秒）。
     */
    public int resolvedTimeoutSeconds() {
        return timeoutSeconds <= 0 ? 60 : timeoutSeconds;
    }

    /**
     * 補正後のポーリング間隔（ミリ秒）を返します（未設定時は 500ms）。
     */
    public long resolvedPollIntervalMillis() {
        return pollIntervalMillis <= 0 ? 500 : pollIntervalMillis;
    }

    /**
     * 補正後のプリンタ名を返します（未設定時は "PDF"）。
     */
    public String resolvedPrinter() {
        return printer == null || printer.isBlank() ? "PDF" : printer;
    }

    /**
     * 補正後のデータソースを返します（未設定時は "CSV"）。
     */
    public String resolvedSource() {
        return source == null || source.isBlank() ? "CSV" : source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SvfRenderOptions other)) {
            return false;
        }
        return timeoutSeconds == other.timeoutSeconds
                && redirect == other.redirect
                && pollIntervalMillis == other.pollIntervalMillis
                && Objects.equals(printer, other.printer)
                && Objects.equals(source, other.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeoutSeconds, redirect, printer, source, pollIntervalMillis);
    }

    @Override
    public String toString() {
        return "SvfRenderOptions[timeoutSeconds=" + timeoutSeconds
                + ", redirect=" + redirect
                + ", printer=" + printer
                + ", source=" + source
                + ", pollIntervalMillis=" + pollIntervalMillis + "]";
    }
}
