package com.financetracker.dto;

// Returned by GET /api/ai/spending-summary → { "summary": "You spent $340 on food..." }
public record SpendingSummaryResponse(String summary) {}
