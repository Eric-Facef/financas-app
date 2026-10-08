package com.eric.financas.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Projeção: total de um tipo de lançamento por dia do calendário. */
public interface DailyTotal {
    LocalDate getOccurredDay();

    BigDecimal getTotal();

    Long getEntries();
}
