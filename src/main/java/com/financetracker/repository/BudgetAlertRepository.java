copackage com.financetracker.repository;

import com.financetracker.entity.AlertType;
import com.financetracker.entity.BudgetAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetAlertRepository extends JpaRepository<BudgetAlert, UUID> {

    // The list/lookup queries below all use LEFT JOIN FETCH a.budget. open-in-view
    // is false, so the Hibernate session is closed by the time the controller maps
    // each alert to a DTO. The DTO reads budget.getCategory(), which would otherwise
    // throw LazyInitializationException — so we load the budget up front, in the same
    // query. LEFT (not inner) because ANOMALY alerts have no budget (it stays null).

    // Unread alerts for a user, newest first — used by GET /api/alerts
    @Query("""
            SELECT a FROM BudgetAlert a
            LEFT JOIN FETCH a.budget
            WHERE a.user.id = :userId AND a.isRead = false
            ORDER BY a.createdAt DESC
            """)
    List<BudgetAlert> findByUser_IdAndIsReadFalseOrderByCreatedAtDesc(@Param("userId") UUID userId);

    // Every alert for a user, newest first (read + unread, all types)
    // — used by GET /api/alerts/all (the "ALL" tab).
    @Query("""
            SELECT a FROM BudgetAlert a
            LEFT JOIN FETCH a.budget
            WHERE a.user.id = :userId
            ORDER BY a.createdAt DESC
            """)
    List<BudgetAlert> findByUser_IdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    // Counts unread alerts for a user — used by GET /api/alerts/count.
    // Spring Data turns "countBy..." into SELECT COUNT(*), so we never load the
    // rows themselves; the database does the counting and returns a single number.
    long countByUser_IdAndIsReadFalse(UUID userId);

    // All alerts of one type for a user, newest first — used by GET /api/alerts?type=ANOMALY
    @Query("""
            SELECT a FROM BudgetAlert a
            LEFT JOIN FETCH a.budget
            WHERE a.user.id = :userId AND a.alertType = :alertType
            ORDER BY a.createdAt DESC
            """)
    List<BudgetAlert> findByUser_IdAndAlertTypeOrderByCreatedAtDesc(@Param("userId") UUID userId,
                                                                    @Param("alertType") AlertType alertType);

    // Prevents duplicate alerts: if an unread alert of this type already exists
    // for this budget, we skip creating another one
    boolean existsByBudget_IdAndAlertTypeAndIsReadFalse(UUID budgetId, AlertType alertType);

    // Ownership-scoped lookup for marking an alert as read. Fetches the budget too,
    // because the controller maps the returned alert to a DTO after the session closes.
    @Query("""
            SELECT a FROM BudgetAlert a
            LEFT JOIN FETCH a.budget
            WHERE a.id = :alertId AND a.user.id = :userId
            """)
    Optional<BudgetAlert> findByIdAndUser_Id(@Param("alertId") UUID alertId, @Param("userId") UUID userId);

    // Marks ALL of a user's unread alerts as read in a single UPDATE statement,
    // and returns how many rows changed — used by PUT /api/alerts/read-all.
    // @Modifying tells Spring Data this query writes (not reads), so it runs an
    // executeUpdate instead of a SELECT. Must run inside a transaction.
    @Modifying
    @Query("UPDATE BudgetAlert a SET a.isRead = true WHERE a.user.id = :userId AND a.isRead = false")
    int markAllReadByUserId(@Param("userId") UUID userId);
}
