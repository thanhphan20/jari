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

### Frontend (not yet wired up)

`jari-frontend/` exists but is not part of the boot/smoke path yet — see the project roadmap for when it lands.

## Smoke Test

Once the stack is up, this checklist proves the core path works end-to-end. All requests go through the gateway at `http://localhost:8080`.

1. **Check every service reports healthy**:
   ```bash
   curl http://localhost:8761/actuator/health   # eureka-discovery
   curl http://localhost:8082/actuator/health   # user-service (identity)
   curl http://localhost:8083/actuator/health   # project-service
   curl http://localhost:8084/actuator/health   # task-service
   curl http://localhost:8085/actuator/health   # notification-service
   ```
   Each should return `{"status":"UP"}` with HTTP 200.

2. **Register a user**:
   ```bash
   curl -X POST http://localhost:8080/auth/register \
     -H "Content-Type: application/json" \
     -d '{"username":"smoketest","password":"Passw0rd!","email":"smoketest@example.com"}'
   ```
   Expect `"user added to the system"`.

3. **Request a token**:
   ```bash
   curl -X POST http://localhost:8080/auth/token \
     -H "Content-Type: application/json" \
     -d '{"username":"smoketest","password":"Passw0rd!"}'
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

The init script only runs when the Postgres data volume is empty. Use `docker compose down -v` to force re-provisioning.

## Known Limitations

This is a learning project and several defects are intentionally left in place until their planned phase (see `openspec/`):

- **Authorization is still absent.** Identity now flows end-to-end (a request through the gateway carries a real, verified `userId`), but no service checks *what* that user may access. Any authenticated user can still read or modify any project's or any other user's data - `GET /api/tasks` returns every task in the system regardless of who asks. Closing this is Phase 3 (`ProjectMember` + project-scoped authorization checks), not this phase.
- **The trusted-header model only holds because the gateway is the sole ingress.** After validating a token, the gateway injects `X-Jari-User-Id`/`X-Jari-Username` into the forwarded request; downstream services (`user`, `project`, `task`, `notification`) trust those headers unconditionally and reject any request that lacks them. This is only safe as long as nothing can reach a downstream service except through the gateway.
- **Every service's port is published to the host** (`8082`-`8085` in `docker-compose.yml`), which is a local-development convenience, not something to carry into a shared environment - it would let anyone on that network set `X-Jari-User-Id` directly and impersonate any user, bypassing the gateway's validation entirely.
- `jari-common`'s `GlobalExceptionHandler` uses the Servlet-based `WebRequest` type, which fails to resolve in the gateway's WebFlux context — a gateway-level exception surfaces as a generic 503 rather than the intended error body.
- Kanban task ordering can collide (`KanbanService.moveTask` doesn't shift siblings).
- `ddl-auto: update` is still in force; no migration tooling yet.

## Development Notes

- Each service runs on a different port to avoid conflicts.
- Services communicate through Eureka service discovery.
- API Gateway routes requests using service names (e.g., `lb://jari-user-service`).
- `jari-common` is shared across all services for consistency — a known coupling point, deliberately not eliminated (see `openspec/changes/collapse-identity-service/design.md`). `jari-security` is a separate, smaller module for exactly this reason: only the services that construct a `JwtUtils` (gateway, user-service) or use `IdentityHeaderFilter` (user, project, task, notification) depend on it.

## Roadmap

Planned work is tracked as OpenSpec changes under `openspec/changes/` (completed phases move to `openspec/changes/archive/` and their capabilities into `openspec/specs/`). Completed: `boot-the-stack`, `collapse-identity-service`. Next: `add-schema-migrations-and-integration-tests` (Flyway + Testcontainers), then Phase 3 (project membership + authorization).

## License

This project is for educational purposes.
