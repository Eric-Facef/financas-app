package com.eric.financas.statement;

import com.eric.financas.common.security.AuthenticatedUser;
import com.eric.financas.statement.dto.StatementResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Extrato")
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService service;

    /** accountId vazio = extrato TOTAL (todas as contas). Datas no formato AAAA-MM-DD; padrão = mês atual. */
    @GetMapping
    public StatementResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam(required = false) UUID accountId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.generate(user.id(), accountId, from, to);
    }
}
