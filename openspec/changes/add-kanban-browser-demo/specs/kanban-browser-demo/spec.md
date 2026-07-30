## ADDED Requirements

### Requirement: A browser client authenticates against the identity service

The frontend SHALL obtain a token by submitting credentials to the identity service, and SHALL NOT require a developer to supply a token by hand.

#### Scenario: Successful login

- **WHEN** a user submits valid credentials
- **THEN** a token is obtained from the identity service
- **AND** the user is shown the board

#### Scenario: Rejected credentials

- **WHEN** a user submits credentials the identity service rejects
- **THEN** an error is shown
- **AND** no token is stored
- **AND** the user remains on the login screen

#### Scenario: The session survives a page reload

- **WHEN** an authenticated user reloads the page
- **THEN** they remain authenticated without re-entering credentials

#### Scenario: The token is attached to every API request

- **WHEN** the client makes a request to a secured endpoint
- **THEN** the request carries the stored token as a bearer credential

### Requirement: An expired or invalid session returns the user to login

A rejected request SHALL move the user to the login screen rather than leaving a blank or partially rendered page.

#### Scenario: A stored token is no longer accepted

- **WHEN** a request fails because the token is expired or invalid
- **THEN** the stored token is discarded
- **AND** the login screen is shown

#### Scenario: An unauthenticated visitor cannot see the board

- **WHEN** a visitor with no stored token opens the application
- **THEN** the login screen is shown
- **AND** no board request is made

### Requirement: The board renders live backend data through the gateway

The frontend SHALL display a project's Kanban board from data retrieved through the gateway, not from fixtures or mocked responses.

#### Scenario: Columns render from the backend response

- **WHEN** an authenticated user opens the board
- **THEN** the columns returned by the backend are displayed in the order received

#### Scenario: Tasks appear in their status column

- **WHEN** the backend returns tasks assigned to a status
- **THEN** each task appears as a card in the column for that status
- **AND** each card shows the task's key and summary

#### Scenario: A board with no tasks is not an error

- **WHEN** the backend returns columns containing no tasks
- **THEN** the empty columns are displayed
- **AND** no error is shown

#### Scenario: A failed board request is visible

- **WHEN** the board request fails for a reason other than authentication
- **THEN** an error state is shown
- **AND** the page does not render blank

#### Scenario: No mocked data path exists

- **WHEN** the client's data layer is inspected
- **THEN** the board is populated only from the backend endpoint, with no fixture or stub fallback

### Requirement: Browser requests reach the gateway without cross-origin rejection

The development setup SHALL allow the browser client to call the gateway without its requests being rejected as cross-origin.

#### Scenario: An API call from the browser reaches the gateway

- **WHEN** the client running in the browser calls a secured endpoint
- **THEN** the request reaches the gateway and a response is returned to the client

#### Scenario: The mechanism and its limit are documented

- **WHEN** a developer reads the frontend documentation
- **THEN** it states how browser requests reach the gateway in development
- **AND** states that serving the client from a different origin requires additional configuration that does not exist yet

### Requirement: The frontend dependency manifest reflects actual usage

Declared dependencies SHALL match what the source imports, so that a production install produces a working build.

#### Scenario: Runtime dependencies are installable without development dependencies

- **WHEN** dependencies are installed excluding development dependencies
- **THEN** every module the source imports is present
- **AND** the production build succeeds

#### Scenario: No dependency is declared without being used

- **WHEN** the dependency manifest is compared against the source imports
- **THEN** no declared dependency is unimported
