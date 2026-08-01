package com.example.jari.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves task 2.3: the test schema comes from the service's own Flyway migration,
 * not from Hibernate generating it from entities. If ddl-auto ever silently became
 * create/update, flyway_schema_history simply wouldn't exist - this fails loudly
 * instead.
 */
class SchemaMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void schemaWasCreatedByFlywayMigration() {
        Integer appliedVersion = jdbcTemplate.queryForObject(
                "select version::int from flyway_schema_history where success = true order by installed_rank desc limit 1",
                Integer.class);

        assertThat(appliedVersion).isEqualTo(1);
    }
}
