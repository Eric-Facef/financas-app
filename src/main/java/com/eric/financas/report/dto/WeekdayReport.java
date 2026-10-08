package com.eric.financas.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @param month  "yyyy-MM"
 * @param until  último dia considerado (hoje, se o mês ainda não acabou). Despesas agendadas para depois não entram.
 * @param topDay dia da semana (ISO) com maior gasto total; null quando não há despesas
 * @param days   sempre 7 itens, de segunda (1) a domingo (7)
 */
public record WeekdayReport(String month, LocalDate until, BigDecimal total, Integer topDay, List<WeekdayStat> days) {
}
