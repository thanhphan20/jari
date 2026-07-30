## Context

Jari is a six-service Spring Boot stack (Eureka, gateway, auth, user, project, task, notification) with Postgres and RabbitMQ, orchestrated by `docker-compose.yml` against a single multi-stage `Dockerfile`. The stated project goal is learning distributed-systems patterns, so this stack has to be startable and observable on demand, repeatedly.

It currently does not start end-to-end. Investigation found five defects, all in build and orchestration configuration rather than application code:

1. **No dependency caching, seven times over.** Each of the seven `Dockerfile` stages runs `COPY . .` then `mvn clean package -pl <module> -am -DskipTests`. Because the source copy precedes dependency resolution, every stage invalidates its cache on any file change and re-downloads plus rebuilds the full dependency graph. A cold `--build` performs seven complete Maven builds.
2. **Oversized build context.** `.dockerignore` excludes `**/target`, `.git`, `.idea`, `*.iml`, `.mvn`, and `.DS_Store` — but not `node_modules`. `jari-frontend/node_modules` is therefore streamed into the daemon for all seven stages.
3. **Wrong Postgres volume mount.** `postgres_data:/var/lib/postgresql` mounts over the *parent* of the Postgres data directory rather than `/var/lib/postgresql/data`.
4. **`depends_on` without readiness conditions.** Plain `depends_on` orders container creation only, not readiness. Services race Postgres, RabbitMQ, and Eureka and fail their first connection; `restart: always` then retries them silently.
5. **Non-repeatable database provisioning.** `postgres/init-db.sql` is mounted into `/docker-entrypoint-initdb.d/`, which the Postgres image executes *only when the data directory is empty*. Any pre-existing volume — including one left over from the MySQL-to-Postgres migration — leaves all five databases missing.

Defects 1 and 2 together mean a first build can take tens of minutes with no useful progress output, which is easily mistaken for a hang. Defects 3, 4, and 5 interact: a stale or misplaced data directory means the init script never runs, so every data service fails to connect, and `restart: always` presents that as an indefinitely "starting" stack. This combination is the most probable explanation for the reported symptom.

## Goals / Non-Goals

**Goals:**

- `docker compose up --build` produces a fully running stack with no manual steps.
- A cold rebuild is fast enough to iterate on (target: dependency resolution cached across stages and across rebuilds that do not touch `pom.xml`).
- Startup failures surface as failures, not as indefinite restarts.
- Database provisioning is repeatable regardless of volume state.
- "It boots" is a documented, repeatable check.

**Non-Goals:**

- No application code changes beyond what boot verification itself requires. Known application defects (unresolvable user identity, missing authorization, Kanban ordering collisions) are deliberately left in place; they belong to later phases and fixing them here would obscure whether the boot fix worked. Two narrow exceptions surfaced by the boot smoke check itself are in scope: the gateway's `AuthenticationFilter` status codes (see Decisions), and permitting `/actuator/health` in `auth-service`'s `SecurityConfig` — discovered during verification, since `auth-service` was the only service whose Spring Security chain blocked its own health endpoint with 403, violating the `local-stack-boot` spec's "every service reports its health" requirement.
- No production concerns: no image registry, no orchestration beyond compose, no secrets management, no TLS.
- No frontend container. The frontend runs outside compose via `bun run dev`.
- No test suite. That is the next phase.

## Decisions

### Split the Dockerfile into a cached dependency layer plus thin runtime stages

A shared builder stage copies only the parent `pom.xml` and each module's `pom.xml`, runs `mvn dependency:go-offline`, and *then* copies sources. Per-service stages build their module from that cached base, and each final stage is a JRE image (`eclipse-temurin:21-jre`) containing only the built jar.

*Why:* moves dependency resolution above the source-copy layer, so editing Java code no longer re-resolves dependencies, and one download serves all services. Runtime images drop from a full Maven toolchain to a JRE plus one jar.

*Alternatives considered:* (a) Keep one image and select the service via an env var at entrypoint — simpler Dockerfile, but every service image then carries every service's jar, and a change anywhere rebuilds everything. (b) Build jars on the host with `mvnw` and `COPY` them in — fastest builds, but requires a working local JDK 21 and makes the container build non-hermetic. (a) is a reasonable fallback if the multi-stage split proves fiddly; (b) is rejected because reproducibility is the point of this phase.

