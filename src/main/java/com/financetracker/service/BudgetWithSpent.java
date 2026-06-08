package com.financetracker.service;

import com.financetracker.entity.Budget;
import java.math.BigDecimal;

// Pairs a budget with its computed spent amount (sum of EXPENSE transactions in
// the same category/month). Like BudgetStatus and AccountWithBalance, this is an
// internal service return value, not an API DTO — spent is derived, never stored.
public record BudgetWithSpent(Budget budget, BigDecimal spentAmount) {}
