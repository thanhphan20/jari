# Jari - Jira Clone Microservices Architecture

A Jira-style project management system: projects, issues, and a drag-and-drop Kanban board, built as six Spring Boot microservices behind a Spring Cloud Gateway with a React + Vite frontend.

Built as a **learning project for distributed-systems patterns** — service discovery, gateway-centralised authentication, identity propagation across service boundaries, and database-per-service. Not aimed at production; several defects are deliberately left in place until their planned phase.

- [`spec.md`](spec.md) — behavioural contract, API surface, deployment constraints, and known gaps. **Read the deployment constraints before running this anywhere but localhost.**
- [`AGENTS.md`](AGENTS.md) — contributor and AI-agent conventions.
- [`openspec/`](openspec/) — change proposals, capability specs, and the archive of completed work.

## Architecture

Every browser request enters through the API Gateway, which is the only ingress. The gateway validates the JWT, then injects trusted identity headers (`X-Jari-User-Id`, `X-Jari-Username`) into the forwarded request. Downstream services resolve each other by name through Eureka, and each owns its own Postgres database — no service reads another's tables.

```mermaid
flowchart TB
    Browser["Browser<br/>React + Vite (:5173)"]

    subgraph Edge
        Gateway["API Gateway :8080<br/>JWT validation<br/>injects identity headers"]
    end

    subgraph Discovery
        Eureka["Eureka :8761<br/>service registry"]
    end

    subgraph Services
        User["User Service :8082<br/>identity + profile"]
        Project["Project Service :8083"]
        Task["Task Service :8084<br/>tasks + kanban"]
        Notification["Notification Service :8085"]
    end

    subgraph Data["Postgres :15432 → 5432"]
        UserDb[("jari_user")]
        ProjectDb[("jari_project")]
        TaskDb[("jari_task")]
        NotificationDb[("jari_notification")]
    end

    Rabbit["RabbitMQ :5672<br/>provisioned, not yet used"]

    Browser -->|"/api, /auth<br/>same-origin via Vite proxy"| Gateway

    Gateway -->|"lb://"| User
    Gateway -->|"lb://"| Project
    Gateway -->|"lb://"| Task
    Gateway -->|"lb://"| Notification

    Gateway -.->|"registers + resolves"| Eureka
    User -.-> Eureka
    Project -.-> Eureka
    Task -.-> Eureka
    Notification -.->|registers| Eureka

    User --> UserDb
    Project --> ProjectDb
    Task --> TaskDb
    Notification --> NotificationDb

    Rabbit ~~~ Services
```

Dashed lines are Eureka registration and lookup; solid lines are request or query paths. RabbitMQ is drawn detached on purpose — it runs in `docker-compose.yml` but no service declares an AMQP dependency yet.

| Service | Port | Responsibility | Database |
|---|---|---|---|
| Eureka Discovery | 8761 | Service registry; dashboard at `http://localhost:8761` | — |
| API Gateway | 8080 | Sole ingress; routes by service name via `lb://`, validates JWTs, injects identity headers | — |
| User Service | 8082 | Identity: registration, login, JWT issuance, profile CRUD | `jari_user` |
| Project Service | 8083 | Project CRUD | `jari_project` |
| Task Service | 8084 | Task/issue CRUD and the Kanban board | `jari_task` |
| Notification Service | 8085 | Notification CRUD | `jari_notification` |

Two shared modules, each depended on only by the services that need it:

- **`jari-common`** — shared DTOs (`BaseDto`, `ResponseDto`, `ErrorResponseDto`), common exceptions and handlers. Used by all services; a known coupling point, deliberately not eliminated.
- **`jari-security`** — JWT handling (`JwtUtils`), gateway-injected identity header names (`IdentityHeaders`), and the filter downstream services use to require them (`IdentityHeaderFilter`).

There used to be a separate Auth Service; it was merged into User Service (`openspec/changes/archive/2026-07-30-collapse-identity-service`) because the two-table split was an accident, not a real boundary, and it made a user's own id unresolvable from a token.

## Project Structure

