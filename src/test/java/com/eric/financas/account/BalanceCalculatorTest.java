package com.eric.financas.account;

import com.eric.financas.transaction.AccountTypeTotal;
import com.eric.financas.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalanceCalculatorTest {

    private static AccountTypeTotal total(TransactionType type, String value) {
        UUID accountId = UUID.randomUUID();
        return new AccountTypeTotal() {
            public UUID getAccountId() { return accountId; }
            public TransactionType getEntryType() { return type; }
            public BigDecimal getTotal() { return new BigDecimal(value); }
        };
    }

    @Test
    void sumsCreditsAndSubtractsDebits() {
        var totals = List.of(
                total(TransactionType.INCOME, "3000.00"),
                total(TransactionType.EXPENSE, "1200.50"),
                total(TransactionType.TRANSFER_IN, "100.00"),
                total(TransactionType.TRANSFER_OUT, "500.00"));

        BigDecimal result = BalanceCalculator.balance(new BigDecimal("50.00"), totals);

        assertEquals(new BigDecimal("1449.50"), result);
    }

    @Test
    void returnsInitialBalanceWhenNoMovements() {
        assertEquals(new BigDecimal("10.00"), BalanceCalculator.balance(new BigDecimal("10.00"), List.of()));
    }
}
