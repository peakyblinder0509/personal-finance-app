package com.financetracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
    name = "budgets",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_budget_user_category_month_year",
        columnNames = {"user_id", "category", "month", "year"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal limitAmount;            // maps to "limit_amount" via Spring naming strategy

    // No spent_amount column: how much has been spent in this category/month is
    // computed on demand from EXPENSE transactions. See BudgetService.

    @Column(nullable = false)
    private int month;                         // 1–12

    @Column(nullable = false)
    private int year;                          // e.g. 2024
}
