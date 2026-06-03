package com.financetracker.dto;

// Returned by POST /api/ai/categorize → { "category": "Entertainment" }
public record CategorizeResponse(String category) {}
