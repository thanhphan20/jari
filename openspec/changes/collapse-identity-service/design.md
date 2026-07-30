## Context

Jari has two services that both believe they own users. `jari-auth-service` holds `username`, `password`, `email` in `jari_auth`. `jari-user-service` holds `email`, `password`, `firstName`, `lastName`, `username`, `avatarUrl`, `active` in `jari_user`. Registration writes to auth, which publishes the whole entity to `user.exchange`, and `UserRegistrationConsumer` builds a *new* row in `jari_user` with `firstName` and `lastName` hardcoded to `"New"` and `"User"`.

Both tables use `GenerationType.IDENTITY` in separate databases, and the consumer does not carry the id across. The two sequences are therefore independent and unrelated. Meanwhile `JwtUtils.generateToken` passes an empty claims map, so the token identifies the user only by `sub: <username>`.

The net effect is that `Task.assigneeId` — a `Long` — has no defined referent. Which sequence is it from? Neither service can answer. This is why "assign a task to a user" is not implementable today, and it is why this change has to come before any feature work.

Two further problems ride along. `AuthService.saveUser` publishes credential hashes through RabbitMQ into a second database. And `JwtUtils.SECRET` is a compile-time constant in `jari-common`, which all six services depend on — so the signing key is in source, and any change to `jari-common` forces all six services to rebuild in lockstep, which is the distributed-monolith coupling that microservices exist to avoid.

Prior decision from planning: collapse the two services rather than fix the synchronization. The split is not a domain boundary anyone would deliberately draw; it is an accident that created three bugs.

## Goals / Non-Goals

**Goals:**

- One service owns identity. One table, one sequence, one source of truth.
- A stable `userId` is available to every service handling a request, without a name lookup.
- Credential material exists in exactly one database and never on the broker.
- The signing secret comes from configuration.
- `jari-common` stops being a lockstep coupling point for security code.

**Non-Goals:**

- **Authorization is not in scope.** After this change, downstream services will know *who* is calling but still will not check *what they may access*. `GET /api/tasks` still returns every task. That is Phase 3, and conflating the two would make neither verifiable.
- No refresh tokens, no token revocation, no session store. The 30-minute expiry stands.
- No OAuth2/OIDC provider, no external identity provider.
- No schema migration tooling. `ddl-auto: update` remains in force through this change; Flyway is the next change. This change is destructive to local data by design.
- No user-facing UI.

## Decisions

### `jari-user-service` survives; `jari-auth-service` is deleted

The surviving module keeps the name of the domain it owns. Its `User` entity is already the richer one (profile fields, `active`, timestamps) and needs only the credential columns, which are two fields. The auth module's authentication machinery — `AuthController`, `AuthService`, `SecurityConfig`, `CustomUserDetails`, `CustomUserDetailsService`, `DataSeeder` — moves in largely unchanged.

*Why this direction:* moving two columns and a package of security classes into the richer entity is less work and less risk than moving seven profile columns plus an OpenAPI config into the thinner one. The database that survives, `jari_user`, is also the one whose id is what `Task.assigneeId` and `Project.leadUserId` conceptually already mean.

*Alternative considered:* keep `jari-auth-service` and fold profile into it. Rejected — more code movement, and it leaves the surviving service named for a mechanism (auth) rather than a domain (users), which invites the same confused boundary again later.

*Alternative considered:* keep both, add bidirectional event sync with preserved ids. Rejected in planning: it pays real distributed-systems complexity for a boundary that is not a real boundary. The project has better places to spend that complexity — Phase 3 and Phase 8 authorization, and the Phase 6 event flow.

### Gateway validates; downstream services trust injected headers

The gateway remains the only component that parses and verifies a JWT. After validation it injects `X-User-Id` and `X-User-Name` into the forwarded request. Downstream services read those headers, and reject requests lacking them.

*Why:* it keeps token format knowledge in one place, so changing the token does not touch five services. It also removes any need for downstream services to resolve a username to an id.

*The security property this depends on, stated plainly:* downstream services trust those headers unconditionally. That is only sound because the gateway is the sole ingress. `docker-compose.yml` currently publishes every service port to the host, which in a shared environment would let anyone set `X-User-Id` to any value and impersonate anyone. This is acceptable for local development and is why the spec requires it to be documented rather than silently assumed.

*Critical implementation detail:* the gateway must overwrite these headers, not append. If a client sends `X-User-Id: 1` and the gateway adds its own, a downstream service reading "the" header may read the client's. Use a set/replace operation, and verify with a request that supplies the headers deliberately.

*Alternative considered:* forward the JWT and have each service validate it independently (a shared resource-server config). More defensible security — no trusted-header assumption — but every service then needs the signing key and token-parsing code, which is exactly the shared-security-code coupling being removed. Worth revisiting if Jari ever gains a second ingress.

### Add `userId` to the token, and keep `sub` as the username

`sub` stays the username for human readability in logs and debugging; `userId` is added as the machine-usable identifier, along with `roles`.

*Why not make `sub` the userId:* keeping the username in `sub` means existing log lines and the Swagger flow stay readable, and it costs one extra claim.

