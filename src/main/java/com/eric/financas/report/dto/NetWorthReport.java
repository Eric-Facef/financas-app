package com.eric.financas.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Saldo no fim de cada mês, separado em contas correntes e poupança.
 *
 * @param endMonth último mês da série; a lista vem em ordem cronológica
 */
public record NetWorthReport(String endMonth, List<Point> points) {

    /**
     * @param asOf data de corte do saldo (fim do mês, ou hoje se o mês ainda não acabou:
     *             lançamentos futuros não entram, igual ao saldo do Dashboard)
     */
    public record Point(String month, LocalDate asOf, BigDecimal checking, BigDecimal savings, BigDecimal total) {
    }
}
