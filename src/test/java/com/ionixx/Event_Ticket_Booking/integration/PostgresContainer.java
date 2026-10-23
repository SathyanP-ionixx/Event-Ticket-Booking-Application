package com.ionixx.Event_Ticket_Booking.integration;

//import org.testcontainers.postgresql.PostgreSQLContainer;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container for the whole test JVM. It starts the first time this class is touched
 * and is removed by Testcontainers' Ryuk sidecar when the JVM exits, so every integration test class
 * (and Spring's cached application context) shares it instead of paying a start-up per class.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class PostgresContainer {

    static final PostgreSQLContainer INSTANCE = new PostgreSQLContainer("postgres:16-alpine");

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {}
}
