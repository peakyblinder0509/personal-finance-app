package com.financetracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body for POST /api/ai/categorize → { "description": "Netflix 15.99" }
// @Size caps the length so a caller can't send a huge blob of text to the AI.
public record CategorizeRequest(
        @NotBlank @Size(max = 200) String description
) {}
