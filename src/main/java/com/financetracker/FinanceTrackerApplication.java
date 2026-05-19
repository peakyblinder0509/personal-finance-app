package com.financetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Finance Tracker application.
 *
 * @SpringBootApplication is a shortcut that combines three annotations:
 *
 *   1. @Configuration       — marks this class as a source of Spring beans
 *   2. @EnableAutoConfiguration — tells Spring Boot to auto-configure libraries
 *                                 based on what JARs are on the classpath
 *                                 (e.g. "I see PostgreSQL driver → configure a DataSource")
 *   3. @ComponentScan       — scans this package and all sub-packages for
 *                             @Component, @Service, @Repository, @Controller, etc.
 *
 * The main() method hands control to SpringApplication.run(), which:
 *   - Starts the embedded Tomcat server
 *   - Creates the Spring application context (the "IoC container")
 *   - Runs all Flyway migrations against the database
 *   - Registers all your beans and wires them together
 */
@SpringBootApplication
public class FinanceTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceTrackerApplication.class, args);
    }
}
