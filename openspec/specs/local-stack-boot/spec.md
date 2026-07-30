# local-stack-boot Specification

## Purpose

TBD - created by archiving change boot-the-stack. Update Purpose after archive.

## Requirements

### Requirement: Full stack starts from a single command

The system SHALL start all infrastructure and application services from a single `docker compose up` invocation, with no manual sequencing, no manual database creation, and no manual restarts.

#### Scenario: Cold start from a clean machine state

- **WHEN** an operator removes all project volumes and runs `docker compose up --build`
- **THEN** Postgres, RabbitMQ, Eureka, the gateway, and all five application services reach a running state
- **AND** no service enters a restart loop

#### Scenario: Restart with an existing volume

- **WHEN** an operator runs `docker compose down` followed by `docker compose up` without removing volumes
- **THEN** all five application databases are present
- **AND** every data service connects successfully on its first attempt

#### Scenario: Dependencies are ready before dependants start

- **WHEN** the stack starts
- **THEN** no application service attempts a database or broker connection before Postgres and RabbitMQ report healthy
- **AND** no application service attempts registration before Eureka reports healthy

### Requirement: Every service reports its health

Each service SHALL expose a health endpoint that reports `UP` only when the service's own dependencies are reachable.

#### Scenario: All services report healthy after startup

- **WHEN** the stack has finished starting
- **THEN** each service's `/actuator/health` endpoint returns HTTP 200 with status `UP`

#### Scenario: A service reports unhealthy when its database is unreachable

- **WHEN** Postgres is stopped while a data service is running
- **THEN** that service's health endpoint reports a non-`UP` status rather than continuing to report `UP`

### Requirement: All services are discoverable

Every application service SHALL register itself with Eureka under its configured application name.

#### Scenario: Registration visible in the Eureka dashboard

- **WHEN** the stack has finished starting
- **AND** an operator opens the Eureka dashboard
- **THEN** the gateway and all five application services appear as registered instances with status `UP`

### Requirement: A request through the gateway reaches a service and returns data

The gateway SHALL route an external request to the correct service and return that service's response, proving the discovery, routing, and persistence path end-to-end.

#### Scenario: Registration and token issuance through the gateway

- **WHEN** a client posts valid registration details to the gateway's registration route
- **THEN** the request is routed to the identity service and a user record is persisted
- **AND** a subsequent token request with those credentials returns a signed JWT

#### Scenario: An authenticated read reaches a downstream service

- **WHEN** a client sends a request to a secured route through the gateway with a valid `Authorization: Bearer` header
- **THEN** the gateway forwards the request to the target service
- **AND** the client receives that service's response body with HTTP 200

#### Scenario: A request without credentials is rejected at the gateway

- **WHEN** a client sends a request to a secured route with no `Authorization` header
- **THEN** the gateway rejects the request with an HTTP 401 status
- **AND** the response body does not contain a stack trace

### Requirement: The boot baseline is reproducible

The configuration that produces a working stack SHALL be committed to version control, and the procedure that verifies it SHALL be documented.

#### Scenario: No untracked source or configuration

- **WHEN** an operator runs `git status` after this change
- **THEN** no Java source file, frontend source file, or compose/Docker configuration file is reported as untracked

#### Scenario: Documented startup instructions match the repository

- **WHEN** a newcomer follows the startup instructions in `README.md`
- **THEN** every referenced file exists in the repository
- **AND** every referenced technology matches what the compose file actually runs

#### Scenario: Documented smoke check passes

- **WHEN** an operator follows the documented smoke procedure against a freshly started stack
- **THEN** every step succeeds without manual intervention
