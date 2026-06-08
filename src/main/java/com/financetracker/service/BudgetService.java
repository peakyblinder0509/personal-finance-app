package com.financetracker.service;

import com.financetracker.entity.Budget;
import com.financetracker.entity.User;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.repository.BudgetRepository;
import com.financetracker.repository.TransactionRepository;
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
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public BudgetService(BudgetRepository budgetRepository,
                         TransactionRepository transactionRepository,
                         UserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BudgetWithSpent create(UUID userId, String category, BigDecimal limitAmount, int month, int year) {
        log.debug("Creating budget: category='{}', limit={}, month={}/{}, userId={}",
                category, limitAmount, month, year, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Budget saved = budgetRepository.save(Budget.builder()
                .user(user).category(category)
                .limitAmount(limitAmount)
                .month(month).year(year)
                .build());

        log.info("Budget created: id={}, category='{}', limit={}, month={}/{}, userId={}",
                saved.getId(), category, limitAmount, month, year, userId);
        // There may already be transactions in this category this month, so compute
        // spent rather than assuming 0.
        return new BudgetWithSpent(saved, spentFor(userId, saved));
    }

    public List<BudgetWithSpent> getByMonth(UUID userId, int month, int year) {
        List<Budget> budgets = budgetRepository.findByUser_IdAndMonthAndYear(userId, month, year);
        log.debug("Fetched {} budgets for userId={}, month={}/{}", budgets.size(), userId, month, year);
        return budgets.stream()
                .map(b -> new BudgetWithSpent(b, spentFor(userId, b)))
                .toList();
    }

    public BudgetStatus getBudgetStatus(UUID userId, String category, int month, int year) {
        log.debug("Fetching budget status: category='{}', month={}/{}, userId={}", category, month, year, userId);

        Budget budget = budgetRepository
                .findByUser_IdAndCategoryAndMonthAndYear(userId, category, month, year)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Budget not found: " + category + " " + month + "/" + year));

        BigDecimal spent = spentFor(userId, budget);

        BigDecimal percentUsed = BigDecimal.ZERO;
        if (budget.getLimitAmount().compareTo(BigDecimal.ZERO) > 0) {
            percentUsed = spent
                    .divide(budget.getLimitAmount(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        log.debug("Budget status: category='{}', spent={}, limit={}, percent={}%, userId={}",
                category, spent, budget.getLimitAmount(), percentUsed, userId);

        return new BudgetStatus(budget, spent, percentUsed);
    }

    // Computes how much has been spent against a budget: the sum of EXPENSE
    // transactions in the same category, month and year for this user.
    private BigDecimal spentFor(UUID userId, Budget budget) {
        return transactionRepository.sumExpensesByUserCategoryAndMonth(
                userId, budget.getCategory(), budget.getYear(), budget.getMonth());
    }
}
