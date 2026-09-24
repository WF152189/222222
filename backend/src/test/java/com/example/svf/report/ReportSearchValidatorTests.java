package com.example.svf.report;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ReportSearchValidatorTests {
    private final ReportSearchValidator validator = new ReportSearchValidator();

    @Test
    void validate_accepts_valid_search_criteria() {
        ReportSearchRequest request = new ReportSearchRequest(
                "20240102",
                "0830",
                "20240105",
                "1730",
                "10001"
        );

        ReportSearchValidator.ValidatedSearchCriteria result = validator.validate(request);

        assertThat(result.startDateTime()).isEqualTo(LocalDateTime.of(2024, 1, 2, 8, 30));
        assertThat(result.endDateTime()).isEqualTo(LocalDateTime.of(2024, 1, 5, 17, 30));
        assertThat(result.businessId()).isEqualTo("10001");
    }

    @Test
    void validate_accepts_null_request_as_no_constraints() {
        ReportSearchValidator.ValidatedSearchCriteria result = validator.validate(null);

        assertThat(result.startDateTime()).isNull();
        assertThat(result.endDateTime()).isNull();
        assertThat(result.businessId()).isNull();
    }

    @Test
    void validate_rejects_time_without_date_and_invalid_business_id() {
        ReportSearchRequest request = new ReportSearchRequest(
                null,
                "0900",
                null,
                null,
                "99999"
        );

        ReportValidationException exception = catchThrowableOfType(
                () -> validator.validate(request),
                ReportValidationException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getErrors())
                .extracting(ValidationErrorDetail::field)
                .contains("startTime", "businessId");
        assertThat(exception.getErrors())
                .extracting(ValidationErrorDetail::message)
                .contains(
                        "開始時刻を指定する場合は、開始年月日も入力してください。",
                        "有効な業務IDを入力してください。"
                );
    }

    @Test
    void validate_rejects_start_after_end() {
        ReportSearchRequest request = new ReportSearchRequest(
                "20240105",
                "0900",
                "20240102",
                "0800",
                "10002"
        );

        ReportValidationException exception = catchThrowableOfType(
                () -> validator.validate(request),
                ReportValidationException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getErrors())
                .extracting(ValidationErrorDetail::field)
                .contains("dateRange");
        assertThat(exception.getErrors())
                .extracting(ValidationErrorDetail::message)
                .contains("開始日時は終了日時以前となるように指定してください。");
    }

    @Test
    void validate_rejects_invalid_date_and_time_formats() {
        ReportSearchRequest request = new ReportSearchRequest(
                "2024/01/02",
                "9:00",
                "20240230",
                "2400",
                "10001"
        );

        ReportValidationException exception = catchThrowableOfType(
                () -> validator.validate(request),
                ReportValidationException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getErrors())
                .extracting(ValidationErrorDetail::field)
                .contains("startDate", "startTime", "endDate", "endTime");
        assertThat(exception.getErrors())
                .extracting(ValidationErrorDetail::message)
                .contains(
                        "開始年月日は8桁の半角数字で入力してください。",
                        "開始時刻は4桁の半角数字で入力してください。",
                        "終了年月日に有効な日付を入力してください。",
                        "終了時刻に有効な時刻を入力してください。"
                );
    }

    @Test
    void validate_rejects_end_time_without_end_date() {
        ReportSearchRequest request = new ReportSearchRequest(
                "20240102",
                "0900",
                null,
                "0930",
                "10001"
        );

        ReportValidationException exception = catchThrowableOfType(
                () -> validator.validate(request),
                ReportValidationException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getErrors())
                .extracting(ValidationErrorDetail::field)
                .contains("endTime");
        assertThat(exception.getErrors())
                .extracting(ValidationErrorDetail::message)
                .contains("終了時刻を指定する場合は、終了年月日も入力してください。");
    }

    @Test
    void validate_rejects_business_id_with_wrong_length() {
        ReportSearchRequest request = new ReportSearchRequest(
                "20240102",
                "0900",
                "20240103",
                "1000",
                "1234"
        );

        ReportValidationException exception = catchThrowableOfType(
                () -> validator.validate(request),
                ReportValidationException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getErrors())
                .extracting(ValidationErrorDetail::field)
                .contains("businessId");
        assertThat(exception.getErrors())
                .extracting(ValidationErrorDetail::message)
                .contains("業務IDは5桁の半角数字で入力してください。");
    }
}
