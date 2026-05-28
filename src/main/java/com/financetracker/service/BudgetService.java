package com.financetracker.service;

import com.financetracker.entity.Budget;
import com.financetracker.entity.User;
import com.financetracker.exception.ResourceNotFoundException;
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
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    public BudgetService(BudgetRepository budgetRepository, UserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Budget create(UUID userId, String category, BigDecimal limitAmount, int month, int year) {
        log.debug("Creating budget: category='{}', limit={}, month={}/{}, userId={}",
                category, limitAmount, month, year, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Budget saved = budgetRepository.save(Budget.builder()
                .user(user).category(category)
                .limitAmount(limitAmount).spentAmount(BigDecimal.ZERO)
                .month(month).year(year)
                .build());

        log.info("Budget created: id={}, category='{}', limit={}, month={}/{}, userId={}",
                saved.getId(), category, limitAmount, month, year, userId);
        return saved;
    }

    public List<Budget> getByMonth(UUID userId, int month, int year) {
        List<Budget> budgets = budgetRepository.findByUser_IdAndMonthAndYear(userId, month, year);
        log.debug("Fetched {} budgets for userId={}, month={}/{}", budgets.size(), userId, month, year);
        return budgets;
    }

    public BudgetStatus getBudgetStatus(UUID userId, String category, int month, int year) {
        log.debug("Fetching budget status: category='{}', month={}/{}, userId={}", category, month, year, userId);

        Budget budget = budgetRepository
                .findByUser_IdAndCategoryAndMonthAndYear(userId, category, month, year)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Budget not found: " + category + " " + month + "/" + year));

        BigDecimal percentUsed = BigDecimal.ZERO;
        if (budget.getLimitAmount().compareTo(BigDecimal.ZERO) > 0) {
            percentUsed = budget.getSpentAmount()
                    .divide(budget.getLimitAmount(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        log.debug("Budget status: category='{}', spent={}, limit={}, percent={}%, userId={}",
                category, budget.getSpentAmount(), budget.getLimitAmount(), percentUsed, userId);

        return new BudgetStatus(budget, percentUsed);
    }
}
