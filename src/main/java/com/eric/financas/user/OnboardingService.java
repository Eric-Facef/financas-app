package com.eric.financas.user;

import com.eric.financas.account.Account;
import com.eric.financas.account.AccountRepository;
import com.eric.financas.account.AccountType;
import com.eric.financas.category.Category;
import com.eric.financas.category.CategoryKind;
import com.eric.financas.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Dados iniciais de um novo usuário: contas e categorias padrão.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final AccountRepository accounts;
    private final CategoryRepository categories;

    @Transactional
    public void createDefaults(UUID userId) {
        accounts.saveAll(List.of(
                new Account(userId, "Conta Corrente", AccountType.CHECKING, BigDecimal.ZERO),
                new Account(userId, "Poupança", AccountType.SAVINGS, BigDecimal.ZERO)));

        categories.saveAll(List.of(
                new Category(userId, "Alimentação", CategoryKind.EXPENSE, "🛒", "#f59e0b"),
                new Category(userId, "Transporte", CategoryKind.EXPENSE, "🚗", "#3b82f6"),
                new Category(userId, "Moradia", CategoryKind.EXPENSE, "🏠", "#6366f1"),
                new Category(userId, "Lazer", CategoryKind.EXPENSE, "🎯", "#a855f7"),
                new Category(userId, "Outros", CategoryKind.EXPENSE, "💳", "#64748b"),
                new Category(userId, "Salário", CategoryKind.INCOME, "💰", "#10b981"),
                new Category(userId, "Outras receitas", CategoryKind.INCOME, "💵", "#14b8a6")));
    }
}
