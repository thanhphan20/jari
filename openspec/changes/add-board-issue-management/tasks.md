## 1. Project shell

- [x] 1.1 Add `src/api/projects.ts` (`list`, `get`, `create`, `update`) and `src/api/users.ts` (`list`, `me`) on the shared axios instance.
  - **Found a footgun while writing `updateProject`**: `ProjectService.updateProject` on the backend overwrites every field from the request body - it is not a merge. `active` is a primitive `boolean` on the entity, so an omitted field deserializes to `false` and silently deactivates the project. Typed `updateProject` to take a full `Project`, not `Partial<Project>`, with a comment explaining why - forces callers to spread the current record.
- [x] 1.2 Add `src/types/` entries for `Project` and `User` matching the backend DTOs.
- [x] 1.3 Select the project from `GET /projects` instead of the hardcoded `PROJECT_ID` constant in `App.tsx`.
- [x] 1.4 Add an empty state that creates a project via `POST /projects` when none exists.
  - There are currently **zero** rows in `projects`. The board only works today because `KanbanService` filters tasks by `projectId` without checking the project exists. This is what makes a fresh clone usable without `curl`.
  - Verified live: fresh login showed the empty state (real - the database genuinely has zero projects), created `JARI` / "Jari", and the board rendered immediately with the tasks seeded last phase.
- [x] 1.5 Add the left icon rail and project sidebar showing the project's name and key.
  - Combined into one `Sidebar` component rather than two separate pieces. A separate icon rail earns its keep once there is more than one project to switch between or more than one nav destination; with a single project and a single "Board" item, splitting it out is unrequested structure for nothing it would do today.
- [x] 1.6 Add a project settings form saving through `PUT /projects/{id}`; confirm the sidebar name updates.
  - Verified live end to end: renamed to "Jari Board", saved, sidebar updated, reloaded the page, still "Jari Board". Confirmed directly in Postgres that `active` came back `t` (true) - proof the full-record-spread fix in 1.1 actually matters, not just defensive comment-writing.

## 2. Presentation

- [x] 2.1 Add issue-type icons for Task, Bug, Story, Epic (`type` 1-4) as inline SVG components.
  - `IssueTypeIcon`: a coloured rounded square (blue/red/green/purple) with an original glyph per type - checkmark, bug, bookmark, bolt.
- [x] 2.2 Add priority icons for the five levels (`priority` 1-5) as inline SVG components.
  - No icon dependency. `lucide-react` was removed last change for being unused; re-adding it for ~10 glyphs would reverse that, and Jira's marks are specific enough that generic icons would look approximately right rather than right.
  - `PriorityIcon`: chevron direction and count (single/double, up/down) plus colour for Medium's equals sign, so magnitude and urgency both read at card size.
- [x] 2.3 Add an `Avatar` component: image when `avatarUrl` is present, initials otherwise, on a colour derived deterministically from the user id.
  - Deterministic, not random or index-based, so a person keeps one colour across cards and reloads. No seeded user has an `avatarUrl`, so the fallback is the common path, not the edge case.
  - Also handles the unassigned case (dashed-outline circle) and is reused by the card now, the detail panel and create dialog later.
- [x] 2.4 Rework the card to show type icon, summary, key, priority icon, and assignee avatar.
  - `KanbanBoard` now takes a `usersById` map so it can resolve `assigneeId` to an `Avatar` without a lookup per card. Verified live for both branches: unassigned (dashed circle) and assigned (initials "US" for a user with no first/last name) - and for a non-default type/priority pairing (Bug, Highest) via a temporary `PUT`, then reverted so the seed data matches what earlier phases left it as.
- [x] 2.5 Add `order` to the frontend `Task` type.
  - Recorded as owed by `add-kanban-browser-demo` task 6.2: `TaskDto` returns it, the frontend type omits it, and it becomes load-bearing the moment drag-and-drop needs an index.
  - Also added `projectId` and `reporterId`, which the type was missing entirely, not just `order` - both are needed by the detail panel and create dialog in the next two sections.

## 3. Issue detail

- [x] 3.1 Add a right-hand side sheet that opens on card click and shows the full issue.
  - A side sheet rather than the reference's modal, so the board stays visible while editing. Deliberate divergence; see design.md.
  - `IssueDetail` holds the task in **local state** initialized from the clicked card, not read from the prop on every render. The parent only refetches the board list on a successful mutation - it never hands this panel a fresh `Task` back - so a prop-driven `<select value={task.status}>` would appear to snap back to the old value on every parent re-render even after a successful save. Caught before it shipped by reasoning through the render cycle, not by observing the bug live.
