# Jari - Jira Clone Microservices Architecture

A microservices-based project management system inspired by Jira, built with Spring Boot and Spring Cloud. Built as a learning project for distributed-systems patterns — see [`openspec/`](openspec/) for the active plan and rationale.

## Architecture Overview

### Infrastructure

- **Postgres** (Port: 5432) — one database per service (`jari_user`, `jari_project`, `jari_task`, `jari_notification`)
- **RabbitMQ** (Ports: 5672 AMQP, 15672 management UI) — not currently used by any service; kept running for Phase 6's planned `TaskAssigned` event

### Services

1. **Eureka Discovery Service** (Port: 8761) — service registry; dashboard at `http://localhost:8761`
2. **API Gateway** (Port: 8080) — single entry point, routes to services by name via `lb://`, validates JWTs on secured routes and injects trusted identity headers downstream
3. **User Service** (Port: 8082) — the identity service: registration, login, JWT issuance, user profile CRUD. Database: `jari_user`
4. **Project Service** (Port: 8083) — project CRUD. Database: `jari_project`
5. **Task Service** (Port: 8084) — task/issue CRUD and Kanban board. Database: `jari_task`
6. **Notification Service** (Port: 8085) — notification CRUD. Database: `jari_notification`

There used to be a separate Auth Service; it was merged into User Service (`openspec/changes/collapse-identity-service`) because the two-table split was an accident, not a real boundary, and it made a user's own id unresolvable from a token.

### Common Module

- `jari-common`: shared DTOs (`BaseDto`, `ResponseDto`, `ErrorResponseDto`), common exceptions and exception handlers
- `jari-security`: JWT handling (`JwtUtils`), the gateway-injected identity header names (`IdentityHeaders`), and the shared filter downstream services use to require them (`IdentityHeaderFilter`) — only the services that actually need one of these depend on this module

## Technology Stack

- **Java**: 21
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.1
- **Spring Cloud Gateway**: API Gateway
- **Netflix Eureka**: Service Discovery
- **Spring Data JPA**: Data persistence
- **PostgreSQL**: Database (one per service)
- **RabbitMQ**: Async messaging
- **Maven**: Build tool
- **Docker & Docker Compose**: Containerization

## Project Structure

```
jari/
├── pom.xml                          # Parent POM
├── docker-compose.yml               # Docker Compose configuration
├── Dockerfile                       # Multi-stage Dockerfile
├── postgres/init-db.sql             # Creates the four per-service databases
├── openspec/                        # Active change proposals and specs
├── jari-common/                     # Common module (DTOs, exceptions)
├── jari-security/                   # Shared JWT + identity-header module
├── jari-discovery/                  # Eureka Discovery Service
├── jari-gateway/                    # API Gateway
├── jari-user-service/               # Identity + User Service
├── jari-project-service/            # Project Service
├── jari-task-service/               # Task Service
├── jari-notification-service/       # Notification Service
└── jari-frontend/                   # React frontend (in progress, not yet wired up)
```

## Prerequisites

- Java 21
- Maven 3.6+
- Docker and Docker Compose

## Getting Started

### Docker Deployment (recommended)

```bash
docker compose up --build
```

This builds all six service images and starts Postgres, RabbitMQ, and every service in dependency order (Postgres/RabbitMQ healthy → Eureka healthy → gateway and app services). Add `-d` to run detached.

**Expect the first build to take several minutes.** The Dockerfile does not yet cache Maven dependencies across stages (see `openspec/changes/boot-the-stack/design.md`), so each service's build resolves its own dependencies from scratch. On a machine that has already pulled `maven:3.9-eclipse-temurin-21`, a cold `--build` takes roughly 3-6 minutes; add a few more minutes on the very first run for that base image pull (~700MB). A build that appears to sit at "load build context" or "RUN mvn clean package" for a few minutes is working, not hung.

**If a container fails to connect to Postgres or RabbitMQ on first boot**, re-run `docker compose up --build` — dependencies now wait for `condition: service_healthy` rather than just container creation, so this should not happen. If it does, check `docker compose ps` for a service stuck in a restart loop before assuming it's a hang.

