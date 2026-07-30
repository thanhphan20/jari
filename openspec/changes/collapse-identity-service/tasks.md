## 1. Decisions to settle before coding

- [ ] 1.1 Confirm the roles model for this phase: global roles only in the token, project roles deliberately excluded (see design open questions).
- [ ] 1.2 Choose the identity header names and record the choice (`X-User-Id` versus a namespaced `X-Jari-User-Id`).
- [ ] 1.3 Decide whether `DataSeeder` survives the collapse.

## 2. Clean slate

- [ ] 2.1 Remove `jari_auth` from `postgres/init-db.sql`.
- [ ] 2.2 Drop the `jari_auth` and `jari_user` databases locally (`docker compose down -v` is sufficient).
- [ ] 2.3 Confirm the stack still boots with four databases before changing any code — this isolates a boot break from a refactor break.

## 3. Identity service consolidation

- [ ] 3.1 Add credential fields to `jari-user-service`'s `User` entity.
- [ ] 3.2 Move `SecurityConfig`, `CustomUserDetails`, and `CustomUserDetailsService` from auth into `jari-user-service`; wire `PasswordEncoder` and `AuthenticationManager`.
- [ ] 3.3 Move `AuthController`, `AuthService`, and `AuthRequest` into `jari-user-service`.
- [ ] 3.4 Move `DataSeeder` if task 1.3 decided to keep it; otherwise delete it.
- [ ] 3.5 Remove all password fields from user response DTOs; confirm no endpoint returns password material.
- [ ] 3.6 Point the gateway's `/auth/**` route at the identity service.
- [ ] 3.7 Verify register and token issuance work end-to-end through the gateway against the consolidated service.

## 4. Delete the replication flow

- [ ] 4.1 Delete `UserRegistrationConsumer`.
- [ ] 4.2 Delete `RabbitConfig` from both the former auth service and the user service.
- [ ] 4.3 Remove the RabbitMQ publish from `AuthService.saveUser`.
- [ ] 4.4 Remove RabbitMQ dependencies and datasource config from the identity service's `pom.xml` and `application.yml`; leave RabbitMQ in compose for Phase 6.
- [ ] 4.5 Confirm no password hash is present on the broker or in any second database after a registration.

## 5. Security starter and secret externalization

- [ ] 5.1 Create the security starter module; add it to the parent `pom.xml`.
- [ ] 5.2 Move `JwtUtils` out of `jari-common` into the starter; make gateway and identity service depend on it and remove the dependency elsewhere.
- [ ] 5.3 Replace the hardcoded `SECRET` constant with configuration, supplied via environment variable in compose.
- [ ] 5.4 Make startup fail with a named-property message when the secret is absent; verify by starting with it unset.
- [ ] 5.5 Verify gateway and identity service resolve the same secret — a mismatch produces intermittent 401s that look like a token bug.
- [ ] 5.6 Confirm the secret value appears in no source file.

## 6. Token claims

- [ ] 6.1 Add the `userId` claim to issued tokens, populated from the persisted user's primary key.
- [ ] 6.2 Add the `roles` claim per the task 1.1 decision.
- [ ] 6.3 Verify the claim by decoding a freshly issued token and comparing `userId` against the database row.
- [ ] 6.4 Verify `userId` is identical across two separate logins by the same user.

## 7. Gateway propagation

- [ ] 7.1 Replace the raw `RuntimeException` throws in `AuthenticationFilter` with HTTP 401 responses carrying no stack trace.
- [ ] 7.2 Inject the identity headers into forwarded requests after successful validation.
- [ ] 7.3 Use replace semantics, not append — verify with a request that supplies the identity headers itself and confirm the client values do not reach the downstream service.
- [ ] 7.4 Verify an expired token yields 401, and a signature-tampered token yields 401.
- [ ] 7.5 Confirm open routes (register, token, Swagger) still work without an `Authorization` header.
- [ ] 7.6 Decide and apply whether open routes also strip inbound identity headers (design open question).

## 8. Downstream identity consumption

- [ ] 8.1 Add identity-header reading to project, task, and notification services, rejecting requests that lack the headers with HTTP 401.
- [ ] 8.2 Verify a direct call to a service port, bypassing the gateway, is rejected.
- [ ] 8.3 Add `GET /api/users/me`, resolving the caller from the propagated identity.
- [ ] 8.4 Verify `/me` ignores a client-supplied identifier and still returns the authenticated caller.
- [ ] 8.5 Verify `/me` without credentials returns 401.

## 9. Remove the auth module

- [ ] 9.1 Delete the `jari-auth-service` directory.
- [ ] 9.2 Remove its module entry from the parent `pom.xml`.
- [ ] 9.3 Remove its `Dockerfile` stage.
- [ ] 9.4 Remove its `docker-compose.yml` service and its Swagger route and `springdoc` entry from the gateway config.
- [ ] 9.5 Confirm a clean container build succeeds — not just a local `mvn install`, since the new starter module must be wired into the Docker build correctly.

## 10. Verify and document

- [ ] 10.1 Re-run the Phase 0 smoke procedure; confirm it still passes with five services instead of six.
- [ ] 10.2 Extend the smoke procedure with the token-claim check and the `/me` check.
- [ ] 10.3 Document the trusted-header trust model explicitly: downstream services trust identity headers unconditionally, and this is only sound because the gateway is the sole ingress.
- [ ] 10.4 Document the locally published service ports as a development-only exposure that must not reach a shared environment.
- [ ] 10.5 State clearly in the change summary that authorization is still absent — any authenticated user can still read all tasks — and that Phase 3 closes it.
- [ ] 10.6 Update `README.md`: service count, endpoint list, and the removal of the registration event flow.
