package com.example.svf.report;

import java.math.BigDecimal;

public record ReportLine(
        String itemName,
        int quantity,
        BigDecimal unitPrice
) {
    public BigDecimal amount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
