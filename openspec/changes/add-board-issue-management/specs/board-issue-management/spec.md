## ADDED Requirements

### Requirement: Issues can be moved between and within columns by dragging

A user SHALL be able to drag an issue to another column or to a different position within its column, and the change SHALL be persisted.

#### Scenario: Dragging to another column changes the issue's status

- **WHEN** a user drags an issue onto a different column
- **THEN** the issue appears in that column
- **AND** the change is persisted, so it survives a reload

#### Scenario: Dragging within a column reorders it

- **WHEN** a user drags an issue to a different position in its own column
- **THEN** the issue occupies that position
- **AND** the new order survives a reload

#### Scenario: The board updates before the server confirms

- **WHEN** a user completes a drag
- **THEN** the board shows the new position immediately, without waiting for the request

#### Scenario: A failed move reverts

- **WHEN** a move request fails
- **THEN** the issue returns to the position it held before the drag
- **AND** the failure is reported to the user

#### Scenario: A drop outside any column changes nothing

- **WHEN** a drag ends outside every column
- **THEN** the board is unchanged
- **AND** no request is sent

### Requirement: Every drag operation has a non-drag equivalent

Moving an issue SHALL NOT require a pointer, so the board remains operable by keyboard.

#### Scenario: Status can be changed without dragging

- **WHEN** a user opens an issue and changes its status through a form control
- **THEN** the issue moves to the corresponding column
- **AND** the result matches what dragging it there would have produced

### Requirement: An issue can be opened and edited

Selecting an issue SHALL reveal its full detail, and its editable fields SHALL be changeable in place.

#### Scenario: Opening an issue shows its detail

- **WHEN** a user selects an issue on the board
- **THEN** its key, summary, description, type, priority, status, assignee, and reporter are shown

#### Scenario: An edit persists

- **WHEN** a user changes an editable field and the change is saved
- **THEN** the new value is persisted
- **AND** the board reflects it without a manual refresh

#### Scenario: Dismissing the detail view leaves the issue unchanged

- **WHEN** a user closes the detail view without editing
- **THEN** the issue is unchanged

### Requirement: Issues can be created and deleted

A user SHALL be able to add an issue to the board and remove one from it.

#### Scenario: A created issue appears on the board

- **WHEN** a user creates an issue with a summary, type, and priority
- **THEN** it appears in the board's first column
- **AND** it is persisted

#### Scenario: A created issue is given a key

- **WHEN** an issue is created
- **THEN** it is assigned an issue key derived from the project
- **AND** the key is shown on its card

#### Scenario: Deleting an issue requires confirmation

- **WHEN** a user deletes an issue
- **THEN** they are asked to confirm first
- **AND** the issue is removed from the board only after confirming

### Requirement: Type, priority, and people are legible on the card

A card SHALL communicate its type, priority, and assignee without being opened.

#### Scenario: Type and priority are visually distinct

- **WHEN** the board displays issues of different types and priorities
- **THEN** each type is visually distinguishable from the others
- **AND** each priority level is visually distinguishable from the others

#### Scenario: An assigned issue shows its assignee

- **WHEN** an issue has an assignee
- **THEN** the card shows that person

#### Scenario: A person with no avatar image still renders

- **WHEN** a user has no avatar image
- **THEN** a readable fallback is shown in its place
- **AND** the same person is represented consistently across the board

#### Scenario: An unassigned issue is not broken by the absence

- **WHEN** an issue has no assignee
- **THEN** the card renders without an assignee and without error

### Requirement: The board can be filtered

A user SHALL be able to narrow the board to a subset of its issues.

#### Scenario: Filtering by text

- **WHEN** a user enters search text
- **THEN** only issues whose summary matches remain visible

#### Scenario: Filtering by assignee

- **WHEN** a user filters by a person
- **THEN** only issues assigned to that person remain visible

#### Scenario: Filtering to the caller's own issues

- **WHEN** a user restricts the board to their own issues
- **THEN** only issues assigned to the authenticated caller remain visible

#### Scenario: Filters can be cleared

- **WHEN** filters are active
- **THEN** the user can clear them in one action and see the whole board again

#### Scenario: Filtering does not change the data

- **WHEN** any filter is applied
- **THEN** no issue is modified

#### Scenario: Empty columns remain visible while filtering

- **WHEN** a filter excludes every issue in a column
- **THEN** that column is still shown, empty

### Requirement: The board belongs to a visible project

The interface SHALL show which project is being viewed, and its details SHALL be editable.

#### Scenario: The project is identified

- **WHEN** a user views the board
- **THEN** the project's name is shown

#### Scenario: Project details can be edited

- **WHEN** a user changes the project's name or description and saves
- **THEN** the change is persisted
- **AND** the displayed project name updates

#### Scenario: A first run with no project offers to create one

- **WHEN** no project exists
- **THEN** the user is offered a way to create one
- **AND** creating it leads to that project's board

#### Scenario: The project is not hardcoded

- **WHEN** the application selects which project to display
- **THEN** it does so from data returned by the API rather than from a fixed identifier in the source
