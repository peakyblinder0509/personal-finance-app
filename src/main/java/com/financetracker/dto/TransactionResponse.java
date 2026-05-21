package com.financetracker.dto;

import com.financetracker.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        BigDecimal amount,
        TransactionType type,
        String category,
        String description,
        LocalDate date
) {}
