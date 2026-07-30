## 1. Build plumbing

- [ ] 1.1 Add Flyway and Testcontainers BOM entries to the parent `pom.xml` dependency management.
- [ ] 1.2 Configure the Failsafe plugin for `*IT` classes, leaving Surefire to run `*Test` classes.
- [ ] 1.3 Verify `mvn test` still succeeds with no container runtime available, and that `mvn verify` invokes the integration phase.

## 2. Identity service: migrations

- [ ] 2.1 Drop the identity database so the baseline is derived from entities rather than accumulated drift.
- [ ] 2.2 Generate the schema once from entities, dump it, and review the output for column types, nullability, and constraint naming.
- [ ] 2.3 Hand-clean the dump into `V1__baseline.sql` under the service's own migration directory.
- [ ] 2.4 Add the Flyway dependency and switch `ddl-auto` to `validate`.
- [ ] 2.5 Start against an empty database; confirm migrations apply and the service starts with no validation mismatch.
- [ ] 2.6 Restart; confirm no migration re-applies and startup succeeds.
- [ ] 2.7 Verify drift detection: add an entity field with no migration, confirm startup fails naming the mismatch, then revert.

## 3. Identity service: test harness and first test

- [ ] 3.1 Add Testcontainers and `spring-boot-testcontainers` test dependencies.
- [ ] 3.2 Create a base integration test class with a static Postgres container wired via `@ServiceConnection`.
- [ ] 3.3 Confirm the test schema is created by the service's own migrations, not by entity generation.
- [ ] 3.4 Verify a deliberately broken migration fails the suite with a message naming the migration, then revert.
- [ ] 3.5 Test registration: a user row is persisted and the stored password is a hash rather than the submitted plaintext.
- [ ] 3.6 Test authentication: a token is issued and its `userId` claim equals the persisted primary key.
- [ ] 3.7 Test invalid credentials: HTTP 401 and no token issued.
- [ ] 3.8 Test self-lookup with authenticated identity: returns the registered user.
- [ ] 3.9 Test self-lookup without identity: HTTP 401.
- [ ] 3.10 Confirm the provisioned container is removed after the run and that two consecutive runs both pass.

## 4. Remaining services: migrations

- [ ] 4.1 Baseline the project service using the pattern established in section 2.
- [ ] 4.2 Baseline the task service. Record faithfully that `Task.key` has no unique constraint — it is a known defect belonging to a later phase, not something to fix here.
- [ ] 4.3 Baseline the notification service.
- [ ] 4.4 Switch all three to `validate` and confirm each starts against an empty database.
- [ ] 4.5 Confirm no service's migrations reference another service's tables.
- [ ] 4.6 Bring the full stack up from removed volumes and confirm every service migrates and starts.

## 5. Remaining services: test harness

- [ ] 5.1 Add a Postgres-backed integration test base and one smoke test to the project service.
- [ ] 5.2 Same for the task service.
- [ ] 5.3 Same for the notification service, including a RabbitMQ container since it will consume in Phase 6.
- [ ] 5.4 Extract the duplicated container setup into a shared test-scoped support module now that the duplication is visible.
- [ ] 5.5 Confirm the shared module is `test` scope everywhere so it cannot affect a production artifact.

## 6. Cross-service testing decision

- [ ] 6.1 Document the two-tier approach: per-service `*IT` with stubbed siblings, plus one `*E2EIT` over the whole stack.
- [ ] 6.2 Record which tier owns which concern — circuit-breaker and failure-injection behavior in tier one, the definition of done in tier two.
- [ ] 6.3 Record explicitly that the end-to-end test is deferred to Phase 6, and why building it now would mean asserting behavior that does not exist.
- [ ] 6.4 Map each clause of the project's definition of done to the named test that will assert it.

## 7. Verify and document

- [ ] 7.1 Run `mvn verify` from a clean checkout with volumes removed; confirm all tests pass.
- [ ] 7.2 Confirm the failure message is clear when the container runtime is unavailable, and does not present as an unrelated connection error.
- [ ] 7.3 Document the Docker prerequisite, the `*Test` versus `*IT` convention, and how to run each.
- [ ] 7.4 Document the forward-only migration rule: never edit an applied migration, always add a new one.
- [ ] 7.5 Settle the design open questions — `DataSeeder` versus test-owned fixtures, single versus split baseline files, and whether publication assertions need a real broker — and record each decision.
- [ ] 7.6 Record any defects the new tests revealed as separate changes rather than fixing them here.
