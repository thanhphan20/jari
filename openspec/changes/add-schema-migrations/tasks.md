## 1. Build plumbing

- [x] 1.1 Add Flyway to the parent `pom.xml` dependency management.
  - `flyway.version` pinned to `9.22.3` — the version Spring Boot 3.2.4's own dependency management already resolves, so this makes the version explicit rather than overriding it.
  - **Reverted mid-change**: a Testcontainers BOM and Failsafe/Surefire configuration were added here before the scope split, then removed. Build plumbing for tests that do not exist yet is scaffolding; it moves to `add-integration-test-harness` along with the tests that use it.

## 2. Baseline migrations

- [x] 2.1 Write `V1__baseline.sql` for the identity service.
  - `users`: id, email, password, first_name, last_name, username, avatar_url, active, created_at, updated_at. Unique constraints on `email` and `username`, named `uk_users_email` / `uk_users_username`.
- [x] 2.2 Write `V1__baseline.sql` for the project service.
  - `projects`, unique `key` (`uk_projects_key`). `lead_user_id` is a plain `BIGINT`, not a foreign key — commented in the file, since the referenced table lives in another service's database.
- [x] 2.3 Write `V1__baseline.sql` for the task service, recording `Task.key`'s missing unique constraint faithfully.
  - `tasks` has `key VARCHAR(255) NOT NULL` with **no** unique constraint, matching the entity. The file carries a `KNOWN DEFECT` comment: nothing stops two tasks sharing `PROJ-123`, the per-project key counter that makes uniqueness enforceable belongs to a later phase, and adding the constraint here would mean the baseline no longer describes the current entities.
  - `order` maps to column `task_order`, since `order` is reserved in Postgres and cannot be an unquoted column name.
- [x] 2.4 Write `V1__baseline.sql` for the notification service.
  - `notifications`, with `user_id` a plain `BIGINT` for the same cross-database reason as project's `lead_user_id`.
- [x] 2.5 Add the Flyway dependency and switch `ddl-auto` to `validate` in all four services.
  - `flyway-core` added to each service's `pom.xml`; `ddl-auto: update` → `validate` in each `application.yml`, with a comment naming Flyway as the schema owner so a later reader does not "helpfully" switch it back.
  - `mvn -DskipTests install` succeeds across all modules.

## 3. Verify the migrations at runtime

**Status: mostly verified.** 3.1-3.3 were executed and passed while implementing `add-kanban-browser-demo`, which needed a working backend. 3.4 remains open.

- [x] 3.1 Bring the stack up from removed volumes; confirm each service applies its migrations and starts with no validation mismatch.
  - Stack brought up from a destroyed volume on Flyway-built images. All four data services report `{"status":"UP"}` with `ddl-auto: validate` in force — meaning Hibernate compared every entity against the migrated schema and found no mismatch. The hand-written baselines match the entities.
- [x] 3.2 Restart without clearing volumes; confirm no migration re-applies and startup succeeds.
  - `docker compose restart user-service` against the already-migrated database: came back `UP`, and `flyway_schema_history` still holds exactly one row.
- [x] 3.3 Confirm `flyway_schema_history` records the applied version in each of the four databases.
  - All four report `1 baseline success=true`: `jari_user`, `jari_project`, `jari_task`, `jari_notification`.
- [ ] 3.4 Verify drift detection: add an entity field with no migration, confirm startup fails naming the mismatch, then revert.
  - Worth doing deliberately rather than assuming: per design, `validate` checks columns and types but **not** constraints, so this proves column drift detection only.
  - Still open. Note that 3.1 is *weak* evidence for this — a passing `validate` shows the schema matches, not that a mismatch would be caught. Only deliberately breaking it proves the mechanism.

## 4. Document

- [ ] 4.1 Document the forward-only migration rule in `README.md`: never edit an applied migration, always add a new one.
- [ ] 4.2 Document `docker compose down -v` as the supported way to rebuild a schema from scratch.
- [x] 4.3 Note in `README.md` that migrations are currently verified by booting the stack, not by an automated test, and that `add-integration-test-harness` closes that gap.
  - Done in the Roadmap section while updating it for `add-kanban-browser-demo`. The stale "`ddl-auto: update` is still in force; no migration tooling yet" line in Known Limitations was removed at the same time — it had become false and leaving it while editing the surrounding section would have been worse than the small scope bleed.

## 5. Decisions settled during this change

- [x] 5.1 One baseline file per service, not split by table.
  - Each service has exactly one table today, so one file is simpler to review. Revisit if a baseline ever describes more than about three tables.
- [x] 5.2 Hand-write the baseline rather than shipping Hibernate's generated DDL.
  - Hibernate's output is a reference for types and nullability; the committed file uses readable constraint names. Safe because `validate` ignores constraint names entirely — and that same fact is why a clean boot is not a proof of schema correctness. Both halves recorded in design.md.
- [x] 5.3 Cross-service id columns are plain `BIGINT`, never foreign keys, and each baseline says so in a comment.
- [x] 5.4 Split the integration-test harness into its own change.
  - Rationale and the cost of the split are recorded in design.md ("The test harness is a separate phase") and the proposal's Out of Scope section. The `DataSeeder`-versus-test-fixtures question moves to that change, where it is an actual question.