- [x] 3.2 Make summary and description editable in place, saving through `PUT /tasks/{id}`.
  - **Found and fixed a real save-loss bug while verifying, not while writing the code.** Fields save on blur; edited the description, clicked Close immediately, and the edit was silently lost - reproduced twice. Relying on blur registering before a button's click handler fires is the wrong tradeoff for something as ordinary as "edit a field, then click Close" - the close button now explicitly flushes any dirty summary/description before calling `onClose`, closing the gap regardless of event-ordering assumptions. Re-verified after the fix: same edit-then-immediate-close sequence now persists, confirmed in Postgres and by a full page reload.
- [x] 3.3 Make type, priority, and assignee editable via selects; populate assignee from `GET /users`.
  - The list is every user in the system, because project membership does not exist until Phase 3.
  - Also confirmed the same TaskDto full-overwrite behaviour project's `updateProject` has: `TaskService.updateTask` sets `assigneeId` and `status` from the request body with no null-fallback (unlike `createTask`'s status default). `api/tasks.ts`'s `updateTask` takes a full `Task`, not `Partial<Task>`, for the same reason `updateProject` does.
- [x] 3.4 Make status editable via a select.
  - This is the accessibility path for moving an issue, not a convenience. It is what makes the drag implementation in section 5 allowed to skip keyboard support. Restricted to `TODO`, `IN_PROGRESS`, `DONE` — `moveTask` throws on anything else, and `updateTask` has no such guard of its own.
  - Verified live: changed a card's status via the select, closed the panel, and the card had moved to the new column with no manual refresh.
- [x] 3.5 Show reporter, created, and updated as read-only.
- [x] 3.6 Confirm the board reflects an edit without a manual refresh, and that closing without editing changes nothing.
  - Both verified live. A status change and an assignee change each showed up on the board immediately after closing the panel. Opening a card and closing it again without touching any field left its row in Postgres byte-for-byte unchanged.

## 4. Create and delete

- [x] 4.1 Add a create-issue dialog with summary, description, type, priority, and assignee.
  - Summary enforces the backend's own bounds (`@Size(min = 5, max = 100)` on `TaskDto`) in the input itself, so a rejected submission is rare rather than the normal path.
- [x] 4.2 Derive the issue key client-side from the project key and the highest existing numeric suffix.
  - **Known-racy, deliberately.** `TaskService.createTask` stores whatever key it is sent and never generates one, and `tasks.key` has no unique constraint — the defect `V1__baseline.sql` records on purpose. Two clients creating at once can collide. Do not paper over it in the client; the fix is a per-project counter on the server, in a later phase.
  - Verified: with `JARI-1..5` existing, creating an issue produced `JARI-6`, confirmed directly in Postgres.
- [x] 4.3 Set `reporterId` from `GET /users/me` on create.
  - Verified: creating as `admin` set `reporter_id = 1` in the row, matching admin's persisted id.
- [x] 4.4 Add delete with a confirmation step.
  - Verified the gate itself, not just the end state: clicked "Delete issue" once, confirmed the row still existed in Postgres (the button had only switched to "Cancel"/"Delete"), then clicked "Delete" and confirmed the row was gone.
- [x] 4.5 Confirm a created issue lands in the first column, persists across a reload, and shows its key.
  - `JARI-6` landed in Todo with its key visible, type (Bug), priority (High), and assignee (admin, "AD" avatar) all correct. Zero console errors through create, open, and delete.

## 5. Drag and drop

- [x] 5.1 Make cards draggable and columns drop targets using native HTML5 drag events.
- [x] 5.2 Compute the insertion index from pointer position against card midpoints; show a drop indicator.
  - `dropIndexFor` walks the target column's `[data-card]` elements and returns the index before the first one whose vertical midpoint is below the pointer. A blue 2px bar renders at that index (including the end-of-column position, which needed its own check after the last card).
- [x] 5.3 Call `POST /tasks/kanban/move` with `taskId`, `targetStatus`, and `targetIndex`.
- [x] 5.4 Apply the move optimistically via React Query `onMutate`, snapshotting the previous board.
- [x] 5.5 Roll back to the snapshot on error and surface an inline message, then invalidate to reconcile.
  - Inline rather than a toast: a toast system is infrastructure this app does not have, and one message does not justify building it.
