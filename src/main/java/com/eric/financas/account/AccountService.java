package com.eric.financas.account;

import com.eric.financas.account.dto.AccountRequest;
import com.eric.financas.account.dto.AccountResponse;
import com.eric.financas.account.dto.AccountUpdateRequest;
import com.eric.financas.audit.AuditAction;
import com.eric.financas.audit.AuditService;
import com.eric.financas.common.exception.BusinessException;
import com.eric.financas.common.exception.ConflictException;
import com.eric.financas.common.exception.NotFoundException;
import com.eric.financas.transaction.AccountTypeTotal;
import com.eric.financas.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AuditService audit;

    /** Saldos "de hoje": lançamentos com data futura ainda não entram. */
    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId) {
        Map<UUID, List<AccountTypeTotal>> totals = transactions.totalsByAccount(userId, LocalDate.now(ZONE)).stream()
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

    @Transactional
    public AccountResponse update(UUID userId, UUID id, AccountUpdateRequest req) {
        Account account = findOwned(userId, id);
        String name = req.name().trim();
        if (!account.getName().equalsIgnoreCase(name) && accounts.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("Já existe uma conta com esse nome");
        }
        account.setName(name);
        if (req.initialBalance() != null) {
            account.setInitialBalance(req.initialBalance().setScale(2));
        }

        audit.record(userId, AuditAction.CONTA_EDITADA, "Account", id.toString(), Map.of("nome", name));
        BigDecimal balance = BalanceCalculator.balance(account.getInitialBalance(),
                transactions.totalsForAccount(id, LocalDate.now(ZONE)));
        return AccountResponse.from(account, balance);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Account account = findOwned(userId, id);
        if (transactions.existsByAccountId(id)) {
            throw new BusinessException("A conta possui lançamentos e não pode ser excluída");
        }
        accounts.delete(account);
        audit.record(userId, AuditAction.CONTA_EXCLUIDA, "Account", id.toString(), Map.of("nome", account.getName()));
    }

    private Account findOwned(UUID userId, UUID id) {
        return accounts.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));
    }
}
