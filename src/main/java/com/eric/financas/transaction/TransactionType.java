package com.eric.financas.transaction;

public enum TransactionType {
    INCOME(true),
    EXPENSE(false),
    TRANSFER_IN(true),
    TRANSFER_OUT(false);

    private final boolean credit;

    TransactionType(boolean credit) {
        this.credit = credit;
    }

    /** true = aumenta o saldo da conta; false = diminui. */
    public boolean isCredit() {
        return credit;
    }
}
