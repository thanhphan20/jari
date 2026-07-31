# add-schema-migrations

Phase 2: Flyway-owned schemas per data service, with `ddl-auto: validate` so entity/schema drift fails startup instead of silently mutating tables.

The Testcontainers integration-test harness originally bundled with this change now has its own phase — see `add-integration-test-harness`. Splitting them keeps this change reviewable as a schema change on its own, and means the harness is not gated on schema work being finished.
