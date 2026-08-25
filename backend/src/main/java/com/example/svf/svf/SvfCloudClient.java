package com.example.svf.svf;

import com.example.svf.svf.model.SvfRenderRequest;
import com.example.svf.svf.model.SvfRenderResult;

/**
 * SVF Cloud クライアントの公開インターフェース。
 *
 * <p>業務システム（帳票管理側）は、このインターフェースを通じて
 * 「帳票テンプレート＋データ」を渡し、「PDF」を受け取るだけです。
 * 認証、JWT 生成、multipart 組み立て、状態ポーリング、エラー変換といった
 * SVF Cloud プロトコルの低レベル詳細は実装クラス内部に完全に隠蔽されます。</p>
 *
 * @see SvfCloudWebApiClient
 */
public interface SvfCloudClient {
    /**
     * SVF Cloud に PDF レンダリングを要求し、生成された PDF を返します。
     *
     * <p>内部的には以下の手順を実行します：</p>
     * <ol>
     *   <li>実行ユーザーのアクセストークンを取得</li>
     *   <li>印刷ジョブを登録（{@code POST /v1/artifacts}）</li>
     *   <li>ジョブ完了までポーリング（オプション）</li>
     *   <li>生成物をダウンロード（{@code GET /v1/artifacts/{id}}）</li>
     * </ol>
     *
     * @param request レンダリング要求（生成物名・フォーム・CSV データ・実行ユーザー・オプション）
     * @return PDF バイナリと SVF Cloud メタ情報を含む結果
     * @throws SvfCloudException 要求不正・認証失敗・ジョブ失敗・タイムアウトなどの SVF Cloud 関連エラー
     */
    SvfRenderResult renderPdf(SvfRenderRequest request);
}
