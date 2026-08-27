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
        LocalDate startDate = validateDate(startDateText, "startDate", true, errors);
        LocalDate endDate = validateDate(endDateText, "endDate", false, errors);
        LocalTime startTime = validateTime(startTimeText, "startTime", true, errors);
        LocalTime endTime = validateTime(endTimeText, "endTime", false, errors);

        if (startTimeText != null && startDateText == null) {
            errors.add(error("RPT-VAL-009", "RPT-E-015",
                    "開始時刻を指定する場合は、開始年月日も入力してください。", "startTime"));
        }
        if (endTimeText != null && endDateText == null) {
            errors.add(error("RPT-VAL-010", "RPT-E-016",
                    "終了時刻を指定する場合は、終了年月日も入力してください。", "endTime"));
        }

        LocalDateTime startDateTime = startDate == null
                ? null
                : startDate.atTime(startTime == null ? LocalTime.MIN : startTime);
        LocalDateTime endDateTime = endDate == null
                ? null
                : endDate.atTime(endTime == null ? LocalTime.MAX : endTime);
        if (startDateTime != null && endDateTime != null && startDateTime.isAfter(endDateTime)) {
            errors.add(error("RPT-VAL-011", "RPT-E-014",
                    "開始日時は終了日時以前となるように指定してください。", "dateRange"));
        }

        if (businessId != null && !BUSINESS_IDS.contains(businessId)) {
            errors.add(error("RPT-VAL-012", "RPT-E-017",
                    "有効な業務IDを入力してください。", "businessId"));
        }
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

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record ValidatedSearchCriteria(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String businessId
    ) {
    }
}
