package com.eric.financas.statement;

import com.eric.financas.statement.dto.StatementDay;
import com.eric.financas.statement.dto.StatementEntry;
import com.eric.financas.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monta o extrato a partir de uma lista de movimentos JÁ ordenada por data. É uma classe "pura"
 * (sem banco nem Spring), por isso é fácil de testar.
 * <p>
 * Visão por conta: toda transferência mexe no saldo (como num extrato de banco).
 * Visão total: transferência entre as próprias contas não muda o total, então vira UMA linha neutra.
 */
public final class StatementCalculator {

    private StatementCalculator() {
    }

    public record Movement(UUID id, LocalDate date, TransactionType type, BigDecimal amount, String description,
                           String accountName, String categoryName, String categoryIcon, UUID transferId) {
    }

    public record TransferInfo(String from, String to) {
    }

    public record Result(List<StatementDay> days, BigDecimal totalIn, BigDecimal totalOut,
                         BigDecimal closingBalance) {
    }

    public static Result build(BigDecimal opening, List<Movement> movements, Map<UUID, TransferInfo> transfers,
                               boolean consolidated) {
        BigDecimal balance = opening;
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        Map<LocalDate, List<StatementEntry>> byDay = new LinkedHashMap<>();

        for (Movement m : movements) {
            if (isHiddenLeg(m, consolidated)) {
                continue;
            }
            BigDecimal delta = delta(m, consolidated);
            balance = balance.add(delta);
            if (delta.signum() > 0) {
                totalIn = totalIn.add(delta);
            } else if (delta.signum() < 0) {
                totalOut = totalOut.add(delta.negate());
            }
            byDay.computeIfAbsent(m.date(), d -> new ArrayList<>())
                    .add(entry(m, transfers, consolidated, delta, balance));
        }

        List<StatementDay> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<StatementEntry>> e : byDay.entrySet()) {
            List<StatementEntry> list = e.getValue();
            days.add(new StatementDay(e.getKey(), list, list.get(list.size() - 1).balanceAfter()));
        }
        return new Result(days, totalIn, totalOut, balance);
    }

    /** Lançamentos futuros: mesma aparência, mas sem saldo (ainda não aconteceram). */
    public static List<StatementEntry> scheduled(List<Movement> movements, Map<UUID, TransferInfo> transfers,
                                                 boolean consolidated) {
        List<StatementEntry> result = new ArrayList<>();
        for (Movement m : movements) {
            if (isHiddenLeg(m, consolidated)) {
                continue;
            }
            result.add(entry(m, transfers, consolidated, delta(m, consolidated), null));
        }
        return result;
    }

    /** Na visão total, só a perna de saída da transferência aparece (a de entrada é omitida). */
    private static boolean isHiddenLeg(Movement m, boolean consolidated) {
        return consolidated && m.type() == TransactionType.TRANSFER_IN;
    }

    private static BigDecimal delta(Movement m, boolean consolidated) {
        return switch (m.type()) {
            case INCOME -> m.amount();
            case EXPENSE -> m.amount().negate();
            case TRANSFER_IN -> consolidated ? BigDecimal.ZERO : m.amount();
            case TRANSFER_OUT -> consolidated ? BigDecimal.ZERO : m.amount().negate();
        };
    }

    private static StatementEntry entry(Movement m, Map<UUID, TransferInfo> transfers, boolean consolidated,
                                        BigDecimal delta, BigDecimal balanceAfter) {
        boolean transfer = m.type() == TransactionType.TRANSFER_IN || m.type() == TransactionType.TRANSFER_OUT;
        String label = null;
        if (transfer && m.transferId() != null) {
            TransferInfo info = transfers.get(m.transferId());
            if (info != null) {
                if (consolidated) {
                    label = info.from() + " → " + info.to();
                } else if (m.type() == TransactionType.TRANSFER_OUT) {
                    label = "Para " + info.to();
                } else {
                    label = "De " + info.from();
                }
            }
        }
        return new StatementEntry(m.id(), m.date(), m.type(), transfer && consolidated, m.description(), label,
                m.accountName(), m.categoryName(), m.categoryIcon(), m.amount(), delta, balanceAfter);
    }
}
