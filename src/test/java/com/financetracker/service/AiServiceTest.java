package com.financetracker.service;

import com.financetracker.ai.AnthropicClient;
import com.financetracker.entity.Account;
import com.financetracker.entity.Budget;
import com.financetracker.entity.Transaction;
import com.financetracker.entity.TransactionType;
import com.financetracker.exception.AiServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit test: AiService's real logic, every dependency faked. No Spring, no
// network, no database — so it's fast and deterministic.
@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock private TransactionService transactionService;
    @Mock private BudgetService budgetService;
    @Mock private BudgetAlertService budgetAlertService;
    @Mock private AnthropicClient anthropicClient;

    @InjectMocks private AiService aiService;

    private final UUID userId = UUID.randomUUID();

    // ── spending summary ──────────────────────────────────────────────────────────

    @Test
    void spendingSummary_noTransactions_returnsLocalMessageWithoutCallingClaude() {
        when(transactionService.getByDateRange(eq(userId), any(), any()))
                .thenReturn(List.of());

        String summary = aiService.spendingSummary(userId);

        assertThat(summary).contains("no recorded expenses");
        // Key cost/privacy check: we must NOT call the paid API when there's no data.
        verify(anthropicClient, never()).complete(anyString(), anyString());
    }

    @Test
    void spendingSummary_withTransactions_returnsClaudeText() {
        when(transactionService.getByDateRange(eq(userId), any(), any()))
                .thenReturn(List.of(expense("Groceries", "120.00")));
        when(anthropicClient.complete(anyString(), anyString()))
                .thenReturn("You spent $120 on groceries this month.");

        String summary = aiService.spendingSummary(userId);

        assertThat(summary).isEqualTo("You spent $120 on groceries this month.");
    }

    @Test
    void spendingSummary_whenClaudeDown_returnsFallback() {
        when(transactionService.getByDateRange(eq(userId), any(), any()))
                .thenReturn(List.of(expense("Groceries", "120.00")));
        when(anthropicClient.complete(anyString(), anyString()))
                .thenThrow(new AiServiceException("Claude API call failed"));

        String summary = aiService.spendingSummary(userId);

        // Graceful degradation: a usable message, not an exception bubbling up.
        assertThat(summary).contains("AI summary unavailable");
        assertThat(summary).contains("Groceries");
    }

    // ── categorize ─────────────────────────────────────────────────────────────────

    @Test
    void categorize_returnsTrimmedFirstLine() {
        when(anthropicClient.complete(anyString(), anyString()))
                .thenReturn("Entertainment\n(because Netflix is a streaming service)");

        String category = aiService.categorize("Netflix 15.99");

        // We keep only the first line so a chatty model can't pollute the result.
        assertThat(category).isEqualTo("Entertainment");
    }

    @Test
    void categorize_blankInput_returnsUncategorizedWithoutCallingClaude() {
        String category = aiService.categorize("   ");

        assertThat(category).isEqualTo("Uncategorized");
        verify(anthropicClient, never()).complete(anyString(), anyString());
    }

    @Test
    void categorize_whenClaudeDown_returnsUncategorized() {
        when(anthropicClient.complete(anyString(), anyString()))
                .thenThrow(new AiServiceException("down"));

        assertThat(aiService.categorize("Netflix 15.99")).isEqualTo("Uncategorized");
    }

    // ── budget advice ────────────────────────────────────────────────────────────

    @Test
    void budgetAdvice_noBudgets_returnsLocalMessage() {
        when(budgetService.getByMonth(eq(userId), anyInt(), anyInt()))
                .thenReturn(List.of());

        String advice = aiService.budgetAdvice(userId);

        assertThat(advice).contains("no budgets set");
        verify(anthropicClient, never()).complete(anyString(), anyString());
    }

    @Test
    void budgetAdvice_whenClaudeDown_returnsFallback() {
        when(budgetService.getByMonth(eq(userId), anyInt(), anyInt()))
                .thenReturn(List.of(budget("Dining", "200.00", "180.00")));
        when(anthropicClient.complete(anyString(), anyString()))
                .thenThrow(new AiServiceException("down"));

        assertThat(aiService.budgetAdvice(userId)).contains("unavailable");
    }

    // ── anomaly detection: the 2x threshold gate ──────────────────────────────────

    // Baseline used by every test below: three $150 restaurant charges → average $150,
    // so the anomaly threshold (2x) is $300.
    private List<Transaction> threeMonthBaselineOf150() {
        return List.of(
                expense("Restaurants", "150.00"),
                expense("Restaurants", "150.00"),
                expense("Restaurants", "150.00"));
    }

    @Test
    void detectAnomaly_atOrBelowTwoXAverage_skipsClaudeAndCreatesNoAlert() {
        when(transactionService.getByCategory(userId, "Restaurants"))
                .thenReturn(threeMonthBaselineOf150());

        // $300 is exactly 2x the $150 average → NOT above the threshold → skip.
        aiService.detectAnomaly(userId, expense("Restaurants", "300.00"));

        // The whole point of the gate: no paid API call, no alert.
        verify(anthropicClient, never()).complete(anyString(), anyString());
        verify(budgetAlertService, never()).createAnomalyAlert(any(), anyString());
    }

    @Test
    void detectAnomaly_aboveTwoXAndClaudeConfirms_createsAnomalyAlert() {
        when(transactionService.getByCategory(userId, "Restaurants"))
                .thenReturn(threeMonthBaselineOf150());
        when(anthropicClient.complete(anyString(), anyString())).thenReturn("ANOMALY");

        // $450 is 3x the $150 average → above threshold → Claude is consulted.
        aiService.detectAnomaly(userId, expense("Restaurants", "450.00"));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(budgetAlertService).createAnomalyAlert(eq(userId), message.capture());
        assertThat(message.getValue())
                .isEqualTo("Unusual charge: $450 at Restaurants — 3x your usual $150 average");
    }

    @Test
    void detectAnomaly_aboveTwoXButClaudeSaysNormal_createsNoAlert() {
        when(transactionService.getByCategory(userId, "Restaurants"))
                .thenReturn(threeMonthBaselineOf150());
        when(anthropicClient.complete(anyString(), anyString())).thenReturn("NORMAL");

        aiService.detectAnomaly(userId, expense("Restaurants", "450.00"));

        // Above the threshold we DO call Claude, but a "NORMAL" verdict raises no alert.
        verify(anthropicClient).complete(anyString(), anyString());
        verify(budgetAlertService, never()).createAnomalyAlert(any(), anyString());
    }

    @Test
    void detectAnomaly_noHistory_skipsWithoutCallingClaude() {
        when(transactionService.getByCategory(userId, "Restaurants"))
                .thenReturn(List.of());

        aiService.detectAnomaly(userId, expense("Restaurants", "450.00"));

        // No baseline → nothing to compare against → no API call, no alert.
        verify(anthropicClient, never()).complete(anyString(), anyString());
        verify(budgetAlertService, never()).createAnomalyAlert(any(), anyString());
    }

    @Test
    void detectAnomaly_whenClaudeDown_skipsSilentlyWithoutThrowing() {
        when(transactionService.getByCategory(userId, "Restaurants"))
                .thenReturn(threeMonthBaselineOf150());
        when(anthropicClient.complete(anyString(), anyString()))
                .thenThrow(new AiServiceException("Claude API call failed"));

        // Fallback rule: a failing API must not bubble up or create an alert.
        assertThatCode(() -> aiService.detectAnomaly(userId, expense("Restaurants", "450.00")))
                .doesNotThrowAnyException();
        verify(budgetAlertService, never()).createAnomalyAlert(any(), anyString());
    }

    @Test
    void detectAnomaly_incomeTransaction_isIgnored() {
        Transaction income = Transaction.builder()
                .id(UUID.randomUUID())
                .account(Account.builder().id(UUID.randomUUID()).build())
                .amount(new BigDecimal("9999.00"))
                .type(TransactionType.INCOME)
                .category("Salary")
                .date(LocalDate.now())
                .build();

        aiService.detectAnomaly(userId, income);

        // Income can't be an overspend anomaly — we don't even look at history.
        verify(transactionService, never()).getByCategory(any(), anyString());
        verify(anthropicClient, never()).complete(anyString(), anyString());
        verify(budgetAlertService, never()).createAnomalyAlert(any(), anyString());
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private Transaction expense(String category, String amount) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .account(Account.builder().id(UUID.randomUUID()).build())
                .amount(new BigDecimal(amount))
                .type(TransactionType.EXPENSE)
                .category(category)
                .date(LocalDate.now())
                .build();
    }

    private BudgetWithSpent budget(String category, String limit, String spent) {
        Budget b = Budget.builder()
                .id(UUID.randomUUID())
                .category(category)
                .limitAmount(new BigDecimal(limit))
                .month(LocalDate.now().getMonthValue())
                .year(LocalDate.now().getYear())
                .build();
        return new BudgetWithSpent(b, new BigDecimal(spent));
    }
}
