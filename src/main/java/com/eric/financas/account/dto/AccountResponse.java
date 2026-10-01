package com.eric.financas.account.dto;

import com.eric.financas.account.Account;
import com.eric.financas.account.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(UUID id, String name, AccountType type, BigDecimal balance, BigDecimal initialBalance) {

    public static AccountResponse from(Account account, BigDecimal balance) {
        return new AccountResponse(account.getId(), account.getName(), account.getType(), balance,
                account.getInitialBalance());
    }
}
