package com.eric.financas.account;

import com.eric.financas.account.dto.AccountRequest;
import com.eric.financas.account.dto.AccountResponse;
import com.eric.financas.audit.AuditAction;
import com.eric.financas.audit.AuditService;
import com.eric.financas.common.exception.ConflictException;
import com.eric.financas.transaction.AccountTypeTotal;
import com.eric.financas.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId) {
        Map<UUID, List<AccountTypeTotal>> totals = transactions.totalsByAccount(userId).stream()
                .collect(Collectors.groupingBy(AccountTypeTotal::getAccountId));

        return accounts.findByUserIdOrderByNameAsc(userId).stream()
                .map(a -> AccountResponse.from(a,
                        BalanceCalculator.balance(a.getInitialBalance(), totals.getOrDefault(a.getId(), List.of()))))
                .toList();
    }

    @Transactional
    public AccountResponse create(UUID userId, AccountRequest req) {
        String name = req.name().trim();
        if (accounts.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("Já existe uma conta com esse nome");
        }
        BigDecimal initial = req.initialBalance() != null ? req.initialBalance().setScale(2) : BigDecimal.ZERO.setScale(2);

        Account account = accounts.save(new Account(userId, name, req.type(), initial));
        audit.record(userId, AuditAction.CONTA_CRIADA, "Account", account.getId().toString(),
                Map.of("nome", name, "tipo", req.type().name()));
        return AccountResponse.from(account, initial);
    }
}
