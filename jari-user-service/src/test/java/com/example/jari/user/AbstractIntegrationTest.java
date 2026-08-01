package com.example.jari.user;

import com.example.jari.testsupport.AbstractPostgresIntegrationTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Adds the identity service's own requirement (a JWT signing secret) on top of the
 * shared Postgres harness in jari-test-support.
 */
@TestPropertySource(properties = {
        // HS256 needs a >=32 byte key; this one exists only for tests.
        "jari.security.jwt.secret=amFyaS1pbnRlZ3JhdGlvbi10ZXN0LXNpZ25pbmcta2V5LTMyYnl0ZXMhIQ=="
})
public abstract class AbstractIntegrationTest extends AbstractPostgresIntegrationTest {
}
