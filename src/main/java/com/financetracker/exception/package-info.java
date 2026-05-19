/**
 * Custom exceptions and global error handling live here.
 *
 * Why custom exceptions instead of returning null or a boolean?
 *   - They carry a clear name that explains what went wrong
 *   - They bubble up through layers automatically — no need to check return values
 *   - A single @ControllerAdvice class can catch them all and return proper HTTP responses
 *
 * Standard exceptions for this project:
 *
 *   ResourceNotFoundException   → 404 Not Found
 *     Thrown when a requested entity (transaction, budget) doesn't exist
 *     for the current user.
 *
 *   UnauthorizedException       → 403 Forbidden
 *     Thrown when a user tries to access a resource that belongs to
 *     someone else.
 *
 *   GlobalExceptionHandler      — @ControllerAdvice class that catches
 *     exceptions and converts them to consistent JSON error responses:
 *     { "status": 404, "message": "Transaction not found", "timestamp": "..." }
 *
 * Annotations you'll use here:
 *   @ResponseStatus      — sets the HTTP status code for an exception class
 *   @ControllerAdvice    — makes a class intercept exceptions from all controllers
 *   @ExceptionHandler    — maps a specific exception type to a handler method
 */
package com.financetracker.exception;
