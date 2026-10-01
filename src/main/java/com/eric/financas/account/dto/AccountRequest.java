package com.eric.financas.account.dto;

import com.eric.financas.account.AccountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull AccountType type,
        @Digits(integer = 15, fraction = 2) BigDecimal initialBalance
) {
}
