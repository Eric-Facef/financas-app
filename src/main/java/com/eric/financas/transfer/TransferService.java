package com.eric.financas.transfer;

import com.eric.financas.account.Account;
import com.eric.financas.account.AccountRepository;
import com.eric.financas.account.AccountType;
import com.eric.financas.account.BalanceCalculator;
import com.eric.financas.audit.AuditAction;
import com.eric.financas.audit.AuditService;
import com.eric.financas.common.exception.BusinessException;
import com.eric.financas.common.exception.NotFoundException;
import com.eric.financas.transaction.Transaction;
import com.eric.financas.transaction.TransactionRepository;
import com.eric.financas.transaction.TransactionType;
import com.eric.financas.transfer.dto.TransferRequest;
import com.eric.financas.transfer.dto.TransferResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Transferência entre contas (ex.: alocar a sobra do mês na poupança).
 * Gera duas linhas ligadas por transfer_id, tudo em uma única transação.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AuditService audit;

    @Transactional
    public TransferResponse transfer(UUID userId, TransferRequest req) {
        if (req.fromAccountId().equals(req.toAccountId())) {
            throw new BusinessException("Conta de origem e destino devem ser diferentes");
        }

        // Trava a conta de origem: duas transferências simultâneas não passam do saldo
        Account from = accounts.findByIdAndUserIdForUpdate(req.fromAccountId(), userId)
                .orElseThrow(() -> new NotFoundException("Conta de origem não encontrada"));
        Account to = accounts.findByIdAndUserId(req.toAccountId(), userId)
                .orElseThrow(() -> new NotFoundException("Conta de destino não encontrada"));

        BigDecimal balance = BalanceCalculator.balance(from.getInitialBalance(),
                transactions.totalsForAccount(from.getId()));
        if (balance.compareTo(req.amount()) < 0) {
            throw new BusinessException("Saldo insuficiente na conta de origem");
        }

        UUID transferId = UUID.randomUUID();
        LocalDate date = req.occurredOn() != null ? req.occurredOn() : LocalDate.now(ZONE);
        BigDecimal amount = req.amount().setScale(2);
        boolean hasDescription = req.description() != null && !req.description().isBlank();

        String outDescription = hasDescription ? req.description().trim() : "Transferência para " + to.getName();
        String inDescription = hasDescription ? req.description().trim() : "Transferência de " + from.getName();

        transactions.save(Transaction.transferLeg(userId, from, TransactionType.TRANSFER_OUT, amount,
                outDescription, date, transferId));
        transactions.save(Transaction.transferLeg(userId, to, TransactionType.TRANSFER_IN, amount,
                inDescription, date, transferId));

        AuditAction action = to.getType() == AccountType.SAVINGS
                ? AuditAction.ALOCACAO_POUPANCA : AuditAction.TRANSFERENCIA;
        audit.record(userId, action, "Transfer", transferId.toString(), Map.of(
                "valor", amount.toPlainString(),
                "origem", from.getName(),
                "destino", to.getName(),
                "descricao", outDescription));

        return new TransferResponse(transferId, amount, from.getName(), to.getName(), date);
    }
}
