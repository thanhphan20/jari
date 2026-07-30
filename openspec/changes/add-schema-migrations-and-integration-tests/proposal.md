## Why

Jari has no tests — zero test source files across all modules — and no schema history. Every data service runs `spring.jpa.hibernate.ddl-auto: update`, meaning the schema is whatever Hibernate inferred from the entity classes on last boot. There is no record of what the schema is, no way to reproduce it, and no way to review a change to it.

Both gaps compound with every subsequent phase. The remaining work adds a `ProjectMember` table, a per-project key counter, task ordering columns, and notification deduplication state — so converting to migrations later means converting strictly more tables. And the phases after this one are where distributed behavior lives: event replication, idempotent consumers, circuit breakers, eventual consistency. Those are precisely the behaviors that cannot be verified by reading code or by clicking through Swagger, and tests written after such a system already appears to work are the least useful tests available.

This change therefore lands before the feature phases, not after them.

## What Changes

- **Flyway owns the schema** in all four data services (identity, project, task, notification). Each service gets a baseline migration reflecting its current entities, plus a versioned migration directory it alone controls.
- **`ddl-auto` changes from `update` to `validate`**, so Hibernate verifies that the entities match the migrated schema and fails fast on drift instead of silently mutating tables.
- **A Testcontainers integration-test harness** per data service: a real Postgres container, and a real RabbitMQ container where the service uses one. Wired through Spring Boot's `@ServiceConnection` so no test-specific datasource configuration is duplicated.
- **A first integration test covering the identity flow** end-to-end within the identity service: register, authenticate, verify the issued token's `userId` claim, and resolve the caller through self-lookup. This is the flow the previous change introduced, and it is currently verified only by hand.
- **`mvn verify` runs the integration tests** from a clean state, so "the tests pass" is one command rather than a procedure.
- **A documented decision on end-to-end coverage.** Testcontainers gives each service real infrastructure, but it does not stand up sibling services. The cross-service assertion in the project's definition of done spans three services, so how that test is built is decided here and implemented when those services exist.

## Capabilities

### New Capabilities

- `schema-migrations`: Each service owns its database schema through versioned, reviewable migrations, and the application refuses to start against a schema that does not match its entities.
- `integration-test-harness`: Automated tests run against real Postgres and RabbitMQ instances, provisioned per test run, with no dependency on a developer's manually started stack.

### Modified Capabilities

None. No service contract changes and no runtime behavior changes for a correctly migrated database. `ddl-auto: validate` changes startup behavior only in the presence of drift, which is the intent rather than a contract change.

## Impact

- **New:** `src/main/resources/db/migration/` with a baseline migration in each of the four data services; `src/test/java/` trees where none exist today; a shared test-support module or per-module test base classes (decided in design).
- **Modified:** `application.yml` in four services (`ddl-auto`, Flyway settings); four service `pom.xml` files (Flyway, Testcontainers, `spring-boot-testcontainers`, JUnit 5 dependencies); parent `pom.xml` (dependency management, failsafe plugin configuration).
- **Build:** `mvn verify` now requires a running Docker daemon. `mvn test` continues to work without one, provided unit and integration tests are separated by naming convention.
- **Destructive to local data:** baselining is done against a clean database so the baseline reflects entities rather than accumulated `ddl-auto` drift. Existing local databases are dropped.
- **No Java production-code changes are expected.** If a test reveals a defect, fixing it belongs to that defect's own change, not this one.
