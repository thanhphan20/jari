## Why

Jari has no tests — zero test source files across all modules. Everything that currently works is known to work because someone ran `curl` against it once.

Two things make this the moment to fix it rather than later:

**The identity flow is verified only by hand.** The preceding change built registration, token issuance with a `userId` claim, gateway identity propagation, and `GET /api/users/me`. Every one of those was confirmed with manual `curl` calls and a hand-decoded JWT payload. None of it will be noticed if it breaks.

**The migrations from `add-schema-migrations` have no automated check.** That change made Flyway the schema owner and set `ddl-auto: validate`, but a broken migration is currently caught by a developer booting the stack, not by `mvn verify`. The change said so explicitly in its own proposal and named this change as the one that closes the gap.

And the phases after this one are where the distributed behavior lives: event replication, idempotent consumers, circuit breakers, eventual consistency. Those are precisely the behaviors that cannot be verified by reading code or clicking through Swagger. Tests written after such a system already appears to work are the least useful tests available — they assert current behavior rather than intended behavior.

## What Changes

- **A Testcontainers harness** per data service: a real Postgres container, and a real RabbitMQ container where the service uses one. Wired through Spring Boot's `@ServiceConnection` so no test-specific datasource configuration is duplicated per service.
- **Tests obtain their schema from the service's own Flyway migrations**, so a broken migration fails the suite. This is the automated check `add-schema-migrations` deferred.
- **A first integration test covering the identity flow**: register, authenticate, verify the issued token's `userId` claim against the persisted primary key, resolve the caller through self-lookup, and confirm both invalid credentials and missing identity are rejected.
- **`mvn verify` runs the integration tests**, so "the tests pass" is one command rather than a procedure. `mvn test` keeps working without a container runtime.
- **A documented decision on end-to-end coverage.** Testcontainers gives each service real infrastructure but does not stand up sibling services. The cross-service assertion in the project's definition of done spans four services, so how that test is built is decided here and implemented when the behavior it asserts exists.

## Capabilities

### New Capabilities

- `integration-test-harness`: Automated tests run against real Postgres and RabbitMQ instances, provisioned per test run, with no dependency on a developer's manually started stack.

### Modified Capabilities

None. This change adds tests and build configuration; it changes no service contract and no runtime behavior.

## Impact

- **New:** `src/test/java/` trees where none exist today; a shared test-scoped support module holding container definitions and base classes.
- **Modified:** parent `pom.xml` (Testcontainers BOM, Failsafe plugin, Surefire `*IT` exclusion); four service `pom.xml` files (Testcontainers, `spring-boot-testcontainers`, JUnit 5 test dependencies).
- **Build:** `mvn verify` now requires a running container runtime. `mvn test` continues to work without one, enforced by the `*Test` / `*IT` naming split.
- **No Java production-code changes are expected.** If a test reveals a defect, fixing it belongs to that defect's own change, not this one. Given that identity was only ever verified by hand, expect this to happen.

## Prerequisites

A working container runtime on the developer's machine. This is a genuine external dependency, not a soft one — it is why this work was split out of `add-schema-migrations` rather than blocking a schema change on an environment problem.

Two environment hazards already observed on the primary development machine, recorded here so they are diagnosed in seconds rather than hours:

- **Postgres 18 rejects some JVM timezone identifiers.** The JDBC driver sends the JVM default timezone as a connection parameter, and Postgres 18 dropped several legacy aliases — `Asia/Saigon` fails where `Asia/Ho_Chi_Minh` succeeds. It surfaces as `FATAL: invalid value for parameter "TimeZone"`, which reads like a configuration error rather than a timezone-alias problem. Task 1.4 decides how the suite handles this.
- **A second Postgres bound to port 5432.** A native Windows Postgres service competing with Docker's port forward produces `password authentication failed` against what looks like the right host and port. Testcontainers assigns random host ports and so is immune, but anything hand-pointed at 5432 is not.
