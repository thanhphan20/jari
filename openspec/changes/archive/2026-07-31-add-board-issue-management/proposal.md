## Why

The board renders and that is all it does. You can log in and look at cards; you cannot move one, open one, change one, create one, or find one. The previous change deliberately shipped it read-only so that a rendering bug and an interaction bug could not arrive together — that has served its purpose, and the board now provably renders.

The gap is wider than it looks, because **the backend already supports almost all of this**. `POST /tasks/kanban/move` exists and correctly reindexes the target column so two issues can never share an order. `GET`/`PUT`/`DELETE /tasks/{id}` exist. `GET /users` exists. `GET`/`PUT /projects/{id}` exist. None of it is reachable from the UI. Work that has already shipped is sitting unused behind a missing frontend, which is the cheapest kind of value left in the project.

The reference point is [oldboyxx/jira_clone](https://github.com/oldboyxx/jira_clone), an MIT-licensed React Jira clone whose `client/src/Project/` decomposes into Board, IssueCreate, IssueSearch, NavbarLeft, ProjectSettings, and Sidebar. That is a good description of the core of a Jira board. This change reimplements that feature set against Jari's own API — the two backends have nothing in common (Java microservices versus a Node monolith), so this is a reimplementation informed by the reference, not a port of it.

## What Changes

- **Drag and drop.** Issues move between columns and reorder within a column, persisted through `POST /tasks/kanban/move`. Optimistic, with rollback if the request fails.
- **Issue detail panel.** Opening a card shows its full record and allows inline editing of summary, description, status, type, priority, and assignee, saved through `PUT /tasks/{id}`.
- **Create and delete issues.** A create dialog and a delete action with confirmation.
- **A visual language for type and priority.** Task / Bug / Story / Epic and the five priority levels each get a distinct icon and colour, so a card is readable at a glance rather than being a wall of text.
- **People.** Assignee and reporter render as avatars resolved from `GET /users`, with initials as the fallback since no seeded user has an `avatarUrl`.
- **Search and filtering.** Filter the board by text, by assignee, by type, and by "only my issues", resolved client-side against the already-fetched board.
- **Project chrome.** A left icon rail and a project sidebar showing the real project name and key from `GET /projects/{id}`, plus a settings form that saves through `PUT /projects/{id}`.
- **A first-run path.** There are currently zero rows in the `projects` table. When no project exists the app offers to create one through `POST /projects`, so a fresh clone is usable without `curl`.

## Capabilities

### New Capabilities

- `board-issue-management`: A user can manage issues on a project board — move, open, edit, create, delete, and filter them — with changes persisted through the API.

### Modified Capabilities

None. `kanban-browser-demo` established login and read-only rendering; this builds on it without changing what it requires.

## Impact

- **New:** an issue detail panel, create-issue dialog, filter bar, project sidebar, icon rail, avatar and icon components, a project API module, a users API module, and drag-and-drop handling on the board.
- **Modified:** `KanbanBoard` gains drag targets and click handling; `App` gains the project shell; `src/types/kanban.ts` gains the `order` field it is currently missing.
- **No backend changes.** No service, entity, migration, or endpoint is touched. If a defect surfaces, it gets its own change, as it did last phase.

## Out of Scope

- **Comments.** The most visible missing Jira feature, and the one with no backend at all — no entity, no table, no endpoint. Adding it is a schema change and belongs with a backend phase.
- **Time tracking** (estimate, time spent, time remaining). No columns exist for it.
- **Multiple assignees.** `Task` has a single `assigneeId`; the reference uses an array. Changing that is a schema change.
- **Project members.** The assignee list is every user in the system, because project membership does not exist until Phase 3. Filtering assignees to members is that phase's work.
- **Client-side routing.** Issue detail, create, search, and settings are all overlays on one screen, so there is still exactly one route. This means **no shareable link to an individual issue**, which is a genuine loss for an issue tracker and the explicit trigger for adding a router.
- **A fourth column.** `KanbanService.STANDARD_COLUMNS` is `TODO`, `IN_PROGRESS`, `DONE`, and `moveTask` rejects anything else. The reference has four. Adding one is a backend change.

## Known Constraint Carried Forward

`POST /tasks` does not generate issue keys — `TaskService.createTask` stores whatever `key` the client sends — and `tasks.key` has no unique constraint, which is the defect the migration baseline recorded deliberately. So the create dialog has to invent a key client-side, and two clients creating an issue simultaneously can produce the same one. This is a real limitation being accepted rather than hidden: the per-project key counter that fixes it properly is backend work in a later phase, and it is the same defect `add-schema-migrations` documented in `V1__baseline.sql`.
