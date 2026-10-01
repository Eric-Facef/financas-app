package com.eric.financas.account;

import com.eric.financas.transaction.AccountTypeTotal;

import java.math.BigDecimal;
import java.util.Collection;

/** Saldo = saldo inicial + créditos - débitos (nunca guardado, sempre derivado do histórico). */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    public static BigDecimal balance(BigDecimal initialBalance, Collection<AccountTypeTotal> totals) {
        BigDecimal result = initialBalance;
        for (AccountTypeTotal t : totals) {
            result = t.getEntryType().isCredit() ? result.add(t.getTotal()) : result.subtract(t.getTotal());
        }
        return result;
    }
}
