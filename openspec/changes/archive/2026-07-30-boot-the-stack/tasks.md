## 1. Baseline and version control

- [x] 1.1 Record the current failure: run `docker compose up --build`, capture the output, and note where it stalls or fails. This is the evidence the change is judged against.
  - Result: Postgres and RabbitMQ images pulled fine. The Maven build stage failed during build-context transfer with `ERROR: invalid file request jari-frontend/node_modules/.bin/acorn` after streaming 181MB of context over ~66s (`target auth-service: failed to solve`). Confirms Findings 2/20 (`.dockerignore` missing `node_modules`) as a hard failure, not just bloat. Full log: `scratchpad/boot-baseline.log`.
- [x] 1.2 Commit the untracked Kanban work in `jari-task-service` (`KanbanController`, `KanbanBoardDto`, `KanbanColumnDto`, `MoveTaskDto`, `KanbanService`) as its own commit.
- [x] 1.3 Commit `jari-frontend/` with a `.gitignore` covering `node_modules` and build output. Do not fix the frontend here — it is Phase 5.
- [x] 1.4 Verify `git status` reports no untracked Java, frontend, or configuration files.

## 2. Build context and image size

- [x] 2.1 Add `**/node_modules` to `.dockerignore`; verify the build context size drops (compare the "Sending build context" figure before and after).
  - Result: context transfer dropped from 181MB (failing) to 9.52MB per service. All 7 images built and all 9 containers started with no restructuring needed — see note on 2.2-2.5 below.
- [ ] 2.2 Restructure the `Dockerfile`: a shared builder stage that copies the parent `pom.xml` and each module `pom.xml`, runs `mvn dependency:go-offline`, then copies sources.
- [ ] 2.3 Convert `jari-discovery` to build from the shared builder stage with an `eclipse-temurin:21-jre` runtime stage; confirm it builds and the container starts.
- [ ] 2.4 Apply the same pattern to the remaining six stages (gateway, auth, user, project, task, notification).
- [ ] 2.5 Confirm dependency caching works: touch one Java file, rebuild, and verify Maven does not re-resolve dependencies.
  - **Deferred, not skipped**: with 2.1 alone the cold build completed and every service booted (slowest single stage 116.5s — tolerable, not the "tens of minutes" the design doc worried about). The caching/slim-runtime rework is real perf/hygiene debt (still full Maven images at runtime, still 7x redundant dependency resolution on every rebuild) but no longer blocks the boot goal. Revisit if iteration speed becomes painful; not required for this change's gate.

## 3. Infrastructure readiness

- [x] 3.1 ~~Correct the Postgres volume mount to `/var/lib/postgresql/data`~~ — **reverted, original was correct.** `postgres:latest` now resolves to Postgres 18, whose Docker image switched to recommending a single mount at the *parent* `/var/lib/postgresql` (it manages version-specific subdirectories internally for `pg_upgrade --link`). Changing the mount broke startup against the existing volume (confirmed via `docker logs jari-postgres`: "PostgreSQL data in: /var/lib/postgresql/data (unused mount/volume)"). Finding 21 in the plan was based on the pre-18 convention and is stale; left as-is. See 6.5 — this incident is why the image gets pinned below.
- [x] 3.2 Make `postgres/init-db.sql` idempotent so it is safe to run against an existing cluster. Used `SELECT ... WHERE NOT EXISTS (...) \gexec` per database (Postgres has no `CREATE DATABASE IF NOT EXISTS`).
  - **Amended after CodeRabbit review**: idempotency alone wasn't enough - Postgres's own `docker-entrypoint-initdb.d` hook only *runs* the script on a genuinely empty data directory, so a database added to the script later would never get created against a volume that already exists. Added a `db-init` compose service that runs the script via `psql` on every `up`, with the five data services gated on `db-init: condition: service_completed_successfully`. Confirmed by dropping `jari_notification` on a live volume and re-running `docker compose up db-init` without `down -v` - it came back.
- [x] 3.3 Add a `healthcheck` to the Postgres service using `pg_isready`.
- [x] 3.4 Add a `healthcheck` to the RabbitMQ service using `rabbitmq-diagnostics -q ping`.
- [x] 3.5 Add a healthcheck to `eureka-discovery`; decide TCP-on-8761 versus actuator health and record the choice (see design open questions).
  - Decision: actuator health (`spring-boot-starter-actuator` already a dependency; `curl` confirmed present in the running image). More faithful than a TCP check, which would report ready before the registry is actually serving.
- [x] 3.6 Convert every `depends_on` in `docker-compose.yml` to `condition: service_healthy`, with `start_period` tuned so a cold-start Postgres does not fail the stack.
- [x] 3.7 Verify the restart-with-existing-volume scenario: `docker compose down` then `up`, and confirm all five databases exist and every data service connects on its first attempt.
  - First attempt (with the since-reverted `/var/lib/postgresql/data` mount) broke startup against the volume from the prior run — see 3.1 note. Retested clean after reverting: `\l` in `jari-postgres` shows all five databases (`jari_auth`, `jari_user`, `jari_project`, `jari_task`, `jari_notification`) present after a fresh `up`.

