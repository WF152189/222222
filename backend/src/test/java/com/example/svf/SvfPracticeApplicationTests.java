package com.example.svf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18080",
                "svf.cloud.base-url=http://localhost:18080/svf-mock"
        })
class SvfPracticeApplicationTests {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void reportFlowReturnsPdfFromMockSvfCloud() {
        String token = login();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<List> reports = restTemplate.exchange(
                "/api/reports",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class);
        assertThat(reports.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(reports.getBody()).hasSize(14);

        ResponseEntity<byte[]> pdf = restTemplate.exchange(
                "/api/reports/RPT-001/pdf",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class);
        assertThat(pdf.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(pdf.getHeaders().getContentType().toString()).contains("application/pdf");
        assertThat(pdf.getBody()).startsWith("%PDF".getBytes());
    }

    private String login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(Map.of("username", "zhangsan", "password", "password"), headers),
                Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsKey("token");
        return response.getBody().get("token").toString();
    }
}
