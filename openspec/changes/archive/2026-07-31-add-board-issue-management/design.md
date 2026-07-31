## Context

After `add-kanban-browser-demo`, `jari-frontend/` logs in and renders a read-only board: `App` gates on auth, React Query fetches `GET /api/tasks/kanban/1`, and `KanbanBoard` paints three columns of cards. Interaction stops there.

What the backend already offers and the UI does not use:

| Endpoint | Use here |
|---|---|
| `POST /tasks/kanban/move` | drag and drop; reindexes the whole target column, so orders cannot collide |
| `GET`/`PUT`/`DELETE /tasks/{id}` | issue detail, inline edit, delete |
| `POST /tasks` | create issue — **requires a client-supplied `key`** |
| `GET /users` | assignee picker and avatars |
| `GET`/`PUT /projects/{id}`, `POST /projects` | project chrome, settings, first-run creation |

Three facts from reading the backend that constrain the design, all verified rather than assumed:

- `KanbanService.STANDARD_COLUMNS` is exactly `TODO`, `IN_PROGRESS`, `DONE`, and `moveTask` throws `IllegalArgumentException` on anything else. Three columns, not four.
- `TaskService.createTask` does not generate keys. It stores `taskDto.getKey()` verbatim.
- The `projects` table currently holds **zero rows**. The board works anyway because `KanbanService.getBoardByProjectId` only filters tasks by `projectId` and never checks the project exists.

## Goals / Non-Goals

**Goals:**

- Every issue operation the backend supports is reachable from the UI.
- The board is readable at a glance: type, priority, and assignee legible without opening a card.
- Changes feel immediate, and a failed write visibly reverts rather than silently diverging from the server.
- Everything achievable by dragging is also achievable without dragging.

**Non-Goals:**

- No backend changes of any kind.
- No comments, time tracking, multiple assignees, or project membership — all need schema.
- No client-side routing, so no deep links to issues.
- No fourth column.
- No frontend tests. The harness phase covers testing and does not extend to the browser.

## Decisions

### Native HTML5 drag and drop, no library

`dragstart` / `dragover` / `drop` handlers on cards and columns, computing the insertion index from pointer position against card midpoints.

*Why:* it is the platform feature for exactly this, and it costs no dependency. The obvious alternatives are both worse buys here: `react-beautiful-dnd` is unmaintained, and `@dnd-kit` is a real dependency for behaviour a few dozen lines of handlers already give us.

*The ceiling, named rather than discovered later:* HTML5 drag events do not fire on touch devices and are not keyboard-operable. So the board is not draggable on a phone and not draggable by keyboard. The upgrade path is `@dnd-kit`, and the trigger is wanting either of those things.

*Why that ceiling is acceptable rather than an accessibility failure:* dragging is not the only way to move an issue. Status is an ordinary select in the issue detail panel, so every move is reachable by keyboard through a normal form control. This is a deliberate pairing — the accessible path is a feature, not a consolation — and it is why the drag implementation is allowed to stay small.

### Optimistic moves with rollback

The dragged card moves in the cache immediately via React Query's `onMutate`, with the previous board snapshotted and restored `onError`, then `invalidateQueries` to reconcile.

*Why:* a drag that visibly snaps back before settling reads as broken. This is the one place where the extra code is justified by how wrong the alternative feels.

*Trade-off:* the client briefly holds a state the server has not confirmed. Bounded by rolling back on error and always reconciling from the server afterwards, so the client cannot silently diverge.

### Overlays, not routes

Issue detail, create, search, and settings are all overlays over the board. `react-router-dom` stays uninstalled.

*Why:* the previous change removed the router and set the trigger at "the second screen". Overlays are not a second screen, so the trigger has not fired.

*What this costs, stated plainly:* no URL identifies an issue, so an issue cannot be linked or bookmarked, and browser back does not close a panel. For an issue tracker that is a real loss, and it is the strongest candidate for the next frontend change. Recorded as the explicit trigger for adding the router rather than dismissed.

### Hand-rolled SVG icons, no icon dependency

Around ten small inline SVG components for the four issue types and five priority levels.

