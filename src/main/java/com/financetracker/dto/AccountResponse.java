package com.financetracker.dto;

import com.financetracker.entity.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(UUID id, String name, AccountType type, BigDecimal balance) {}
