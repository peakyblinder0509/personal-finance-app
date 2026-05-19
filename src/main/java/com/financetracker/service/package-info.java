/**
 * Business logic layer — services live here.
 *
 * Services are the heart of the application. All decisions happen here:
 *   - "Does this user own this transaction?" → security check
 *   - "Calculate the monthly budget remaining" → calculation
 *   - "Send a low-balance notification" → orchestration
 *
 * Services sit between controllers (HTTP) and repositories (database).
 * They should never know anything about HTTP — no HttpServletRequest,
 * no ResponseEntity. That makes them easy to test with plain JUnit.
 *
 * Rules:
 *   - Annotate write methods with @Transactional so the database rolls back
 *     automatically if anything goes wrong mid-method.
 *   - Always filter queries by the authenticated userId — never return data
 *     that belongs to a different user.
 *   - Throw custom exceptions (ResourceNotFoundException, UnauthorizedException)
 *     rather than returning null or a boolean.
 *
 * Annotations you'll use here:
 *   @Service         — marks the class as a Spring bean (same as @Component,
 *                      but communicates intent to other developers)
 *   @Transactional   — wraps the method in a database transaction
 */
package com.financetracker.service;
