package com.financetracker.controller;

import com.financetracker.dto.BudgetAdviceResponse;
import com.financetracker.dto.CategorizeRequest;
import com.financetracker.dto.CategorizeResponse;
import com.financetracker.dto.SpendingSummaryResponse;
import com.financetracker.service.AiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/**
 * HTTP layer for the AI features. Per the project's architecture rule, this class
 * does NO business logic — it only maps requests to {@link AiService} calls and
 * wraps the results in response DTOs.
 *
 * Every endpoint is authenticated (SecurityConfig protects everything except
 * /api/auth/**). We read the logged-in user's id from the JWT principal exactly
 * like the other controllers do.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    // GET /api/ai/spending-summary
    @GetMapping("/spending-summary")
    public ResponseEntity<SpendingSummaryResponse> spendingSummary(Principal principal) {
        String summary = aiService.spendingSummary(userId(principal));
        return ResponseEntity.ok(new SpendingSummaryResponse(summary));
    }

    // POST /api/ai/categorize  body: { "description": "Netflix 15.99" }
    @PostMapping("/categorize")
    public ResponseEntity<CategorizeResponse> categorize(@Valid @RequestBody CategorizeRequest request) {
        String category = aiService.categorize(request.description());
        return ResponseEntity.ok(new CategorizeResponse(category));
    }

    // GET /api/ai/budget-advice
    @GetMapping("/budget-advice")
    public ResponseEntity<BudgetAdviceResponse> budgetAdvice(Principal principal) {
        String advice = aiService.budgetAdvice(userId(principal));
        return ResponseEntity.ok(new BudgetAdviceResponse(advice));
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
