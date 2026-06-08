package com.financetracker.service;

import com.financetracker.entity.AlertType;
import com.financetracker.entity.Budget;
import com.financetracker.entity.BudgetAlert;
import com.financetracker.entity.User;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.repository.BudgetAlertRepository;
import com.financetracker.repository.BudgetRepository;
import com.financetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class BudgetAlertService {

    private static final Logger log = LoggerFactory.getLogger(BudgetAlertService.class);

    private static final BigDecimal WARNING_THRESHOLD  = new BigDecimal("0.80");
    private static final BigDecimal EXCEEDED_THRESHOLD = BigDecimal.ONE;

    private final BudgetAlertRepository alertRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    public BudgetAlertService(BudgetAlertRepository alertRepository,
                               BudgetRepository budgetRepository,
                               UserRepository userRepository) {
        this.alertRepository = alertRepository;
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
    }

    // Called by the scheduled job. @Transactional keeps the Hibernate session
    // open for the entire loop so lazy-loading budget.getUser() works safely.
    @Transactional
    public void processMonthlyBudgetAlerts(int month, int year) {
        List<Budget> budgets = budgetRepository.findByMonthAndYear(month, year);
        log.debug("Checking {} budget(s) for alerts — {}/{}", budgets.size(), month, year);

        int warnings = 0, exceeded = 0;

        for (Budget budget : budgets) {
            if (budget.getLimitAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal ratio = budget.getSpentAmount()
                    .divide(budget.getLimitAmount(), 4, RoundingMode.HALF_UP);

            if (ratio.compareTo(EXCEEDED_THRESHOLD) >= 0) {
                if (!alertRepository.existsByBudget_IdAndAlertTypeAndIsReadFalse(
                        budget.getId(), AlertType.EXCEEDED)) {
                    createAlert(budget, AlertType.EXCEEDED, String.format(
                            "Budget for '%s' has been exceeded (%.0f%% used)",
                            budget.getCategory(),
                            ratio.multiply(BigDecimal.valueOf(100))));
                    exceeded++;
                }
            } else if (ratio.compareTo(WARNING_THRESHOLD) >= 0) {
                if (!alertRepository.existsByBudget_IdAndAlertTypeAndIsReadFalse(
                        budget.getId(), AlertType.WARNING)) {
                    createAlert(budget, AlertType.WARNING, String.format(
                            "Budget for '%s' is at %.0f%% of its limit",
                            budget.getCategory(),
                            ratio.multiply(BigDecimal.valueOf(100))));
                    warnings++;
                }
            }
        }

        log.info("Budget alert job: {} warning(s) and {} exceeded alert(s) created for {}/{}",
                warnings, exceeded, month, year);
    }

    public List<BudgetAlert> getUnreadAlerts(UUID userId) {
        List<BudgetAlert> alerts = alertRepository
                .findByUser_IdAndIsReadFalseOrderByCreatedAtDesc(userId);
        log.debug("Fetched {} unread alert(s) for userId={}", alerts.size(), userId);
        return alerts;
    }

    public long getUnreadCount(UUID userId) {
        long count = alertRepository.countByUser_IdAndIsReadFalse(userId);
        log.debug("User userId={} has {} unread alert(s)", userId, count);
        return count;
    }

    public List<BudgetAlert> getAlertsByType(UUID userId, AlertType type) {
        List<BudgetAlert> alerts = alertRepository
                .findByUser_IdAndAlertTypeOrderByCreatedAtDesc(userId, type);
        log.debug("Fetched {} {} alert(s) for userId={}", alerts.size(), type, userId);
        return alerts;
    }

    /**
     * Creates an ANOMALY alert for a user. Unlike budget alerts, this is not tied
     * to a budget (budget stays null) — it flags an unusual transaction.
     *
     * Called from {@code AiService.detectAnomaly} after Claude confirms the charge
     * is genuinely unusual. getReferenceById gives us a lazy User proxy, which is
     * all we need to set the foreign key without loading the full row.
     */
    @Transactional
    public BudgetAlert createAnomalyAlert(UUID userId, String message) {
        User user = userRepository.getReferenceById(userId);
        BudgetAlert saved = alertRepository.save(BudgetAlert.builder()
                .user(user)
                .budget(null)
                .message(message)
                .alertType(AlertType.ANOMALY)
                .isRead(false)
                .build());
        log.debug("Created ANOMALY alert id={} for userId={}", saved.getId(), userId);
        return saved;
    }

    @Transactional
    public BudgetAlert markAsRead(UUID alertId, UUID userId) {
        BudgetAlert alert = alertRepository.findByIdAndUser_Id(alertId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));
        alert.setRead(true);
        BudgetAlert saved = alertRepository.save(alert);
        log.info("Alert marked as read: id={}, userId={}", alertId, userId);
        return saved;
    }

    private void createAlert(Budget budget, AlertType type, String message) {
        alertRepository.save(BudgetAlert.builder()
                .user(budget.getUser())
                .budget(budget)
                .message(message)
                .alertType(type)
                .isRead(false)
                .build());
        log.debug("Created {} alert for budgetId={}, userId={}",
                type, budget.getId(), budget.getUser().getId());
    }
}
