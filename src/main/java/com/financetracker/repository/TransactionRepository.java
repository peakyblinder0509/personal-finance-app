package com.financetracker.repository;

import com.financetracker.entity.Transaction;
import com.financetracker.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // "Account_User_Id" traverses two relationships:
    // Transaction → account → user → id
    // generates: WHERE account_id IN (SELECT id FROM accounts WHERE user_id = ?)
    //            AND date >= ? AND date <= ?
    List<Transaction> findByAccount_User_IdAndDateBetween(
            UUID userId, LocalDate start, LocalDate end);

    List<Transaction> findByAccount_User_IdAndCategory(UUID userId, String category);

    Optional<Transaction> findByIdAndAccount_User_Id(UUID transactionId, UUID userId);

    // YEAR() and MONTH() are Hibernate 6 HQL functions — available in Spring Boot 3.
    // Sums all transaction amounts for a given user, type, and calendar month.
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.account.user.id = :userId
              AND t.type            = :type
              AND YEAR(t.date)      = :year
              AND MONTH(t.date)     = :month
            """)
    BigDecimal sumByUserTypeAndMonth(@Param("userId") UUID userId,
                                     @Param("type") TransactionType type,
                                     @Param("year") int year,
                                     @Param("month") int month);

    // Computes one account's balance: every INCOME adds, every EXPENSE subtracts.
    // COALESCE returns 0 (not NULL) for an account with no transactions yet.
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN t.type = com.financetracker.entity.TransactionType.INCOME
                                     THEN t.amount ELSE t.amount * -1 END), 0)
            FROM Transaction t
            WHERE t.account.id = :accountId
            """)
    BigDecimal calculateBalance(@Param("accountId") UUID accountId);

    // Net worth = the same income-minus-expense sum across ALL of a user's accounts.
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN t.type = com.financetracker.entity.TransactionType.INCOME
                                     THEN t.amount ELSE t.amount * -1 END), 0)
            FROM Transaction t
            WHERE t.account.user.id = :userId
            """)
    BigDecimal sumNetWorthByUserId(@Param("userId") UUID userId);

    // How much a user has spent in one category in one month — this is a budget's
    // "spent" figure. Only EXPENSE transactions count; income is ignored.
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.account.user.id = :userId
              AND t.type            = com.financetracker.entity.TransactionType.EXPENSE
              AND t.category        = :category
              AND YEAR(t.date)      = :year
              AND MONTH(t.date)     = :month
            """)
    BigDecimal sumExpensesByUserCategoryAndMonth(@Param("userId") UUID userId,
                                                 @Param("category") String category,
                                                 @Param("year") int year,
                                                 @Param("month") int month);
}
