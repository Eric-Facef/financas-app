package com.eric.financas.report.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Receitas x despesas mês a mês.
 *
 * @param endMonth último mês da série ("yyyy-MM"); a lista vem em ordem cronológica
 */
public record MonthlyReport(String endMonth, List<Point> months) {

    /** @param leftover receitas - despesas do mês (pode ser negativa) */
    public record Point(String month, BigDecimal income, BigDecimal expenses, BigDecimal leftover) {
    }
}