### Provision databases idempotently, and treat volume reset as a documented operation

`init-db.sql` becomes safe to run repeatedly (guarded `CREATE DATABASE` via a `DO` block or `\gexec` over a filtered list, since Postgres has no `CREATE DATABASE IF NOT EXISTS`). Separately, the README documents `docker compose down -v` as the way to force re-provisioning.

*Why:* the failure mode being fixed is silent and confusing — services fail to connect for a reason that has nothing to do with the services. Idempotency plus a documented reset removes a whole class of "it worked yesterday."

*Alternative considered:* let each service create its own database on connect (the MySQL-era `createDatabaseIfNotExist=true` behavior). Rejected: Postgres has no equivalent, and it would give five services write authority over database creation.

### Gate startup on readiness, not creation order

Add `healthcheck` to Postgres (`pg_isready`), RabbitMQ (`rabbitmq-diagnostics -q ping`), and Eureka (actuator health), and convert every `depends_on` to `condition: service_healthy`.

*Why:* makes ordering real, and makes a genuine startup failure visible instead of hidden behind restarts.

*Trade-off accepted:* cold start becomes slower in wall-clock terms because stages now actually wait. That is the correct trade: the current version is not faster, only less honest.

### Keep `restart: always` but rely on healthchecks for correctness

Restart policies stay for convenience, but no service depends on restart-retry to reach a working state.

*Why:* restarts should be a safety net, not the mechanism that makes startup work. If a service only starts on its third attempt, that is a defect to fix, not a behavior to depend on.

### Do not fix application defects in this change

The gateway's `AuthenticationFilter` throws raw `RuntimeException` (surfacing as HTTP 500 where 401 is correct), and the spec above requires 401 for unauthenticated requests. That is the single application-code exception in scope, because the smoke check asserts it. Everything else — identity resolution, authorization, ordering — stays untouched.

*Why:* one change, one purpose. A boot fix mixed with behavior fixes cannot be verified as either.

## Risks / Trade-offs

- **Multi-stage refactor breaks a build that "almost" worked** → Change one stage first, confirm it builds and runs, then apply the pattern to the rest. Keep the original `Dockerfile` in git history for comparison.
- **Cold build is still slow on first run** → Expected and acceptable; dependency resolution has to happen once. Document the expected duration in the README so a slow first build is not mistaken for a hang. This is the specific misreading that may have produced the original symptom.
- **Healthchecks turn a slow start into a hard failure** → That is the intent, but tune `start_period` and `retries` so a merely slow Postgres on a cold machine does not fail the stack.
- **`.dockerignore` excludes `.mvn`, so `./mvnw` is unavailable inside the image** → Harmless today because stages invoke the image's own `mvn`. Noted so a future stage does not switch to the wrapper and break mysteriously.
- **Corrected volume mount invalidates existing local data** → Acceptable; there is no data worth keeping. Call out `down -v` explicitly so it is a decision rather than a surprise.
- **Verification is manual** → Accepted for this phase only. The next phase introduces Testcontainers, at which point the smoke procedure should be superseded by an automated test.

## Migration Plan

1. Fix `.dockerignore` and the compose volume mount and healthchecks first — cheap, independently verifiable.
2. Make `init-db.sql` idempotent; verify with both an empty and a pre-existing volume.
3. Restructure the `Dockerfile`, one stage at a time, starting with `jari-discovery` (fewest dependencies).
4. Bring the stack up, work outward from infrastructure: Postgres and RabbitMQ healthy → Eureka healthy → gateway registered → services registered.
5. Run the smoke procedure; record actual cold-build duration in the README.
6. Commit the previously untracked work as a separate commit from the configuration fixes, so a bisect can distinguish them.

**Rollback:** revert the commits. No data migration, no schema change, no external state.

## Open Questions

- **Cold-build duration target.** Is a ~5 minute cold build with a warm dependency cache acceptable, or is host-side `mvnw` building (rejected above for hermeticity) worth revisiting if the multi-stage split lands slower than that?
- **Does Eureka need an actuator health endpoint added** for its healthcheck, or is a TCP check on 8761 sufficient? TCP is simpler but reports ready before the registry is actually serving.
- **Should `postgres:latest` be pinned** to an explicit major version? Unpinned means a future `docker pull` can change the server version underneath an existing volume, which fails to start. Recommendation: pin, but it is a one-line decision outside this change's core purpose.
