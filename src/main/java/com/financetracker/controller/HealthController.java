package com.financetracker.controller;

import com.financetracker.dto.HealthResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness endpoint. Anything that needs to know "is this API running?"
 * (Docker, a load balancer, an uptime checker) hits GET /api/health.
 *
 * This is intentionally trivial: it does NOT touch the database or any
 * external service. It only proves the web server is up and able to respond.
 * Keeping it dependency-free means a slow database can't make the health
 * check hang or fail.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        // Returning the record directly = Spring serializes it to JSON with a
        // default 200 OK status, which is exactly what a health check expects.
        return new HealthResponse("UP", Instant.now());
    }
}
