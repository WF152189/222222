package com.example.svf.report;

import com.example.svf.auth.AuthenticatedUser;
import com.example.svf.svf.SvfCloudClient;
import com.example.svf.svf.model.SvfRenderOptions;
import com.example.svf.svf.model.SvfRenderRequest;
import com.example.svf.svf.model.SvfRenderResult;
import com.example.svf.svf.model.SvfUserContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private final SvfCloudClient svfCloudClient;
    private final ReportSearchValidator reportSearchValidator;
    private final Map<String, ReportDetail> reports = new LinkedHashMap<>();
    private final List<String> convertedReportIds = new ArrayList<>();

    private final Map<String, SearchMetadata> searchMetadata = new LinkedHashMap<>();

    public ReportService(SvfCloudClient svfCloudClient, ReportSearchValidator reportSearchValidator) {
        this.svfCloudClient = svfCloudClient;
        this.reportSearchValidator = reportSearchValidator;
        reports.put("RPT-001", new ReportDetail(
                "RPT-001", "見積書", LocalDate.of(2026, 8, 1), "Q-2026-0001", "東京サンプル商事",
                List.of(new ReportLine("クラウド利用料", 1, new BigDecimal("12000")),
                        new ReportLine("帳票テンプレート作成", 2, new BigDecimal("8000")))));
        reports.put("RPT-002", new ReportDetail(
                "RPT-002", "請求書", LocalDate.of(2026, 8, 5), "INV-2026-0007", "大阪デモ株式会社",
                List.of(new ReportLine("保守サポート", 1, new BigDecimal("30000")),
                        new ReportLine("追加開発", 3, new BigDecimal("15000")))));
        reports.put("RPT-003", new ReportDetail(
                "RPT-003", "納品書", LocalDate.of(2026, 8, 7), "DLV-2026-0012", "名古屋テスト有限会社",
                List.of(new ReportLine("ライセンス", 5, new BigDecimal("9800")))));
        searchMetadata.put("RPT-001", new SearchMetadata(
                LocalDateTime.of(2026, 8, 1, 9, 30), "zhangsan@example.com", "张三", "10001"));
        searchMetadata.put("RPT-002", new SearchMetadata(
                LocalDateTime.of(2026, 8, 5, 13, 15), "zhangsan@example.com", "张三", "10002"));
        searchMetadata.put("RPT-003", new SearchMetadata(
                LocalDateTime.of(2026, 8, 7, 17, 45), "zhangsan@example.com", "张三", "10001"));
    }

    /**
     * 帳票一覧を検索します。
     * {@code number} が指定された場合は帳票番号との前方一致で絞り込み、
     * 未指定（null または空）の場合は全件を返します。
     */
    public List<ReportSummary> findAll(String number) {
        String condition = number == null ? "" : number.trim();
        return reports.values().stream()
                .filter(report -> condition.isEmpty() || report.reportNumber().startsWith(condition))
                .map(report -> new ReportSummary(
                        report.id(),
                        report.name(),
                        report.reportDate(),
                        report.reportNumber(),
                        convertedReportIds.contains(report.id())))
                .toList();
    }

    /**
     * 帳票番号と PDF 名の組を一覧取得します。
     * PDF管理画面の番号検索入力欄が候補一覧を表示するために使用します。
     * 各要素は {"number": 帳票番号, "pdfName": PDF名} の Map です。
     */
    public List<Map<String, String>> findAllNumberItems() {
        return reports.values().stream()
                .map(report -> {
                    Map<String, String> item = new LinkedHashMap<String, String>();
                    item.put("number", report.reportNumber());
                    item.put("pdfName", report.name());
                    return item;
                })
                .toList();
    }

    public ReportSearchResponse search(ReportSearchRequest request, AuthenticatedUser user) {
        ReportSearchValidator.ValidatedSearchCriteria criteria = reportSearchValidator.validate(request);
        List<ReportSearchItem> matched = reports.values().stream()
                .filter(report -> matches(searchMetadata.get(report.id()), criteria, user.userId()))
                .map(report -> {
                    SearchMetadata metadata = searchMetadata.get(report.id());
                    return new ReportSearchItem(
                            report.id(),
                            report.name(),
                            metadata.createdAt().toLocalDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                            metadata.createdBy(),
                            convertedReportIds.contains(report.id()) ? "OUTPUT_COMPLETED" : "NOT_OUTPUT");
                })
                .toList();
        return new ReportSearchResponse(matched, matched.size());
    }

    private boolean matches(SearchMetadata metadata,
                            ReportSearchValidator.ValidatedSearchCriteria criteria, String userId) {
        if (metadata == null || !metadata.ownerUserId().equals(userId)) {
            return false;
        }
        if (criteria.startDateTime() != null && metadata.createdAt().isBefore(criteria.startDateTime())) {
            return false;
        }
        if (criteria.endDateTime() != null && metadata.createdAt().isAfter(criteria.endDateTime())) {
            return false;
        }
        return criteria.businessId() == null || criteria.businessId().equals(metadata.businessId());
    }

    public byte[] convertToPdf(String reportId, SvfUserContext user) {
        ReportDetail report = reports.get(reportId);
        if (report == null) {
            throw new ReportNotFoundException(reportId);
        }
        SvfRenderRequest request = new SvfRenderRequest(
                report.name() + "-" + report.reportNumber(),
                "form/Practice/Report.xml",
                toCsv(report),
                user,
                SvfRenderOptions.pdfCsvDefault());
        SvfRenderResult result = svfCloudClient.renderPdf(request);
        if (!convertedReportIds.contains(reportId)) {
            convertedReportIds.add(reportId);
        }
        return result.getPdf();
    }

    private String toCsv(ReportDetail report) {
        StringBuilder csv = new StringBuilder();
        csv.append("reportId,reportName,reportDate,reportNumber,customerName,itemName,quantity,unitPrice,amount\n");
        for (ReportLine line : report.lines()) {
            csv.append(report.id()).append(',')
                    .append(report.name()).append(',')
                    .append(report.reportDate()).append(',')
                    .append(report.reportNumber()).append(',')
                    .append(report.customerName()).append(',')
                    .append(line.itemName()).append(',')
                    .append(line.quantity()).append(',')
                    .append(line.unitPrice()).append(',')
                    .append(line.amount()).append('\n');
        }
        return csv.toString();
    }

    private record SearchMetadata(
            LocalDateTime createdAt,
            String ownerUserId,
            String createdBy,
            String businessId
    ) {
    }
}
