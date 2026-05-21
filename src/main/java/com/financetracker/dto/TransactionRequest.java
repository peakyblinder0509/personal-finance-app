package com.financetracker.dto;

import com.financetracker.entity.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionRequest(
        @NotNull UUID accountId,
        @NotNull @Positive BigDecimal amount,
        @NotNull TransactionType type,
        @NotBlank String category,
        String description,      // optional — no @NotBlank
        @NotNull LocalDate date
) {}
