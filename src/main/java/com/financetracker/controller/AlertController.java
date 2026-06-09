package com.financetracker.controller;

import com.financetracker.dto.AlertCountResponse;
import com.financetracker.dto.AlertResponse;
import com.financetracker.entity.AlertType;
import com.financetracker.entity.Budget;
import com.financetracker.entity.BudgetAlert;
import com.financetracker.service.BudgetAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final BudgetAlertService budgetAlertService;

    public AlertController(BudgetAlertService budgetAlertService) {
        this.budgetAlertService = budgetAlertService;
    }

    // GET /api/alerts                 → unread alerts (any type)
    // GET /api/alerts?type=ANOMALY    → all alerts of that type
    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAlerts(
            @RequestParam(required = false) AlertType type,
            Principal principal) {

        UUID userId = userId(principal);
        List<BudgetAlert> alerts = (type != null)
                ? budgetAlertService.getAlertsByType(userId, type)
                : budgetAlertService.getUnreadAlerts(userId);

        return ResponseEntity.ok(alerts.stream().map(this::toResponse).toList());
    }

    // GET /api/alerts/all → every alert (read + unread, all types), newest first.
    // The plain GET /api/alerts only returns UNREAD, so this backs the "ALL" tab.
    @GetMapping("/all")
    public ResponseEntity<List<AlertResponse>> getAllAlerts(Principal principal) {
        List<BudgetAlert> alerts = budgetAlertService.getAllAlerts(userId(principal));
        return ResponseEntity.ok(alerts.stream().map(this::toResponse).toList());
    }

    // GET /api/alerts/count → number of unread alerts, for the UI badge.
    // A dedicated count endpoint is far cheaper than fetching the full list
    // just to call .length on the client.
    @GetMapping("/count")
    public ResponseEntity<AlertCountResponse> getUnreadCount(Principal principal) {
        long count = budgetAlertService.getUnreadCount(userId(principal));
        return ResponseEntity.ok(new AlertCountResponse(count));
    }

    // POST /api/alerts/check → runs the budget check for the logged-in user right
    // now (instead of waiting for the daily job) and creates any WARNING/EXCEEDED
    // alerts that are due. Returns { "count": N } = how many alerts were created.
    @PostMapping("/check")
    public ResponseEntity<AlertCountResponse> checkNow(Principal principal) {
        LocalDate today = LocalDate.now();
        int created = budgetAlertService.checkBudgetAlertsForUser(
                userId(principal), today.getMonthValue(), today.getYear());
        return ResponseEntity.ok(new AlertCountResponse(created));
    }

    // PUT /api/alerts/read-all → marks all of the user's unread alerts as read.
    // Returns { "count": N } where N is how many were cleared, so the client can
    // refresh the badge without a second round-trip.
    @PutMapping("/read-all")
    public ResponseEntity<AlertCountResponse> markAllAsRead(Principal principal) {
        int cleared = budgetAlertService.markAllAsRead(userId(principal));
        return ResponseEntity.ok(new AlertCountResponse(cleared));
    }

    // PUT not POST — we are updating the state of an existing resource, not creating one
    @PutMapping("/{id}/read")
    public ResponseEntity<AlertResponse> markAsRead(@PathVariable UUID id, Principal principal) {
        BudgetAlert alert = budgetAlertService.markAsRead(id, userId(principal));
        return ResponseEntity.ok(toResponse(alert));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }

    private AlertResponse toResponse(BudgetAlert a) {
        // ANOMALY alerts are not tied to a budget, so budget may be null.
        Budget budget = a.getBudget();
        return new AlertResponse(
                a.getId(),
                budget != null ? budget.getId() : null,
                budget != null ? budget.getCategory() : null,
                a.getMessage(),
                a.getAlertType(),
                a.isRead(),
                a.getCreatedAt());
    }
}
