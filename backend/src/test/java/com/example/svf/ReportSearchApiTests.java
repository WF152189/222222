package com.example.svf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportSearchApiTests {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void noAdditionalConditionsSearchesByAuthenticatedUser() {
        ResponseEntity<Map> response = search(Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("totalCount", 14);
        assertThat((List<?>) response.getBody().get("reports")).hasSize(14);
    }

    @Test
    void reportNumberSearchReturnsOriginalAndElevenAdditionalReports() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login());

        ResponseEntity<List> response = restTemplate.exchange(
                "/api/reports?number=Q-2026-0001",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(12);
    }

    @Test
    void validDateTimeAndBusinessIdNarrowResults() {
        ResponseEntity<Map> response = search(Map.of(
                "startDate", "20260801",
                "startTime", "1000",
                "endDate", "20260807",
                "endTime", "1800",
                "businessId", "10001"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("totalCount", 1);
        List<Map<String, Object>> reports = (List<Map<String, Object>>) response.getBody().get("reports");
        assertThat(reports.get(0)).containsEntry("reportId", "RPT-003");
    }

    @Test
    void rejectsDateWithInvalidLengthOrCharacters() {
        assertValidationError(Map.of("startDate", "202608A"),
                "RPT-VAL-001", "RPT-E-001", "startDate",
                "開始年月日は8桁の半角数字で入力してください。");
    }

    @Test
    void rejectsNonexistentDate() {
        assertValidationError(Map.of("startDate", "20260230"),
                "RPT-VAL-002", "RPT-E-002", "startDate",
                "開始年月日に有効な日付を入力してください。");
    }

    @Test
    void rejectsNonexistentTime() {
        assertValidationError(Map.of("startDate", "20260801", "startTime", "2460"),
                "RPT-VAL-006", "RPT-E-011", "startTime",
                "開始時刻に有効な時刻を入力してください。");
    }

    @Test
    void rejectsStartTimeWithoutStartDate() {
        assertValidationError(Map.of("startTime", "0900"),
                "RPT-VAL-009", "RPT-E-015", "startTime",
                "開始時刻を指定する場合は、開始年月日も入力してください。");
    }

    @Test
    void rejectsEndTimeWithoutEndDate() {
        assertValidationError(Map.of("endTime", "1800"),
                "RPT-VAL-010", "RPT-E-016", "endTime",
                "終了時刻を指定する場合は、終了年月日も入力してください。");
    }

    @Test
    void rejectsStartDateTimeAfterEndDateTime() {
        assertValidationError(Map.of(
                        "startDate", "20260807", "startTime", "1800",
                        "endDate", "20260807", "endTime", "1700"),
                "RPT-VAL-011", "RPT-E-014", "dateRange",
                "開始日時は終了日時以前となるように指定してください。");
    }

    @Test
    void rejectsUnknownBusinessId() {
        assertValidationError(Map.of("businessId", "99999"),
                "RPT-VAL-012", "RPT-E-017", "businessId",
                "有効な業務IDを入力してください。");
    }

    @Test
    void rejectsBusinessIdWithInvalidLengthOrCharacters() {
        assertValidationError(Map.of("businessId", "１２A"),
                "RPT-VAL-015", "RPT-E-018", "businessId",
                "業務IDは5桁の半角数字で入力してください。");
    }

    @Test
    void returnsDateRangeErrorBeforeBusinessIdError() {
        ResponseEntity<Map> response = search(Map.of(
                "startDate", "20260807", "startTime", "1800",
                "endDate", "20260807", "endTime", "1700",
                "businessId", "99999"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<Map<String, String>> errors = (List<Map<String, String>>) response.getBody().get("errors");
        assertThat(errors).extracting(error -> error.get("code"))
                .containsExactly("RPT-VAL-011", "RPT-VAL-012");
    }

    @Test
    void returnsOneValidationErrorForEachInvalidField() {
        ResponseEntity<Map> response = search(Map.of(
                "startDate", "20260230",
                "endTime", "2460",
                "businessId", "99999"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
        List<Map<String, String>> errors = (List<Map<String, String>>) response.getBody().get("errors");
        assertThat(errors).extracting(error -> error.get("code"))
                .containsExactly("RPT-VAL-002", "RPT-VAL-008", "RPT-VAL-012");
        assertThat(errors).extracting(error -> error.get("field"))
                .containsExactly("startDate", "endTime", "businessId");
        assertThat(response.getBody().get("traceId")).isNotNull();
    }

    @Test
    void returnsErrorsInScreenFieldOrderWithoutDerivedRangeError() {
        ResponseEntity<Map> response = search(Map.of(
                "startDate", "20260230",
                "startTime", "2460",
                "endDate", "20261301",
                "endTime", "9999",
                "businessId", "99999"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<Map<String, String>> errors = (List<Map<String, String>>) response.getBody().get("errors");
        assertThat(errors).extracting(error -> error.get("code"))
                .containsExactly(
                        "RPT-VAL-002",
                        "RPT-VAL-006",
                        "RPT-VAL-004",
                        "RPT-VAL-008",
                        "RPT-VAL-012");
        assertThat(errors).extracting(error -> error.get("field"))
                .doesNotContain("dateRange");
    }

    private void assertValidationError(Map<String, String> request,
                                       String code, String messageId, String field, String message) {
        ResponseEntity<Map> response = search(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
        List<Map<String, String>> errors = (List<Map<String, String>>) response.getBody().get("errors");
        assertThat(errors).singleElement().satisfies(error -> {
            assertThat(error).containsEntry("code", code);
            assertThat(error).containsEntry("messageId", messageId);
            assertThat(error).containsEntry("field", field);
            assertThat(error).containsEntry("message", message);
        });
    }

    private ResponseEntity<Map> search(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Trace-Id", "search-api-test-trace");
        return restTemplate.exchange(
                "/api/reports/search",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    private String login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(Map.of("username", "zhangsan", "password", "password"), headers),
                Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody().get("token").toString();
    }
}
