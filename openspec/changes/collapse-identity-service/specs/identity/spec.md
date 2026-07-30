## ADDED Requirements

### Requirement: A single service owns user identity

Exactly one service SHALL own user credentials and user profile data, in one table, in one database. No other service SHALL store credential material.

#### Scenario: Registration creates exactly one user record

- **WHEN** a client registers with a username, email, and password
- **THEN** exactly one user row is created, in the identity service's database
- **AND** no other database contains a row representing that user

#### Scenario: No credential material outside the identity service

- **WHEN** the stack is inspected after a registration
- **THEN** no password or password hash is present in any database other than the identity service's
- **AND** no password or password hash has been published to the message broker

#### Scenario: A profile update has a single point of truth

- **WHEN** a user's email is changed
- **THEN** the change is visible immediately on subsequent reads
- **AND** no reconciliation between services is required for the change to take effect

### Requirement: Passwords are never stored or transmitted in recoverable form

The identity service SHALL store passwords only as a salted one-way hash, and SHALL never return password material in any response.

#### Scenario: Password is hashed at rest

- **WHEN** a user registers
- **THEN** the stored password value is a bcrypt hash, not the submitted plaintext

#### Scenario: Password is absent from responses

- **WHEN** any endpoint returns a user representation
- **THEN** the response body contains no password or password hash field

### Requirement: Issued tokens carry a stable user identifier

Tokens SHALL include a `userId` claim holding the identity service's primary key for the authenticated user, so that the gateway can propagate a stable identity downstream via headers without a lookup by name. Downstream services never receive or parse the token itself - they read the gateway-injected identity headers (see the `gateway-identity-propagation` capability).

#### Scenario: Token contains the user's identifier

- **WHEN** a client authenticates successfully
- **THEN** the returned JWT contains a `userId` claim
- **AND** that value equals the primary key of the authenticated user's row

#### Scenario: Identifier is stable across sessions

- **WHEN** the same user authenticates on two separate occasions
- **THEN** the `userId` claim is identical in both tokens

#### Scenario: Token contains the user's roles

- **WHEN** a client authenticates successfully
- **THEN** the returned JWT contains a `roles` claim listing the user's granted roles

### Requirement: Authentication rejects invalid credentials

The identity service SHALL reject authentication attempts with unknown usernames or incorrect passwords, without revealing which of the two was wrong.

#### Scenario: Unknown username

- **WHEN** a client requests a token for a username that does not exist
- **THEN** the response is HTTP 401
- **AND** the response body does not indicate whether the username exists

#### Scenario: Wrong password

- **WHEN** a client requests a token for an existing username with an incorrect password
- **THEN** the response is HTTP 401
- **AND** no token is issued

### Requirement: A caller can resolve their own identity

The system SHALL provide an endpoint that returns the authenticated caller's own user record, derived from the request's authenticated identity rather than from a client-supplied identifier.

#### Scenario: Authenticated self-lookup

- **WHEN** an authenticated client requests its own user record
- **THEN** the response contains the user whose `userId` matches the authenticated identity

#### Scenario: Self-lookup ignores client-supplied identifiers

- **WHEN** an authenticated client requests its own user record while supplying a different user's identifier in the request
- **THEN** the response still describes the authenticated caller, not the supplied identifier

#### Scenario: Unauthenticated self-lookup is rejected

- **WHEN** an unauthenticated client requests the self-lookup endpoint
- **THEN** the response is HTTP 401

### Requirement: The token signing secret is supplied by configuration

The JWT signing secret SHALL be provided through external configuration and SHALL NOT be present as a literal in source code.

#### Scenario: No secret in source

- **WHEN** the repository is searched for the signing secret
- **THEN** no source file contains the secret value

#### Scenario: Startup fails without a configured secret

- **WHEN** a service that signs or validates tokens starts with no signing secret configured
- **THEN** startup fails with a message naming the missing configuration
- **AND** the service does not start with a default or empty secret

## REMOVED Requirements

### Requirement: User registration is replicated to a second service over the message broker

**Reason**: The replication existed solely to keep two `users` tables in two databases in agreement. With one service owning identity there is one table, so there is nothing to replicate. The flow was also publishing bcrypt password hashes through RabbitMQ and persisting them in a second database, and it never preserved the originating primary key — which is the root cause of user identity being unresolvable.

**Migration**: No consumer migration is required; no external client depended on this flow. Delete the `user.exchange` exchange, the `user.registration` queue, the `RabbitConfig` in both services, and `UserRegistrationConsumer`. Existing local data in `jari_auth` and `jari_user` is discarded — drop both databases and re-register users. The `TaskAssigned` flow introduced in Phase 6 becomes the project's event-driven example instead.
