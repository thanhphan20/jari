## Why

No service in Jari can determine who is making a request, which makes the core product behavior unimplementable rather than merely insecure.

Three defects combine to cause this:

- The issued JWT carries only `sub: <username>`. `JwtUtils.generateToken` builds an empty claims map, so there is no user identifier in the token.
- `jari-auth-service` and `jari-user-service` each own a `users` table in a separate database, each with its own `GenerationType.IDENTITY` sequence. `UserRegistrationConsumer` builds a fresh row rather than preserving the originating id, so auth user 4 and user-service user 7 are the same person and nothing records that.
- The gateway validates the token's signature and then forwards no identity at all.

The consequence: `Task.assigneeId` and `Task.reporterId` are `Long` columns that no service can resolve from a request. "Assign a task to a user" cannot be built. Separately, every downstream endpoint is effectively public to any authenticated caller — `GET /api/tasks` returns every task in the system regardless of who asks.

Two related defects fall out of the same design: `AuthService.saveUser` publishes the entire `User` entity, including the bcrypt password hash, to RabbitMQ, and `UserRegistrationConsumer` writes that hash into a second database. And the JWT signing secret is a hardcoded constant in `jari-common`, a module every service depends on.

## What Changes

- **BREAKING** — `jari-auth-service` is removed. `jari-user-service` becomes the single identity service, owning credentials and profile in one table in `jari_user`. The `jari_auth` database is dropped.
- **BREAKING** — the `user.registration` RabbitMQ flow is deleted along with its exchange, queue, and consumer. It exists only to synchronize the two user tables, which no longer exist separately. This removes credential material from the message broker entirely.
- Issued JWTs gain a stable `userId` claim alongside the existing subject, plus `roles`.
- The gateway, after validating a token, injects the authenticated identity into the downstream request as trusted headers. Downstream services read identity from those headers and reject requests that lack them.
- A `GET /api/users/me` endpoint resolves the caller to their own user record — the check that proves identity now flows end-to-end.
- The JWT signing secret moves out of source into configuration, supplied per environment.
- `jari-common` is reduced to genuinely stable shared types; JWT handling moves into a dedicated security starter so that a DTO change no longer forces a lockstep rebuild of all six services.
- The gateway's `AuthenticationFilter` returns proper HTTP status codes instead of throwing raw `RuntimeException`.

## Capabilities

### New Capabilities

- `identity`: One service owns user credentials and profile. Registration, authentication, token issuance with a stable user identifier, and self-lookup.
- `gateway-identity-propagation`: The gateway is the single point of token validation and injects authenticated identity into downstream requests; downstream services trust those headers and only those.

### Modified Capabilities

None. `local-stack-boot` is affected operationally — one fewer service, one fewer database — but its requirements are stated in terms of "all application services" and remain true.

## Impact

- **Removed:** `jari-auth-service` module, its Dockerfile stage, its compose service, the `jari_auth` database, `RabbitConfig` in both auth and user services, `UserRegistrationConsumer`.
- **Moved into `jari-user-service`:** `AuthController`, `AuthService`, `SecurityConfig`, `CustomUserDetails`, `CustomUserDetailsService`, `DataSeeder`, `AuthRequest`.
- **Modified:** `User` entity in `jari-user-service` gains the credential fields; `JwtUtils` gains claims and loses its hardcoded secret; gateway `AuthenticationFilter` and `RouterValidator`; `jari-gateway` routes (`/auth/**` now targets the identity service); `docker-compose.yml`; `Dockerfile`; parent `pom.xml`.
- **New:** security starter module; `GET /api/users/me`.
- **Schema:** the identity table gains credential columns. No migration tooling exists yet (`ddl-auto: update` is still in force), so this change is destructive to existing local data — acceptable, as there is no data worth preserving. Migrations arrive in the next change.
- **Downstream services are not yet enforcing authorization.** They will read identity headers, but project-scoped access control is Phase 3. This change makes identity *available*, not yet *enforced*.
