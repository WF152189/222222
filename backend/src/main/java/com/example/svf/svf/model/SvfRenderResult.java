package com.example.svf.svf.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * SVF Cloud レンダリングの実行結果を表すモデル。
 *
 * <p>PDF バイナリ本体に加えて、SVF Cloud 側で追跡可能なメタ情報
 * （artifactId / actionId）をまとめて返します。</p>
 *
 * <p>不変クラス: 生成後にフィールドは変更されません。
 * PDF バイナリはコンストラクタと {@link #getPdf()} の両方で防御的コピーを行い、
 * 外部からの配列書き換えによる内容・ハッシュ値の変動を防ぎます。</p>
 */
public final class SvfRenderResult {
    /**
     * 生成された PDF のバイナリ内容。{@code /v1/artifacts/{artifactId}} から
     * ダウンロードしたデータ本体。
     */
    private final byte[] pdf;
    /**
     * SVF Cloud が割り当てた生成物の一意 ID。
     * 生成物の再取得や障害調査時の特定に使用可能。
     */
    private final String artifactId;
    /**
     * 印刷ジョブの実行記録 ID。{@code GET /v1/actions/{actionId}} による
     * 状態ポーリングに使用。SVF Cloud の実行履歴（Activity History）に対応。
     */
    private final String actionId;

    public SvfRenderResult(byte[] pdf, String artifactId, String actionId) {
        // 防御的コピー: 呼び出し元が保持する配列の事後変更が内容に反映されないようにする
        this.pdf = pdf == null ? null : pdf.clone();
        this.artifactId = artifactId;
        this.actionId = actionId;
    }

    /** 生成された PDF のバイナリを返します（防御的コピー済み）。 */
    public byte[] getPdf() {
        // 防御的コピー: 返却先による配列書き換えが内部状態に影響しないようにする
        return pdf == null ? null : pdf.clone();
    }

    /** SVF Cloud が割り当てた生成物 ID を返します。 */
    public String getArtifactId() {
        return artifactId;
    }

    /** 印刷ジョブの実行記録 ID を返します。 */
    public String getActionId() {
        return actionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SvfRenderResult other)) {
            return false;
        }
        // byte[] は内容比較とする（record の参照比較より厳密な等価性）
        return Arrays.equals(pdf, other.pdf)
                && Objects.equals(artifactId, other.artifactId)
                && Objects.equals(actionId, other.actionId);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(pdf);
        result = 31 * result + Objects.hashCode(artifactId);
        result = 31 * result + Objects.hashCode(actionId);
        return result;
    }

    @Override
    public String toString() {
        // PDF バイナリは巨大になり得るため、サイズのみ出力する
        return "SvfRenderResult[pdfSize=" + (pdf == null ? 0 : pdf.length)
                + ", artifactId=" + artifactId
                + ", actionId=" + actionId + "]";
    }
}
