package com.eric.financas.transaction.dto;

import com.eric.financas.transaction.TransactionType;

import java.time.YearMonth;
import java.util.UUID;

public record TransactionFilter(YearMonth month, TransactionType type, UUID categoryId, UUID accountId) {
}