```
jari/
├── pom.xml                          # Parent POM
├── docker-compose.yml               # Postgres, RabbitMQ, and all six services
├── Dockerfile                       # Multi-stage Dockerfile
├── postgres/init-db.sql             # Creates the four per-service databases
├── openspec/                        # Change proposals, specs, and archive
├── jari-common/                     # Shared DTOs and exception handling
├── jari-security/                   # Shared JWT + identity-header module
├── jari-discovery/                  # Eureka Discovery Service
├── jari-gateway/                    # API Gateway
├── jari-user-service/               # Identity + User Service
├── jari-project-service/            # Project Service
├── jari-task-service/               # Task Service
├── jari-notification-service/       # Notification Service
└── jari-frontend/                   # React + Vite client
```

## Technology Stack

**Backend** — Java 21 · Maven (multi-module) · Spring Boot 3.2.4 · Spring Cloud 2023.0.1 · Spring Cloud Gateway (WebFlux) · Netflix Eureka · Spring Data JPA · Flyway 9.22.3 (with `ddl-auto: validate`, so entity drift fails startup) · PostgreSQL 18 · Lombok 1.18.40 · Docker Compose

**Frontend** — React 19 · TypeScript 5.9 · Vite 8 · Bun · Tailwind CSS v4 · TanStack Query v5 · Axios · TipTap 3 (rich-text editor) · Phosphor Icons · ESLint 9

**Provisioned but unused** — RabbitMQ 3, running in `docker-compose.yml` for a planned `TaskAssigned` event. No module declares an AMQP dependency yet.

## Getting Started

Requires **Java 21**, **Maven 3.6+**, **Docker + Docker Compose**, and **Bun** (frontend).

### 1. Start the backend

```bash
docker compose up --build
```

Starts Postgres, RabbitMQ, and all six services in dependency order (Postgres/RabbitMQ healthy → Eureka healthy → gateway and app services). Add `-d` to run detached.

**The first build takes several minutes.** The Dockerfile does not cache Maven dependencies across stages, so each service resolves its own from scratch: roughly 3-6 minutes once `maven:3.9-eclipse-temurin-21` is pulled, plus a few more on the very first run for that ~700MB base image. A build sitting at "load build context" or "RUN mvn clean package" is working, not hung.

**If the build fails**, retry once before investigating. BuildKit's `bake` mode builds all service images concurrently and Maven Central occasionally rejects one of the simultaneous dependency-resolution requests under that load — observed to be transient.

**After restarting a single service**, requests routed to it can return a transient `503` for up to ~30 seconds while the gateway's Eureka registry cache catches up. Not a defect; retry.

### 2. Start the frontend

```bash
cd jari-frontend
bun install
bun run dev
```

Open http://localhost:5173 and sign in with the seeded `admin` / `admin123` (or `user` / `user123`).

On a fresh database there are zero projects, so you land on a "Create your first project" screen. From there the board is a working issue tracker: create issues, edit them in a modal, drag cards between and within columns (optimistic, reverts on failure), and filter by text, assignee, type, or "only my issues". Every drag operation has a non-drag equivalent, so nothing requires a pointer.

Verify the stack end-to-end with the smoke test in [`spec.md`](spec.md#local-verification).

### Local development without Docker

Requires a local Postgres and RabbitMQ (or point `application.yml` at remote ones).

```bash
mvn clean install
```

Start in dependency order: `jari-discovery` → `jari-gateway` → `jari-user-service` → the rest, each via `mvn spring-boot:run` in its module directory. `jari-gateway` and `jari-user-service` need `JARI_SECURITY_JWT_SECRET` set — both fail to start without it, by design.

### Useful commands

```bash
docker compose down -v     # wipe Postgres volume; next `up` re-runs postgres/init-db.sql
docker compose ps          # check for a service stuck in a restart loop
```

### Connecting a database client

Postgres is published on host port **15432**, not 5432 (`localhost` / `postgres` / `postgres`, databases `jari_user`, `jari_project`, `jari_task`, `jari_notification`).

The non-standard port is deliberate. If something already holds 5432 — a native Postgres install, most commonly — Docker **does not fail loudly**: the container starts with the port unpublished and a client pointed at `localhost:5432` silently reaches the *other* server, presenting as an auth failure or a server with no `jari_*` databases. Neither points at the real cause. Verify a mapping is actually bound with `docker inspect jari-postgres --format '{{json .NetworkSettings.Ports}}'`.

## License

This project is for educational purposes.
