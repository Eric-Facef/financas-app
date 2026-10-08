package com.eric.financas.statement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StatementResponse(
        UUID accountId,
        String accountName,
        /** true = visão "Total" (todas as contas juntas) */
        boolean consolidated,
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        BigDecimal openingBalance,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal closingBalance,
        /** dias em ordem cronológica; cada um com seus lançamentos e o saldo do fim do dia */
        List<StatementDay> days,
        /** lançamentos com data futura: aparecem à parte e ainda não entram no saldo */
        List<StatementEntry> scheduled
) {
}