**If the build itself fails** (a Maven module reports a non-zero exit under `docker compose --build`), retry once before investigating further — BuildKit's `bake` mode builds all seven service images concurrently, and Maven Central occasionally rejects one of the simultaneous dependency-resolution requests under that load. This has been observed to be transient: rebuilding the same module in isolation (`docker build --target <service>`) succeeded immediately after a bake-mode failure.

**Reset the databases** with:

```bash
docker compose down -v
```

This removes the Postgres volume, so the next `up` re-runs `postgres/init-db.sql` and recreates all four databases from scratch. Necessary after a schema change made outside of the (not-yet-added) migration tooling, or if you just want a clean slate.

**After restarting a single service**, requests routed to it through the gateway can return a transient `503` for up to ~30 seconds. This is the gateway's local Eureka registry cache catching up with the new registration (Eureka's default client fetch interval) — not a defect. Retry after a short wait rather than assuming something is broken.

### Local Development (without Docker)

Requires a local Postgres and RabbitMQ (or point `application.yml` at remote ones), and Maven.

```bash
mvn clean install
```

Start services in dependency order: `jari-discovery` → `jari-gateway` → `jari-user-service` → the remaining services, each via `mvn spring-boot:run` in its module directory. `jari-gateway` and `jari-user-service` also need `JARI_SECURITY_JWT_SECRET` set in the environment (see Known Limitations) - both fail to start without it, by design.

### Frontend

`jari-frontend/` is a React + Vite client: log in against the identity service, then manage a project's Kanban board against live backend data. Bring the stack up first — the frontend has nothing to show without it.

```bash
cd jari-frontend
bun install
bun run dev
```

Open http://localhost:5173 and sign in with the seeded `admin` / `admin123` (or `user` / `user123`).

**On a fresh database** there are zero projects, so you land on a "Create your first project" screen instead of a board — `POST /projects` creates one and you're straight into it. From there the board is a working issue tracker, not a read-only view:

- **Create an issue** with the "Create issue" button — summary, description, type, priority, assignee.
- **Open a card** to edit it in a side panel: summary and description save on blur (or immediately if you close right after editing), status/type/priority/assignee are selects, and delete has a confirmation step.
- **Drag a card** between or within columns to change its status or reorder it — this updates immediately and reconciles with the server in the background; if the move fails it reverts and says so.
- **Move a card without dragging**: open it and change Status in the select. Every operation the board supports has a non-drag path, so nothing requires a pointer.
- **Filter the board** by text, by clicking an assignee's avatar, by issue type, or "only my issues" — all client-side over the board already fetched, so it's instant and touches no data.

**How browser requests reach the gateway.** The Vite dev server proxies `/api` and `/auth` to `http://localhost:8080`, so the browser makes same-origin requests. This is load-bearing, not a convenience: a cross-origin call would be preflighted, and a CORS preflight carries no `Authorization` header, so the gateway's `AuthenticationFilter` rejects it with 401 before the real request is ever sent. Same-origin requests are not preflighted, so the problem does not arise.

The consequence is that **this works for the dev server only**. Serving the frontend from any other origin needs real CORS on the gateway — including short-circuiting `OPTIONS` ahead of authentication — or serving the built assets through the gateway itself. Neither exists yet. `jari-frontend/.env.example` documents the override variables and repeats this warning.

**Constraints that will otherwise look like bugs:**

