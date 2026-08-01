# Jari Specification

Behavioural contract for Jari, plus the defects deliberately left in place. This is the summary; per-capability scenarios are exercised by the automated test suite (see readme.md's "Running the tests") rather than tracked in separate spec files.

## Accepted Capabilities

Four capabilities are accepted and enforced by the test suite:

| Capability | Owns |
|---|---|
| `identity` | User records, password hashing, JWT issuance, self-lookup |
| `gateway-identity-propagation` | Token validation at the edge, identity-header injection, downstream trust boundary |
| `board-issue-management` | Kanban drag/drop, issue CRUD, filtering, card legibility |
| `local-stack-boot` | Single-command startup, health reporting, discovery, reproducibility |

### identity

- A single service owns user identity — registration creates exactly one user record, and no credential material exists outside it.
- Passwords are hashed at rest and absent from every response.
- Issued tokens carry a stable `userId` claim and a `roles` claim.
- Authentication rejects invalid credentials without revealing whether the username exists.
- A caller can resolve their own identity; self-lookup ignores client-supplied identifiers.
- The token signing secret comes from configuration only — no secret in source, and startup fails without one.

### gateway-identity-propagation

- The gateway is the single point of token validation. Missing, tampered, and expired tokens are all rejected there, without leaking internals.
- The gateway injects authenticated identity downstream and **overwrites** any client-supplied identity headers.
- Downstream services derive identity only from those propagated headers, and reject requests without them.
- Trusted headers are trustworthy *only* because the gateway is the sole entry point — see Deployment Constraints below.

### board-issue-management

- Issues move between and within columns by dragging; the board updates before the server confirms and reverts on failure.
- Every drag operation has a non-drag equivalent, so nothing requires a pointer.
- Issues can be opened and edited, created, and deleted (with confirmation).
- Type, priority, and assignee are legible on the card, including when a person has no avatar image and when an issue is unassigned.
- The board can be filtered.

### local-stack-boot

- The full stack starts from a single command, cold or with an existing volume, with dependencies ready before dependants.
- Every service reports health, and reports unhealthy when its database is unreachable.
- All services register with Eureka; a request through the gateway reaches a service and returns data.
- The boot baseline is reproducible: documented instructions match the repository, and the documented smoke check passes.

## Deployment Constraints

**These are hard requirements, not suggestions.** The impersonation bypass below is demonstrated and working, not hypothetical.

- **Downstream service ports MUST NOT be published** outside a single developer's machine. `docker-compose.yml` publishes `8082`-`8085` as a local-development convenience. In staging, shared, or multi-tenant environments those services **MUST** sit on a network unreachable from anywhere but the gateway.
- **Nothing may reach a downstream service except through the gateway.** `IdentityHeaderFilter` checks only that identity headers are *present*, not that they came from the gateway. A direct call to a downstream port with a forged `X-Jari-User-Id` header succeeds and impersonates that user (verified directly against a running service).
- **`JARI_SECURITY_JWT_SECRET` must be supplied by configuration.** The gateway and user-service both fail to start without it, by design.

## API Surface

All requests go through the gateway at `http://localhost:8080`. Auth routes are open; everything under `/api` needs `Authorization: Bearer <token>`.

### Identity (User Service)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/register` | Register a new user |
| `POST` | `/auth/token` | Log in; returns a JWT with `userId` and `roles` claims |
| `GET` | `/auth/validate` | Validate a token |
| `GET` | `/api/users/me` | The caller's own record, resolved from the gateway-injected identity (ignores client-supplied identifiers) |
| `POST` `GET` `PUT` `DELETE` | `/api/users`, `/api/users/{id}` | User CRUD |

### Projects

| Method | Path | Purpose |
|---|---|---|
| `POST` `GET` `PUT` `DELETE` | `/api/projects`, `/api/projects/{id}` | Project CRUD |
| `GET` | `/api/projects/key/{key}` | Look up by project key |

### Tasks

| Method | Path | Purpose |
|---|---|---|
| `POST` `GET` `PUT` `DELETE` | `/api/tasks`, `/api/tasks/{id}` | Task CRUD |
| `GET` | `/api/tasks/key/{key}` | Look up by issue key |
| `GET` | `/api/tasks/project/{projectId}` | Tasks in a project |
| `GET` | `/api/tasks/assignee/{assigneeId}` | Tasks for an assignee |
| `GET` | `/api/tasks/kanban/{projectId}` | The Kanban board for a project |
| `POST` | `/api/tasks/kanban/move` | Move a task between columns |

### Notifications

| Method | Path | Purpose |
|---|---|---|
| `POST` `GET` `DELETE` | `/api/notifications`, `/api/notifications/{id}` | Notification create / read / delete |
| `GET` | `/api/notifications/user/{userId}` | A user's notifications |
| `GET` | `/api/notifications/user/{userId}/unread` | Unread only |
| `GET` | `/api/notifications/user/{userId}/unread/count` | Unread count |
| `PUT` | `/api/notifications/{id}/read` | Mark one read |
| `PUT` | `/api/notifications/user/{userId}/read-all` | Mark all read |

`PUT` on tasks, projects, and users **overwrites the full record** rather than merging — an omitted field is written as null or false. Callers must send the complete object.

## Local Verification

`mvn verify` (see readme.md's "Running the tests") covers the identity flow and per-service migrations, but stubs siblings rather than exercising the whole stack together. This checklist remains the real cross-service end-to-end gate — it exercises the `local-stack-boot` and `gateway-identity-propagation` requirements above.

1. **Every service reports healthy.** `--fail` makes the command itself fail on a non-2xx instead of printing a 503 body and exiting 0:
   ```bash
   curl --fail http://localhost:8761/actuator/health   # eureka-discovery
   curl --fail http://localhost:8082/actuator/health   # user-service (identity)
   curl --fail http://localhost:8083/actuator/health   # project-service
   curl --fail http://localhost:8084/actuator/health   # task-service
   curl --fail http://localhost:8085/actuator/health   # notification-service
   ```
   Each returns `{"status":"UP"}` with HTTP 200.

2. **Register a user.** Use a fresh username each run — the smoketest user persists in `jari_user`, and re-registering the same name fails before you reach token issuance:
   ```bash
   RUN_ID=$(date +%s)
   curl -X POST http://localhost:8080/auth/register \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"smoketest-$RUN_ID\",\"password\":\"Passw0rd!\",\"email\":\"smoketest-$RUN_ID@example.com\"}"
   ```
   Expect `"user added to the system"`.

3. **Request a token** (same `$RUN_ID`):
   ```bash
   curl -X POST http://localhost:8080/auth/token \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"smoketest-$RUN_ID\",\"password\":\"Passw0rd!\"}"
   ```
   Expect a JWT. Decode its payload (`echo "<payload-segment>" | base64 -d`) and confirm a `userId` claim matching the row's primary key in `jari_user.users`, plus a `roles` claim.

4. **Call a secured route with the token** — expect HTTP 200 and a `ResponseDto` body:
   ```bash
   curl http://localhost:8080/api/projects -H "Authorization: Bearer <token>"
   ```

5. **Call it with no token** — expect HTTP 401, no stack trace:
   ```bash
   curl -i http://localhost:8080/api/projects
   ```

6. **Self-lookup** — expect HTTP 200 describing the registered user with no `password` field; HTTP 401 without the header:
   ```bash
   curl http://localhost:8080/api/users/me -H "Authorization: Bearer <token>"
   ```

7. **A direct call bypassing the gateway is rejected** — expect HTTP 401 from `project-service`'s own `IdentityHeaderFilter`, since no identity header is present:
   ```bash
   curl -i http://localhost:8083/projects
   ```

8. **Postgres-down resilience.** `docker stop jari-postgres`, then re-check step 1 for a data service — health should flip to `{"status":"DOWN"}` / 503 (slower than you'd expect; HikariCP's failure detection isn't instant). `docker start jari-postgres` and confirm recovery to `UP` / 200.

## Known Gaps

This is a learning project; the following defects are intentionally left in place until their planned phase.

### Security

- **Authorization is absent.** Identity flows end-to-end (a request through the gateway carries a real, verified `userId`), but no service checks *what* that user may access. Any authenticated user can read or modify any project's or any other user's data — `GET /api/tasks` returns every task in the system regardless of who asks. Closing this is Phase 3 (`ProjectMember` + project-scoped checks).
- **The trusted-header model has no cryptographic backing.** Covered under Deployment Constraints above; restated here because it is the single largest gap.
- **The frontend keeps its token in `localStorage`**, readable by any script on the origin and therefore XSS-exposed in a way an `HttpOnly` cookie is not. It lives there so a page reload does not log you out. Acceptable on one developer's machine; must not survive contact with a shared deployment. The honest fix is a cookie set by the gateway, which makes the gateway a session participant rather than a stateless token validator — an architectural change, not a tweak.
- **`RouterValidator` decides the auth boundary by substring match.** `path.contains(uri)` over its open-endpoints list means a path like `/api/tasks/1/swagger-ui` skips `AuthenticationFilter` entirely, and task-service's `IdentityHeaderFilter` exempts `/swagger-ui` too, so such a request clears both gates. It returns 404 today because no handler matches, so nothing leaks — but the boundary holds by accident rather than by design. Fix is prefix matching.

### Correctness

- **Issue keys can collide.** The create dialog derives a key client-side from the highest existing numeric suffix, because `TaskService.createTask` never generates one. Two clients creating at the same moment can produce the same key, since `tasks.key` has no unique constraint — recorded in the schema baseline on purpose. The real fix is a server-side per-project counter.
- **`jari-common`'s `GlobalExceptionHandler` uses the Servlet-based `WebRequest` type**, which fails to resolve in the gateway's WebFlux context — a gateway-level exception surfaces as a generic 503 rather than the intended error body.

### Incomplete by design

- **RabbitMQ is provisioned but unused.** It runs in `docker-compose.yml` and services wait on its health check, but no module declares an AMQP dependency — nothing publishes or consumes. It is there for the planned `TaskAssigned` event.
- **Project membership does not exist.** The assignee list is therefore every user in the system, not project members (Phase 3).
- **One assignee per issue.** `Task.assigneeId` is a single field; the reference app supports multiple. Changing it is a schema change.
- **Reporter is read-only.** `TaskService.updateTask` never writes `reporterId` — it is set once at creation, matching Jira's own semantics.
- **Only three board columns** (`TODO`, `IN_PROGRESS`, `DONE`). `KanbanService.STANDARD_COLUMNS` is exactly these three and `moveTask` rejects anything else; a fourth column is a backend change.
- **No frontend router.** Issue detail, create, and settings are all overlays on one route, so no issue has a shareable URL.
- **Sidebar nav rows are inert.** Releases, Issues and filters, Pages, Reports, and Components render muted and unclickable to match the reference layout; none has a backing route.
- **CORS does not exist on the gateway.** The Vite dev-server proxy is what makes browser requests same-origin. Serving the frontend from any other origin needs real CORS (including short-circuiting `OPTIONS` ahead of authentication) or serving built assets through the gateway.

### Tooling

- **One `bun audit` finding remains** in `brace-expansion`, reached via ESLint's own `minimatch` dependency. Every patched version changes the export shape incompatibly with what ESLint installs, so overriding it breaks linting outright (verified). Needs an upstream ESLint bump. Dev-only, never shipped.
- **Drag-and-drop's rollback-on-failure behaviour is verified only for a whole-service outage**, not the narrower single-request-failure case — the integration suite doesn't yet inject a single failing request into an otherwise-healthy service.
