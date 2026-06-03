package com.financetracker.service;

import com.financetracker.ai.AnthropicClient;
import com.financetracker.entity.Budget;
import com.financetracker.entity.Transaction;
import com.financetracker.entity.TransactionType;
import com.financetracker.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final AnthropicClient anthropicClient;

    public AiService(TransactionService transactionService,
                     BudgetService budgetService,
                     AnthropicClient anthropicClient) {
        this.transactionService = transactionService;
        this.budgetService = budgetService;
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
        List<Budget> budgets = budgetService.getByMonth(userId, today.getMonthValue(), today.getYear());

        if (budgets.isEmpty()) {
            return "You have no budgets set for this month. Create a few category budgets to get personalized advice.";
        }

        StringBuilder status = new StringBuilder();
        for (Budget b : budgets) {
            // Only aggregated numbers leave our system — category, limit, spent.
            status.append("- %s: spent $%s of $%s budget%n"
                    .formatted(b.getCategory(), b.getSpentAmount(), b.getLimitAmount()));
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

    private String formatTotals(Map<String, BigDecimal> totals) {
        if (totals.isEmpty()) {
            return "(nothing)";
        }
        StringBuilder sb = new StringBuilder();
        totals.forEach((category, amount) -> sb.append("- %s: $%s%n".formatted(category, amount)));
        return sb.toString();
    }
}
