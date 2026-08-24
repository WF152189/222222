package com.example.svf.report;

import com.example.svf.svf.SvfCloudClient;
import com.example.svf.svf.model.SvfRenderOptions;
import com.example.svf.svf.model.SvfRenderRequest;
import com.example.svf.svf.model.SvfRenderResult;
import com.example.svf.svf.model.SvfUserContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private final SvfCloudClient svfCloudClient;
    private final Map<String, ReportDetail> reports = new LinkedHashMap<>();
    private final List<String> convertedReportIds = new ArrayList<>();

    public ReportService(SvfCloudClient svfCloudClient) {
        this.svfCloudClient = svfCloudClient;
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
    }

    public List<ReportSummary> findAll() {
        return reports.values().stream()
                .map(report -> new ReportSummary(
                        report.id(),
                        report.name(),
                        report.reportDate(),
                        report.reportNumber(),
                        convertedReportIds.contains(report.id())))
                .toList();
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
        return result.pdf();
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
}
