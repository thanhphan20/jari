## 1. Decisions to settle before coding

- [x] 1.1 Confirm the roles model for this phase: global roles only in the token, project roles deliberately excluded (see design open questions).
  - Confirmed: token carries a flat `roles` claim (global roles only, e.g. `["USER"]`). Project-scoped roles are Phase 3's `ProjectMember` concern and never enter the token, per the design doc's own reasoning (a project role baked into a token can't be revoked before expiry).
- [x] 1.2 Choose the identity header names and record the choice (`X-User-Id` versus a namespaced `X-Jari-User-Id`).
  - Chose namespaced: `X-Jari-User-Id`, `X-Jari-Username`. Makes it obvious at a glance these are gateway-injected, not something a client would plausibly send on its own.
- [x] 1.3 Decide whether `DataSeeder` survives the collapse.
  - Keep it, moved into `jari-user-service`. Makes the destructive re-registration this change requires (dropping `jari_auth`/`jari_user`) cheap, and gives two fixture users for the Phase 5 browser demo. Superseding it with Testcontainers fixtures is Phase 2's call, not this one's.

## 2. Clean slate

- [x] 2.1 Remove `jari_auth` from `postgres/init-db.sql`.
- [ ] 2.2 Drop the `jari_auth` and `jari_user` databases locally (`docker compose down -v` is sufficient).
- [ ] 2.3 Confirm the stack still boots with four databases before changing any code — this isolates a boot break from a refactor break.
  - Skipped the isolate-first ordering in practice: wrote all the code in one pass instead of pausing here. Noted so the deviation is visible; verification happens in section 10 regardless.

## 3. Identity service consolidation

- [x] 3.1 Add credential fields to `jari-user-service`'s `User` entity.
  - Entity already had `email`/`password` from the original merge target. Relaxed `firstName`/`lastName` to nullable, since registration only supplies username/email/password and profile fields are filled in later via the update endpoint.
- [x] 3.2 Move `SecurityConfig`, `CustomUserDetails`, and `CustomUserDetailsService` from auth into `jari-user-service`; wire `PasswordEncoder` and `AuthenticationManager`.
  - Merged rather than replaced: `SecurityConfig` stays `.anyRequest().permitAll()` (matching the pre-merge user-service config) plus the auth beans (`PasswordEncoder`, `AuthenticationProvider`, `AuthenticationManager`). Spring Security here only backs the login flow now - it is not the request-level authorization boundary. Bringing over auth's original `.anyRequest().authenticated()` would have 403'd every `/users/**` call, since Spring Security has no way to recognize a gateway-issued JWT as an authenticated session. That boundary is the gateway plus the new `IdentityHeaderFilter` (task 8.1, extended below).
- [x] 3.3 Move `AuthController`, `AuthService`, and `AuthRequest` into `jari-user-service`.
  - Also replaced `AuthController`'s raw `@RequestBody User user` binding (the pre-existing anti-pattern - JSON mapped directly onto the JPA entity) with a new `RegisterRequest` DTO, since moving the endpoint was the natural point to not carry that forward.
- [x] 3.4 Move `DataSeeder` if task 1.3 decided to keep it; otherwise delete it.
- [x] 3.5 Remove all password fields from user response DTOs; confirm no endpoint returns password material.
  - `UserDto` already excludes `password` from anywhere it's used as a response body (it's on the DTO for the create path only; verified `getUserById`/`getAllUsers`/`/me` all map through `UserService.mapToDto`, which never sets it).
- [x] 3.6 Point the gateway's `/auth/**` route at the identity service.
  - Route id renamed `auth-service` -> `auth-routes`, `uri: lb://jari-user-service`.
- [ ] 3.7 Verify register and token issuance work end-to-end through the gateway against the consolidated service.

## 4. Delete the replication flow

- [x] 4.1 Delete `UserRegistrationConsumer`.
- [x] 4.2 Delete `RabbitConfig` from both the former auth service and the user service.
- [x] 4.3 Remove the RabbitMQ publish from `AuthService.saveUser`.
- [x] 4.4 Remove RabbitMQ dependencies and datasource config from the identity service's `pom.xml` and `application.yml`; leave RabbitMQ in compose for Phase 6.
  - Removed `spring-boot-starter-amqp` from `jari-user-service`'s `pom.xml` and `SPRING_RABBITMQ_HOST` + the `rabbitmq` `depends_on` entry from its compose block. The `rabbitmq` service itself stays in `docker-compose.yml`.
- [ ] 4.5 Confirm no password hash is present on the broker or in any second database after a registration.

## 5. Security starter and secret externalization

- [x] 5.1 Create the security starter module; add it to the parent `pom.xml`.
  - New `jari-security` module: `JwtUtils`, `IdentityHeaders` (header name constants), `IdentityHeaderFilter` (shared servlet filter for downstream identity enforcement).
- [x] 5.2 Move `JwtUtils` out of `jari-common` into the starter; make gateway and identity service depend on it and remove the dependency elsewhere.
  - Removed jjwt dependencies from `jari-common`'s `pom.xml` along with the class. Gateway and `jari-user-service` depend on `jari-security`; so do project/task/notification (for `IdentityHeaderFilter`/`IdentityHeaders`, not `JwtUtils` - they never parse tokens).
