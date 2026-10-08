package com.eric.financas.report;

import com.eric.financas.common.security.AuthenticatedUser;
import com.eric.financas.report.dto.WeekdayReport;
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

@Tag(name = "Relatórios")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final ReportService service;

    @GetMapping("/weekday")
    public WeekdayReport weekday(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.weekday(user.id(), month != null ? month : YearMonth.now(ZONE));
    }
}
