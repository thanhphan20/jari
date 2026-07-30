## Context

Jari is a Spring Boot 3.2.4 / Java 21 / Spring Cloud 2023.0.1 stack on Postgres and RabbitMQ. After the preceding change it has four data services: identity, project, task, notification. Two services hold no data (Eureka, gateway).

Two absences motivate this change. There are no test source files anywhere in the repository. And all four data services run `spring.jpa.hibernate.ddl-auto: update`, so the live schema is a side effect of whatever entity classes were on the classpath at last boot — unversioned, unreviewed, and unreproducible.

The sequencing argument matters here. The phases that follow add a `ProjectMember` table, a per-project key counter, task ordering state, and consumer deduplication state, and they introduce the behaviors the project exists to study: circuit-breaking on a synchronous dependency, event-driven replication, idempotent consumption, and a measurable staleness window. None of those are verifiable by reading code or exercising Swagger. Retrofitting migrations after four more tables exist is strictly more work, and retrofitting tests onto a distributed system that already appears to work produces tests that assert current behavior rather than intended behavior.

## Goals / Non-Goals

**Goals:**

- Every schema is defined by reviewable files, applied in order, recorded in history.
- Entity/schema drift is a startup failure, not a silent schema mutation.
- Integration tests run against real Postgres and real RabbitMQ, provisioned per run, on a machine with nothing manually started.
- The identity flow — the one the previous change built and verified only by hand — has an automated test.
- One command runs everything.
- The approach for cross-service testing is decided now, before the services that need it exist.

**Non-Goals:**

- No CI pipeline. That is a later concern; this change makes CI possible without configuring it.
- No coverage target. A coverage percentage would reward testing getters over testing event ordering.
- No frontend tests. The frontend is a demo surface, not a gate.
- No cross-service end-to-end test *implemented* here — only decided. The services it would span do not have their behavior yet.
- No production-code changes. If a test surfaces a defect, that defect gets its own change; folding a fix in here would make it unclear whether the harness or the fix is being verified.
- No rewrite of existing schema shape. The baseline captures what the entities currently define, defects included. `Task.key` lacking a unique constraint is a known defect belonging to a later phase; the baseline records it faithfully rather than quietly fixing it.

## Decisions

### Flyway, not Liquibase

Plain SQL migrations under `src/main/resources/db/migration`, auto-applied by Spring Boot's autoconfiguration when `flyway-core` is on the classpath.

*Why:* the migrations are Postgres-specific and always will be; Liquibase's database-abstraction changelogs buy portability this project does not want and add a second dialect to learn. SQL files are also the more honest artifact — a reviewer reads exactly what runs.

*Trade-off:* no automatic rollback. Accepted; forward-only migrations with a documented `down -v` reset is the right model for a project with no production data.

### Baseline generated from a clean database, not from the current one

Drop each database, let Hibernate generate the schema once from entities via `ddl-auto: create`, dump the result, hand-clean it into `V1__baseline.sql`, then switch to `validate` and confirm the service starts.

*Why:* the current databases contain accumulated `ddl-auto: update` residue — columns from entity fields that were renamed or removed never get dropped. Baselining from a live database would enshrine that residue permanently.

*Alternative considered:* `flyway.baselineOnMigrate` against the existing database, treating current state as version 1. Rejected: it makes the schema unreproducible from the files, which is the entire point.

*Practical note:* the generated dump is a starting point, not the artifact. Hibernate's output needs review for constraint naming and column types before it becomes the baseline.

### `ddl-auto: validate`, not `none`

*Why:* `validate` catches the specific mistake this project will make repeatedly — adding an entity field and forgetting the migration — at startup, loudly. `none` would let the application start and fail later at query time with a confusing error.

*Trade-off:* `validate` is stricter than it looks and will reject mismatches that are cosmetically harmless, particularly around column types and `LocalDateTime` precision. Expect to iterate on the baseline once.

### Testcontainers via `@ServiceConnection`

Spring Boot 3.1+ `@ServiceConnection` on a `@Container` field wires the container's connection details into the context automatically, so no test datasource properties are duplicated per service.

*Why:* the alternative is `@DynamicPropertySource` blocks repeated in every test class — the same URL/username/password plumbing four times over, drifting independently.

### Container reuse per test class, not per test method

Containers are declared `static` so one Postgres serves every test in a class, and each test isolates itself by transaction rollback or explicit cleanup rather than by a fresh container.

*Why:* container startup dominates runtime. A per-test container turns a fast suite into a slow one, and a slow suite gets skipped.

*Trade-off:* tests share a database and can pollute each other. Mitigation: rely on transactional rollback for repository-level tests, and explicit truncation for tests that cross a transaction boundary — which the event-driven tests in later phases necessarily will.

*Deliberately not adopted yet:* Testcontainers' reuse-across-runs flag. It makes the suite faster but requires opt-in developer configuration and can carry state between runs, which conflicts with the requirement that a run not be affected by a previous one.

### Shared test-support module

One module holding the container definitions and base test classes, depended on with `<scope>test</scope>`.

*Why:* four copies of container setup will diverge.

