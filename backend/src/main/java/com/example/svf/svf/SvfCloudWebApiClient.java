package com.example.svf.svf;

import com.example.svf.svf.model.SvfRenderOptions;
import com.example.svf.svf.model.SvfRenderRequest;
import com.example.svf.svf.model.SvfRenderResult;
import com.example.svf.svf.model.SvfUserContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * SVF Cloud WebAPI と通信する {@link SvfCloudClient} の実装。
 *
 * <p>PDF レンダリングの全体フロー：</p>
 * <ol>
 *   <li>要求を検証（必須項目チェック）</li>
 *   <li>実行ユーザーのアクセストークンを {@link SvfTokenProvider} から取得（ユーザー単位キャッシュ）</li>
 *   <li>印刷ジョブを登録: {@code POST /v1/artifacts}（multipart/form-data）</li>
 *   <li>オプション指定時は {@code GET /v1/actions/{actionId}} で完了までポーリング</li>
 *   <li>Location ヘッダーの URI から生成物（PDF）をダウンロード</li>
 * </ol>
 *
 * <p>すべての HTTP エラーは {@link SvfCloudException} に変換されるため、
 * 業務システム側は WebAPI の詳細を意識する必要がありません。</p>
 */
@Component
public class SvfCloudWebApiClient implements SvfCloudClient {
    private final SvfTokenProvider tokenProvider;
    private final RestClient restClient;

