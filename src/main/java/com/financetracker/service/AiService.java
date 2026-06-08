package com.financetracker.service;

import com.financetracker.ai.AnthropicClient;
import com.financetracker.entity.Budget;
import com.financetracker.entity.Transaction;
import com.financetracker.entity.TransactionType;
import com.financetracker.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Business logic for the AI features. This is where we decide WHAT to ask Claude.
 *
 * Two rules drive every method here:
 *   1. Summarize before sending. We never ship raw transaction rows (which can
 *      contain merchant names, notes, etc.) to a third party. We aggregate to
 *      category totals first — that's enough for a useful answer and leaks far less.
 *   2. Degrade gracefully. Every Claude call is wrapped so that if the API is down
 *      (AiServiceException), the user gets a helpful fallback string, not a 500.
 *
 * These methods are read-only (no DB writes), so unlike the write methods elsewhere
 * in the project they are NOT annotated with @Transactional.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    // How many months of history define "usual" spending for a category.
    private static final int BASELINE_MONTHS = 3;
    // A charge must exceed this multiple of the category average before we even
    // consider calling Claude. Below it, we skip the API entirely (no cost, no noise).
    private static final BigDecimal ANOMALY_MULTIPLIER = new BigDecimal("2");

    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final BudgetAlertService budgetAlertService;
    private final AnthropicClient anthropicClient;

    public AiService(TransactionService transactionService,
                     BudgetService budgetService,
                     BudgetAlertService budgetAlertService,
                     AnthropicClient anthropicClient) {
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.budgetAlertService = budgetAlertService;
        this.anthropicClient = anthropicClient;
    }

    // ── Feature 1: plain-English spending summary ─────────────────────────────────

    public String spendingSummary(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate thisMonthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = thisMonthStart.minusMonths(1);

        // Aggregate to category totals — this is the "summarize before sending" step.
        Map<String, BigDecimal> thisMonth = expensesByCategory(
                transactionService.getByDateRange(userId, thisMonthStart, today));
        Map<String, BigDecimal> lastMonth = expensesByCategory(
                transactionService.getByDateRange(userId, lastMonthStart, thisMonthStart.minusDays(1)));

        // No data yet → answer locally, don't waste a paid API call.
        if (thisMonth.isEmpty()) {
            return "You have no recorded expenses this month yet. Add some transactions to get a summary.";
        }

        String userPrompt = """
                Here are my expenses by category for this month and last month.
                Write a short, friendly, plain-English summary (2-4 sentences).
                Mention the biggest categories and any notable changes vs last month.

                This month so far:
                %s

                Last month:
                %s
                """.formatted(formatTotals(thisMonth), formatTotals(lastMonth));

        return ask(
                "You are a concise personal-finance assistant. Use plain English and round to whole dollars.",
                userPrompt,
                "Here is your spending this month: " + formatTotals(thisMonth)
                        + " (AI summary unavailable right now).");
    }

    // ── Feature 2: auto-categorize a transaction description ──────────────────────

    public String categorize(String description) {
        if (description == null || description.isBlank()) {
            return "Uncategorized";
        }

        String userPrompt = """
                Categorize this transaction into ONE common personal-finance category
                (for example: Groceries, Dining, Entertainment, Transport, Utilities,
                Housing, Shopping, Health, Income, Other).
                Reply with ONLY the category name, nothing else.

                Transaction: "%s"
                """.formatted(description.trim());

        // Fallback is "Uncategorized" so the caller always gets a usable value.
        String category = ask(
                "You are a strict classifier. Output only a single category word or short phrase.",
                userPrompt,
                "Uncategorized");

        // Defensive: keep only the first line in case the model adds extra text.
        return category.lines().findFirst().orElse("Uncategorized").trim();
    }

    // ── Feature 3: personalized budget advice ─────────────────────────────────────

    public String budgetAdvice(UUID userId) {
        LocalDate today = LocalDate.now();
        List<BudgetWithSpent> budgets = budgetService.getByMonth(userId, today.getMonthValue(), today.getYear());

        if (budgets.isEmpty()) {
            return "You have no budgets set for this month. Create a few category budgets to get personalized advice.";
        }

        StringBuilder status = new StringBuilder();
        for (BudgetWithSpent bws : budgets) {
            Budget b = bws.budget();
            // Only aggregated numbers leave our system — category, limit, spent.
            status.append("- %s: spent $%s of $%s budget%n"
                    .formatted(b.getCategory(), bws.spentAmount(), b.getLimitAmount()));
        }

        String userPrompt = """
                Here is my budget status for this month (category: spent of limit).
                Give 2-3 specific, encouraging saving recommendations in plain English.
                Point out where I'm over or close to my limit.

                %s
                """.formatted(status.toString());

        return ask(
                "You are a supportive personal-finance coach. Be specific and practical.",
                userPrompt,
                "Budget advice is unavailable right now. As a tip: review the categories where you're closest to your limit.");
    }

    // ── Feature 4: anomaly detection on a new transaction ─────────────────────────

    /**
     * React to a newly-created transaction. This runs AFTER the DB transaction that
     * created it has COMMITTED ({@code TransactionPhase.AFTER_COMMIT}), so anomaly
     * detection can never roll back or block transaction creation — by the time we
     * run, the transaction is already safely persisted.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCreated(TransactionCreatedEvent event) {
        detectAnomaly(event.userId(), event.transaction());
    }

    /**
     * Flags an unusually large charge for a category and, if Claude confirms it is
     * genuinely unusual (not e.g. an annual subscription), raises an ANOMALY alert.
     *
     * Cost/privacy design:
     *   - We compute the category's recent average locally and only call Claude when
     *     the charge is more than {@value #ANOMALY_MULTIPLIER}x that average. Normal
     *     spending never triggers a paid API call.
     *   - We send Claude ONLY the category name and the amounts — never the merchant
     *     name or description.
     *   - If Claude is unavailable, we skip silently (no alert), so a failing API
     *     never affects the user's transactions.
     */
    public void detectAnomaly(UUID userId, Transaction transaction) {
        // Income can't be an overspend anomaly; only expenses are checked.
        if (transaction.getType() != TransactionType.EXPENSE) {
            log.debug("Anomaly check skipped: transaction id={} is not an expense", transaction.getId());
            return;
        }

        String category = transaction.getCategory();
        BigDecimal amount = transaction.getAmount();

        BigDecimal average = averageForCategory(userId, transaction);
        if (average == null) {
            // No prior history → we have no baseline to compare against, so skip.
            log.debug("Anomaly check skipped: no {}-month history for category='{}', userId={}",
                    BASELINE_MONTHS, category, userId);
            return;
        }

        BigDecimal threshold = average.multiply(ANOMALY_MULTIPLIER);
        if (amount.compareTo(threshold) <= 0) {
            // The 2x gate: below the threshold we do NOT call Claude at all.
            log.debug("Anomaly check skipped: amount {} within {}x average {} for category='{}', userId={}",
                    amount, ANOMALY_MULTIPLIER, average, category, userId);
            return;
        }

        // Above the threshold → ask Claude to confirm. Only category + amounts leave us.
        boolean confirmed;
        try {
            confirmed = claudeConfirmsAnomaly(category, amount, average);
        } catch (AiServiceException e) {
            // Fallback rule: if the API fails, skip silently — never block anything.
            log.warn("Anomaly check degraded: Claude unavailable for category='{}', userId={} ({})",
                    category, userId, e.getMessage());
            return;
        }

        if (!confirmed) {
            log.debug("Anomaly check: Claude judged amount {} in category='{}' as expected — no alert",
                    amount, category);
            return;
        }

        BigDecimal multiple = amount.divide(average, 0, RoundingMode.HALF_UP);
        String message = "Unusual charge: $%s at %s — %sx your usual $%s average".formatted(
                wholeDollars(amount), category, multiple.toPlainString(), wholeDollars(average));

        budgetAlertService.createAnomalyAlert(userId, message);
        log.info("Anomaly detected for userId={}, category='{}': amount={} is {}x the {}-month average {}",
                userId, category, amount, multiple, BASELINE_MONTHS, average);
    }

    /**
     * Average amount of same-category EXPENSE transactions over the last
     * {@link #BASELINE_MONTHS} months, excluding the transaction being evaluated.
     * Returns {@code null} when there is no history to average.
     */
    private BigDecimal averageForCategory(UUID userId, Transaction current) {
        LocalDate cutoff = LocalDate.now().minusMonths(BASELINE_MONTHS);

        List<Transaction> history = transactionService.getByCategory(userId, current.getCategory()).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> !t.getDate().isBefore(cutoff))
                .filter(t -> !t.getId().equals(current.getId()))
                .toList();

        if (history.isEmpty()) {
            return null;
        }

        BigDecimal total = history.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);
    }

    /** Asks Claude whether an over-threshold charge is a genuine anomaly. */
    private boolean claudeConfirmsAnomaly(String category, BigDecimal amount, BigDecimal average) {
        String userPrompt = """
                A user's average spend in the "%s" category is about $%s per transaction
                over the last %d months. A new charge of $%s just occurred.
                Is this a genuine spending anomaly worth flagging, or an expected
                variation (for example an annual subscription, insurance renewal, or an
                occasional but normal larger purchase)?
                Reply with EXACTLY one word: ANOMALY or NORMAL.
                """.formatted(category, wholeDollars(average), BASELINE_MONTHS, wholeDollars(amount));

        // Note: this calls the client directly (not the ask() fallback helper) because
        // here a failure means "skip", handled by the caller's catch — not a text reply.
        String answer = anthropicClient.complete(
                "You are an anomaly-detection assistant for personal finance. You only ever "
                        + "receive a category and amounts — never merchant names. "
                        + "Reply with exactly one word: ANOMALY or NORMAL.",
                userPrompt);

        return answer != null && answer.trim().toUpperCase().startsWith("ANOMALY");
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    /**
     * The single place that calls Claude and applies the graceful-fallback rule:
     * on any AiServiceException, log it and return the supplied fallback text.
     */
    private String ask(String systemPrompt, String userPrompt, String fallback) {
        try {
            return anthropicClient.complete(systemPrompt, userPrompt);
        } catch (AiServiceException e) {
            log.warn("AI feature degraded to fallback: {}", e.getMessage());
            return fallback;
        }
    }

    /** Sums EXPENSE transactions per category. Income is ignored for spending views. */
    private Map<String, BigDecimal> expensesByCategory(List<Transaction> transactions) {
        Map<String, BigDecimal> totals = new TreeMap<>(); // TreeMap → stable, sorted output
        for (Transaction t : transactions) {
            if (t.getType() == TransactionType.EXPENSE) {
                totals.merge(t.getCategory(), t.getAmount(), BigDecimal::add);
            }
        }
        return totals;
    }

    /** Formats an amount as a whole-dollar string (e.g. 450.00 → "450"). */
    private String wholeDollars(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatTotals(Map<String, BigDecimal> totals) {
        if (totals.isEmpty()) {
            return "(nothing)";
        }
        StringBuilder sb = new StringBuilder();
        totals.forEach((category, amount) -> sb.append("- %s: $%s%n".formatted(category, amount)));
        return sb.toString();
    }
}
