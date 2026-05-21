package com.financetracker.dto;

import java.math.BigDecimal;

public record BudgetStatusResponse(BudgetResponse budget, BigDecimal percentUsed) {}
