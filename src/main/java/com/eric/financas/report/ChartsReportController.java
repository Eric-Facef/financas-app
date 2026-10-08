package com.eric.financas.report;

import com.eric.financas.common.security.AuthenticatedUser;
import com.eric.financas.report.dto.CategoryCompareReport;
import com.eric.financas.report.dto.MonthlyReport;
import com.eric.financas.report.dto.NetWorthReport;
import com.eric.financas.report.dto.PaceReport;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Gráficos extras da aba Relatórios. Fica separado do {@link ReportController} (mesmo prefixo, rotas diferentes)
 * para não alterar o que já funciona. Todas as rotas exigem login (anyRequest().authenticated()).
 */
@Tag(name = "Relatórios")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ChartsReportController {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final ChartsReportService service;

    /** Receitas x despesas dos últimos {@code months} meses (2 a 12) até o mês informado. */
    @GetMapping("/monthly")
    public MonthlyReport monthly(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                 @RequestParam(defaultValue = "6") int months) {
        return service.monthly(user.id(), orNow(month), clamp(months));
    }

    /** Despesa acumulada por dia: mês informado x mês anterior. */
    @GetMapping("/pace")
    public PaceReport pace(@AuthenticationPrincipal AuthenticatedUser user,
                           @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.pace(user.id(), orNow(month));
    }

    /** Despesas por categoria: mês informado x mês anterior. */
    @GetMapping("/categories")
    public CategoryCompareReport categories(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.categories(user.id(), orNow(month));
    }

    /** Saldo no fim de cada um dos últimos {@code months} meses (2 a 12), contas x poupança. */
    @GetMapping("/net-worth")
    public NetWorthReport netWorth(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                   @RequestParam(defaultValue = "12") int months) {
        return service.netWorth(user.id(), orNow(month), clamp(months));
    }

    private static YearMonth orNow(YearMonth month) {
        return month != null ? month : YearMonth.now(ZONE);
    }

    private static int clamp(int months) {
        return Math.max(2, Math.min(months, 12));
    }
}
