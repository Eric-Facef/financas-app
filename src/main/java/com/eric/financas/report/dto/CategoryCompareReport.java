package com.eric.financas.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Despesas por categoria: mês escolhido x mês anterior. As categorias menores vão somadas em "Outras".
 */
public record CategoryCompareReport(String month, String previousMonth, List<Row> rows) {

    /** @param categoryId null na linha "Outras" */
    public record Row(UUID categoryId, String name, String icon, String color,
                      BigDecimal current, BigDecimal previous) {
    }
}
