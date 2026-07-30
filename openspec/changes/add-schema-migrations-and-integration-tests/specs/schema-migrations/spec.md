## ADDED Requirements

### Requirement: Each service owns its schema through versioned migrations

Every service with a database SHALL define its schema as an ordered set of versioned migration files within its own module, and SHALL apply them automatically at startup.

#### Scenario: Schema is created from migrations on an empty database

- **WHEN** a service starts against an empty database
- **THEN** its migrations run in version order
- **AND** the resulting schema supports every operation the service exposes

#### Scenario: Migrations are idempotent across restarts

- **WHEN** a service that has already migrated is restarted
- **THEN** no migration is re-applied
- **AND** startup succeeds without schema modification

#### Scenario: No service migrates another service's schema

- **WHEN** the migration files of any service are inspected
- **THEN** they reference only tables belonging to that service's own database

#### Scenario: Migration history is recorded

- **WHEN** migrations have been applied
- **THEN** the database records which versions were applied, in what order, and whether each succeeded

### Requirement: Schema drift fails startup

A service SHALL validate that its entity mappings match the migrated schema at startup, and SHALL refuse to start when they do not. A service SHALL NOT alter the schema to resolve a mismatch.

#### Scenario: Entity added without a migration

- **WHEN** an entity gains a field with no corresponding migration
- **AND** the service is started
- **THEN** startup fails with an error identifying the mismatch
- **AND** the database schema is left unmodified

#### Scenario: Matching schema starts cleanly

- **WHEN** entities and migrated schema agree
- **THEN** the service starts successfully

#### Scenario: Schema is never inferred from entities

- **WHEN** any service's persistence configuration is inspected
- **THEN** no service is configured to create or update schema from entity definitions

### Requirement: The baseline migration reflects entities, not accumulated drift

The initial migration for each service SHALL be derived from a clean database, so it describes the schema the entities define rather than the residue of prior automatic schema updates.

#### Scenario: Baseline verified against a clean database

- **WHEN** the baseline migration is applied to an empty database
- **AND** the service is then started with validation enabled
- **THEN** startup succeeds with no reported mismatch

#### Scenario: Baseline is reviewable

- **WHEN** a developer reads a service's baseline migration
- **THEN** the tables, columns, types, nullability, and constraints are explicit in the file rather than implied by entity annotations

### Requirement: Migrations are reviewable and forward-only

Migration files SHALL be immutable once applied outside a developer's own machine, and schema changes SHALL be expressed as new migrations rather than edits to existing ones.

#### Scenario: A schema change adds a new migration

- **WHEN** a developer needs to change a table
- **THEN** they add a new versioned migration
- **AND** they do not modify an already-applied migration file

#### Scenario: An altered applied migration is detected

- **WHEN** an already-applied migration file is modified and the service restarts
- **THEN** startup fails rather than silently proceeding with a divergent schema
