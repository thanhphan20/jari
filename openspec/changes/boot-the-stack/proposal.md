## Why

The Jari stack does not currently start end-to-end, which blocks every other phase of work: a distributed system cannot teach anything about eventual consistency, event ordering, or failure handling while it fails to boot. Four concrete defects in the container build and compose configuration explain the failure, and none of them are in application code — so no amount of feature work will fix them.

This change makes `docker compose up` produce a running, verifiable stack. It has no feature value and is purely a prerequisite.

## What Changes

- **`.dockerignore` extended to exclude `node_modules`** (and other build output). It is currently absent, so `jari-frontend/node_modules` is copied into the build context of all seven stages.
- **Startup ordering made deterministic** via `healthcheck` plus `depends_on: condition: service_healthy` for Postgres, RabbitMQ, and Eureka. Today `depends_on` only orders container *creation*, and `restart: always` converts the resulting connection-refused crash loop into what looks like slow startup.
- **Postgres pinned to `postgres:18`** rather than `latest`, since an unpinned tag silently changed major version mid-development and altered the image's recommended volume-mount convention. The compose file's existing `postgres_data:/var/lib/postgresql` mount is correct for Postgres 18 (see design.md) and is not changed by this proposal.
- **Database provisioning made repeatable.** `postgres/init-db.sql` only executes when the volume is empty, so any pre-existing volume leaves all five databases uncreated and every data service unable to connect.
- **Untracked work committed.** `KanbanController`, `KanbanBoardDto`, `KanbanColumnDto`, `MoveTaskDto`, `KanbanService`, and `jari-frontend/` are currently untracked; a boot baseline that is not in version control cannot be reproduced or bisected.
- **A documented smoke procedure** that proves the stack works, so "it boots" becomes a repeatable check rather than an impression.
- **BREAKING (deferred, not delivered here)** — a Dockerfile restructure for dependency caching and slim runtime images was originally scoped into this change but is not part of it. `.dockerignore` alone was enough to get every service booting with no restart loops, so the caching rework is tracked as unchecked, deliberately deferred work in `tasks.md` (2.2-2.5) rather than claimed as done.

## Capabilities

### New Capabilities

- `local-stack-boot`: The full stack starts from a single command, every service registers with service discovery and reports healthy, and a request routed through the gateway reaches a service and returns data.

### Modified Capabilities

None new, but this change does alter observable application behavior, not just build/orchestration config: `/actuator/health` on `auth-service` is now publicly reachable (was 403), and the gateway now returns 401 instead of 500 for unauthenticated requests to a secured route. Both are narrow, intentional exceptions the boot smoke check itself required — see design.md's Non-Goals — not evidence of a broader contract change.

## Impact

- `.dockerignore` — new exclusions.
- `docker-compose.yml` — healthchecks, `depends_on` conditions, `postgres:18` pin. The existing volume mount is unchanged (see design.md).
- `postgres/init-db.sql` — made idempotent (`CREATE DATABASE` guarded), plus a documented volume-reset path.
- `README.md` — startup instructions corrected; they currently reference MySQL and `compose.yml`, neither of which matches the repo.
- Git — six untracked paths committed.
- Two narrow application-code changes (see Modified Capabilities above). No database schema changes.
