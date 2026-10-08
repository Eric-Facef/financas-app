package com.eric.financas.statement.dto;

import com.eric.financas.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha do extrato.
 * amount = valor sempre positivo; delta = efeito no saldo exibido (+ entrada, - saída, 0 em transferência interna).
 * balanceAfter = saldo corrido depois desta linha (nulo nos lançamentos agendados).
 */
public record StatementEntry(
        UUID id,
        LocalDate date,
        TransactionType type,
        boolean internal,
        String description,
        String label,
        String accountName,
        String categoryName,
        String categoryIcon,
        BigDecimal amount,
        BigDecimal delta,
        BigDecimal balanceAfter
) {
}
