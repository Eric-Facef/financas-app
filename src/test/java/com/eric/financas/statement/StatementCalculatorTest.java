package com.eric.financas.statement;

import com.eric.financas.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementCalculatorTest {

    private static StatementCalculator.Movement movement(TransactionType type, String amount, String day,
                                                         UUID transferId, String account) {
        return new StatementCalculator.Movement(UUID.randomUUID(), LocalDate.parse(day), type,
                new BigDecimal(amount), "teste", account, null, null, transferId);
    }

    @Test
    void computesRunningBalanceForSingleAccount() {
        UUID transferId = UUID.randomUUID();
        List<StatementCalculator.Movement> movements = List.of(
                movement(TransactionType.INCOME, "1000.00", "2026-10-01", null, "Santander"),
                movement(TransactionType.EXPENSE, "200.00", "2026-10-01", null, "Santander"),
                movement(TransactionType.TRANSFER_OUT, "300.00", "2026-10-02", transferId, "Santander"));
        Map<UUID, StatementCalculator.TransferInfo> transfers =
                Map.of(transferId, new StatementCalculator.TransferInfo("Santander", "Poupança"));

        StatementCalculator.Result result =
                StatementCalculator.build(new BigDecimal("50.00"), movements, transfers, false);

        assertEquals(new BigDecimal("1000.00"), result.totalIn());
        assertEquals(new BigDecimal("500.00"), result.totalOut());
        assertEquals(new BigDecimal("550.00"), result.closingBalance());
        assertEquals(2, result.days().size());
        assertEquals(new BigDecimal("850.00"), result.days().get(0).closingBalance());
        assertEquals("Para Poupança", result.days().get(1).entries().get(0).label());
    }

    @Test
    void consolidatedViewCollapsesInternalTransfers() {
        UUID transferId = UUID.randomUUID();
        List<StatementCalculator.Movement> movements = List.of(
                movement(TransactionType.INCOME, "100.00", "2026-10-01", null, "Nubank"),
                movement(TransactionType.TRANSFER_OUT, "40.00", "2026-10-01", transferId, "Nubank"),
                movement(TransactionType.TRANSFER_IN, "40.00", "2026-10-01", transferId, "Poupança"));
        Map<UUID, StatementCalculator.TransferInfo> transfers =
                Map.of(transferId, new StatementCalculator.TransferInfo("Nubank", "Poupança"));

        StatementCalculator.Result result =
                StatementCalculator.build(BigDecimal.ZERO, movements, transfers, true);

        assertEquals(new BigDecimal("100.00"), result.totalIn());
        assertEquals(BigDecimal.ZERO, result.totalOut());
        assertEquals(new BigDecimal("100.00"), result.closingBalance());
        assertEquals(2, result.days().get(0).entries().size());
        assertTrue(result.days().get(0).entries().get(1).internal());
    }
}
