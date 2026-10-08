package com.eric.financas.report;

import com.eric.financas.account.AccountRepository;
import com.eric.financas.report.ChartsCalculator.AccountInfo;
import com.eric.financas.report.ChartsCalculator.DayRow;
import com.eric.financas.report.dto.CategoryCompareReport;
import com.eric.financas.report.dto.MonthlyReport;
import com.eric.financas.report.dto.NetWorthReport;
import com.eric.financas.report.dto.PaceReport;
import com.eric.financas.transaction.TransactionRepository;
import com.eric.financas.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gráficos extras da aba Relatórios. Só lê; reaproveita as consultas que já existem em
 * {@link TransactionRepository}, então não mexe em nada do restante do sistema.
 */
@Service
@RequiredArgsConstructor
public class ChartsReportService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int CATEGORY_LIMIT = 8;

    private final TransactionRepository transactions;
    private final AccountRepository accounts;

    @Transactional(readOnly = true)
    public MonthlyReport monthly(UUID userId, YearMonth end, int months) {
        LocalDate start = end.minusMonths(months - 1L).atDay(1);
        LocalDate last = end.atEndOfMonth();
        return ChartsCalculator.monthly(end, months,
                dayRows(userId, TransactionType.INCOME, start, last),
                dayRows(userId, TransactionType.EXPENSE, start, last));
    }

    @Transactional(readOnly = true)
    public PaceReport pace(UUID userId, YearMonth month) {
        LocalDate today = LocalDate.now(ZONE);
        YearMonth prev = month.minusMonths(1);
        int elapsed = ChartsCalculator.elapsedDays(month, today);

        // despesas agendadas para depois de hoje não entram (o gráfico mostra o que já aconteceu)
        List<DayRow> current = elapsed == 0 ? List.of()
                : dayRows(userId, TransactionType.EXPENSE, month.atDay(1), month.atDay(elapsed));
        List<DayRow> previous = dayRows(userId, TransactionType.EXPENSE, prev.atDay(1), prev.atEndOfMonth());
        return ChartsCalculator.pace(month, today, current, previous);
    }

    @Transactional(readOnly = true)
    public CategoryCompareReport categories(UUID userId, YearMonth month) {
        YearMonth prev = month.minusMonths(1);
        return ChartsCalculator.compareCategories(month,
                transactions.totalsByCategory(userId, TransactionType.EXPENSE, month.atDay(1), month.atEndOfMonth()),
                transactions.totalsByCategory(userId, TransactionType.EXPENSE, prev.atDay(1), prev.atEndOfMonth()),
                CATEGORY_LIMIT);
    }

    @Transactional(readOnly = true)
    public NetWorthReport netWorth(UUID userId, YearMonth end, int months) {
        LocalDate today = LocalDate.now(ZONE);
        List<AccountInfo> infos = accounts.findByUserIdOrderByNameAsc(userId).stream()
                .map(a -> new AccountInfo(a.getId(), a.getType(), a.getInitialBalance()))
                .toList();

        List<NetWorthReport.Point> points = new ArrayList<>(months);
        for (int i = months - 1; i >= 0; i--) {
            YearMonth m = end.minusMonths(i);
            LocalDate asOf = m.atEndOfMonth().isAfter(today) ? today : m.atEndOfMonth();
            points.add(ChartsCalculator.netWorthPoint(m, asOf, infos, transactions.totalsByAccount(userId, asOf)));
        }
        return new NetWorthReport(end.toString(), points);
    }

    private List<DayRow> dayRows(UUID userId, TransactionType type, LocalDate from, LocalDate to) {
        return transactions.dailyTotals(userId, type, from, to).stream()
                .map(d -> new DayRow(d.getOccurredDay(), d.getTotal()))
                .toList();
    }
}
