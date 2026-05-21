package com.financetracker.dto;

import com.financetracker.entity.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

// PUT uses a separate DTO from POST — accountId is intentionally absent
// because a transaction cannot be moved to a different account after creation.
public record TransactionUpdateRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull TransactionType type,
        @NotBlank String category,
        String description,
        @NotNull LocalDate date
) {}