- [x] 5.3 Replace the hardcoded `SECRET` constant with configuration, supplied via environment variable in compose.
  - `JwtUtils` constructor takes `${jari.security.jwt.secret}` via `@Value`. Compose sets `JARI_SECURITY_JWT_SECRET` (relaxed-binds to that property) on `api-gateway` and `user-service` - the only two services that construct a `JwtUtils` bean. Reused the same base64 value that was previously hardcoded, since only *where* it lives was in scope, not the value itself.
- [ ] 5.4 Make startup fail with a named-property message when the secret is absent; verify by starting with it unset.
  - Implemented (`IllegalStateException` naming the property and env var if blank); runtime verification pending.
- [ ] 5.5 Verify gateway and identity service resolve the same secret — a mismatch produces intermittent 401s that look like a token bug.
- [x] 5.6 Confirm the secret value appears in no source file.
  - Grepped the repo after the edit: only reference is the compose env var.

## 6. Token claims

- [x] 6.1 Add the `userId` claim to issued tokens, populated from the persisted user's primary key.
  - `AuthService.login` resolves `CustomUserDetails.getId()` (added) and passes it to `JwtUtils.generateToken(userId, username, roles)`.
- [x] 6.2 Add the `roles` claim per the task 1.1 decision.
  - Hardcoded `List.of("USER")` for now - no roles table exists yet; matches the single-role reality the old `CustomUserDetails.getAuthorities()` already encoded.
- [ ] 6.3 Verify the claim by decoding a freshly issued token and comparing `userId` against the database row.
- [ ] 6.4 Verify `userId` is identical across two separate logins by the same user.

## 7. Gateway propagation

- [x] 7.1 Replace the raw `RuntimeException` throws in `AuthenticationFilter` with HTTP 401 responses carrying no stack trace.
  - Already done in Phase 0; preserved through this rewrite.
- [x] 7.2 Inject the identity headers into forwarded requests after successful validation.
- [x] 7.3 Use replace semantics, not append — verify with a request that supplies the identity headers itself and confirm the client values do not reach the downstream service.
  - Implemented via strip-then-set on the same mutated-request builder (see 7.6). Runtime verification pending.
- [ ] 7.4 Verify an expired token yields 401, and a signature-tampered token yields 401.
- [ ] 7.5 Confirm open routes (register, token, Swagger) still work without an `Authorization` header.
- [x] 7.6 Decide and apply whether open routes also strip inbound identity headers (design open question).
  - Decided yes: the filter strips inbound `X-Jari-*` headers unconditionally on every route, secured or not, before doing anything else. Cheap, and closes the gap the design doc flagged (a client could otherwise set identity headers directly on an open route with nothing to strip them).

## 8. Downstream identity consumption

- [x] 8.1 Add identity-header reading to project, task, and notification services, rejecting requests that lack the headers with HTTP 401.
  - **Scope correction**: also added to `jari-user-service` itself, which the original task list omitted. The `gateway-identity-propagation` spec says "a downstream service" generically, not "three of the four remaining services" - user-service's own `/users/**` CRUD endpoints needed the same enforcement as project/task/notification, exempting only `/auth/**` (pre-identity), `/actuator/health`, and Swagger paths. Implemented as one shared `IdentityHeaderFilter` in `jari-security`, configured per service via a small `IdentityFilterConfig` each - the mechanism is identical across all four, only the open-path list differs.
  - **Integration detail not in the original task**: all four services also had to exempt `/v3/api-docs` and `/swagger-ui`, since the gateway's Swagger-aggregation routes call those paths directly without going through `AuthenticationFilter` (no `AuthenticationFilter` in those routes' filter chains) - so no identity headers are ever present on that path. Without the exemption, Swagger aggregation would have broken.
- [ ] 8.2 Verify a direct call to a service port, bypassing the gateway, is rejected.
- [x] 8.3 Add `GET /api/users/me`, resolving the caller from the propagated identity.
  - `@RequestHeader(IdentityHeaders.USER_ID)` on the controller method. Note: by the time this method runs, `IdentityHeaderFilter` has already guaranteed the header is present (task 8.1) - the `@RequestHeader`-required behavior is defense-in-depth, not the actual enforcement point.
- [ ] 8.4 Verify `/me` ignores a client-supplied identifier and still returns the authenticated caller.
- [ ] 8.5 Verify `/me` without credentials returns 401.

## 9. Remove the auth module

- [x] 9.1 Delete the `jari-auth-service` directory.
- [x] 9.2 Remove its module entry from the parent `pom.xml`.
- [x] 9.3 Remove its `Dockerfile` stage.
- [x] 9.4 Remove its `docker-compose.yml` service and its Swagger route and `springdoc` entry from the gateway config.
- [ ] 9.5 Confirm a clean container build succeeds — not just a local `mvn install`, since the new starter module must be wired into the Docker build correctly.

## 10. Verify and document

- [ ] 10.1 Re-run the Phase 0 smoke procedure; confirm it still passes with five services instead of six.
- [ ] 10.2 Extend the smoke procedure with the token-claim check and the `/me` check.
- [ ] 10.3 Document the trusted-header trust model explicitly: downstream services trust identity headers unconditionally, and this is only sound because the gateway is the sole ingress.
- [ ] 10.4 Document the locally published service ports as a development-only exposure that must not reach a shared environment.
- [ ] 10.5 State clearly in the change summary that authorization is still absent — any authenticated user can still read all tasks — and that Phase 3 closes it.
- [ ] 10.6 Update `README.md`: service count, endpoint list, and the removal of the registration event flow.
