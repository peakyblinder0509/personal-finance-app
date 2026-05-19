/**
 * Configuration classes live here.
 *
 * Spring Boot auto-configures most things, but some setup requires explicit
 * Java code — this is where that code lives.
 *
 * Typical configuration classes in this project:
 *
 *   SecurityConfig      — defines which endpoints are public vs. protected,
 *                         configures JWT filter chain, sets up CORS policy,
 *                         disables CSRF (safe for stateless REST APIs)
 *
 *   JwtConfig           — bean definitions for the JWT secret key, token
 *                         parser, and token generator
 *
 *   ApplicationConfig   — other beans that don't fit elsewhere, e.g.
 *                         PasswordEncoder (BCrypt), ModelMapper
 *
 * Every class here is annotated with @Configuration, which tells Spring:
 * "treat @Bean methods in this class as bean definitions."
 *
 * Annotations you'll use here:
 *   @Configuration   — marks the class as a source of bean definitions
 *   @Bean            — declares a method whose return value is a Spring bean
 *   @EnableWebSecurity — activates Spring Security's web support
 */
package com.financetracker.config;
