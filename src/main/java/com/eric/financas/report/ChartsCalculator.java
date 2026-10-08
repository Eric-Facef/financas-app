package com.eric.financas.report;

import com.eric.financas.account.AccountType;
import com.eric.financas.account.BalanceCalculator;
import com.eric.financas.report.dto.CategoryCompareReport;
import com.eric.financas.report.dto.MonthlyReport;
import com.eric.financas.report.dto.NetWorthReport;
import com.eric.financas.report.dto.PaceReport;
import com.eric.financas.transaction.AccountTypeTotal;
import com.eric.financas.transaction.CategoryTotal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contas dos gráficos extras da aba Relatórios. Sem Spring/JPA, para ser testada isoladamente
 * (mesmo estilo do {@link WeekdayCalculator}).
 */
public final class ChartsCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final String OTHERS_COLOR = "#6d6d7c";

    private ChartsCalculator() {
    }

    /** Total de um dia do calendário (receitas ou despesas, conforme a consulta que gerou). */
    public record DayRow(LocalDate day, BigDecimal total) {
    }

    /** Dados da conta necessários para reconstruir o saldo em uma data passada. */
    public record AccountInfo(UUID id, AccountType type, BigDecimal initialBalance) {
    }

    // ------------------------------------------------------------------ receitas x despesas por mês

    public static MonthlyReport monthly(YearMonth end, int months, List<DayRow> income, List<DayRow> expenses) {
        Map<YearMonth, BigDecimal> in = sumByMonth(income);
        Map<YearMonth, BigDecimal> out = sumByMonth(expenses);
        List<MonthlyReport.Point> points = new ArrayList<>(months);
        for (int i = months - 1; i >= 0; i--) {
            YearMonth m = end.minusMonths(i);
            BigDecimal inc = money(in.get(m));
            BigDecimal exp = money(out.get(m));
            points.add(new MonthlyReport.Point(m.toString(), inc, exp, inc.subtract(exp)));
        }
        return new MonthlyReport(end.toString(), points);
    }

    private static Map<YearMonth, BigDecimal> sumByMonth(List<DayRow> rows) {
        Map<YearMonth, BigDecimal> map = new HashMap<>();
        for (DayRow r : rows) {
            map.merge(YearMonth.from(r.day()), r.total(), BigDecimal::add);
        }
        return map;
    }

    // ------------------------------------------------------------------ ritmo de gastos acumulado

    /** Quantos dias do mês já passaram: 0 se é futuro, o mês inteiro se já acabou. */
    public static int elapsedDays(YearMonth month, LocalDate today) {
        if (today.isBefore(month.atDay(1))) {
            return 0;
        }
        if (today.isAfter(month.atEndOfMonth())) {
            return month.lengthOfMonth();
        }
        return today.getDayOfMonth();
    }

    public static PaceReport pace(YearMonth month, LocalDate today, List<DayRow> currentRows, List<DayRow> previousRows) {
        YearMonth prev = month.minusMonths(1);
        int elapsed = elapsedDays(month, today);

        List<BigDecimal> current = cumulative(month, elapsed, currentRows);
        List<BigDecimal> previous = cumulative(prev, prev.lengthOfMonth(), previousRows);

        BigDecimal currentTotal = elapsed == 0 ? ZERO : current.get(elapsed - 1);
        BigDecimal previousSamePoint = elapsed == 0 ? ZERO : previous.get(Math.min(elapsed, previous.size()) - 1);
        BigDecimal previousTotal = previous.get(previous.size() - 1);
        return new PaceReport(month.toString(), prev.toString(), month.lengthOfMonth(), elapsed,
                current, previous, currentTotal, previousSamePoint, previousTotal);
    }

    /** Soma corrida das despesas, um valor por dia (dia 1 .. {@code days}). */
    private static List<BigDecimal> cumulative(YearMonth month, int days, List<DayRow> rows) {
        BigDecimal[] perDay = new BigDecimal[days];
        java.util.Arrays.fill(perDay, ZERO);
        for (DayRow r : rows) {
            int d = r.day().getDayOfMonth();
            if (YearMonth.from(r.day()).equals(month) && d <= days) {
                perDay[d - 1] = perDay[d - 1].add(r.total());
            }
        }
        List<BigDecimal> result = new ArrayList<>(days);
        BigDecimal running = ZERO;
        for (BigDecimal v : perDay) {
            running = running.add(v);
            result.add(running);
        }
        return result;
    }

    // ------------------------------------------------------------------ categorias: mês x mês anterior

    public static CategoryCompareReport compareCategories(YearMonth month, List<CategoryTotal> current,
                                                          List<CategoryTotal> previous, int limit) {
        Map<UUID, Mutable> byCategory = new LinkedHashMap<>();
        for (CategoryTotal c : current) {
            byCategory.computeIfAbsent(c.getCategoryId(), id -> new Mutable(c)).current = c.getTotal();
        }
        for (CategoryTotal c : previous) {
            byCategory.computeIfAbsent(c.getCategoryId(), id -> new Mutable(c)).previous = c.getTotal();
        }

        List<Mutable> sorted = new ArrayList<>(byCategory.values());
        sorted.sort(Comparator.comparing((Mutable m) -> m.current.add(m.previous)).reversed());

        List<CategoryCompareReport.Row> rows = new ArrayList<>();
        BigDecimal othersCurrent = ZERO;
        BigDecimal othersPrevious = ZERO;
        boolean hasOthers = false;
        for (int i = 0; i < sorted.size(); i++) {
            Mutable m = sorted.get(i);
            if (i < limit) {
                rows.add(new CategoryCompareReport.Row(m.id, m.name, m.icon, m.color, money(m.current), money(m.previous)));
            } else {
                hasOthers = true;
                othersCurrent = othersCurrent.add(m.current);
                othersPrevious = othersPrevious.add(m.previous);
            }
        }
        if (hasOthers) {
            rows.add(new CategoryCompareReport.Row(null, "Outras", null, OTHERS_COLOR,
                    money(othersCurrent), money(othersPrevious)));
        }
        return new CategoryCompareReport(month.toString(), month.minusMonths(1).toString(), rows);
    }

    private static final class Mutable {
        final UUID id;
        final String name;
        final String icon;
        final String color;
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal previous = BigDecimal.ZERO;

        Mutable(CategoryTotal c) {
            this.id = c.getCategoryId();
            this.name = c.getCategoryName();
            this.icon = c.getIcon();
            this.color = c.getColor();
        }
    }

    // ------------------------------------------------------------------ saldo ao longo do tempo

    /**
     * Saldo de todas as contas numa data, com a mesma regra do Dashboard
     * (saldo inicial + créditos - débitos até a data de corte).
     */
    public static NetWorthReport.Point netWorthPoint(YearMonth month, LocalDate asOf, List<AccountInfo> accounts,
                                                     Collection<AccountTypeTotal> totals) {
        Map<UUID, List<AccountTypeTotal>> byAccount = new HashMap<>();
        for (AccountTypeTotal t : totals) {
            byAccount.computeIfAbsent(t.getAccountId(), id -> new ArrayList<>()).add(t);
        }
        BigDecimal checking = ZERO;
        BigDecimal savings = ZERO;
        for (AccountInfo a : accounts) {
            BigDecimal balance = BalanceCalculator.balance(a.initialBalance(), byAccount.getOrDefault(a.id(), List.of()));
            if (a.type() == AccountType.SAVINGS) {
                savings = savings.add(balance);
            } else {
                checking = checking.add(balance);
            }
        }
        return new NetWorthReport.Point(month.toString(), asOf, money(checking), money(savings),
                money(checking.add(savings)));
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
