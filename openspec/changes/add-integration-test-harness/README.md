# add-integration-test-harness

Phase 2b: a Testcontainers integration-test harness running against real Postgres and RabbitMQ, plus the first automated test of the identity flow and a decided approach for cross-service coverage.

Split out of `add-schema-migrations`, which landed the Flyway migrations this harness verifies. That change's migrations are currently checked by booting the stack by hand; closing that gap is the first thing this one does.
