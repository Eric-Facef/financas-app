package com.eric.financas.dashboard;

import com.eric.financas.common.security.AuthenticatedUser;
import com.eric.financas.dashboard.dto.DashboardResponse;
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

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final DashboardService service;

    @GetMapping
    public DashboardResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.get(user.id(), month != null ? month : YearMonth.now(ZONE));
    }
}
