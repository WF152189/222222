package com.example.svf.report;

import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ReportSearchValidator {
    private static final Pattern DATE_PATTERN = Pattern.compile("^[0-9]{8}$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^[0-9]{4}$");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> BUSINESS_IDS = Set.of("10001", "10002", "10003");

    public ValidatedSearchCriteria validate(ReportSearchRequest request) {
        ReportSearchRequest source = request == null
                ? new ReportSearchRequest(null, null, null, null, null)
                : request;
        String startDateText = normalize(source.startDate());
        String startTimeText = normalize(source.startTime());
        String endDateText = normalize(source.endDate());
        String endTimeText = normalize(source.endTime());
        String businessId = normalize(source.businessId());

        // 項目間の入力依存を最優先で検証する。
        if (startTimeText != null && startDateText == null) {
            throw validationException("RPT-VAL-009", "RPT-E-015",
                    "開始時刻を指定する場合は、開始年月日も入力してください。", "startTime");
        }
        if (endTimeText != null && endDateText == null) {
            throw validationException("RPT-VAL-010", "RPT-E-016",
                    "終了時刻を指定する場合は、終了年月日も入力してください。", "endTime");
        }

        // 画面の項目順に検証し、最初のエラーを検出した時点で終了する。
        LocalDate startDate = validateDate(startDateText, "startDate", true);
        LocalTime startTime = validateTime(startTimeText, "startTime", true);
        LocalDate endDate = validateDate(endDateText, "endDate", false);
        LocalTime endTime = validateTime(endTimeText, "endTime", false);

        LocalDateTime startDateTime = startDate == null
                ? null
                : startDate.atTime(startTime == null ? LocalTime.MIN : startTime);
        LocalDateTime endDateTime = endDate == null
                ? null
                : endDate.atTime(endTime == null ? LocalTime.MAX : endTime);
        if (startDateTime != null && endDateTime != null && startDateTime.isAfter(endDateTime)) {
            throw validationException("RPT-VAL-011", "RPT-E-014",
                    "開始日時は終了日時以前となるように指定してください。", "dateRange");
        }

        if (businessId != null && !BUSINESS_IDS.contains(businessId)) {
            throw validationException("RPT-VAL-012", "RPT-E-017",
                    "有効な業務IDを入力してください。", "businessId");
        }
        return new ValidatedSearchCriteria(startDateTime, endDateTime, businessId);
    }

    private LocalDate validateDate(String value, String field, boolean start) {
        if (value == null) {
            return null;
        }
        if (!DATE_PATTERN.matcher(value).matches()) {
            throw validationException(start ? "RPT-VAL-001" : "RPT-VAL-003",
                    start ? "RPT-E-001" : "RPT-E-008",
                    start ? "開始年月日は8桁の半角数字で入力してください。"
                            : "終了年月日は8桁の半角数字で入力してください。",
                    field);
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw validationException(start ? "RPT-VAL-002" : "RPT-VAL-004",
                    start ? "RPT-E-002" : "RPT-E-009",
                    start ? "開始年月日に有効な日付を入力してください。"
                            : "終了年月日に有効な日付を入力してください。",
                    field);
        }
    }

    private LocalTime validateTime(String value, String field, boolean start) {
        if (value == null) {
            return null;
        }
        if (!TIME_PATTERN.matcher(value).matches()) {
            throw validationException(start ? "RPT-VAL-005" : "RPT-VAL-007",
                    start ? "RPT-E-010" : "RPT-E-012",
                    start ? "開始時刻は4桁の半角数字で入力してください。"
                            : "終了時刻は4桁の半角数字で入力してください。",
                    field);
        }
        try {
            return LocalTime.of(Integer.parseInt(value.substring(0, 2)),
                    Integer.parseInt(value.substring(2, 4)));
        } catch (DateTimeException ex) {
            throw validationException(start ? "RPT-VAL-006" : "RPT-VAL-008",
                    start ? "RPT-E-011" : "RPT-E-013",
                    start ? "開始時刻に有効な時刻を入力してください。"
                            : "終了時刻に有効な時刻を入力してください。",
                    field);
        }
    }

    private ReportValidationException validationException(
            String code, String messageId, String message, String field) {
        ValidationErrorDetail detail = new ValidationErrorDetail(code, messageId, message, field);
        return new ReportValidationException(List.of(detail));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 検証を通過した検索条件。
     * 不変クラス: 検証済みの開始日時・終了日時・業務IDを保持する。
     * いずれも未指定時は null（条件なし）を表す。
     */
    public static final class ValidatedSearchCriteria {
        private final LocalDateTime startDateTime;
        private final LocalDateTime endDateTime;
        private final String businessId;

        ValidatedSearchCriteria(LocalDateTime startDateTime, LocalDateTime endDateTime, String businessId) {
            this.startDateTime = startDateTime;
            this.endDateTime = endDateTime;
            this.businessId = businessId;
        }

        /** 検索範囲の開始日時（未指定なら null）。 */
        public LocalDateTime startDateTime() {
            return startDateTime;
        }

        /** 検索範囲の終了日時（未指定なら null）。 */
        public LocalDateTime endDateTime() {
            return endDateTime;
        }

        /** 業務ID（未指定なら null）。 */
        public String businessId() {
            return businessId;
        }
    }
}
