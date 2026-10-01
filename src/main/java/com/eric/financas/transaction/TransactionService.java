package com.eric.financas.transaction;

import com.eric.financas.account.Account;
import com.eric.financas.account.AccountRepository;
import com.eric.financas.audit.AuditAction;
import com.eric.financas.audit.AuditService;
import com.eric.financas.category.Category;
import com.eric.financas.category.CategoryKind;
import com.eric.financas.category.CategoryRepository;
import com.eric.financas.common.exception.BusinessException;
import com.eric.financas.common.exception.NotFoundException;
import com.eric.financas.common.web.PageResponse;
import com.eric.financas.transaction.dto.TransactionFilter;
import com.eric.financas.transaction.dto.TransactionRequest;
import com.eric.financas.transaction.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(UUID userId, TransactionFilter filter, int page, int size) {
        YearMonth month = filter.month() != null ? filter.month() : YearMonth.now(ZONE);
        var spec = TransactionSpecs.filter(userId, month.atDay(1), month.atEndOfMonth(),
                filter.type(), filter.categoryId(), filter.accountId());
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "occurredOn", "createdAt"));

        return PageResponse.from(transactions.findAll(spec, pageable), TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID userId, UUID id) {
        return TransactionResponse.from(findOwned(userId, id));
    }

    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest req) {
        Transaction t = new Transaction();
        t.setUserId(userId);
        apply(t, userId, req);
        transactions.save(t);

        audit.record(userId, AuditAction.NOVO_LANCAMENTO, "Transaction", t.getId().toString(), details(t));
        return TransactionResponse.from(t);
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID id, TransactionRequest req) {
        Transaction t = findOwned(userId, id);
        if (t.getTransferId() != null) {
            throw new BusinessException("Transferências não podem ser editadas; exclua e refaça");
        }
        apply(t, userId, req);

        audit.record(userId, AuditAction.EDITAR_LANCAMENTO, "Transaction", t.getId().toString(), details(t));
        return TransactionResponse.from(t);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Transaction t = findOwned(userId, id);
        Instant now = Instant.now();

        if (t.getTransferId() != null) {
            // Apagar uma perna da transferência apaga as duas, para não desbalancear as contas
            transactions.findByTransferIdAndUserIdAndDeletedAtIsNull(t.getTransferId(), userId)
                    .forEach(leg -> leg.setDeletedAt(now));
        } else {
            t.setDeletedAt(now);
        }
        audit.record(userId, AuditAction.EXCLUIR_LANCAMENTO, "Transaction", t.getId().toString(), details(t));
    }

    // ---------------------------------------------------------------- helpers

    private Transaction findOwned(UUID userId, UUID id) {
        return transactions.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new NotFoundException("Lançamento não encontrado"));
    }

    private void apply(Transaction t, UUID userId, TransactionRequest req) {
        if (req.type() != TransactionType.INCOME && req.type() != TransactionType.EXPENSE) {
            throw new BusinessException("Para transferências use /api/v1/transfers");
        }

        Account account = accounts.findByIdAndUserId(req.accountId(), userId)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));
        Category category = categories.findByIdAndUserId(req.categoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));

        boolean categoryIsIncome = category.getKind() == CategoryKind.INCOME;
        if (categoryIsIncome != (req.type() == TransactionType.INCOME)) {
            throw new BusinessException("A categoria não é compatível com o tipo do lançamento");
        }

        t.setAccount(account);
        t.setCategory(category);
        t.setType(req.type());
        t.setAmount(req.amount().setScale(2));
        t.setDescription(req.description().trim());
        t.setOccurredOn(req.occurredOn() != null ? req.occurredOn() : LocalDate.now(ZONE));
    }

    private static Map<String, String> details(Transaction t) {
        return Map.of(
                "tipo", t.getType().name(),
                "valor", t.getAmount().toPlainString(),
                "descricao", t.getDescription());
    }
}
