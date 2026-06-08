package com.financetracker.service;

import com.financetracker.entity.Budget;
import java.math.BigDecimal;

// A Java record is an immutable data carrier — no boilerplate needed.
// Used here as an internal service return value, not an API response (that's a DTO's job).
// spentAmount is computed from transactions; percentUsed is spent/limit * 100.
public record BudgetStatus(Budget budget, BigDecimal spentAmount, BigDecimal percentUsed) {}
