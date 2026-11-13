package com.chubb.claimops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the claims platform.
 *
 * <p>The application is a modular monolith with three top-level packages:
 * <ul>
 *   <li>{@code claim}: the write model. Owns claims and their state machine, exposes the REST API and
 *       publishes domain events to Kafka.</li>
 *   <li>{@code exposure}: read models. Consumes those events to maintain liability exposure and the audit
 *       trail. It never reads the claim tables, so it can later move to its own service without code changes.</li>
 *   <li>{@code common}: cross-cutting concerns: header-based identity, error mapping and configuration.</li>
 * </ul>
 */
@SpringBootApplication
public class ClaimOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimOpsApplication.class, args);
    }
}
