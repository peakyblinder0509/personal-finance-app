package com.financetracker.dto;

// Returned by GET /api/ai/budget-advice → { "advice": "You could save $200/month..." }
public record BudgetAdviceResponse(String advice) {}
