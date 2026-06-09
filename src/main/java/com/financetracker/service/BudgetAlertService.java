package com.financetracker.service;

import com.financetracker.entity.AlertType;
import com.financetracker.entity.Budget;
import com.financetracker.entity.BudgetAlert;
import com.financetracker.entity.User;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.repository.BudgetAlertRepository;
import com.financetracker.repository.BudgetRepository;
import com.financetracker.repository.TransactionRepository;
import com.financetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class BudgetAlertService {

    private static final Logger log = LoggerFactory.getLogger(BudgetAlertService.class);

    private static final BigDecimal WARNING_THRESHOLD  = new BigDecimal("0.80");
    private static final BigDecimal EXCEEDED_THRESHOLD = BigDecimal.ONE;

    private final BudgetAlertRepository alertRepository;
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public BudgetAlertService(BudgetAlertRepository alertRepository,
                               BudgetRepository budgetRepository,
                               TransactionRepository transactionRepository,
                               UserRepository userRepository) {
        this.alertRepository = alertRepository;
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // Called by the scheduled job (all users). @Transactional keeps the Hibernate
    // session open for the whole loop so lazy-loading budget.getUser() works safely.
    @Transactional
    public void processMonthlyBudgetAlerts(int month, int year) {
        List<Budget> budgets = budgetRepository.findByMonthAndYear(month, year);
        int created = processBudgets(budgets, month, year);
        log.info("Budget alert job: {} alert(s) created for {}/{}", created, month, year);
    }

    // Same check, scoped to ONE user — used by POST /api/alerts/check so a user can
    // trigger their own budget alerts on demand. Returns how many alerts were created.
    @Transactional
    public int checkBudgetAlertsForUser(UUID userId, int month, int year) {
        List<Budget> budgets = budgetRepository.findByUser_IdAndMonthAndYear(userId, month, year);
        int created = processBudgets(budgets, month, year);
        log.info("On-demand budget check: {} alert(s) created for userId={}, {}/{}",
                created, userId, month, year);
        return created;
    }

    /**
     * Re-check budget alerts automatically whenever a transaction is created, so a
     * WARNING/EXCEEDED alert appears as soon as the spending crosses a threshold —
     * no waiting for the daily job. We use the SAME pattern as anomaly detection:
     * run AFTER the transaction's DB commit ({@code AFTER_COMMIT}) so this check can
     * never roll back or block transaction creation.
     *
     * We check the month/year of the transaction's date (not "today"), so back-dated
     * transactions are credited to the correct month's budget.
     */
    // REQUIRES_NEW: the original transaction has already committed by AFTER_COMMIT,
    // so the alert writes need their own fresh transaction. Annotating the listener
    // (which Spring invokes through the proxy) is what makes @Transactional apply —
    // an internal call to checkBudgetAlertsForUser would bypass the proxy.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCreated(TransactionCreatedEvent event) {
        LocalDate date = event.transaction().getDate();
        checkBudgetAlertsForUser(event.userId(), date.getMonthValue(), date.getYear());
    }

    // Shared loop: for each budget, compute spend and create a WARNING/EXCEEDED
    // alert if a threshold is crossed and no matching unread alert already exists.
    // Returns the total number of alerts created.
    private int processBudgets(List<Budget> budgets, int month, int year) {
        log.debug("Checking {} budget(s) for alerts — {}/{}", budgets.size(), month, year);
        int created = 0;

        for (Budget budget : budgets) {
            if (budget.getLimitAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            // Spent is computed from this category's EXPENSE transactions for the
            // month being processed, not read from the budget row.
            BigDecimal spent = transactionRepository.sumExpensesByUserCategoryAndMonth(
                    budget.getUser().getId(), budget.getCategory(), year, month);

            BigDecimal ratio = spent
                    .divide(budget.getLimitAmount(), 4, RoundingMode.HALF_UP);

            if (ratio.compareTo(EXCEEDED_THRESHOLD) >= 0) {
                if (!alertRepository.existsByBudget_IdAndAlertTypeAndIsReadFalse(
                        budget.getId(), AlertType.EXCEEDED)) {
                    createAlert(budget, AlertType.EXCEEDED, String.format(
                            "Budget for '%s' has been exceeded (%.0f%% used)",
                            budget.getCategory(),
                            ratio.multiply(BigDecimal.valueOf(100))));
                    created++;
                }
            } else if (ratio.compareTo(WARNING_THRESHOLD) >= 0) {
                if (!alertRepository.existsByBudget_IdAndAlertTypeAndIsReadFalse(
                        budget.getId(), AlertType.WARNING)) {
                    createAlert(budget, AlertType.WARNING, String.format(
                            "Budget for '%s' is at %.0f%% of its limit",
                            budget.getCategory(),
                            ratio.multiply(BigDecimal.valueOf(100))));
                    created++;
                }
            }
        }
        return created;
    }

    public List<BudgetAlert> getUnreadAlerts(UUID userId) {
        List<BudgetAlert> alerts = alertRepository
                .findByUser_IdAndIsReadFalseOrderByCreatedAtDesc(userId);
        log.debug("Fetched {} unread alert(s) for userId={}", alerts.size(), userId);
        return alerts;
    }

    public List<BudgetAlert> getAllAlerts(UUID userId) {
        List<BudgetAlert> alerts = alertRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        log.debug("Fetched {} total alert(s) for userId={}", alerts.size(), userId);
        return alerts;
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        int cleared = alertRepository.markAllReadByUserId(userId);
        log.info("Marked {} alert(s) as read for userId={}", cleared, userId);
        return cleared;
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
