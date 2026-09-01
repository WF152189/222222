package com.example.svf.svf;

import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfRenderOptions;
import com.example.svf.svf.model.SvfRenderRequest;
import com.example.svf.svf.model.SvfRenderResult;
import com.example.svf.svf.model.SvfUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link SvfCloudWebApiClient} の単体テスト。
 *
 * <p>{@link MockRestServiceServer} で SVF Cloud の HTTP 応答を再現し、
 * 特に印刷ステータスのポーリング判定（中間状態の継続・終端失敗状態の即時エラー）と
 * Location / 生成物の検証をネットワークなしで確認します。</p>
 */
class SvfCloudWebApiClientTests {
    /** ジョブ登録成功時に SVF Cloud が Location ヘッダーで返すダウンロード URI。 */
    private static final String LOCATION =
            "http://svf.test/v1/artifacts/art-1?action=action-1&ticket=ticket-1";

    private MockRestServiceServer mockServer;
    private SvfCloudWebApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        // RestClient にモックサーバーを束ねてからクライアントを構築する
        mockServer = MockRestServiceServer.bindTo(builder).build();
        SvfCloudProperties properties = new SvfCloudProperties(
                "mock", "http://svf.test", "client", "secret", "user-1", "テスト太郎",
                null, null, 3600L, 0L, false, false, 10L);
        SvfTokenProvider tokenProvider = user -> "test-token";
        client = new SvfCloudWebApiClient(tokenProvider, builder, properties);
    }

    /**
     * 中間状態 11（準備中）→ 21（作成中）→ 22（作成完了）を経て
     * 2（完了）に到達するまでポーリングが継続し、最終的に PDF を受け取れること。
     */
    @Test
    void pollsThroughIntermediateStatesUntilCompleted() {
        expectJobRegistration();
        mockServer.expect(requestTo("http://svf.test/v1/actions/action-1"))
                .andRespond(withSuccess("{\"state\":11}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://svf.test/v1/actions/action-1"))
                .andRespond(withSuccess("{\"state\":21}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://svf.test/v1/actions/action-1"))
                .andRespond(withSuccess("{\"state\":22}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://svf.test/v1/actions/action-1"))
                .andRespond(withSuccess("{\"state\":2}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(LOCATION))
                .andRespond(withSuccess("%PDF-1.4 mock".getBytes(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_OCTET_STREAM));

        SvfRenderResult result = client.renderPdf(request());

        assertThat(result.getPdf()).isEqualTo("%PDF-1.4 mock".getBytes(StandardCharsets.UTF_8));
        assertThat(result.getArtifactId()).isEqualTo("art-1");
        assertThat(result.getActionId()).isEqualTo("action-1");
        mockServer.verify();
    }

    /** 終端失敗状態（3 / 5 / 6）はポーリングを打ち切って即座に例外化されること。 */
    @Test
    void failsImmediatelyOnTerminalFailureStates() {
        for (int failedState : new int[]{3, 5, 6}) {
            setUp();
            expectJobRegistration();
            mockServer.expect(requestTo("http://svf.test/v1/actions/action-1"))
                    .andRespond(withSuccess("{\"state\":" + failedState + "}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.renderPdf(request()))
                    .isInstanceOf(SvfCloudException.class)
                    .hasMessageContaining("state=" + failedState);
            mockServer.verify();
        }
    }

    /** ジョブ登録応答に Location ヘッダーがない場合は応答不正として拒否すること。 */
    @Test
    void rejectsMissingLocationHeader() {
        mockServer.expect(method(HttpMethod.POST))
                .andExpect(requestTo("http://svf.test/v1/artifacts"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThatThrownBy(() -> client.renderPdf(request()))
                .isInstanceOf(SvfCloudException.class)
                .hasMessageContaining("Location");
        mockServer.verify();
    }

    /** 完了後にダウンロードした生成物が空の場合は拒否すること。 */
    @Test
    void rejectsEmptyArtifact() {
        expectJobRegistration();
        mockServer.expect(requestTo("http://svf.test/v1/actions/action-1"))
                .andRespond(withSuccess("{\"state\":2}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(LOCATION))
                .andRespond(withSuccess(new byte[0], MediaType.APPLICATION_OCTET_STREAM));

        assertThatThrownBy(() -> client.renderPdf(request()))
                .isInstanceOf(SvfCloudException.class)
                .hasMessageContaining("empty artifact");
        mockServer.verify();
    }

    /** ジョブ登録（202 + Location）の共通期待を登録します。 */
    private void expectJobRegistration() {
        mockServer.expect(method(HttpMethod.POST))
                .andExpect(requestTo("http://svf.test/v1/artifacts"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .location(URI.create(LOCATION)));
    }

    /** テスト用のレンダリング要求を生成します。 */
    private SvfRenderRequest request() {
        return new SvfRenderRequest(
                "見積書",
                "form/Practice/Report.xml",
                "a,b\n1,2\n",
                new SvfUserContext("user-1", "テスト太郎"),
                SvfRenderOptions.pdfCsvDefault());
    }
}
