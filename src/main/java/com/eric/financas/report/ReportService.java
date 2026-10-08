package com.eric.financas.report;

import com.eric.financas.report.dto.WeekdayReport;
import com.eric.financas.transaction.TransactionRepository;
import com.eric.financas.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final TransactionRepository transactions;

    /**
     * Despesas do mês por dia da semana. O banco devolve no máximo ~31 linhas (uma por dia com gasto);
     * o agrupamento por dia da semana é feito em Java, sem funções específicas do PostgreSQL.
     */
    @Transactional(readOnly = true)
    public WeekdayReport weekday(UUID userId, YearMonth month) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = month.atDay(1);
        LocalDate end = WeekdayCalculator.lastDay(month, today);

        List<WeekdayCalculator.Row> rows = end.isBefore(start)
                ? List.of()   // mês futuro: nada aconteceu ainda
                : transactions.dailyTotals(userId, TransactionType.EXPENSE, start, end).stream()
                        .map(d -> new WeekdayCalculator.Row(d.getOccurredDay(), d.getTotal(), d.getEntries()))
                        .toList();
        return WeekdayCalculator.build(month, today, rows);
    }
}
