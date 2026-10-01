package com.eric.financas.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** O tipo da conta (corrente/poupança) não muda depois de criada. */
public record AccountUpdateRequest(
        @NotBlank @Size(max = 80) String name,
        @Digits(integer = 15, fraction = 2) BigDecimal initialBalance
) {
}
