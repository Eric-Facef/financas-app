package com.eric.financas.transaction.dto;

import com.eric.financas.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Apenas INCOME e EXPENSE; transferências têm endpoint próprio (/api/v1/transfers). */
public record TransactionRequest(
        @NotNull UUID accountId,
        @NotNull UUID categoryId,
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 15, fraction = 2, message = "Use no máximo 2 casas decimais") BigDecimal amount,
        @NotBlank @Size(max = 160) String description,
        LocalDate occurredOn
) {
}
