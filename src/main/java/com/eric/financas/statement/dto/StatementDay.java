package com.eric.financas.statement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StatementDay(LocalDate date, List<StatementEntry> entries, BigDecimal closingBalance) {
}
