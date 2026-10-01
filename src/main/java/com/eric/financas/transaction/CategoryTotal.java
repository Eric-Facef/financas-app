package com.eric.financas.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/** Projeção: total gasto por categoria no período. */
public interface CategoryTotal {
    UUID getCategoryId();

    String getCategoryName();

    String getIcon();

    String getColor();

    BigDecimal getTotal();
}