*Counter-argument acknowledged:* this adds another shared module, the same coupling being reduced elsewhere by shrinking `jari-common`. The difference is that this is test-scoped — a change to it cannot force a production redeploy, only a test recompile. Worth stating so the apparent inconsistency is a decision rather than an oversight.

### Separate unit from integration tests by naming convention

`*Test` runs in Surefire without Docker; `*IT` runs in Failsafe during `verify`.

*Why:* keeps `mvn test` usable when Docker is not running, and makes the container-runtime requirement explicit rather than a mysterious failure.

*Consequence to make loud:* the failure message when Docker is absent must be clear. Testcontainers' native failure is reasonably explicit, but the README should name the prerequisite so nobody debugs it as a connection problem.

### Cross-service testing: decided now, implemented later

Per-service integration tests provision infrastructure, not sibling services. The project's definition of done — user A creates a project, invites B, assigns a task, B sees it, a non-member gets 403, a notification exists — spans identity, project, task, and notification.

**Decision:** two tiers.

1. **Per-service integration tests** (`*IT`) with real infrastructure and *stubbed* siblings. When task-service calls project-service over Feign in Phase 3, the sibling is stubbed at the HTTP boundary. This is where circuit-breaker behavior gets tested, by making the stub fail or hang.
2. **One end-to-end test, `ProjectCollaborationE2EIT`,** that brings up the whole stack via Testcontainers' compose support and drives it through the gateway. It asserts the definition of done and nothing more — it is the single most expensive test in the suite and must not become the place where edge cases accumulate. Each clause maps to one assertion in this one class: user A creates a project and invites B; B is assigned a task; B can see it; a non-member gets 403; a notification exists for B.

*Why two tiers rather than only end-to-end:* an end-to-end test cannot easily simulate "project-service is timing out," which is the specific behavior Phase 3 exists to teach. And a single end-to-end test that covers everything fails uninformatively.

*Why not only per-service:* stubbed siblings can agree with each other and both be wrong. The end-to-end test is what catches a serialization or routing mismatch between two services that each pass their own tests.

*Deferred:* the end-to-end test lands with Phase 6, when all the behavior it asserts exists. Building it now would mean asserting behavior that has not been written.

## Risks / Trade-offs

- **`ddl-auto: validate` rejects a baseline that looks correct** → Expect one or two rounds of iteration, particularly on timestamp precision and constraint naming. Budget for it rather than treating it as a blocker; work one service at a time so a failure is localized.
- **The baseline enshrines existing schema defects** → Deliberate. `Task.key` has no unique constraint; the baseline records that. Fixing it here would make the change's diff span two concerns. Note it so the later phase knows the migration is its job.
- **Shared test module reintroduces coupling** → Test-scoped only, so it cannot force a production redeploy. Stated above as an accepted inconsistency.
- **Suite requires Docker** → Documented prerequisite; unit tests remain runnable without it. This will also constrain CI to a Docker-capable runner later.
- **Container startup makes the suite slow enough to skip** → Static containers per class, and keep the expensive end-to-end tier to a single test. If the suite still grows slow, revisit the reuse flag with its state-leakage trade-off understood.
- **Tests pollute each other through a shared database** → Transactional rollback where possible; explicit truncation where the test crosses a transaction boundary. The later event-driven tests will need the latter, since a consumer commits in its own transaction.
- **Writing tests reveals real defects immediately** → Likely, given identity was only ever verified by hand. Resist fixing them here: record each as its own change, or this change never finishes.

## Migration Plan

1. Add Flyway and Testcontainers dependency management to the parent `pom.xml`; configure Failsafe.
2. Convert one service end-to-end first — identity, since it has the flow worth testing. Baseline from clean, switch to `validate`, confirm boot, then add the harness and the identity flow test.
3. Apply the established pattern to project, task, and notification.
4. Extract the shared test-support module once two services exist and the duplication is visible — not before, so it is factored from real usage rather than guessed.
5. Confirm `mvn verify` passes from a clean checkout with volumes removed.
6. Document the Docker prerequisite, the two-tier testing decision, and the `down -v` reset path.

**Rollback:** revert the commits and restore `ddl-auto: update`. Local databases are dropped either way.

## Open Questions

- **Does `DataSeeder` survive into the test harness, or do tests build their own fixtures?** Two mechanisms creating users will diverge. Recommendation: tests own their fixtures; `DataSeeder` remains only as a development convenience behind a profile, if it survives the previous change at all.
- **Baseline as one file per service or split by table?** One `V1__baseline.sql` is simpler to review; splitting is tidier as the count grows. Recommendation: one file, since each service currently has one or two tables.
- **Should the migration directory be under version control per service or centralized?** Per service, to match schema ownership — worth stating explicitly because a central directory is the natural instinct and it quietly breaks the ownership boundary this change is establishing.

## Decided (was an open question)

**Test-scope RabbitMQ for services that publish but do not consume.** Decision: a real RabbitMQ Testcontainer for publish/consume integration behavior — the whole point of the Phase 6 event flow is ordering and delivery semantics, which a `RabbitTemplate`-boundary mock cannot exercise. `RabbitTemplate`-boundary assertions (verifying the template was called with the right message) are supplemental, fast unit-level coverage on top of that, not a substitute for it. Task 7.5 (tasks.md) is updated to verify and record this decision rather than leaving it open.
