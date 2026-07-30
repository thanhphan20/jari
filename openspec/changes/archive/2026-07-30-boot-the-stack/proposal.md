## Why

The Jari stack does not currently start end-to-end, which blocks every other phase of work: a distributed system cannot teach anything about eventual consistency, event ordering, or failure handling while it fails to boot. Five concrete defects in the container build and compose configuration explain the failure, and none of them are in application code — so no amount of feature work will fix them.

This change makes `docker compose up` produce a running, verifiable stack. It has no feature value and is purely a prerequisite.

## What Changes

- **Dockerfile restructured for dependency caching and slim runtime images.** All seven build stages currently run `COPY . .` followed by a full `mvn clean package -am`, so Maven re-resolves and rebuilds every dependency seven times with zero layer reuse. The resulting images are the full `maven:3.9-eclipse-temurin-21` image (build tooling and sources included) rather than a JRE runtime.
- **`.dockerignore` extended to exclude `node_modules`** (and other build output). It is currently absent, so `jari-frontend/node_modules` is copied into the build context of all seven stages.
- **Postgres volume mount corrected** from `/var/lib/postgresql` to `/var/lib/postgresql/data`. The current mount point shadows the data directory's parent instead of the data directory.
- **Startup ordering made deterministic** via `healthcheck` plus `depends_on: condition: service_healthy` for Postgres, RabbitMQ, and Eureka. Today `depends_on` only orders container *creation*, and `restart: always` converts the resulting connection-refused crash loop into what looks like slow startup.
- **Database provisioning made repeatable.** `postgres/init-db.sql` only executes when the volume is empty, so any pre-existing volume leaves all five databases uncreated and every data service unable to connect.
- **Untracked work committed.** `KanbanController`, `KanbanBoardDto`, `KanbanColumnDto`, `MoveTaskDto`, `KanbanService`, and `jari-frontend/` are currently untracked; a boot baseline that is not in version control cannot be reproduced or bisected.
- **A documented smoke procedure** that proves the stack works, so "it boots" becomes a repeatable check rather than an impression.

## Capabilities

### New Capabilities

- `local-stack-boot`: The full stack starts from a single command, every service registers with service discovery and reports healthy, and a request routed through the gateway reaches a service and returns data.

### Modified Capabilities

None. No existing spec-level behavior changes — this change fixes build and orchestration defects without altering any service's contract.

## Impact

- `Dockerfile` — restructured into a cached dependency layer plus per-service runtime stages.
- `.dockerignore` — new exclusions.
- `docker-compose.yml` — healthchecks, `depends_on` conditions, corrected volume mount.
- `postgres/init-db.sql` — made idempotent (`CREATE DATABASE` guarded), plus a documented volume-reset path.
- `README.md` — startup instructions corrected; they currently reference MySQL and `compose.yml`, neither of which matches the repo.
- Git — six untracked paths committed.
- No Java source changes. No API changes. No database schema changes.
