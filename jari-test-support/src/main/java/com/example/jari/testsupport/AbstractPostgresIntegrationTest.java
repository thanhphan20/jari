package com.example.jari.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for *IT classes: one Postgres container shared across every *IT class in a
 * service module, wired via @ServiceConnection so no datasource property is
 * hand-plumbed. Extracted here after project-service and task-service duplicated it
 * identically (see design.md "Shared test-support module, extracted after the second
 * service") - test-scoped only, so a change here recompiles tests, never a production
 * artifact.
 *
 * Deliberately NOT using @Testcontainers/@Container: that JUnit5 extension stops the
 * container after each test class even for a static field shared via a common base
 * class, which broke a second *IT class in the same run with "Connection refused"
 * once the first class's @AfterAll tore the container down. Starting it manually in a
 * static initializer and never calling stop() is the documented pattern for a
 * container meant to survive multiple test classes - Testcontainers' Ryuk sidecar
 * removes it when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "eureka.client.enabled=false")
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    static {
        POSTGRES.start();
    }
}