*Why:* `lucide-react` was removed last change for being installed and unimported; re-adding it now would reverse that for roughly ten glyphs. Jira's type and priority marks are also specific enough (a coloured rounded square with a glyph) that generic icons would look approximately right rather than right, and hand-rolled SVG gives exact control for less code than the dependency.

### Avatars fall back to initials

No seeded user has an `avatarUrl`, so the common case is the fallback: initials on a colour derived deterministically from the user id.

*Why deterministic:* the same person is the same colour on every card and across reloads, which is what makes an avatar scannable. A random or index-derived colour would reshuffle as the board changes.

### Filtering is client-side

Text, assignee, type, and "only my issues" filter the already-fetched board in memory.

*Why:* the board arrives as one payload of every issue in the project, so filtering it server-side would mean more requests for less responsiveness. There is no endpoint for it either.

*Ceiling:* this stops being right when a project has enough issues that fetching them all is the problem, at which point filtering is the least of it. Not a concern at demo scale.

### Client-generated issue keys, with the raciness admitted

The create dialog derives a key from the project key and the highest existing numeric suffix on the board, e.g. `JARI-6`.

*Why it has to be here:* `createTask` stores whatever key it is given and never generates one, so something must. The client is the only thing that can, today.

*Why it is wrong and accepted anyway:* the derivation reads the board the client happens to hold, so two clients creating at the same moment produce the same key — and `tasks.key` has no unique constraint to stop them. `V1__baseline.sql` records that missing constraint deliberately as a known defect belonging to a later phase. This change inherits it rather than working around it, because the correct fix is a per-project counter on the server, and faking uniqueness in the client would hide the defect instead of leaving it visible.

### First run creates a project through the existing endpoint

The app fetches `GET /projects` and uses the first. If none exists, it shows an empty state offering to create one via `POST /projects`.

*Why:* there are zero projects today, and the board only works because `KanbanService` never checks. Hardcoding `PROJECT_ID = 1` was honest for a read-only demo but becomes a lie once the sidebar shows a project name. Using the endpoint that already exists removes the hardcoded constant and makes a fresh clone usable without `curl`.

*Why not seed it in the backend:* that would be a backend change, which this change does not make.

## Risks / Trade-offs

- **Drag and drop is the highest-risk part.** Index computation off by one, drops on the wrong column, drops outside any column. Mitigated by testing each case explicitly rather than only the happy path, and by the optimistic rollback making a failed write visible.
- **Optimistic updates can mask a failing backend** — the UI shows success for a moment regardless. Rollback plus reconcile bounds it, but a reviewer should know the UI is briefly ahead of the server by design.
- **Client-generated keys can collide**, and nothing in the database will stop them. Accepted and documented above; it is a pre-existing backend defect surfaced, not created, by this change.
- **The assignee list is every user in the system**, because project membership does not exist. Fine at two seeded users; wrong in principle, and Phase 3's job.
- **No touch or keyboard dragging.** Bounded by status being editable in the detail panel, so no operation is mouse-only.
- **More surface, no tests.** This change roughly triples the frontend, and the test harness is still deferred. Verification is manual and browser-based, as it was last phase. Worth stating because the ratio of untested code to tested code gets worse here.

## Migration Plan

1. Project shell first: fetch or create a project, render the rail and sidebar, replace the hardcoded `PROJECT_ID`. Everything else hangs off a real project.
2. Presentation: type and priority icons, avatars, richer cards. Cheap, and makes every later step easier to eyeball.
3. Issue detail panel, read-only, then inline editing field by field.
4. Create and delete.
5. Filter bar.
6. Drag and drop last, with optimistic update and rollback — the riskiest piece, on top of a board already proven to work.
7. Verify each operation in the browser and confirm persistence survives a reload.

**Rollback:** revert the commits. Nothing outside `jari-frontend/` changes.

## Open Questions

- **Should the detail panel be a modal or a side sheet?** Recommendation: a right-hand side sheet. It keeps the board visible while editing, which suits a board-first app, and it avoids the full-screen modal's awkwardness at wide viewports. The reference uses a modal; this is a deliberate divergence, not an oversight.
- **Should a failed optimistic move show a toast, or revert silently?** Recommendation: revert and show an inline message on the board. A toast system is infrastructure this app does not have yet, and adding one for a single message is not worth it.
