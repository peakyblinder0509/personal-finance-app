package com.financetracker.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BudgetRequest(
        @NotBlank String category,
        @NotNull @Positive BigDecimal limitAmount,
        @Min(1) @Max(12) int month,
        @Min(2000) int year
) {}
