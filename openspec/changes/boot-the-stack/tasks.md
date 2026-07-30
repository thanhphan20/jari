## 1. Baseline and version control

- [ ] 1.1 Record the current failure: run `docker compose up --build`, capture the output, and note where it stalls or fails. This is the evidence the change is judged against.
- [ ] 1.2 Commit the untracked Kanban work in `jari-task-service` (`KanbanController`, `KanbanBoardDto`, `KanbanColumnDto`, `MoveTaskDto`, `KanbanService`) as its own commit.
- [ ] 1.3 Commit `jari-frontend/` with a `.gitignore` covering `node_modules` and build output. Do not fix the frontend here — it is Phase 5.
- [ ] 1.4 Verify `git status` reports no untracked Java, frontend, or configuration files.

## 2. Build context and image size

- [ ] 2.1 Add `**/node_modules` to `.dockerignore`; verify the build context size drops (compare the "Sending build context" figure before and after).
- [ ] 2.2 Restructure the `Dockerfile`: a shared builder stage that copies the parent `pom.xml` and each module `pom.xml`, runs `mvn dependency:go-offline`, then copies sources.
- [ ] 2.3 Convert `jari-discovery` to build from the shared builder stage with an `eclipse-temurin:21-jre` runtime stage; confirm it builds and the container starts.
- [ ] 2.4 Apply the same pattern to the remaining six stages (gateway, auth, user, project, task, notification).
- [ ] 2.5 Confirm dependency caching works: touch one Java file, rebuild, and verify Maven does not re-resolve dependencies.

## 3. Infrastructure readiness

- [ ] 3.1 Correct the Postgres volume mount to `/var/lib/postgresql/data`.
- [ ] 3.2 Make `postgres/init-db.sql` idempotent so it is safe to run against an existing cluster.
- [ ] 3.3 Add a `healthcheck` to the Postgres service using `pg_isready`.
- [ ] 3.4 Add a `healthcheck` to the RabbitMQ service using `rabbitmq-diagnostics -q ping`.
- [ ] 3.5 Add a healthcheck to `eureka-discovery`; decide TCP-on-8761 versus actuator health and record the choice (see design open questions).
- [ ] 3.6 Convert every `depends_on` in `docker-compose.yml` to `condition: service_healthy`, with `start_period` tuned so a cold-start Postgres does not fail the stack.
- [ ] 3.7 Verify the restart-with-existing-volume scenario: `docker compose down` then `up`, and confirm all five databases exist and every data service connects on its first attempt.

## 4. Bring the stack up

- [ ] 4.1 Start infrastructure only (`postgres`, `rabbitmq`) and confirm both report healthy.
- [ ] 4.2 Start `eureka-discovery`; confirm the dashboard is reachable and reports healthy.
- [ ] 4.3 Start `api-gateway`; confirm it registers with Eureka.
- [ ] 4.4 Start the five application services; confirm each registers with Eureka and reports `UP` at `/actuator/health`.
- [ ] 4.5 Confirm no service is in a restart loop (`docker compose ps` shows no repeated restart counts).

## 5. Prove it works

- [ ] 5.1 Register a user through the gateway; confirm the row is persisted in the identity database.
- [ ] 5.2 Request a token with those credentials; confirm a signed JWT is returned.
- [ ] 5.3 Call a secured route through the gateway with that token; confirm HTTP 200 and a real response body.
- [ ] 5.4 Call the same route with no `Authorization` header; confirm HTTP 401 and no stack trace in the response. This requires replacing the raw `RuntimeException` in the gateway's `AuthenticationFilter` — the one application-code change in scope.
- [ ] 5.5 Stop Postgres while a data service runs; confirm that service's health endpoint stops reporting `UP`. Restart Postgres and confirm recovery.

## 6. Document and close

- [ ] 6.1 Rewrite the `README.md` startup section: it currently references MySQL and `compose.yml`, neither of which matches the repository.
- [ ] 6.2 Document the smoke procedure from section 5 as a numbered checklist a newcomer can follow.
- [ ] 6.3 Record the measured cold-build duration in the README so a slow first build is not mistaken for a hang.
- [ ] 6.4 Document `docker compose down -v` as the way to force database re-provisioning.
- [ ] 6.5 Decide whether to pin the `postgres` image to an explicit major version (design open question) and apply or record the decision.
- [ ] 6.6 Run the full cold-start scenario one final time from removed volumes, following only the README, and confirm every step passes.
