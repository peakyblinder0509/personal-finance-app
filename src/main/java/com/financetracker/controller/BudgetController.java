package com.financetracker.controller;

import com.financetracker.dto.BudgetRequest;
import com.financetracker.dto.BudgetResponse;
import com.financetracker.dto.BudgetStatusResponse;
import com.financetracker.entity.Budget;
import com.financetracker.service.BudgetService;
import com.financetracker.service.BudgetStatus;
import com.financetracker.service.BudgetWithSpent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    // GET /api/budgets?month=1&year=2024  ← explicit month/year
    // GET /api/budgets                    ← defaults to current month
    @GetMapping
    public ResponseEntity<List<BudgetResponse>> getAll(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            Principal principal) {

        int m = month != null ? month : LocalDate.now().getMonthValue();
        int y = year  != null ? year  : LocalDate.now().getYear();

        List<BudgetResponse> budgets = budgetService.getByMonth(userId(principal), m, y)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(budgets);
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody BudgetRequest request,
                                                  Principal principal) {
        BudgetWithSpent budget = budgetService.create(
                userId(principal), request.category(),
                request.limitAmount(), request.month(), request.year());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(budget));
    }

    // GET /api/budgets/status?category=Groceries&month=1&year=2024
    @GetMapping("/status")
    public ResponseEntity<BudgetStatusResponse> getStatus(
            @RequestParam String category,
            @RequestParam int month,
            @RequestParam int year,
            Principal principal) {

        BudgetStatus status = budgetService.getBudgetStatus(userId(principal), category, month, year);
        BudgetResponse budget = toResponse(status.budget(), status.spentAmount());
        return ResponseEntity.ok(new BudgetStatusResponse(budget, status.percentUsed()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }

    private BudgetResponse toResponse(BudgetWithSpent bws) {
        return toResponse(bws.budget(), bws.spentAmount());
    }

    private BudgetResponse toResponse(Budget b, BigDecimal spent) {
        return new BudgetResponse(
                b.getId(), b.getCategory(), b.getLimitAmount(),
                spent, b.getMonth(), b.getYear());
    }
}
