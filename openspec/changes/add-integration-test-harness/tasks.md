## 1. Build plumbing

- [ ] 1.1 Add the Testcontainers BOM to the parent `pom.xml` dependency management.
  - `1.19.7` is the version Spring Boot 3.2.4 resolves; pin it explicitly rather than overriding.
- [ ] 1.2 Configure the Failsafe plugin for `*IT` classes, and exclude `*IT` from Surefire so it does not run them twice.
- [ ] 1.3 Verify `mvn test` still succeeds with no container runtime available, and that `mvn verify` invokes the integration phase.
- [ ] 1.4 Settle the JVM-timezone question and record the decision.
  - Postgres 18 rejects legacy timezone aliases the JDBC driver may send (`Asia/Saigon` fails, `Asia/Ho_Chi_Minh` succeeds), surfacing as `FATAL: invalid value for parameter "TimeZone"`. Decide between pinning a timezone for Surefire/Failsafe (suite is machine-independent) and treating it as a per-machine environment fix (suite is not reproducible for the next developer). Design recommends pinning.

## 2. Identity service: harness and the migration check

- [ ] 2.1 Add Testcontainers and `spring-boot-testcontainers` test dependencies to the identity service.
- [ ] 2.2 Create a base integration test class with a static Postgres container wired via `@ServiceConnection`.
- [ ] 2.3 Confirm the test schema is created by the service's own Flyway migrations, not by entity generation.
  - This is the check `add-schema-migrations` deferred. Until it passes, that change's migrations remain verified only by hand.
- [ ] 2.4 Verify a deliberately broken migration fails the suite with a message naming the migration, then revert.
  - Confirm the failure names the migration rather than surfacing as a wall of unrelated errors — every integration test is also a migration test, so a schema mistake fails many at once.
- [ ] 2.5 Verify entity drift fails the suite: add an entity field with no migration, confirm failure, then revert.

## 3. Identity service: the identity flow

- [ ] 3.1 Test registration: a user row is persisted and the stored password is a hash rather than the submitted plaintext.
- [ ] 3.2 Test authentication: a token is issued and its `userId` claim equals the persisted primary key.
- [ ] 3.3 Test invalid credentials: HTTP 401 and no token issued.
- [ ] 3.4 Test self-lookup with authenticated identity: returns the registered user.
- [ ] 3.5 Test self-lookup without identity: HTTP 401.
- [ ] 3.6 Confirm the provisioned container is removed after the run and that two consecutive runs both pass.

## 4. Remaining services

- [ ] 4.1 Add a Postgres-backed integration test base and one smoke test to the project service.
- [ ] 4.2 Same for the task service.
- [ ] 4.3 Same for the notification service, including a RabbitMQ container since it will consume in Phase 6.
- [ ] 4.4 Extract the duplicated container setup into a shared test-scoped support module now that the duplication is visible.
  - Deliberately after 4.1/4.2 rather than before: factoring from two real usages beats guessing from one.
- [ ] 4.5 Confirm the shared module is `test` scope everywhere so it cannot affect a production artifact.

## 5. Cross-service testing decision

- [ ] 5.1 Document the two-tier approach: per-service `*IT` with stubbed siblings, plus one named end-to-end test, `ProjectCollaborationE2EIT`, over the whole stack.
- [ ] 5.2 Record which tier owns which concern — circuit-breaker and failure-injection behavior in tier one, the definition of done in tier two.
- [ ] 5.3 Record explicitly that the end-to-end test is deferred to Phase 6, and why building it now would mean asserting behavior that does not exist.
- [ ] 5.4 Map each clause of the project's definition of done to the named test that will assert it.
- [ ] 5.5 Verify the real-broker RabbitMQ decision still holds once Phase 6's event flow exists, rather than reaffirming it from theory.

## 6. Verify and document

- [ ] 6.1 Run `mvn verify` from a clean checkout with volumes removed; confirm all tests pass.
- [ ] 6.2 Confirm the failure message is clear when the container runtime is unavailable, and does not present as an unrelated connection error.
- [ ] 6.3 Document the container-runtime prerequisite, the `*Test` versus `*IT` convention, and how to run each.
- [ ] 6.4 Document the two environment hazards so neither costs an afternoon: Postgres 18's rejection of legacy timezone aliases, and a second Postgres bound to port 5432 producing an authentication error against what looks like the right host.
- [ ] 6.5 Settle whether `DataSeeder` survives or tests own their fixtures, and record the decision.
  - Two mechanisms creating users will diverge. Design recommends tests own their fixtures, with `DataSeeder` behind a profile as a development convenience.
- [ ] 6.6 Record any defects the new tests revealed as separate changes rather than fixing them here.
  - Expect some: identity was only ever verified by hand.
