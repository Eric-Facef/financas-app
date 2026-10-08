package com.eric.financas.report.dto;

import java.math.BigDecimal;

/**
 * Gasto acumulado num dia da semana.
 *
 * @param dayOfWeek   padrão ISO: 1 = segunda ... 7 = domingo
 * @param total       soma das despesas
 * @param entries     quantidade de lançamentos
 * @param occurrences quantas vezes esse dia da semana já aconteceu no período
 * @param average     total / occurrences (média por dia), 0 se ainda não aconteceu
 */
public record WeekdayStat(int dayOfWeek, BigDecimal total, long entries, int occurrences, BigDecimal average) {
}
