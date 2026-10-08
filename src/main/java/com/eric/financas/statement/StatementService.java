package com.eric.financas.statement;

import com.eric.financas.account.Account;
import com.eric.financas.account.AccountRepository;
import com.eric.financas.account.BalanceCalculator;
import com.eric.financas.common.exception.BusinessException;
import com.eric.financas.common.exception.NotFoundException;
import com.eric.financas.statement.dto.StatementResponse;
import com.eric.financas.transaction.AccountTypeTotal;
import com.eric.financas.transaction.Transaction;
import com.eric.financas.transaction.TransactionRepository;
import com.eric.financas.transaction.TransactionSpecs;
import com.eric.financas.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Extrato por conta (accountId informado) ou total (accountId nulo).
 * Mesma regra de datas do saldo: o que tem data futura aparece em "agendados" e não entra no saldo.
 */
@Service
@RequiredArgsConstructor
public class StatementService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final long MAX_DAYS = 366;

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    @Transactional(readOnly = true)
    public StatementResponse generate(UUID userId, UUID accountId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZONE);
        YearMonth current = YearMonth.from(today);
        LocalDate start = from != null ? from : current.atDay(1);
        LocalDate end = to != null ? to : current.atEndOfMonth();
        if (end.isBefore(start)) {
            throw new BusinessException("A data final não pode ser anterior à data inicial");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw new BusinessException("O período máximo do extrato é de 1 ano");
        }

        boolean consolidated = accountId == null;
        List<Account> scope = consolidated
                ? accounts.findByUserIdOrderByNameAsc(userId)
                : List.of(accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada")));
        String title = consolidated ? "Todas as contas" : scope.get(0).getName();

        // Saldo anterior = saldo no fim do dia anterior ao período (limitado a hoje)
        LocalDate dayBefore = start.minusDays(1);
        LocalDate openingAsOf = dayBefore.isBefore(today) ? dayBefore : today;
        Map<UUID, List<AccountTypeTotal>> totals = transactions.totalsByAccount(userId, openingAsOf).stream()
                .collect(Collectors.groupingBy(AccountTypeTotal::getAccountId));
        BigDecimal opening = scope.stream()
                .map(a -> BalanceCalculator.balance(a.getInitialBalance(), totals.getOrDefault(a.getId(), List.of())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var spec = TransactionSpecs.filter(userId, start, end, null, null, consolidated ? null : accountId);
        List<Transaction> rows = transactions.findAll(spec, Sort.by(Sort.Direction.ASC, "occurredOn", "createdAt"));

        Map<UUID, StatementCalculator.TransferInfo> transfers = transferInfo(userId, rows);
        List<StatementCalculator.Movement> ledger = new ArrayList<>();
        List<StatementCalculator.Movement> upcoming = new ArrayList<>();
        for (Transaction t : rows) {
            StatementCalculator.Movement movement = toMovement(t);
            if (t.getOccurredOn().isAfter(today)) {
                upcoming.add(movement);
            } else {
                ledger.add(movement);
            }
        }

        StatementCalculator.Result result = StatementCalculator.build(opening, ledger, transfers, consolidated);
        return new StatementResponse(
                consolidated ? null : accountId, title, consolidated, start, end, Instant.now(),
                opening, result.totalIn(), result.totalOut(), result.closingBalance(),
                result.days(), StatementCalculator.scheduled(upcoming, transfers, consolidated));
    }

    /** Descobre origem e destino de cada transferência (na visão por conta só uma perna está na lista). */
    private Map<UUID, StatementCalculator.TransferInfo> transferInfo(UUID userId, List<Transaction> rows) {
        Set<UUID> ids = rows.stream().map(Transaction::getTransferId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String[]> names = new HashMap<>();
        for (Transaction leg : transactions.findByUserIdAndTransferIdInAndDeletedAtIsNull(userId, ids)) {
            String[] pair = names.computeIfAbsent(leg.getTransferId(), k -> new String[2]);
            if (leg.getType() == TransactionType.TRANSFER_OUT) {
                pair[0] = leg.getAccount().getName();
            } else if (leg.getType() == TransactionType.TRANSFER_IN) {
                pair[1] = leg.getAccount().getName();
            }
        }
        Map<UUID, StatementCalculator.TransferInfo> result = new HashMap<>();
        names.forEach((id, pair) -> result.put(id, new StatementCalculator.TransferInfo(
                Objects.requireNonNullElse(pair[0], "?"), Objects.requireNonNullElse(pair[1], "?"))));
        return result;
    }

    private static StatementCalculator.Movement toMovement(Transaction t) {
        var category = t.getCategory();
        return new StatementCalculator.Movement(
                t.getId(), t.getOccurredOn(), t.getType(), t.getAmount(), t.getDescription(),
                t.getAccount().getName(),
                category != null ? category.getName() : null,
                category != null ? category.getIcon() : null,
                t.getTransferId());
    }
}
