package com.eric.financas.report;

import com.eric.financas.report.dto.WeekdayReport;
import com.eric.financas.report.dto.WeekdayStat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Agrupa despesas por dia da semana. Sem dependência de Spring/JPA para poder ser testada isoladamente.
 */
public final class WeekdayCalculator {

    private WeekdayCalculator() {
    }

    /** Despesas somadas de um mesmo dia do calendário. */
    public record Row(LocalDate day, BigDecimal total, long entries) {
    }

    /** Último dia considerado: o fim do mês, ou hoje se o mês ainda não terminou. */
    public static LocalDate lastDay(YearMonth month, LocalDate today) {
        LocalDate monthEnd = month.atEndOfMonth();
        return monthEnd.isAfter(today) ? today : monthEnd;
    }

    public static WeekdayReport build(YearMonth month, LocalDate today, List<Row> rows) {
        LocalDate start = month.atDay(1);
        LocalDate end = lastDay(month, today);

        Map<DayOfWeek, BigDecimal> totals = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, Long> entries = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, Integer> occurrences = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            totals.put(d, BigDecimal.ZERO);
            entries.put(d, 0L);
            occurrences.put(d, 0);
        }

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            occurrences.merge(d.getDayOfWeek(), 1, Integer::sum);
        }
        for (Row row : rows) {
            if (row.day().isBefore(start) || row.day().isAfter(end)) {
                continue;
            }
            totals.merge(row.day().getDayOfWeek(), row.total(), BigDecimal::add);
            entries.merge(row.day().getDayOfWeek(), row.entries(), Long::sum);
        }

        List<WeekdayStat> days = new ArrayList<>(7);
        BigDecimal grand = BigDecimal.ZERO;
        Integer top = null;
        BigDecimal topTotal = BigDecimal.ZERO;
        for (DayOfWeek d : DayOfWeek.values()) {   // MONDAY..SUNDAY
            BigDecimal total = totals.get(d).setScale(2, RoundingMode.HALF_UP);
            int occ = occurrences.get(d);
            BigDecimal average = occ == 0
                    ? BigDecimal.ZERO.setScale(2)
                    : total.divide(BigDecimal.valueOf(occ), 2, RoundingMode.HALF_UP);
            days.add(new WeekdayStat(d.getValue(), total, entries.get(d), occ, average));
            grand = grand.add(total);
            if (total.compareTo(topTotal) > 0) {
                topTotal = total;
                top = d.getValue();
            }
        }
        return new WeekdayReport(month.toString(), end, grand, top, days);
    }
}
