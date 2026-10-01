package com.eric.financas.dashboard;

import com.eric.financas.account.AccountService;
import com.eric.financas.account.AccountType;
import com.eric.financas.account.dto.AccountResponse;
import com.eric.financas.dashboard.dto.CategoryShare;
import com.eric.financas.dashboard.dto.DashboardResponse;
import com.eric.financas.transaction.TransactionRepository;
import com.eric.financas.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AccountService accountService;
    private final TransactionRepository transactions;

    @Transactional(readOnly = true)
    public DashboardResponse get(UUID userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<AccountResponse> accounts = accountService.list(userId);
        BigDecimal totalBalance = accounts.stream().map(AccountResponse::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal income = orZero(transactions.sumByType(userId, TransactionType.INCOME, start, end));
        BigDecimal expenses = orZero(transactions.sumByType(userId, TransactionType.EXPENSE, start, end));
        BigDecimal allocated = orZero(transactions.sumByTypeAndAccountType(
                userId, TransactionType.TRANSFER_IN, AccountType.SAVINGS, start, end));

        BigDecimal leftover = income.subtract(expenses);

        List<CategoryShare> byCategory = transactions
                .totalsByCategory(userId, TransactionType.EXPENSE, start, end).stream()
                .map(c -> new CategoryShare(c.getCategoryId(), c.getCategoryName(), c.getIcon(), c.getColor(),
                        c.getTotal(), percentage(c.getTotal(), expenses)))
                .toList();

        return new DashboardResponse(month.toString(), accounts, totalBalance, income, expenses,
                leftover, allocated, leftover.subtract(allocated), byCategory);
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO);
    }
}