- **Only three columns** (`Todo`, `In Progress`, `Done`). `KanbanService.STANDARD_COLUMNS` is exactly these three, and `moveTask` rejects anything else — a fourth column is a backend change.
- **One assignee per issue, and no confirm-you-meant-it on reassigning.** `Task.assigneeId` is a single field; the reference app this was modelled on supports multiple. Changing that is a schema change.
- **The assignee list is every user in the system**, not project members — project membership doesn't exist yet (Phase 3).
- **Issue keys can collide.** The create dialog derives a key client-side from the highest existing numeric suffix (`JARI-1..5` existing → `JARI-6`), because `TaskService.createTask` never generates one itself. Two clients creating at the same moment can produce the same key, since `tasks.key` has no unique constraint — a known defect the schema baseline records on purpose. The real fix is a server-side per-project counter.
- **No shareable link to an issue.** Detail, create, search, and settings are all overlays on one route — there is no router yet, so there's nothing to put a URL on. This is the strongest candidate for the next frontend change.

## Smoke Test

Once the stack is up, this checklist proves the core path works end-to-end. All requests go through the gateway at `http://localhost:8080`.

1. **Check every service reports healthy**. Use `--fail` so the command itself fails on a non-2xx response instead of printing a 503 body and exiting 0:
   ```bash
   curl --fail http://localhost:8761/actuator/health   # eureka-discovery
   curl --fail http://localhost:8082/actuator/health   # user-service (identity)
   curl --fail http://localhost:8083/actuator/health   # project-service
   curl --fail http://localhost:8084/actuator/health   # task-service
   curl --fail http://localhost:8085/actuator/health   # notification-service
   ```
   Each should return `{"status":"UP"}` with HTTP 200.

2. **Register a user**. Use a fresh username each run — the smoketest user persists in `jari_user`, and registering the same username twice fails before you get to test token issuance and routing:
   ```bash
   RUN_ID=$(date +%s)
   curl -X POST http://localhost:8080/auth/register \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"smoketest-$RUN_ID\",\"password\":\"Passw0rd!\",\"email\":\"smoketest-$RUN_ID@example.com\"}"
   ```
   Expect `"user added to the system"`.

3. **Request a token** (reuse the same `$RUN_ID`):
   ```bash
   curl -X POST http://localhost:8080/auth/token \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"smoketest-$RUN_ID\",\"password\":\"Passw0rd!\"}"
   ```
   Expect a JWT string back. Decode its payload (`echo "<payload-segment>" | base64 -d`) and confirm it contains a `userId` claim matching the row's primary key in `jari_user.users`, and a `roles` claim.

4. **Call a secured route with the token**:
   ```bash
   curl http://localhost:8080/api/projects -H "Authorization: Bearer <token>"
   ```
   Expect HTTP 200 with a `ResponseDto` body.

5. **Call the same route with no token**:
   ```bash
   curl -i http://localhost:8080/api/projects
   ```
   Expect HTTP 401, no stack trace.

6. **Call the identity self-lookup endpoint**:
   ```bash
   curl http://localhost:8080/api/users/me -H "Authorization: Bearer <token>"
   ```
   Expect HTTP 200 describing the registered user, with no `password` field. Without the `Authorization` header, expect HTTP 401.

7. **Confirm a direct call bypassing the gateway is rejected**:
   ```bash
   curl -i http://localhost:8083/projects
   ```
   Expect HTTP 401 - `project-service`'s own `IdentityHeaderFilter` rejects it, since no gateway-injected identity header is present.