### Extract a security starter; shrink `jari-common`

JWT generation and validation move out of `jari-common` into a small module that only the gateway and identity service depend on. `jari-common` keeps `ResponseDto`, `ErrorResponseDto`, `BaseDto`, and the exception types.

*Why:* today a change to `ResponseDto` forces a rebuild of the security code, and a change to the security code forces a rebuild of all six services. Splitting means only the two services that actually handle tokens depend on token code.

*Acknowledged, not solved:* `jari-common` remains a shared jar across six services, which is still lockstep coupling for DTOs. The planning decision was to shrink it and document the trade-off rather than eliminate it, because full elimination means duplicating DTOs across six modules for limited learning value.

### The signing secret comes from configuration, and startup fails without it

No default, no fallback, no empty string. A missing secret is a startup failure with a message naming the missing property.

*Why fail rather than generate:* a generated-per-boot secret would make the gateway and identity service disagree, producing intermittent 401s that look like a token bug. A hard failure at startup is unmissable.

### Delete the registration event rather than fix it

The exchange, queue, `RabbitConfig` in both services, and `UserRegistrationConsumer` all go. RabbitMQ stays in compose — Phase 6 needs it for `TaskAssigned`.

*Why:* the flow's only purpose was reconciling two tables that no longer both exist. Keeping it "for the pattern" would leave credential hashes flowing over the broker to demonstrate an event flow that has no reason to exist. Phase 6 provides a real one.

## Risks / Trade-offs

- **Identity is available but not enforced after this change** → Downstream services will read `X-User-Id` and still return everyone's data. This is a deliberate, temporary state. It must be stated in the change summary so nobody mistakes this change for having fixed authorization; Phase 3 is what closes it.
- **Trusted headers are spoofable if any downstream port is reachable** → Documented as a requirement rather than fixed, since local port publishing is genuinely useful for debugging. The mitigation is documentation plus never carrying this compose file into a shared environment.
- **Header injection appends instead of replacing** → A real and easily missed defect. Covered by an explicit spec scenario that sends the headers from the client and asserts they are overwritten.
- **Destructive to local data** → `jari_auth` is dropped, and the identity table changes shape under `ddl-auto: update`, which does not reliably handle column additions to a populated table with non-null constraints. Mitigation: drop both databases and re-register. `DataSeeder` makes this cheap, which is an argument for keeping it.
- **`ddl-auto: update` may not produce the intended schema** → Adding non-null credential columns to an existing `jari_user.users` table will fail or silently produce a nullable column. Mitigation: start from a dropped database. This is also the concrete argument for the migrations change that follows.
- **Breaking every route while mid-refactor** → Sequence the work so the stack stays bootable: schema and entity first, then auth code movement, then token claims, then gateway propagation, then service-side enforcement. Verify the boot smoke check at each step.
- **The security starter is a new module** → Parent `pom.xml`, Dockerfile stages, and the `-am` build flags all need to know about it. Small but easy to half-finish; verify a clean container build, not just a local `mvn install`.

## Migration Plan

1. Drop `jari_auth` and `jari_user`; remove `jari_auth` from `init-db.sql`. Starting clean avoids every `ddl-auto` hazard below.
2. Add credential fields to `jari-user-service`'s `User`; move the auth security package in; wire `SecurityConfig` and `PasswordEncoder`.
3. Move `AuthController` and `AuthService`; point the gateway's `/auth/**` route at the identity service.
4. Extract the security starter; externalize the secret; add `userId` and `roles` claims.
5. Update the gateway filter: proper status codes, header injection with overwrite semantics.
6. Add identity-header reading and rejection to downstream services.
7. Add `GET /api/users/me`.
8. Delete `jari-auth-service`: module, parent `pom.xml` entry, Dockerfile stage, compose service.
9. Re-run the Phase 0 smoke check, extended with the token-claim and `/me` assertions.

**Rollback:** revert the commits and restore `jari_auth` to `init-db.sql`. Local user data is lost either way; there is none worth keeping.

## Open Questions

- **Roles model.** What roles exist at this stage? A single `USER` role is enough to make the `roles` claim real, but Phase 3 introduces per-project roles (`OWNER`/`MEMBER`), which are a different axis from global roles. Decide whether the token's `roles` claim means global roles only, and leave project roles out of the token entirely — the planning notes lean this way, since project roles in a token make removal ineffective until expiry.
- **Header names.** `X-User-Id` / `X-User-Name` are conventional but unnamespaced. Prefer `X-Jari-User-Id` to make it obvious these are internally injected and must be stripped at the edge?
- **Keep `DataSeeder`?** It makes the destructive re-registration cheap and gives a two-user fixture for the Phase 5 browser demo. Counter-argument: the next change introduces Testcontainers fixtures, which may supersede it.
- **Does the gateway need to strip inbound identity headers on open routes too?** Overwriting covers secured routes, but an open route currently forwards whatever the client sent. Low impact today since no open route reads identity, but it is a cheap belt-and-braces step.