## 4. Bring the stack up

- [x] 4.1 Start infrastructure only (`postgres`, `rabbitmq`) and confirm both report healthy.
- [x] 4.2 Start `eureka-discovery`; confirm the dashboard is reachable and reports healthy.
- [x] 4.3 Start `api-gateway`; confirm it registers with Eureka.
  - 4.1-4.3 verified via the dependency graph rather than manual staging: `docker compose up -d` output shows Postgres/RabbitMQ reaching `Healthy` before Eureka starts, and Eureka reaching `Healthy` before any app service starts — the healthcheck-gated ordering from section 3 enforces this automatically.
- [x] 4.4 Start the five application services; confirm each registers with Eureka and reports `UP` at `/actuator/health`.
  - All 5 confirmed at their ports (8081-8085). `auth-service` initially returned 403 (Spring Security blocking its own health endpoint) — fixed by permitting `/actuator/health` in its `SecurityConfig`; see design.md's amended Non-Goals note.
- [x] 4.5 Confirm no service is in a restart loop (`docker compose ps` shows no repeated restart counts).
  - Confirmed: all 9 containers show `Up <duration>` with no `Restarting` status across two separate `up` runs.

## 5. Prove it works

- [x] 5.1 Register a user through the gateway; confirm the row is persisted in the identity database.
  - `POST /auth/register` via the gateway → `SELECT` in `jari_auth.users` confirms the row. First attempt returned a 503 caused by Eureka client-side registry-cache lag right after `auth-service` was rebuilt (resolved on retry ~15s later, not a real defect — see note below).
- [x] 5.2 Request a token with those credentials; confirm a signed JWT is returned.
  - Decoded JWT returned from `POST /auth/token`.
- [x] 5.3 Call a secured route through the gateway with that token; confirm HTTP 200 and a real response body.
  - `GET /api/projects` with `Authorization: Bearer <token>` → HTTP 200, real `ResponseDto` body.
- [x] 5.4 Call the same route with no `Authorization` header; confirm HTTP 401 and no stack trace in the response. This requires replacing the raw `RuntimeException` in the gateway's `AuthenticationFilter` — the one application-code change in scope.
  - Confirmed 500 pre-fix, 401 with empty body post-fix. Also surfaced (not fixed, out of scope): `jari-common`'s `GlobalExceptionHandler` uses the Servlet-based `WebRequest` type, which fails to resolve in the gateway's WebFlux context — a routing-level exception there gets masked behind a generic 503 instead of the intended `ErrorResponseDto` body. Worth a follow-up change.
- [x] 5.5 Stop Postgres while a data service runs; confirm that service's health endpoint stops reporting `UP`. Restart Postgres and confirm recovery.
  - `docker stop jari-postgres` → `user-service` `/actuator/health` → `{"status":"DOWN"}` / 503. `docker start jari-postgres` → healthy within ~15s → `user-service` health returns `{"status":"UP"}` / 200.

**Operational note surfaced during 5.1**: the gateway's local Eureka registry cache can lag up to ~30s behind a service (re)registering (default Eureka client fetch interval). A request through the gateway immediately after restarting a downstream service can get a transient 503 with no useful error body — not a defect, but worth documenting in the README so it isn't mistaken for one during the smoke procedure (feeds into task 6.2).

## 6. Document and close

- [x] 6.1 Rewrite the `README.md` startup section: it currently references MySQL and `compose.yml`, neither of which matches the repository.
- [x] 6.2 Document the smoke procedure from section 5 as a numbered checklist a newcomer can follow.
- [x] 6.3 Record the measured cold-build duration in the README so a slow first build is not mistaken for a hang.
- [x] 6.4 Document `docker compose down -v` as the way to force database re-provisioning.
- [x] 6.5 Decide whether to pin the `postgres` image to an explicit major version (design open question) and apply or record the decision.
  - Decided and applied earlier than planned: `postgres:latest` resolved to Postgres 18 mid-session and changed its recommended volume layout, breaking the stack when the mount path was (incorrectly) "fixed" per the stale Finding 21 — see 3.1's note. Pinned to `postgres:18` to prevent a future silent `docker pull` from doing this again.
- [x] 6.6 Run the full cold-start scenario one final time from removed volumes, following only the README, and confirm every step passes.
  - First attempt (`docker compose down -v && docker compose up -d --build`) failed with `project-service` Maven build exit code 1 under bake's parallel build load. Rebuilding that stage in isolation (`docker build --target project-service`) succeeded cleanly — confirmed transient (likely Maven Central contention across 7 concurrent builds), not a real defect. Retry succeeded: all 9 containers up, no restarts.
  - Ran the full README smoke checklist end to end on the fresh boot: all 6 health endpoints `UP` (after the documented Eureka-registry-cache warm-up — hit the transient 503 on the first `/auth/register` exactly as documented, retried per the README's own note), register → token → secured 200 → unauthenticated 401 → Postgres stop/start → `user-service` DOWN then UP. Every step passed.
