## Context

Jari is a Spring Boot 3.2.4 / Java 21 / Spring Cloud 2023.0.1 stack on Postgres and RabbitMQ, with four data services (identity, project, task, notification) and two stateless ones (Eureka, gateway).

There are no test source files anywhere in the repository. The preceding two changes built real behavior — gateway identity propagation, token claims, Flyway-owned schemas — and verified all of it by hand.

`add-schema-migrations` deliberately left one gap for this change: it made Flyway the schema owner but had no automated way to prove a migration works. Its own proposal names this change as the closer. That is the first requirement here, not an afterthought.

## Goals / Non-Goals

**Goals:**

- Integration tests run against real Postgres and real RabbitMQ, provisioned per run, on a machine with nothing manually started.
- Tests get their schema from the service's own migrations, so a broken migration fails the suite.
- The identity flow — built last phase, verified only by hand — has an automated test.
- One command runs everything; unit tests stay runnable without a container runtime.
- The approach for cross-service testing is decided now, before the services that need it exist.

**Non-Goals:**

- No CI pipeline. This change makes CI possible without configuring it.
- No coverage target. A coverage percentage would reward testing getters over testing event ordering.
- No frontend tests. The frontend is a demo surface, not a gate.
- No cross-service end-to-end test *implemented* here — only decided. The services it would span do not have their behavior yet.
- No production-code changes. If a test surfaces a defect, that defect gets its own change; folding a fix in here would make it unclear whether the harness or the fix is being verified.

## Decisions

### Testcontainers via `@ServiceConnection`

Spring Boot 3.1+ `@ServiceConnection` on a `@Container` field wires the container's connection details into the context automatically, so no test datasource properties are duplicated per service.

*Why:* the alternative is `@DynamicPropertySource` blocks repeated in every test class — the same URL/username/password plumbing four times over, drifting independently.

*Bonus that matters given the environment:* Testcontainers assigns random host ports, so the suite is immune to the port-5432 collision described in the proposal.

### Container reuse per test class, not per test method

Containers are declared `static` so one Postgres serves every test in a class, and each test isolates itself by transaction rollback or explicit cleanup rather than by a fresh container.

*Why:* container startup dominates runtime. A per-test container turns a fast suite into a slow one, and a slow suite gets skipped.

*Trade-off:* tests share a database and can pollute each other. Mitigation: transactional rollback for repository-level tests, explicit truncation for tests that cross a transaction boundary — which the event-driven tests in later phases necessarily will, since a consumer commits in its own transaction.

*Deliberately not adopted:* Testcontainers' reuse-across-runs flag. It makes the suite faster but requires opt-in developer configuration and can carry state between runs, which conflicts with the requirement that a run not be affected by a previous one.

### Tests run migrations; they never generate schema from entities

Test configuration leaves Flyway enabled and `ddl-auto` at `validate`, exactly as production runs.

*Why:* a harness that generates its schema from entities would pass against a broken migration, which inverts the point. This is the specific gap `add-schema-migrations` left open, so it gets a deliberate test — a migration is temporarily broken and the suite is confirmed to fail naming that migration.

*Consequence worth stating:* this makes every integration test also a migration test, which is why a migration mistake should surface as a broad, obvious failure rather than a subtle one.

### Separate unit from integration tests by naming convention

`*Test` runs in Surefire without a container runtime; `*IT` runs in Failsafe during `verify`.

*Why:* keeps `mvn test` usable when Docker is not running, and makes the container-runtime requirement explicit rather than a mysterious failure.

*Consequence to make loud:* the failure message when the runtime is absent must be clear. Testcontainers' native message is reasonably explicit, but the README must name the prerequisite so nobody debugs it as a connection problem — particularly given the two environment hazards in the proposal, both of which present as authentication or configuration errors rather than as what they are.

### Shared test-support module, extracted after the second service

One module holding container definitions and base test classes, depended on with `<scope>test</scope>` — but written only once two services have visibly duplicated the setup.

*Why extract:* four copies of container setup will diverge.

*Why not first:* factoring from two real usages produces a better shape than guessing at one. The design's own migration plan orders it this way deliberately.

*Counter-argument acknowledged:* this adds another shared module, the same coupling being reduced elsewhere by shrinking `jari-common`. The difference is that this is test-scoped — a change to it cannot force a production redeploy, only a test recompile. Stated so the apparent inconsistency reads as a decision rather than an oversight.

### RabbitMQ is tested against a real broker, not a mock

