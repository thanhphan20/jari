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

- [ ] 2.1 Add issue-type icons for Task, Bug, Story, Epic (`type` 1-4) as inline SVG components.
- [ ] 2.2 Add priority icons for the five levels (`priority` 1-5) as inline SVG components.
  - No icon dependency. `lucide-react` was removed last change for being unused; re-adding it for ~10 glyphs would reverse that, and Jira's marks are specific enough that generic icons would look approximately right rather than right.
- [ ] 2.3 Add an `Avatar` component: image when `avatarUrl` is present, initials otherwise, on a colour derived deterministically from the user id.
  - Deterministic, not random or index-based, so a person keeps one colour across cards and reloads. No seeded user has an `avatarUrl`, so the fallback is the common path, not the edge case.
- [ ] 2.4 Rework the card to show type icon, summary, key, priority icon, and assignee avatar.
- [ ] 2.5 Add `order` to the frontend `Task` type.
  - Recorded as owed by `add-kanban-browser-demo` task 6.2: `TaskDto` returns it, the frontend type omits it, and it becomes load-bearing the moment drag-and-drop needs an index.

## 3. Issue detail

- [ ] 3.1 Add a right-hand side sheet that opens on card click and shows the full issue.
  - A side sheet rather than the reference's modal, so the board stays visible while editing. Deliberate divergence; see design.md.
- [ ] 3.2 Make summary and description editable in place, saving through `PUT /tasks/{id}`.
- [ ] 3.3 Make type, priority, and assignee editable via selects; populate assignee from `GET /users`.
  - The list is every user in the system, because project membership does not exist until Phase 3.
- [ ] 3.4 Make status editable via a select.
  - This is the accessibility path for moving an issue, not a convenience. It is what makes the drag implementation in section 5 allowed to skip keyboard support. Restrict options to `TODO`, `IN_PROGRESS`, `DONE` — `moveTask` throws on anything else.
- [ ] 3.5 Show reporter, created, and updated as read-only.
- [ ] 3.6 Confirm the board reflects an edit without a manual refresh, and that closing without editing changes nothing.

## 4. Create and delete

- [ ] 4.1 Add a create-issue dialog with summary, description, type, priority, and assignee.
- [ ] 4.2 Derive the issue key client-side from the project key and the highest existing numeric suffix.
  - **Known-racy, deliberately.** `TaskService.createTask` stores whatever key it is sent and never generates one, and `tasks.key` has no unique constraint — the defect `V1__baseline.sql` records on purpose. Two clients creating at once can collide. Do not paper over it in the client; the fix is a per-project counter on the server, in a later phase.
- [ ] 4.3 Set `reporterId` from `GET /users/me` on create.
- [ ] 4.4 Add delete with a confirmation step.
- [ ] 4.5 Confirm a created issue lands in the first column, persists across a reload, and shows its key.

## 5. Drag and drop

- [ ] 5.1 Make cards draggable and columns drop targets using native HTML5 drag events.
- [ ] 5.2 Compute the insertion index from pointer position against card midpoints; show a drop indicator.
- [ ] 5.3 Call `POST /tasks/kanban/move` with `taskId`, `targetStatus`, and `targetIndex`.
- [ ] 5.4 Apply the move optimistically via React Query `onMutate`, snapshotting the previous board.
- [ ] 5.5 Roll back to the snapshot on error and surface an inline message, then invalidate to reconcile.
  - Inline rather than a toast: a toast system is infrastructure this app does not have, and one message does not justify building it.
- [ ] 5.6 Verify each case explicitly, not just the happy path: across columns, within a column, to an empty column, to the start and end of a column, and dropped outside any column (must be a no-op with no request).
- [ ] 5.7 Verify a failed move visibly reverts — block the request and confirm the card returns to its original position.
- [ ] 5.8 Confirm order and status survive a reload.

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
