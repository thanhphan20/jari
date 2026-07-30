## Why

Jari has no schema history. Every data service runs `spring.jpa.hibernate.ddl-auto: update`, meaning the schema is whatever Hibernate inferred from the entity classes on last boot. There is no record of what the schema is, no way to reproduce it, and no way to review a change to it.

The gap compounds with every subsequent phase. The remaining work adds a `ProjectMember` table, a per-project key counter, task ordering columns, and notification deduplication state — so converting to migrations later means converting strictly more tables. Doing it now, while each service still has exactly one table, is the cheapest this conversion will ever be.

`ddl-auto: update` is also dangerous in a way that is easy to miss: it adds columns but never drops them, so a renamed or removed entity field leaves residue behind permanently. The previous change already hit the sharp edge of this — adding non-null credential columns to a populated table — and had to drop the database to get a clean result.

This change therefore lands before the feature phases, not after them.

## What Changes

- **Flyway owns the schema** in all four data services (identity, project, task, notification). Each service gets a baseline migration reflecting its current entities, plus a versioned migration directory it alone controls.
- **`ddl-auto` changes from `update` to `validate`**, so Hibernate verifies that the entities match the migrated schema and fails fast on drift instead of silently mutating tables.
- **The forward-only rule is documented**: an applied migration is never edited, only superseded by a new one.

## Capabilities

### New Capabilities

- `schema-migrations`: Each service owns its database schema through versioned, reviewable migrations, and the application refuses to start against a schema that does not match its entities.

### Modified Capabilities

None. No service contract changes and no runtime behavior changes for a correctly migrated database. `ddl-auto: validate` changes startup behavior only in the presence of drift, which is the intent rather than a contract change.

## Impact

- **New:** `src/main/resources/db/migration/V1__baseline.sql` in each of the four data services.
- **Modified:** `application.yml` in four services (`ddl-auto: update` → `validate`); four service `pom.xml` files (Flyway dependency); parent `pom.xml` (Flyway version in dependency management).
- **Destructive to local data:** baselining is done against a clean database so the baseline reflects entities rather than accumulated `ddl-auto` drift. Existing local databases are dropped via `docker compose down -v`.
- **No Java production-code changes.** This change touches SQL, YAML, and POM files only.

## Out of Scope

**The integration-test harness is a separate change** — see `add-integration-test-harness`. It was originally bundled here, on the reasoning that migrations and the tests that verify them belong together. Splitting it out was the right call for two reasons: this change stays reviewable as a self-contained schema change, and the harness carries a hard external prerequisite (a working container runtime) that would otherwise block a schema change from landing.

The consequence, stated plainly rather than left implicit: **the migrations in this change are verified by starting the services, not by an automated test.** Until `add-integration-test-harness` lands, a broken migration is caught by a developer booting the stack, not by `mvn verify`. That is a real gap, and closing it is the first thing the next phase does.