A real RabbitMQ Testcontainer for publish/consume behavior. `RabbitTemplate`-boundary assertions (verifying the template was called with the right message) are supplemental fast unit coverage on top of that, never a substitute.

*Why:* the whole point of the Phase 6 event flow is ordering and delivery semantics, which a `RabbitTemplate`-boundary mock cannot exercise. A mock verifies that the code intended to publish; only a broker verifies that publishing works.

*To verify when Phase 6 lands:* that this decision still holds once there is a real event flow to test, rather than being reaffirmed from theory.

### Cross-service testing: decided now, implemented later

Per-service integration tests provision infrastructure, not sibling services. The project's definition of done — user A creates a project, invites B, assigns a task, B sees it, a non-member gets 403, a notification exists — spans identity, project, task, and notification.

**Decision:** two tiers.

1. **Per-service integration tests** (`*IT`) with real infrastructure and *stubbed* siblings. When task-service calls project-service over Feign in Phase 3, the sibling is stubbed at the HTTP boundary. This is where circuit-breaker behavior gets tested, by making the stub fail or hang.
2. **One end-to-end test, `ProjectCollaborationE2EIT`,** bringing up the whole stack via Testcontainers' compose support and driving it through the gateway. It asserts the definition of done and nothing more — it is the single most expensive test in the suite and must not become where edge cases accumulate. Each clause maps to one assertion in this one class.

*Why two tiers rather than only end-to-end:* an end-to-end test cannot easily simulate "project-service is timing out," which is the specific behavior Phase 3 exists to teach. And a single end-to-end test covering everything fails uninformatively.

*Why not only per-service:* stubbed siblings can agree with each other and both be wrong. The end-to-end test is what catches a serialization or routing mismatch between two services that each pass their own tests.

*Deferred:* `ProjectCollaborationE2EIT` lands with Phase 6, when all the behavior it asserts exists. Building it now would mean asserting behavior that has not been written — the test would either be commented out or assert something weaker than the definition of done, and both rot.

## Risks / Trade-offs

- **Suite requires a container runtime** → Documented prerequisite; unit tests remain runnable without one. This also constrains CI to a Docker-capable runner later.
- **Environment hazards present as unrelated errors** → The timezone-alias and duplicate-port-5432 problems both look like authentication or configuration failures. Both are recorded in the proposal and must reach the README, or they will cost someone an afternoon.
- **Container startup makes the suite slow enough to skip** → Static containers per class, and keep the expensive end-to-end tier to a single test. If the suite still grows slow, revisit the reuse flag with its state-leakage trade-off understood.
- **Tests pollute each other through a shared database** → Transactional rollback where possible; explicit truncation where a test crosses a transaction boundary.
- **Writing tests reveals real defects immediately** → Likely, given identity was only ever verified by hand. Resist fixing them here: record each as its own change, or this change never finishes.
- **Every integration test is also a migration test** → A schema mistake fails many tests at once. Good for detection, noisy for diagnosis; the broken-migration task exists to confirm the failure names the migration rather than surfacing as a wall of unrelated errors.

## Migration Plan

1. Add Testcontainers dependency management and Failsafe configuration to the parent `pom.xml`; confirm `mvn test` still passes with no runtime available.
2. Settle the JVM-timezone question so the suite is reproducible across machines.
3. Convert the identity service first, since it has the flow worth testing: base class with a Postgres container, then the identity flow tests.
4. Prove the harness verifies migrations by breaking one deliberately and confirming the failure names it.
5. Apply the pattern to project, task, and notification.
6. Extract the shared test-support module once two services exist and the duplication is visible — not before.
7. Confirm `mvn verify` passes from a clean checkout.
8. Document the prerequisite, the naming convention, and the environment hazards.

**Rollback:** revert the commits. Nothing in this change alters production behavior, so rollback is free.

## Open Questions

- **Does `DataSeeder` survive, or do tests build their own fixtures?** Two mechanisms creating users will diverge. Recommendation: tests own their fixtures, and `DataSeeder` remains a development convenience behind a profile. Inherited from `add-schema-migrations`, which correctly judged it a test question rather than a schema one.
- **How is the JVM timezone handled?** Pinning UTC for Surefire/Failsafe makes the suite machine-independent, at the cost of tests not running in local wall-clock time. The alternative is treating it as a per-machine environment fix, which leaves the suite non-reproducible for the next developer. Task 1.4 decides; the recommendation is to pin it, because a test suite that depends on the developer's locale is a test suite that fails for someone else.
