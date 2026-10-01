package com.eric.financas.dashboard.dto;

import com.eric.financas.account.dto.AccountResponse;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        String month,
        List<AccountResponse> accounts,
        BigDecimal totalBalance,
        BigDecimal income,
        BigDecimal expenses,
        /** receitas - despesas do mês */
        BigDecimal leftover,
        /** quanto da sobra já foi movido para contas poupança no mês */
        BigDecimal allocatedToSavings,
        /** sobra ainda disponível para alocar */
        BigDecimal availableLeftover,
        List<CategoryShare> expensesByCategory
) {
}
