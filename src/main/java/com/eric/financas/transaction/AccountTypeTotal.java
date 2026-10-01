package com.eric.financas.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/** Projeção: total movimentado por conta e tipo (usada para calcular saldos). */
public interface AccountTypeTotal {
    UUID getAccountId();

    TransactionType getEntryType();

    BigDecimal getTotal();
}
