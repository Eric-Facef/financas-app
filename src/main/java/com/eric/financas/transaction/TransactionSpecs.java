package com.eric.financas.transaction;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Filtros dinâmicos da listagem (evita a bagunça de "param is null or ..." em JPQL). */
public final class TransactionSpecs {

    private TransactionSpecs() {
    }

    public static Specification<Transaction> filter(UUID userId, LocalDate from, LocalDate to,
                                                    TransactionType type, UUID categoryId, UUID accountId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.between(root.<LocalDate>get("occurredOn"), from, to));
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("account").get("id"), accountId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
