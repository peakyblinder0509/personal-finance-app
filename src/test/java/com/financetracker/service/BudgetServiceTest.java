package com.financetracker.service;

import com.financetracker.entity.Budget;
import com.financetracker.entity.User;
import com.financetracker.repository.BudgetRepository;
import com.financetracker.repository.TransactionRepository;
import com.financetracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// @ExtendWith(MockitoExtension.class)
//   Hooks Mockito into JUnit 5's lifecycle. Before each @Test it scans this class
//   for @Mock / @InjectMocks fields, creates the mock objects, and injects them.
//   Without it, those annotations would just be inert — the fields would stay null.
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    // @Mock
    //   Creates a fake BudgetRepository. It has the real interface's methods, but
    //   every method returns a default (null/empty) until WE tell it what to return
    //   with when(...).thenReturn(...). No database, no Spring — pure in-memory fake.
    @Mock
    private BudgetRepository budgetRepository;

    // Budgets now read their "spent" figure from transactions, so the service
    // depends on this repository too — we stub it to return a known spent amount.
    @Mock
    private TransactionRepository transactionRepository;

    // BudgetService also needs a UserRepository in its constructor. getBudgetStatus
    // never touches it, but the object still has to be constructable, so we mock it too.
    @Mock
    private UserRepository userRepository;

    // @InjectMocks
    //   Creates a REAL BudgetService (the class under test) and passes the @Mock
    //   objects above into its constructor. So the service's logic is real; only its
    //   dependencies are fake. That is exactly what a unit test wants: isolate the
    //   logic of ONE class from the database and the rest of Spring.
    @InjectMocks
    private BudgetService budgetService;

    private final UUID userId = UUID.randomUUID();

    // ── Scenario 1: 50% used → OK band (below the 80% warning threshold) ──────────
    @Test
    void getBudgetStatus_at50Percent_isOk() {
        // Arrange: spent 100 of a 200 limit = 50%.
        stubBudget(new BigDecimal("200.00"), new BigDecimal("100.00"));

        // Act: run the REAL method against the FAKE repository.
        BudgetStatus status = budgetService.getBudgetStatus(userId, "Groceries", 5, 2026);

        // Assert: the percentage is computed correctly...
        assertThat(status.percentUsed()).isEqualByComparingTo("50.00");
        // ...and it falls in the OK band: under 80%.
        assertThat(status.percentUsed()).isLessThan(new BigDecimal("80"));
    }

    // ── Scenario 2: 85% used → WARNING band (>= 80% but < 100%) ───────────────────
    @Test
    void getBudgetStatus_at85Percent_isWarning() {
        // Arrange: spent 170 of 200 = 85%.
        stubBudget(new BigDecimal("200.00"), new BigDecimal("170.00"));

        BudgetStatus status = budgetService.getBudgetStatus(userId, "Groceries", 5, 2026);

        assertThat(status.percentUsed()).isEqualByComparingTo("85.00");
        // WARNING band: at or above 80%, but not yet over budget.
        assertThat(status.percentUsed()).isGreaterThanOrEqualTo(new BigDecimal("80"));
        assertThat(status.percentUsed()).isLessThan(new BigDecimal("100"));
    }

    // ── Scenario 3: 110% used → EXCEEDED band (>= 100%) ───────────────────────────
    @Test
    void getBudgetStatus_at110Percent_isExceeded() {
        // Arrange: spent 220 of 200 = 110% (over budget).
        stubBudget(new BigDecimal("200.00"), new BigDecimal("220.00"));

        BudgetStatus status = budgetService.getBudgetStatus(userId, "Groceries", 5, 2026);

        assertThat(status.percentUsed()).isEqualByComparingTo("110.00");
        // EXCEEDED band: at or above 100%.
        assertThat(status.percentUsed()).isGreaterThanOrEqualTo(new BigDecimal("100"));
    }

    // ── helper ────────────────────────────────────────────────────────────────────
    // Programs the fake repository: "when getBudgetStatus looks up this budget,
    // hand back one with this limit and spent amount." This is the heart of a unit
    // test — we fully control the inputs so the assertions are deterministic.
    private void stubBudget(BigDecimal limit, BigDecimal spent) {
        Budget budget = Budget.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(userId).build())
                .category("Groceries")
                .limitAmount(limit)
                .month(5)
                .year(2026)
                .build();

        when(budgetRepository.findByUser_IdAndCategoryAndMonthAndYear(userId, "Groceries", 5, 2026))
                .thenReturn(Optional.of(budget));
        // "spent" no longer lives on the budget — it's summed from transactions.
        when(transactionRepository.sumExpensesByUserCategoryAndMonth(userId, "Groceries", 2026, 5))
                .thenReturn(spent);
    }
}
