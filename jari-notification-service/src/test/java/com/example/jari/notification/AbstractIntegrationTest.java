package com.example.jari.notification;

import com.example.jari.testsupport.AbstractPostgresIntegrationTest;

/**
 * Postgres only, deliberately: this service declares no AMQP dependency yet (see
 * readme.md - RabbitMQ is "provisioned but unused"). Design.md calls for testing
 * broker-dependent behavior against a real RabbitMQ container, but there is no
 * consumer to exercise until Phase 6 wires one up. Adding a RabbitMQ container now
 * would only prove Testcontainers can start the image, not anything about this
 * service - the same "asserting behavior that does not exist" trap design.md
 * explicitly avoids for ProjectCollaborationE2EIT. Add it when Phase 6 adds the
 * consumer.
 */
public abstract class AbstractIntegrationTest extends AbstractPostgresIntegrationTest {
}