    /**
     * @param tokenProvider アクセストークン提供者（ユーザー単位キャッシュ付き）
     * @param builder       Spring の RestClient ビルダー
     * @param properties    SVF Cloud 接続設定（ベースURLなど）
     */
    public SvfCloudWebApiClient(SvfTokenProvider tokenProvider, RestClient.Builder builder, com.example.svf.config.SvfCloudProperties properties) {
        this.tokenProvider = tokenProvider;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    /**
     * PDF レンダリングのメイン処理。ジョブ登録→（完了待ち）→ダウンロードを
     * 一括して実行し、PDF バイナリとメタ情報を返します。
     */
    @Override
    public SvfRenderResult renderPdf(SvfRenderRequest request) {
        validateRequest(request);
        SvfUserContext user = request.getUser();
        SvfRenderOptions options = request.getOptions();
        // 実行ユーザー単位のアクセストークンを取得（キャッシュされていれば再利用）
        String token = tokenProvider.getAccessToken(user);
        // 印刷ジョブを登録し、ダウンロード URI と各種 ID を解析する
        PrintJobLocation job = executePrintJob(token, request);
        // オプション指定時はジョブ完了までポーリングしてからダウンロードする
        if (options.getWaitForCompletion()) {
            waitUntilCompleted(token, job.getActionId(), options);
        }
        byte[] pdf = downloadArtifact(token, job.getDownloadUri());
        return new SvfRenderResult(pdf, job.getArtifactId(), job.getActionId());
    }

    /**
     * 印刷ジョブを SVF Cloud に登録します（{@code POST /v1/artifacts}）。
     *
     * <p>multipart/form-data で以下のパートを送信します：</p>
     * <ul>
     *   <li>name: 生成物の名前</li>
     *   <li>printer / source / defaultForm / timeout / redirect: ジョブパラメータ</li>
     *   <li>data/report.csv: 帳票に流し込む CSV データ（ファイルパート）</li>
     * </ul>
     *
     * <p>成功時は 202 Accepted または 303 See Other が返り、Location ヘッダーに
     * ダウンロード URI（artifactId / action / ticket を含む）が示されます。</p>
     *
     * @param token   アクセストークン
     * @param request レンダリング要求
     * @return ダウンロード URI（Location ヘッダーの値）
     * @throws SvfCloudException 想定外のステータス、Location 欠落、HTTP エラーの場合
     */
    private URI requestPrintJob(String token, SvfRenderRequest request) {
        SvfRenderOptions options = request.getOptions();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", request.getArtifactName());
        body.add("printer", options.resolvedPrinter());
        body.add("source", options.resolvedSource());
        body.add("defaultForm", request.getFormPath());
        body.add("timeout", String.valueOf(options.resolvedTimeoutSeconds()));
        body.add("redirect", String.valueOf(options.getRedirect()));
        // CSV データは data/report.csv というファイルパートとして添付
        body.add("data/report.csv", request.getCsvData());

        try {
            // exchange を使うのは、レスポンス本文ではなく Location ヘッダーが欲しいため
            URI location = restClient.post()
                    .uri("/v1/artifacts")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(body)
                    .exchange((httpRequest, response) -> {
                        // SVF Cloud は正常時に 202 または 303 を返す
                        if (response.getStatusCode() != HttpStatus.ACCEPTED && response.getStatusCode() != HttpStatus.SEE_OTHER) {
                            throw new SvfCloudException("SVF Cloud print job failed: " + response.getStatusCode());
                        }
                        URI responseLocation = response.getHeaders().getLocation();
                        if (responseLocation == null) {
                            throw new SvfCloudException("SVF Cloud print job response has no Location header");
                        }
                        return responseLocation;
                    });
            return location;
        } catch (RestClientResponseException ex) {
            throw new SvfCloudException("SVF Cloud print job failed: " + ex.getStatusCode(), ex);
        }
    }

    /**
     * 印刷ジョブが完了するまでポーリングします。
     *
     * <p>state の意味: 0=未処理、1=処理中、2=完了、3以上=失敗。</p>
     *
     * @param token    アクセストークン
     * @param actionId ジョブの実行記録 ID
     * @param options  タイムアウト・ポーリング間隔の元データ
     * @throws SvfCloudException ジョブ失敗またはタイムアウトの場合
     */
    private void waitUntilCompleted(String token, String actionId, SvfRenderOptions options) {
        Instant deadline = Instant.now().plusSeconds(options.resolvedTimeoutSeconds());
        while (Instant.now().isBefore(deadline)) {
            int state = retrieveActionState(token, actionId);
            if (state == 2) {
                // 完了: ダウンロード可能
                return;
            }
            if (state >= 3) {
                // 失敗系状態: すぐエラーとして報告する
                throw new SvfCloudException("SVF Cloud print job failed. actionId=" + actionId + ", state=" + state);
            }
            sleep(options.resolvedPollIntervalMillis());
        }
        throw new SvfCloudException("SVF Cloud print job timed out. actionId=" + actionId);
    }

    /**
     * 印刷ジョブの現在状態を取得します（{@code GET /v1/actions/{actionId}}）。
     *
     * @return state 値（0=未処理、1=処理中、2=完了、3以上=失敗）
     * @throws SvfCloudException 応答が不正、または HTTP エラーの場合
     */
    private int retrieveActionState(String token, String actionId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/v1/actions/{actionId}", actionId)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null || response.get("state") == null) {
                throw new SvfCloudException("SVF Cloud action status response is invalid");
            }
            return Integer.parseInt(response.get("state").toString());
        } catch (RestClientResponseException ex) {
            throw new SvfCloudException("SVF Cloud action status request failed: " + ex.getStatusCode(), ex);
        }
    }

    /**
     * Location ヘッダーで指定された URI から生成物（PDF バイナリ）をダウンロードします。
     * URI には action / ticket のクエリパラメータが含まれています。
     *
     * @param token    アクセストークン
     * @param location ダウンロード URI
     * @return PDF バイナリ
     * @throws SvfCloudException 空の応答、または HTTP エラーの場合
     */
    private byte[] downloadArtifact(String token, URI location) {
        try {
            byte[] response = restClient.get()
                    .uri(location)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(byte[].class);
            if (response == null || response.length == 0) {
                throw new SvfCloudException("SVF Cloud returned empty artifact");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw new SvfCloudException("SVF Cloud artifact download failed: " + ex.getStatusCode(), ex);
        }
    }

    /**
     * レンダリング要求の必須項目を検証します。
     * 検証失敗時は {@link SvfCloudException} を投げ、HTTP 呼び出しは行いません。
     */
    private void validateRequest(SvfRenderRequest request) {
        if (request == null) {
            throw new SvfCloudException("SVF render request is required");
        }
        if (isBlank(request.getArtifactName())) {
            throw new SvfCloudException("SVF artifactName is required");
        }
        if (isBlank(request.getFormPath())) {
            throw new SvfCloudException("SVF formPath is required");
        }
        if (isBlank(request.getCsvData())) {
            throw new SvfCloudException("SVF csvData is required");
        }
        if (request.getUser() == null || request.getUser().isBlank()) {
            throw new SvfCloudException("SVF user context is required");
        }
    }

    /**
     * 印刷ジョブを登録し、Location ヘッダーを解析してジョブ情報を構築します。
     */
    private PrintJobLocation executePrintJob(String token, SvfRenderRequest request) {
        URI location = requestPrintJob(token, request);
        return PrintJobLocation.from(location);
    }

    /**
     * ポーリング間隔のスリープ。最小 100ms を保証し、割り込み時は例外に変換します。
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(100, millis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SvfCloudException("Interrupted while waiting for SVF Cloud print job", ex);
        }
    }

    /** null または空白かどうかの簡易判定。 */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * SVF Cloud が Location ヘッダーで返すダウンロード URI を分解した内部モデル。
     *
     * <p>URI の例: {@code https://host/v1/artifacts/{artifactId}?action={actionId}&ticket={ticket}}</p>
     *
     * <p>不変クラス。フィールドの意味:</p>
     * <ul>
     *   <li>downloadUri: 生成物ダウンロード用の完全な URI</li>
     *   <li>artifactId: 生成物 ID（パスの最終要素）</li>
     *   <li>actionId: 印刷ジョブの実行記録 ID（クエリパラメータ action）</li>
     *   <li>ticket: ダウンロード用の一回限りチケット（クエリパラメータ ticket）</li>
     * </ul>
     */
    private static final class PrintJobLocation {
        private final URI downloadUri;
        private final String artifactId;
        private final String actionId;
        private final String ticket;

        PrintJobLocation(URI downloadUri, String artifactId, String actionId, String ticket) {
            this.downloadUri = downloadUri;
            this.artifactId = artifactId;
            this.actionId = actionId;
            this.ticket = ticket;
        }

        /** 生成物ダウンロード用の完全な URI を返します。 */
        URI getDownloadUri() {
            return downloadUri;
        }

        /** 生成物 ID を返します。 */
        String getArtifactId() {
            return artifactId;
        }

        /** 印刷ジョブの実行記録 ID を返します。 */
        String getActionId() {
            return actionId;
        }

        /** ダウンロード用の一回限りチケットを返します。 */
        String getTicket() {
            return ticket;
        }

        /**
         * Location ヘッダーの URI を解析します。
         * パス末尾から artifactId を、クエリから action / ticket を取り出します。
         * いずれか欠けている場合は SVF Cloud の応答不正とみなして例外を投げます。
         */
        static PrintJobLocation from(URI location) {
            String path = location.getPath();
            String artifactId = path.substring(path.lastIndexOf('/') + 1);
            String actionId = queryParam(location, "action");
            String ticket = queryParam(location, "ticket");
            if (artifactId.isBlank() || actionId == null || ticket == null) {
                throw new SvfCloudException("SVF Cloud Location header is invalid: " + location);
            }
            return new PrintJobLocation(location, artifactId, actionId, ticket);
        }

        /**
         * URI のクエリパラメータを1つ取り出します（URL デコード付き）。
         * 該当パラメータがなければ null を返します。
         */
        private static String queryParam(URI uri, String name) {
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return null;
            }
            for (String pair : query.split("&")) {
                String[] keyValue = pair.split("=", 2);
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                if (name.equals(key)) {
                    return keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                }
            }
            return null;
        }
    }
}
