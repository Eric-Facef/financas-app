package com.eric.financas.transaction;

import com.eric.financas.account.Account;
import com.eric.financas.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 160)
    private String description;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    /** Liga as duas pernas (saída/entrada) de uma transferência. */
    @Column(name = "transfer_id")
    private UUID transferId;

    /** Exclusão lógica: preserva histórico e auditoria. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Transaction transferLeg(UUID userId, Account account, TransactionType type, BigDecimal amount,
                                          String description, LocalDate date, UUID transferId) {
        Transaction t = new Transaction();
        t.userId = userId;
        t.account = account;
        t.type = type;
        t.amount = amount;
        t.description = description;
        t.occurredOn = date;
        t.transferId = transferId;
        return t;
    }
}
