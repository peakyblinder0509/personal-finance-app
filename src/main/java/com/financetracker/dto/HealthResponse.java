package com.financetracker.dto;

import java.time.Instant;

/**
 * Tiny response telling callers (load balancers, Docker, uptime monitors)
 * that the application is alive.
 *
 *   status    — a simple machine-readable string, "UP" when we can answer.
 *   timestamp — when this answer was produced, as a UTC instant
 *               (serialized like "2026-06-04T10:30:00Z" thanks to the
 *               Jackson setting we configured in application.properties).
 */
public record HealthResponse(String status, Instant timestamp) {}
