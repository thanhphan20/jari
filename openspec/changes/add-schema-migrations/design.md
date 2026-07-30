## Context

Jari is a Spring Boot 3.2.4 / Java 21 / Spring Cloud 2023.0.1 stack on Postgres and RabbitMQ. After the preceding change it has four data services: identity, project, task, notification. Two services hold no data (Eureka, gateway).

All four data services run `spring.jpa.hibernate.ddl-auto: update`, so the live schema is a side effect of whatever entity classes were on the classpath at last boot — unversioned, unreviewed, and unreproducible.

The sequencing argument matters. The phases that follow add a `ProjectMember` table, a per-project key counter, task ordering state, and consumer deduplication state. Retrofitting migrations after four more tables exist is strictly more work than doing it now, while each service owns exactly one table.

## Goals / Non-Goals

**Goals:**

- Every schema is defined by reviewable files, applied in order, recorded in history.
- Entity/schema drift is a startup failure, not a silent schema mutation.
- Each service owns its own migrations; no service migrates another's tables.

**Non-Goals:**

- **No integration-test harness.** Split into `add-integration-test-harness`; see "The test harness is a separate phase" below.
- No CI pipeline.
- No rewrite of existing schema shape. The baseline captures what the entities currently define, defects included. `Task.key` lacking a unique constraint is a known defect belonging to a later phase; the baseline records it faithfully rather than quietly fixing it.
- No production-code changes. If baselining surfaces a defect, that defect gets its own change.

## Decisions

### Flyway, not Liquibase

Plain SQL migrations under `src/main/resources/db/migration`, auto-applied by Spring Boot's autoconfiguration when `flyway-core` is on the classpath.

*Why:* the migrations are Postgres-specific and always will be; Liquibase's database-abstraction changelogs buy portability this project does not want and add a second dialect to learn. SQL files are also the more honest artifact — a reviewer reads exactly what runs.

*Trade-off:* no automatic rollback. Accepted; forward-only migrations with a documented `down -v` reset is the right model for a project with no production data.

### Migration directories live per service, not centralized

Each service's migrations sit inside that service's own module.

*Why:* it matches schema ownership. A central directory is the natural instinct and it quietly breaks the ownership boundary the previous change established — worth stating explicitly for that reason.

### The baseline describes the entities, not the current database

The baseline is derived from what the entity classes define, against a clean database — not dumped from a live one.

*Why:* the current databases contain accumulated `ddl-auto: update` residue — columns from entity fields that were renamed or removed never get dropped. Baselining from a live database would enshrine that residue permanently.

*Alternative considered:* `flyway.baselineOnMigrate` against the existing database, treating current state as version 1. Rejected: it makes the schema unreproducible from the files, which is the entire point.

### The baseline is hand-written, and constraints are named deliberately

Hibernate's generated DDL is a reference for column types and nullability, not the artifact that ships. The committed baseline is hand-written with readable constraint names (`uk_users_email`) rather than Hibernate's generated hashes (`UK_6dotkott2kjsp8vw4d0m25fb7`).

*Why this is safe:* Hibernate's `validate` checks that tables and columns exist with compatible types. It does not check constraint names, unique constraints, indexes, or foreign keys. So naming constraints for humans costs nothing at startup and makes the file reviewable, which the spec explicitly requires ("explicit in the file rather than implied by entity annotations").

*Corollary worth knowing:* because `validate` ignores constraints entirely, it will **not** catch a missing unique constraint or a wrong nullability. `validate` is drift detection for columns, not a proof of schema conformance. A clean startup means "the columns line up," not "the migration is correct."

### Cross-database references are not foreign keys

`Project.leadUserId`, `Task.assigneeId`, `Task.reporterId`, and `Notification.userId` all hold identity-service user ids. None becomes a foreign key.

*Why:* the referenced table lives in another service's database. A foreign key would be impossible to declare and, if the databases were ever colocated, would be a boundary violation dressed up as data integrity. The baseline files say this in a comment so the omission reads as a decision rather than an oversight.

### `ddl-auto: validate`, not `none`

*Why:* `validate` catches the specific mistake this project will make repeatedly — adding an entity field and forgetting the migration — at startup, loudly. `none` would let the application start and fail later at query time with a confusing error.

*Trade-off:* `validate` is stricter than it looks on column types and `LocalDateTime` precision, and will reject mismatches that are cosmetically harmless. Expect to iterate on the baseline once.

### The test harness is a separate phase

Originally this change carried both the migrations and a Testcontainers integration-test harness, on the reasoning that migrations and the tests verifying them belong together.

**Decision:** split them. Migrations land here; the harness lands in `add-integration-test-harness`.

*Why:* the harness has a hard external prerequisite — a working container runtime — so coupling a schema change to it means an environment problem blocks a schema change. Splitting also keeps each change's diff answerable to one question: "is this schema right?" versus "does this harness verify it?"

*What the split costs, stated rather than glossed:* the migrations in this change are verified by booting the services, not by an automated test. A broken migration is caught by a developer, not by `mvn verify`. This is the gap the next phase exists to close, which is why that phase should follow closely rather than drift.

*Deliberately not left behind here:* no Testcontainers dependency management, no Failsafe plugin configuration. Build plumbing for tests that do not exist yet is scaffolding, and it belongs with the change that uses it.

## Risks / Trade-offs

- **`ddl-auto: validate` rejects a baseline that looks correct** → Expect one or two rounds of iteration, particularly on timestamp precision. Work one service at a time so a failure is localized.
- **The baseline enshrines existing schema defects** → Deliberate. `Task.key` has no unique constraint; the baseline records that and says so in a comment. Fixing it here would make the change's diff span two concerns.
- **`validate` gives false confidence** → It checks columns, not constraints. Documented above so a clean boot is not mistaken for a correct schema.
- **No automated verification until the next phase** → Accepted as the cost of the split, and called out in the proposal rather than buried.
- **Destructive to local data** → `down -v` and re-register. There is no local data worth preserving.

## Migration Plan

1. Add Flyway to dependency management in the parent `pom.xml`.
2. Write `V1__baseline.sql` for each of the four data services from their entity definitions.
3. Add the Flyway dependency and switch `ddl-auto` to `validate`, one service at a time.
4. Bring the stack up from removed volumes; confirm each service migrates and starts.
5. Restart without clearing volumes; confirm no migration re-applies.
6. Verify drift detection by adding an entity field with no migration, then revert.
7. Document the forward-only rule and the `down -v` reset path.

**Rollback:** revert the commits and restore `ddl-auto: update`. Local databases are dropped either way.

## Open Questions

- **Baseline as one file per service or split by table?** Resolved: one `V1__baseline.sql` per service, since each currently has exactly one table. Revisit if a service's baseline ever needs to describe more than about three tables.
- **Does `DataSeeder` survive?** Deferred to `add-integration-test-harness`, where it becomes a real question — two mechanisms creating users will diverge once tests own their fixtures. It has no bearing on the schema, so it does not need answering here.
