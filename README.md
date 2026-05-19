# Personal Finance Tracker — REST API

A REST API for tracking personal finances. Built with Java 21, Spring Boot 3, PostgreSQL, and JWT authentication.

---

## Architecture

The application is organized into four horizontal layers. Each layer has a single responsibility and only talks to the layer directly below it.

```
HTTP Request
     │
     ▼
┌─────────────┐
│  Controller │  ← Handles HTTP. Validates input. Calls service. Returns response.
└──────┬──────┘
       │  calls
       ▼
┌─────────────┐
│   Service   │  ← All business logic lives here. Enforces user ownership rules.
└──────┬──────┘
       │  calls
       ▼
┌─────────────┐
│ Repository  │  ← Database access only. Spring Data JPA auto-generates SQL.
└──────┬──────┘
       │  reads/writes
       ▼
┌─────────────┐
│  PostgreSQL │
└─────────────┘
```

### Why layers?

**Single Responsibility** — each class does one thing. A controller doesn't know how to calculate a budget; a repository doesn't know about HTTP status codes.

**Testability** — you can test the service layer with plain JUnit (no web server needed). You test controllers with MockMvc (no database needed). Isolated units are fast and reliable.

**Replaceability** — if you switch from PostgreSQL to MongoDB tomorrow, only the repository layer changes. The controller and service don't care.

---

## Package Guide

| Package | Contents | Rule |
|---|---|---|
| `controller` | `@RestController` classes | HTTP only — no business logic |
| `service` | `@Service` classes | All decisions happen here |
| `repository` | `@Repository` interfaces | Database access only |
| `entity` | `@Entity` classes | One class = one database table |
| `dto` | Request/Response objects | API contract — never expose entities |
| `config` | `@Configuration` classes | Security, JWT, beans |
| `exception` | Custom exceptions + `@ControllerAdvice` | Consistent error responses |

---

## Tech Stack

| Technology | Why |
|---|---|
| **Java 21** | Latest LTS. Virtual threads available for high concurrency. |
| **Spring Boot 3** | Auto-configuration eliminates boilerplate setup. |
| **Spring Data JPA** | Generates SQL from method names — no hand-written queries for common cases. |
| **Spring Security** | Battle-tested authentication and authorization framework. |
| **PostgreSQL** | Reliable, ACID-compliant relational database. |
| **Flyway** | Version-controls the database schema — every schema change is a tracked SQL file. |
| **Lombok** | Generates getters, setters, constructors at compile time — less noise in entity/DTO classes. |
| **JWT** | Stateless authentication — the server doesn't store sessions. |

---

## Running Locally

### Prerequisites
- Java 21
- PostgreSQL running on port 5432

### 1. Create the database

```sql
CREATE DATABASE finance_tracker;
```

### 2. Set environment variables

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/finance_tracker
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=your_password
export JWT_SECRET=your-long-random-secret-string
```

### 3. Run

```bash
./gradlew bootRun
```

Flyway will automatically create the tables on first startup.

### 4. Run tests

```bash
./gradlew test
```

---

## Database Schema

Flyway migrations live in `src/main/resources/db/migration/`.

Naming convention: `V{version}__{description}.sql`

| Migration | Description |
|---|---|
| `V1__init_schema.sql` | Creates `users`, `transactions`, `budgets` tables |

---

## Security Model

- Passwords are hashed with **BCrypt** before storage — never stored as plain text.
- Every API request (except login/register) requires a **JWT Bearer token**.
- Every database query filters by `userId` — users can only see their own data.
- Secrets (`JWT_SECRET`, database password) are read from **environment variables** — never hardcoded.

---

## API Overview

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create account |
| `POST` | `/api/auth/login` | Get JWT token |
| `GET` | `/api/transactions` | List my transactions |
| `POST` | `/api/transactions` | Create transaction |
| `PUT` | `/api/transactions/{id}` | Update transaction |
| `DELETE` | `/api/transactions/{id}` | Delete transaction |
| `GET` | `/api/budgets` | List my budgets |
| `POST` | `/api/budgets` | Create budget |
