/**
 * Data access layer — repositories live here.
 *
 * Repositories are the only place that talks to the database.
 * Spring Data JPA generates the SQL for you from method names:
 *
 *   findByUserId(Long userId)
 *   → SELECT * FROM transactions WHERE user_id = ?
 *
 *   findByUserIdAndCategory(Long userId, String category)
 *   → SELECT * FROM transactions WHERE user_id = ? AND category = ?
 *
 * For more complex queries, annotate a method with @Query and write JPQL
 * (Java Persistence Query Language, which looks like SQL but uses class
 * and field names instead of table and column names).
 *
 * Every query MUST include a userId parameter — this prevents one user
 * from accidentally reading another user's data (broken object-level
 * authorization, OWASP API Security Top 10 #1).
 *
 * Annotations you'll use here:
 *   @Repository      — marks the class as a Spring bean; also enables
 *                      Spring's exception translation (turns SQL exceptions
 *                      into Spring's DataAccessException hierarchy)
 *   @Query           — lets you write a custom JPQL or native SQL query
 */
package com.financetracker.repository;
