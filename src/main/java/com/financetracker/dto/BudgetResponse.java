package com.financetracker.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        String category,
        BigDecimal limitAmount,
        BigDecimal spentAmount,
        int month,
        int year
) {}
