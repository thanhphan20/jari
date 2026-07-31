## ADDED Requirements

### Requirement: Integration tests run against real infrastructure

Integration tests SHALL exercise services against real Postgres and, where the service uses it, real RabbitMQ, provisioned automatically for the test run.

#### Scenario: Tests provision their own database

- **WHEN** a developer runs the integration test suite on a machine with no manually started stack
- **THEN** the required database is provisioned automatically
- **AND** the tests execute against it

#### Scenario: Tests do not use an in-memory substitute

- **WHEN** the test configuration is inspected
- **THEN** no integration test is configured against an in-memory or embedded database engine

#### Scenario: Broker-dependent behavior is tested against a real broker

- **WHEN** a service publishes or consumes messages
- **THEN** its integration tests exercise that behavior against a real broker instance rather than a mock

#### Scenario: Test infrastructure is discarded after the run

- **WHEN** an integration test run completes
- **THEN** the provisioned infrastructure is removed
- **AND** no state from that run affects a subsequent run

#### Scenario: Provisioned infrastructure does not collide with a developer's own services

- **WHEN** the host already runs a database on the conventional port
- **THEN** the provisioned container is still reached correctly by the suite
- **AND** the tests do not connect to the host's own instance

### Requirement: Tests verify migrations rather than bypassing them

Integration tests SHALL obtain their schema by running the service's own migrations, so a broken migration fails the test suite. This is the automated verification that `add-schema-migrations` deliberately deferred: before this capability exists, a broken migration is caught only by a developer starting the service.

#### Scenario: Test schema comes from migrations

- **WHEN** an integration test starts
- **THEN** the schema is created by applying the service's migration files
- **AND** the schema is not generated from entity definitions

#### Scenario: A broken migration fails the suite

- **WHEN** a migration file contains invalid SQL
- **THEN** the integration test suite fails
- **AND** the failure identifies the migration at fault

#### Scenario: Entity drift from the migrated schema fails the suite

- **WHEN** an entity declares a field that no migration provides a column for
- **THEN** the integration test suite fails rather than passing against a generated schema

### Requirement: The identity flow is covered end-to-end within its service

The identity service's registration, authentication, and self-lookup flow SHALL be verified by an automated test rather than by manual inspection.

#### Scenario: Registration persists a user

- **WHEN** the test registers a user through the service's HTTP surface
- **THEN** a corresponding row exists in the database
- **AND** the stored password is a hash, not the submitted plaintext

#### Scenario: Authentication issues a token carrying the user's identifier

- **WHEN** the test authenticates as the registered user
- **THEN** a token is returned
- **AND** decoding it yields a `userId` claim equal to that user's persisted primary key

#### Scenario: Invalid credentials are rejected

- **WHEN** the test authenticates with an incorrect password
- **THEN** the response status is 401 and no token is issued

#### Scenario: Self-lookup resolves the authenticated caller

- **WHEN** the test calls the self-lookup endpoint carrying the authenticated identity
- **THEN** the response describes the registered user

#### Scenario: Self-lookup rejects an unauthenticated caller

- **WHEN** the test calls the self-lookup endpoint with no authenticated identity
- **THEN** the response status is 401

### Requirement: The suite runs from a single command

The full test suite SHALL be runnable with one command from a clean checkout, and unit tests SHALL remain runnable without a container runtime.

#### Scenario: One command runs everything

- **WHEN** a developer runs the project's verify command from a clean checkout
- **THEN** unit and integration tests both execute
- **AND** the command's exit status reflects whether all tests passed

#### Scenario: Unit tests do not require a container runtime

- **WHEN** a developer runs only the unit test phase on a machine with no container runtime available
- **THEN** the unit tests execute and report results

#### Scenario: A missing container runtime is reported clearly

- **WHEN** the integration test suite is run with no container runtime available
- **THEN** the failure message identifies the missing runtime as the cause
- **AND** does not present as an unrelated connection or timeout error

#### Scenario: The suite does not depend on the developer's machine configuration

- **WHEN** the suite is run on a machine whose locale or timezone differs from the author's
- **THEN** the tests produce the same result

### Requirement: Cross-service coverage has a decided approach

Because per-service integration tests provision infrastructure but not sibling services, the approach for verifying flows that span multiple services SHALL be decided and documented before those flows are built.

#### Scenario: The approach is documented

- **WHEN** a developer looks for how cross-service flows will be tested
- **THEN** the chosen approach is documented, including what it covers and what it does not

#### Scenario: The project's definition of done maps onto a named test

- **WHEN** the cross-service definition of done is reviewed
- **THEN** it is traceable to a specific planned test rather than to manual verification

#### Scenario: Each tier's responsibility is recorded

- **WHEN** a developer needs to know where a given behavior should be tested
- **THEN** the documentation states that failure-injection and circuit-breaker behavior belong to the per-service tier
- **AND** that the cross-service definition of done belongs to the single end-to-end test

#### Scenario: The deferral of the end-to-end test is explicit

- **WHEN** a developer asks why the end-to-end test does not exist yet
- **THEN** the documentation records that it is deferred until the behavior it asserts is built
- **AND** explains that building it earlier would mean asserting behavior that does not exist
