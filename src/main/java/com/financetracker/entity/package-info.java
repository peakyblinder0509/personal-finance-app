/**
 * Database entities — JPA-mapped classes live here.
 *
 * An entity is a Java class that maps directly to a database table.
 * Each instance of the class represents one row in that table.
 *
 *   @Entity class Transaction  →  table "transactions"
 *   Long id                    →  column "id" (primary key)
 *   BigDecimal amount          →  column "amount"
 *
 * IMPORTANT: Entities are NEVER returned from controllers as JSON.
 * Use DTOs (see the dto package) for all API input and output.
 * Reasons:
 *   - Entities can contain sensitive fields (e.g. password hashes)
 *   - Entity structure couples your API contract to your DB schema
 *   - Lazy-loaded JPA relationships cause runtime errors when serialized
 *
 * Annotations you'll use here:
 *   @Entity          — marks the class as a JPA entity (maps to a table)
 *   @Table           — optionally specify the exact table name
 *   @Id              — marks the primary key field
 *   @GeneratedValue  — auto-increment strategy (IDENTITY = DB auto-increment)
 *   @Column          — optionally specify column name, nullable, length
 *   @ManyToOne       — many transactions belong to one user
 *   @OneToMany       — one user has many transactions
 */
package com.financetracker.entity;
