package com.eric.financas.report;

import com.eric.financas.account.AccountType;
import com.eric.financas.report.ChartsCalculator.AccountInfo;
import com.eric.financas.report.ChartsCalculator.DayRow;
import com.eric.financas.report.dto.CategoryCompareReport;
import com.eric.financas.report.dto.MonthlyReport;
import com.eric.financas.report.dto.NetWorthReport;
import com.eric.financas.report.dto.PaceReport;
import com.eric.financas.transaction.AccountTypeTotal;
import com.eric.financas.transaction.CategoryTotal;
import com.eric.financas.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChartsCalculatorTest {

    private static DayRow day(String date, String total) {
        return new DayRow(LocalDate.parse(date), new BigDecimal(total));
    }

    private static CategoryTotal cat(UUID id, String name, String total) {
        return new CategoryTotal() {
            public UUID getCategoryId() { return id; }
            public String getCategoryName() { return name; }
            public String getIcon() { return null; }
            public String getColor() { return "#fff"; }
            public BigDecimal getTotal() { return new BigDecimal(total); }
        };
    }

    private static AccountTypeTotal total(UUID account, TransactionType type, String value) {
        return new AccountTypeTotal() {
            public UUID getAccountId() { return account; }
            public TransactionType getEntryType() { return type; }
            public BigDecimal getTotal() { return new BigDecimal(value); }
        };
    }

    @Test
    void monthlyFillsEmptyMonthsWithZeroAndComputesLeftover() {
        MonthlyReport r = ChartsCalculator.monthly(YearMonth.of(2026, 10), 3,
                List.of(day("2026-10-05", "3000.00"), day("2026-08-05", "2500.00")),
                List.of(day("2026-10-02", "100.50"), day("2026-10-20", "200.00")));

        assertEquals(List.of("2026-08", "2026-09", "2026-10"),
                r.months().stream().map(MonthlyReport.Point::month).toList());
        assertEquals(new BigDecimal("0.00"), r.months().get(1).income());
        assertEquals(new BigDecimal("3000.00"), r.months().get(2).income());
        assertEquals(new BigDecimal("300.50"), r.months().get(2).expenses());
        assertEquals(new BigDecimal("2699.50"), r.months().get(2).leftover());
    }

    @Test
    void paceAccumulatesAndComparesWithSameDayOfPreviousMonth() {
        // hoje = dia 3 de outubro; setembro tem 30 dias
        PaceReport r = ChartsCalculator.pace(YearMonth.of(2026, 10), LocalDate.parse("2026-10-03"),
                List.of(day("2026-10-01", "10.00"), day("2026-10-03", "5.00")),
                List.of(day("2026-09-02", "40.00"), day("2026-09-30", "100.00")));

        assertEquals(3, r.elapsedDays());
        assertEquals(List.of(new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("15.00")), r.current());
        assertEquals(30, r.previous().size());
        assertEquals(new BigDecimal("15.00"), r.currentTotal());
        assertEquals(new BigDecimal("40.00"), r.previousSamePoint());
        assertEquals(new BigDecimal("140.00"), r.previousTotal());
    }

    @Test
    void paceForFutureMonthHasNoCurrentPoints() {
        PaceReport r = ChartsCalculator.pace(YearMonth.of(2026, 12), LocalDate.parse("2026-10-03"), List.of(), List.of());
        assertEquals(0, r.elapsedDays());
        assertEquals(List.of(), r.current());
        assertEquals(new BigDecimal("0.00"), r.currentTotal());
    }

    @Test
    void paceForPastMonthCoversTheWholeMonth() {
        PaceReport r = ChartsCalculator.pace(YearMonth.of(2026, 2), LocalDate.parse("2026-10-03"),
                List.of(day("2026-02-28", "1.00")), List.of());
        assertEquals(28, r.elapsedDays());
        assertEquals(28, r.current().size());
        assertEquals(new BigDecimal("1.00"), r.currentTotal());
    }

    @Test
    void categoriesMergeBothMonthsAndGroupTheSmallOnesAsOthers() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        CategoryCompareReport r = ChartsCalculator.compareCategories(YearMonth.of(2026, 10),
                List.of(cat(a, "Mercado", "300.00"), cat(b, "Lazer", "50.00")),
                List.of(cat(a, "Mercado", "200.00"), cat(c, "Saúde", "10.00")), 2);

        assertEquals("2026-09", r.previousMonth());
        assertEquals(3, r.rows().size());
        assertEquals("Mercado", r.rows().get(0).name());
        assertEquals(new BigDecimal("300.00"), r.rows().get(0).current());
        assertEquals(new BigDecimal("200.00"), r.rows().get(0).previous());
        assertEquals("Lazer", r.rows().get(1).name());
        assertEquals(new BigDecimal("0.00"), r.rows().get(1).previous());
        assertEquals("Outras", r.rows().get(2).name());
        assertNull(r.rows().get(2).categoryId());
        assertEquals(new BigDecimal("10.00"), r.rows().get(2).previous());
    }

    @Test
    void netWorthSplitsCheckingAndSavingsUsingInitialBalancePlusMovements() {
        UUID checking = UUID.randomUUID(), savings = UUID.randomUUID();
        List<AccountInfo> accounts = List.of(
                new AccountInfo(checking, AccountType.CHECKING, new BigDecimal("100.00")),
                new AccountInfo(savings, AccountType.SAVINGS, BigDecimal.ZERO));

        NetWorthReport.Point p = ChartsCalculator.netWorthPoint(YearMonth.of(2026, 10), LocalDate.parse("2026-10-31"),
                accounts, List.of(
                        total(checking, TransactionType.INCOME, "1000.00"),
                        total(checking, TransactionType.EXPENSE, "300.00"),
                        total(checking, TransactionType.TRANSFER_OUT, "400.00"),
                        total(savings, TransactionType.TRANSFER_IN, "400.00")));

        assertEquals(new BigDecimal("400.00"), p.checking());
        assertEquals(new BigDecimal("400.00"), p.savings());
        assertEquals(new BigDecimal("800.00"), p.total());
    }
}
