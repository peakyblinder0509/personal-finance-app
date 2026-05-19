/**
 * HTTP layer — controllers live here.
 *
 * A controller's ONLY job is to:
 *   1. Receive an HTTP request
 *   2. Validate the input (via @Valid on DTOs)
 *   3. Call a service method
 *   4. Return an HTTP response
 *
 * Controllers must NOT contain business logic. If you find yourself writing
 * an if-statement that isn't about HTTP (status codes, headers, routing),
 * move that logic to the service layer.
 *
 * Annotations you'll use here:
 *   @RestController  — marks the class as a controller that returns JSON
 *   @RequestMapping  — maps a URL prefix to the class
 *   @GetMapping, @PostMapping, @PutMapping, @DeleteMapping — map HTTP methods
 *   @PathVariable    — binds /users/{id} → Long id
 *   @RequestBody     — deserializes the request JSON body into a DTO
 *   @Valid           — triggers validation rules declared on the DTO
 */
package com.financetracker.controller;
