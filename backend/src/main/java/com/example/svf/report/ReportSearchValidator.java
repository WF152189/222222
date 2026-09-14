package com.example.svf.report;

import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ReportSearchValidator {
    private static final Pattern DATE_PATTERN = Pattern.compile("^[0-9]{8}$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^[0-9]{4}$");
    private static final Pattern BUSINESS_ID_PATTERN = Pattern.compile("^[0-9]{5}$");
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

        List<ValidationErrorDetail> errors = new ArrayList<>();

        // 1. 開始年月日の桁数・半角・実在性を検証する。
        LocalDate startDate = validateDate(startDateText, "startDate", true, errors);

        // 2. 開始時刻の桁数・半角・実在性を検証する。
        LocalTime startTime = validateTime(startTimeText, "startTime", true, errors);

        // 3. 開始年月日と開始時刻の入力依存を検証する。
        if (startTimeText != null && startDateText == null && !hasFieldError(errors, "startTime")) {
            errors.add(error("RPT-VAL-009", "RPT-E-015",
                    "開始時刻を指定する場合は、開始年月日も入力してください。", "startTime"));
        }

        // 4. 終了年月日の桁数・半角・実在性を検証する。
        LocalDate endDate = validateDate(endDateText, "endDate", false, errors);

        // 5. 終了時刻の桁数・半角・実在性を検証する。
        LocalTime endTime = validateTime(endTimeText, "endTime", false, errors);

        // 6. 終了年月日と終了時刻の入力依存を検証する。
        if (endTimeText != null && endDateText == null && !hasFieldError(errors, "endTime")) {
            errors.add(error("RPT-VAL-010", "RPT-E-016",
                    "終了時刻を指定する場合は、終了年月日も入力してください。", "endTime"));
        }

        // 7. 単項目・依存チェックが正常な日時だけを組み立て、前後関係を検証する。
        LocalDateTime startDateTime = startDate == null || (startTimeText != null && startTime == null)
                ? null
                : startDate.atTime(startTime == null ? LocalTime.MIN : startTime);
        LocalDateTime endDateTime = endDate == null || (endTimeText != null && endTime == null)
                ? null
                : endDate.atTime(endTime == null ? LocalTime.MAX : endTime);
        if (startDateTime != null && endDateTime != null && startDateTime.isAfter(endDateTime)) {
            errors.add(error("RPT-VAL-011", "RPT-E-014",
                    "開始日時は終了日時以前となるように指定してください。", "dateRange"));
        }

        // 8. 業務番号の桁数・半角および利用可能な業務ID一覧への存在を検証する。
        validateBusinessId(businessId, errors);

        if (!errors.isEmpty()) {
            throw new ReportValidationException(errors);
        }
        return new ValidatedSearchCriteria(startDateTime, endDateTime, businessId);
    }

    private LocalDate validateDate(String value, String field, boolean start,
                                   List<ValidationErrorDetail> errors) {
        if (value == null) {
            return null;
        }
        if (!DATE_PATTERN.matcher(value).matches()) {
            errors.add(error(start ? "RPT-VAL-001" : "RPT-VAL-003",
                    start ? "RPT-E-001" : "RPT-E-008",
                    start ? "開始年月日は8桁の半角数字で入力してください。"
                            : "終了年月日は8桁の半角数字で入力してください。",
                    field));
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            errors.add(error(start ? "RPT-VAL-002" : "RPT-VAL-004",
                    start ? "RPT-E-002" : "RPT-E-009",
                    start ? "開始年月日に有効な日付を入力してください。"
                            : "終了年月日に有効な日付を入力してください。",
                    field));
            return null;
        }
    }

    private LocalTime validateTime(String value, String field, boolean start,
                                   List<ValidationErrorDetail> errors) {
        if (value == null) {
            return null;
        }
        if (!TIME_PATTERN.matcher(value).matches()) {
            errors.add(error(start ? "RPT-VAL-005" : "RPT-VAL-007",
                    start ? "RPT-E-010" : "RPT-E-012",
                    start ? "開始時刻は4桁の半角数字で入力してください。"
                            : "終了時刻は4桁の半角数字で入力してください。",
                    field));
            return null;
        }
        try {
            return LocalTime.of(Integer.parseInt(value.substring(0, 2)),
                    Integer.parseInt(value.substring(2, 4)));
        } catch (DateTimeException ex) {
            errors.add(error(start ? "RPT-VAL-006" : "RPT-VAL-008",
                    start ? "RPT-E-011" : "RPT-E-013",
                    start ? "開始時刻に有効な時刻を入力してください。"
                            : "終了時刻に有効な時刻を入力してください。",
                    field));
            return null;
        }
    }

    private ValidationErrorDetail error(String code, String messageId, String message, String field) {
        return new ValidationErrorDetail(code, messageId, message, field);
    }

    private void validateBusinessId(String businessId, List<ValidationErrorDetail> errors) {
        if (businessId == null) {
            return;
        }
        if (!BUSINESS_ID_PATTERN.matcher(businessId).matches()) {
            errors.add(error("RPT-VAL-015", "RPT-E-018",
                    "業務IDは5桁の半角数字で入力してください。", "businessId"));
            return;
        }
        if (!BUSINESS_IDS.contains(businessId)) {
            errors.add(error("RPT-VAL-012", "RPT-E-017",
                    "有効な業務IDを入力してください。", "businessId"));
        }
    }

    private boolean hasFieldError(List<ValidationErrorDetail> errors, String field) {
        return errors.stream().anyMatch(error -> field.equals(error.field()));
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
