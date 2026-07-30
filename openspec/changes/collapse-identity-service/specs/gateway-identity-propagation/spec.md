## ADDED Requirements

### Requirement: The gateway is the single point of token validation

The gateway SHALL validate the token on every request to a secured route, and SHALL reject the request before it reaches any downstream service if validation fails.

#### Scenario: Valid token is forwarded

- **WHEN** a request to a secured route carries a validly signed, unexpired token
- **THEN** the gateway forwards the request to the target service

#### Scenario: Missing token is rejected at the gateway

- **WHEN** a request to a secured route carries no `Authorization` header
- **THEN** the gateway responds with HTTP 401
- **AND** no downstream service receives the request

#### Scenario: Tampered token is rejected at the gateway

- **WHEN** a request carries a token whose signature does not verify
- **THEN** the gateway responds with HTTP 401
- **AND** no downstream service receives the request

#### Scenario: Expired token is rejected at the gateway

- **WHEN** a request carries a correctly signed token whose expiry has passed
- **THEN** the gateway responds with HTTP 401

#### Scenario: Rejections do not leak internals

- **WHEN** the gateway rejects a request for any of the above reasons
- **THEN** the response body contains no stack trace and no exception class name

#### Scenario: Open routes bypass validation

- **WHEN** a request targets a route designated as open, such as registration or token issuance
- **THEN** the gateway forwards it without requiring an `Authorization` header

### Requirement: The gateway injects authenticated identity into downstream requests

After validating a token, the gateway SHALL add the authenticated user's identifier and username to the forwarded request as headers, so downstream services need neither to parse the token nor to look the user up by name.

#### Scenario: Identity headers are present downstream

- **WHEN** the gateway forwards a validated request
- **THEN** the forwarded request carries a user-identifier header whose value is the token's `userId` claim
- **AND** carries a username header whose value is the token's subject

#### Scenario: Client-supplied identity headers are overwritten

- **WHEN** a client sends a request that already contains the identity headers with arbitrary values
- **THEN** the gateway replaces those values with the ones derived from the validated token
- **AND** the client-supplied values do not reach any downstream service

### Requirement: Downstream services derive identity only from propagated headers

A downstream service SHALL determine the calling user from the gateway-injected identity headers, and SHALL reject any request that does not carry them.

#### Scenario: Request with identity headers is served

- **WHEN** a downstream service receives a request carrying valid identity headers
- **THEN** it processes the request as that user

#### Scenario: Request without identity headers is rejected

- **WHEN** a downstream service receives a request with no identity headers
- **THEN** it responds with HTTP 401 and does not process the request

#### Scenario: Direct access bypassing the gateway is rejected

- **WHEN** a client calls a downstream service's port directly, bypassing the gateway
- **THEN** the request is rejected because it carries no gateway-injected identity

### Requirement: Trusted headers are only trustworthy because the gateway is the sole entry point

The deployment SHALL ensure downstream services are not reachable from outside the internal network, since they accept identity headers without independent verification.

#### Scenario: The trust assumption is documented

- **WHEN** a developer reads the identity propagation documentation
- **THEN** it states explicitly that downstream services trust identity headers unconditionally
- **AND** states that exposing a downstream port externally would allow identity spoofing

#### Scenario: Externally published ports are recorded as a known local-only exposure

- **WHEN** the compose configuration publishes downstream service ports to the host
- **THEN** that exposure is documented as a local development convenience that must not be carried into a shared environment
