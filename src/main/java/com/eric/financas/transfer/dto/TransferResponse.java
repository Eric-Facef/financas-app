package com.eric.financas.transfer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransferResponse(UUID transferId, BigDecimal amount, String fromAccount, String toAccount,
                               LocalDate occurredOn) {
}
