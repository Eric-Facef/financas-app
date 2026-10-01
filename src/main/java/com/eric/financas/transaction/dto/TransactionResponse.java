package com.eric.financas.transaction.dto;

import com.eric.financas.transaction.Transaction;
import com.eric.financas.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate occurredOn,
        UUID accountId,
        String accountName,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        UUID transferId,
        Instant createdAt
) {
    /** Chamar dentro de uma transação (acessa associações lazy). */
    public static TransactionResponse from(Transaction t) {
        var category = t.getCategory();
        return new TransactionResponse(
                t.getId(), t.getType(), t.getAmount(), t.getDescription(), t.getOccurredOn(),
                t.getAccount().getId(), t.getAccount().getName(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                category != null ? category.getIcon() : null,
                category != null ? category.getColor() : null,
                t.getTransferId(), t.getCreatedAt());
    }
}