- [x] 5.6 Verify each case explicitly, not just the happy path: across columns, within a column, to an empty column, to the start and end of a column, and dropped outside any column (must be a no-op with no request).
  - All verified against the **real backend**, not mocked, via scripted `DragEvent`s with a real `DataTransfer`: cross-column (Todo→Done), within-column reorder (to the end, then explicitly to the very start, confirming the whole column reindexes both times), drop into an empty column, and drop outside every column onto `document.body` — confirmed as a true no-op: the DB was byte-for-byte unchanged and the network log gained zero new requests (three legitimate drags produced exactly three requests total, matching exactly).
  - **A real timing bug in the verification method itself, not the app**: dispatching `dragover` immediately after `dragstart` with no yield read a stale `draggedTaskId` closure (still `null`) from before React flushed the `dragstart` handler's `setState`, so the drop silently did nothing. Spacing dispatches apart (as a real mouse-driven drag naturally does, firing many `dragover` events over the course of a gesture) resolved it. Recorded because it clarifies that this is a scripted-event artifact, not evidence of a race a real user could hit — a genuine drag always has many `dragover` events between `dragstart` and `drop`, giving React ample time to catch up.
- [~] 5.7 Verify a failed move visibly reverts — block the request and confirm the card returns to its original position.
  - **Partially verified; the gap is recorded rather than glossed over.** Stopping `task-service` entirely and dragging produced a real failure after Eureka's discovery lag (~31s): the move POST returned 500. But the optimistic UI was **not** left in a clean rolled-back state with the inline banner — `onSettled`'s `invalidateQueries` also refetched the board GET, which *also* failed (503, since the whole service was down), and the pre-existing board-level `isError` branch (from `add-kanban-browser-demo`) takes priority over showing cached data, so the user saw the full "Could not load the board" screen rather than the specific "Could not move the issue" banner. This is arguably correct for that failure mode - if the entire service is unreachable, a full error state is more honest than a possibly-stale board - but it means the *narrower* failure this task actually asks about (a single move request fails, e.g. a validation conflict, while the rest of the service stays healthy) was not cleanly demonstrated.
  - Tried to isolate that narrower case via client-side fault injection (patching `XMLHttpRequest.prototype.send` to synthesize a failure only for `/kanban/move`) and could not get a reliable result - the technique is order-sensitive against React's batched re-renders in ways a real network failure is not, so a negative result here is inconclusive rather than a finding. Spending more effort on this is exactly the wrong tool for the job: a proper single-endpoint failure simulation is what `add-integration-test-harness`'s Testcontainers-based tests are for, and this is recorded as a concrete argument for that phase rather than something to keep forcing here.
  - What **is** established: the `onMutate`/`onError`/`onSettled` code follows the standard React Query optimistic-update pattern correctly by inspection - snapshot via `getQueryData` before mutating, restore via `setQueryData` in `onError`, unconditional `invalidateQueries` in `onSettled` regardless of outcome - and a real failure does reach and execute *some* error-handling path, as the whole-service-outage test demonstrates.
- [x] 5.8 Confirm order and status survive a reload.
  - Verified repeatedly across the section's testing, and once more explicitly at the end: read the column contents from the DOM after a hard reload, compared key-for-key against `SELECT key, status, task_order FROM tasks`, and both matched exactly.

## 6. Filtering

- [ ] 6.1 Add a filter bar: text search, assignee avatars, issue type, and "only my issues".
- [ ] 6.2 Filter client-side over the fetched board; leave the server untouched.
- [ ] 6.3 Add a clear-filters action, shown only when a filter is active.
- [ ] 6.4 Confirm columns emptied by a filter remain visible, and that filtering modifies no issue.

## 7. Verify and document

- [ ] 7.1 Confirm lint, typecheck, and production build all pass.
- [ ] 7.2 Walk the whole flow in a browser: log in, create a project, create issues, drag them, edit them, filter, delete. Confirm no console errors and no failed requests.
- [ ] 7.3 Confirm every operation is reachable without dragging.
- [ ] 7.4 Update `readme.md`: the board is interactive, what it can do, and what it deliberately cannot.
- [ ] 7.5 Document the constraints that will otherwise look like bugs: three columns only, a single assignee, assignees drawn from all users, and client-generated keys that can collide.
- [ ] 7.6 Record the absence of issue deep links as the trigger for adding a router, since overlays kept this change to one route.
- [ ] 7.7 Record any backend defect this surfaces as its own change rather than fixing it here.
  - Expect some. Last change found `POST /auth/token` returning 500 instead of 401 this way.