8. **Verify Postgres-down resilience**: `docker stop jari-postgres`, then re-check step 1 for a data service — its health should flip to `{"status":"DOWN"}` / 503 (this can take longer than you'd expect - HikariCP's failure detection isn't instant). `docker start jari-postgres` and confirm it recovers to `UP` / 200.

## API Endpoints

All requests should go through the API Gateway at `http://localhost:8080`.

### User Service (identity + profile)

Auth routes are open (no token required) - everything else needs `Authorization: Bearer <token>`.

- `POST /auth/register` - Register a new user
- `POST /auth/token` - Log in, returns a JWT (includes `userId` and `roles` claims)
- `GET /auth/validate` - Validate a token
- `GET /api/users/me` - Get the authenticated caller's own user record, resolved from the gateway-injected identity (ignores any client-supplied identifier)
- `POST /api/users` - Create user
- `GET /api/users/{id}` - Get user by ID
- `GET /api/users` - Get all users
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user

### Project Service

- `POST /api/projects` - Create project
- `GET /api/projects/{id}` - Get project by ID
- `GET /api/projects/key/{key}` - Get project by key
- `GET /api/projects` - Get all projects
- `PUT /api/projects/{id}` - Update project
- `DELETE /api/projects/{id}` - Delete project

### Task Service

- `POST /api/tasks` - Create task
- `GET /api/tasks/{id}` - Get task by ID
- `GET /api/tasks/key/{key}` - Get task by key
- `GET /api/tasks` - Get all tasks
- `GET /api/tasks/project/{projectId}` - Get tasks by project
- `GET /api/tasks/assignee/{assigneeId}` - Get tasks by assignee
- `PUT /api/tasks/{id}` - Update task
- `DELETE /api/tasks/{id}` - Delete task
- `GET /api/tasks/kanban/{projectId}` - Get the Kanban board for a project
- `POST /api/tasks/kanban/move` - Move a task between columns

### Notification Service

- `POST /api/notifications` - Create notification
- `GET /api/notifications/{id}` - Get notification by ID
- `GET /api/notifications/user/{userId}` - Get notifications by user
- `GET /api/notifications/user/{userId}/unread` - Get unread notifications
- `GET /api/notifications/user/{userId}/unread/count` - Get unread count
- `PUT /api/notifications/{id}/read` - Mark as read
- `PUT /api/notifications/user/{userId}/read-all` - Mark all as read
- `DELETE /api/notifications/{id}` - Delete notification

## Service Discovery

- Eureka Dashboard: `http://localhost:8761`
- View all registered services and their status

## Database Configuration

Each microservice uses its own Postgres database, created by `postgres/init-db.sql` on first boot:

- `jari_user` - User Service (identity + profile)
- `jari_project` - Project Service
- `jari_task` - Task Service
- `jari_notification` - Notification Service

`postgres/init-db.sql` is mounted into the Postgres image's `docker-entrypoint-initdb.d`, which runs it **only when the data volume is first initialised**. The consequence to know about: adding a database to that script later will *not* create it against a volume that already exists. Use `docker compose down -v` to wipe and re-run the script from scratch.

### Connecting with a database client

The container publishes Postgres on host port **15432**, not 5432:

| Setting | Value |
|---|---|
| Host | `localhost` |
| Port | `15432` |
| Database | `jari_user`, `jari_project`, `jari_task`, or `jari_notification` |
| User / password | `postgres` / `postgres` |

The non-standard port is deliberate. If something else on the machine already holds 5432 — a native Postgres install, most commonly — Docker **does not fail loudly**: the container starts with the port simply unpublished, and a client pointed at `localhost:5432` silently reaches the *other* server instead. That presents as an authentication failure or as a server with no `jari_*` databases, neither of which points at the real cause. Publishing on 15432 sidesteps the collision entirely.

To check whether a mapping is actually live rather than merely requested, compare what was asked for against what got bound:

```bash
docker inspect jari-postgres --format '{{json .NetworkSettings.Ports}}'
```

An empty array for `5432/tcp` means the bind failed. The services themselves are unaffected either way — they reach the database as `postgres:5432` over the Docker network, which is independent of any host mapping.

## Known Limitations

This is a learning project and several defects are intentionally left in place until their planned phase (see `openspec/`):

- **Authorization is still absent.** Identity now flows end-to-end (a request through the gateway carries a real, verified `userId`), but no service checks *what* that user may access. Any authenticated user can still read or modify any project's or any other user's data - `GET /api/tasks` returns every task in the system regardless of who asks. Closing this is Phase 3 (`ProjectMember` + project-scoped authorization checks), not this phase.
- **The trusted-header model only holds because the gateway is the sole ingress.** After validating a token, the gateway injects `X-Jari-User-Id`/`X-Jari-Username` into the forwarded request; downstream services (`user`, `project`, `task`, `notification`) trust those headers unconditionally - `IdentityHeaderFilter` checks only that the headers are *present*, not that they came from the gateway. This is not a theoretical gap: a direct call to a downstream port with a forged `X-Jari-User-Id` header succeeds and impersonates that user (verified - see `collapse-identity-service` tasks.md 8.2). It is only safe as long as nothing can reach a downstream service except through the gateway.
- **Every service's port is published to the host** (`8082`-`8085` in `docker-compose.yml`), which is a local-development convenience. Outside a single developer's own machine (staging, shared, multi-tenant), downstream service ports **MUST NOT** be published and those services **MUST** sit on a network unreachable from outside the gateway - this is a hard requirement, not a suggestion, precisely because the impersonation above is a demonstrated, working bypass, not a hypothetical.
- **The frontend keeps its token in `localStorage`**, which is readable by any script on the origin and therefore XSS-exposed in a way an `HttpOnly` cookie is not. It is stored there so a page reload does not log you out, which during a demo reads as a bug rather than a design choice. Same shape of admission as the trusted-header entry above: acceptable on a single developer's machine, must not survive contact with a shared deployment. The honest fix is a cookie set by the gateway, which makes the gateway a session participant rather than a stateless token validator — an architectural change, not a tweak.
- **Bad credentials return HTTP 500, not 401.** `POST /auth/token` with a wrong password or an unknown username surfaces Spring Security's `BadCredentialsException` unmapped, producing `{"message":"Bad credentials","status":500}`. This violates the accepted `identity` capability spec, which requires 401 for both cases. The body is at least uniform across the two, so it does not leak whether the username exists. Found by the browser demo; being fixed in its own change rather than folded into that one.
- `jari-common`'s `GlobalExceptionHandler` uses the Servlet-based `WebRequest` type, which fails to resolve in the gateway's WebFlux context — a gateway-level exception surfaces as a generic 503 rather than the intended error body.
- **`RouterValidator` decides the auth boundary by substring match.** `path.contains(uri)` over its open-endpoints list means a path like `/api/tasks/1/swagger-ui` skips `AuthenticationFilter` entirely, and task-service's `IdentityHeaderFilter` exempts `/swagger-ui` too, so such a request clears both gates. It returns 404 today because no handler matches, so nothing leaks — but the boundary holds by accident rather than by design. Fix is prefix matching, in its own change.

## Development Notes

- Each service runs on a different port to avoid conflicts.
- Services communicate through Eureka service discovery.
- API Gateway routes requests using service names (e.g., `lb://jari-user-service`).
- `jari-common` is shared across all services for consistency — a known coupling point, deliberately not eliminated (see `openspec/changes/collapse-identity-service/design.md`). `jari-security` is a separate, smaller module for exactly this reason: only the services that construct a `JwtUtils` (gateway, user-service) or use `IdentityHeaderFilter` (user, project, task, notification) depend on it.

## Roadmap

Planned work is tracked as OpenSpec changes under `openspec/changes/` (completed phases move to `openspec/changes/archive/` and their capabilities into `openspec/specs/`).

Completed: `boot-the-stack`, `collapse-identity-service`, `add-kanban-browser-demo`, `add-board-issue-management`.

In progress:

- `add-schema-migrations` — Flyway owns each service's schema, with `ddl-auto: validate` so entity drift fails startup. Migrations apply and all services boot against them; the remaining runtime checks (restart idempotency, deliberate drift detection) are still open.
- `add-integration-test-harness` — Testcontainers against real Postgres and RabbitMQ, plus the first automated identity-flow test. Deferred; until it lands, migrations are verified by booting the stack rather than by `mvn verify`, and drag-and-drop's rollback-on-failure behavior is verified only for a whole-service outage, not the narrower single-request-failure case (see that change's tasks.md 5.7) — a proper isolated-failure test needs exactly the harness this phase adds.

Next: a router, so an issue can have a shareable URL (currently everything is an overlay on one route, by choice — see the Frontend section); then Phase 3 (project membership + authorization), which is also what narrows the assignee list to actual project members instead of every user in the system.

## License

This project is for educational purposes.
