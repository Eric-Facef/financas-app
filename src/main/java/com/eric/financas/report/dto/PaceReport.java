package com.eric.financas.report.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ritmo de gastos: despesa acumulada dia a dia, mês escolhido x mês anterior.
 *
 * @param daysInMonth        dias do mês escolhido
 * @param elapsedDays        dias já decorridos (0 = mês futuro; = daysInMonth se o mês já acabou)
 * @param current            acumulado do mês escolhido; tem {@code elapsedDays} itens (índice 0 = dia 1)
 * @param previous           acumulado do mês anterior, mês inteiro
 * @param currentTotal       gasto acumulado até hoje no mês escolhido
 * @param previousSamePoint  quanto o mês anterior tinha acumulado nesse mesmo dia do mês
 * @param previousTotal      total do mês anterior inteiro
 */
public record PaceReport(String month, String previousMonth, int daysInMonth, int elapsedDays,
                         List<BigDecimal> current, List<BigDecimal> previous,
                         BigDecimal currentTotal, BigDecimal previousSamePoint, BigDecimal previousTotal) {
}
