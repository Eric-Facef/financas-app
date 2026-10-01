package com.eric.financas.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.eric.financas.account.AccountType;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<Transaction> findByTransferIdAndUserIdAndDeletedAtIsNull(UUID transferId, UUID userId);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByAccountId(UUID accountId);

    /** Evita N+1 ao montar a listagem (conta e categoria vêm no mesmo SELECT). */
    @Override
    @EntityGraph(attributePaths = {"account", "category"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    @Query("""
            select t.account.id as accountId, t.type as entryType, sum(t.amount) as total
            from Transaction t
            where t.userId = :userId and t.deletedAt is null and t.occurredOn <= :asOf
            group by t.account.id, t.type
            """)
    List<AccountTypeTotal> totalsByAccount(@Param("userId") UUID userId, @Param("asOf") LocalDate asOf);

    @Query("""
            select t.account.id as accountId, t.type as entryType, sum(t.amount) as total
            from Transaction t
            where t.account.id = :accountId and t.deletedAt is null and t.occurredOn <= :asOf
            group by t.account.id, t.type
            """)
    List<AccountTypeTotal> totalsForAccount(@Param("accountId") UUID accountId, @Param("asOf") LocalDate asOf);

    @Query("""
            select sum(t.amount) from Transaction t
            where t.userId = :userId and t.type = :type and t.deletedAt is null
              and t.occurredOn between :periodStart and :periodEnd
            """)
    BigDecimal sumByType(@Param("userId") UUID userId,
                         @Param("type") TransactionType type,
                         @Param("periodStart") LocalDate periodStart,
                         @Param("periodEnd") LocalDate periodEnd);

    @Query("""
            select sum(t.amount) from Transaction t
            where t.userId = :userId and t.type = :type and t.account.type = :accountType
              and t.deletedAt is null
              and t.occurredOn between :periodStart and :periodEnd
            """)
    BigDecimal sumByTypeAndAccountType(@Param("userId") UUID userId,
                                       @Param("type") TransactionType type,
                                       @Param("accountType") AccountType accountType,
                                       @Param("periodStart") LocalDate periodStart,
                                       @Param("periodEnd") LocalDate periodEnd);

    @Query("""
            select c.id as categoryId, c.name as categoryName, c.icon as icon, c.color as color,
                   sum(t.amount) as total
            from Transaction t join t.category c
            where t.userId = :userId and t.type = :type and t.deletedAt is null
              and t.occurredOn between :periodStart and :periodEnd
            group by c.id, c.name, c.icon, c.color
            order by sum(t.amount) desc
            """)
    List<CategoryTotal> totalsByCategory(@Param("userId") UUID userId,
                                         @Param("type") TransactionType type,
                                         @Param("periodStart") LocalDate periodStart,
                                         @Param("periodEnd") LocalDate periodEnd);
}
