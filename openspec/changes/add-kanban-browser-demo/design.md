## Context

`jari-frontend/` is a Vite 8 + React 19 + TypeScript + Tailwind 4 skeleton. It runs, and it renders one heading.

What already exists, unconnected:

- `src/components/KanbanBoard.tsx` — a pure presentational component taking `board: KanbanBoard | null` and `isLoading: boolean`. It handles loading, null, and empty-column states already.
- `src/api/kanban.ts` — `getKanbanBoard(projectId)` and `moveTask(...)`, both bare axios calls with no credentials.
- `src/types/kanban.ts` — `Task`, `KanbanColumn`, `KanbanBoard`, matching the backend DTOs.

What the backend already provides, verified by reading it:

- `POST /auth/token` returns a raw JWT string (not JSON-wrapped), and is an open route at the gateway.
- `GET /api/tasks/kanban/{projectId}` returns `ResponseDto<KanbanBoardDto>`, which is why the frontend reads `response.data.data`.
- `KanbanService.getBoardByProjectId` always returns three columns — `TODO`, `IN_PROGRESS`, `DONE` — regardless of whether any tasks exist, and does **not** call project-service. Two consequences worth stating: no project row needs to exist for the board to render, and the frontend's "No columns configured" branch is unreachable through this endpoint.
- `DataSeeder` seeds `admin/admin123` and `user/user123`, and no tasks.

## Goals / Non-Goals

**Goals:**

- A developer opens the browser, logs in, and sees a Kanban board populated from the real backend.
- The gateway's identity propagation is exercised by a real browser client rather than a crafted `curl` request.
- The frontend's dependency manifest is honest about what is runtime and what is unused.

**Non-Goals:**

- No backend code changes. If the demo reveals a backend defect, it gets its own change.
- No drag-and-drop, no routing, no project picker, no task creation, no notifications UI.
- No gateway CORS configuration.
- No frontend tests. The integration-test harness is its own phase and does not cover the browser.
- No design system or visual polish. The existing Tailwind classes in `KanbanBoard` are adequate.

## Decisions

### The Vite dev proxy, not gateway CORS

`vite.config.ts` gains a `server.proxy` entry mapping `/api` to `http://localhost:8080`. The browser then makes same-origin requests to the Vite dev server, which forwards them server-side.

*Why:* it makes the CORS problem not exist rather than solving it. Same-origin requests are not preflighted, so the `OPTIONS`-versus-`AuthenticationFilter` deadlock never arises. It is roughly five lines of config against a `CorsWebFilter` bean, an allowed-origins list, and a decision about how the gateway should treat preflight requests to secured routes.

*The deadlock this avoids, stated plainly so nobody "fixes" it by adding CORS later without understanding it:* a CORS preflight is an `OPTIONS` request that deliberately carries no `Authorization` header. `RouterValidator.isSecured` returns true for `/api/**`. `AuthenticationFilter` returns 401 when the header is absent. So a preflight to any secured route is rejected before CORS headers could matter. Adding CORS headers alone does not fix this; the gateway would also need to short-circuit `OPTIONS` before authentication. That is real work, and the proxy makes it unnecessary.

*Trade-off:* this works for the dev server only. A production build served from any other origin needs real CORS, or needs to be served through the gateway as static assets. Recorded as the trigger in the proposal's Out of Scope rather than pre-built.

*Alternative considered:* configure `spring.cloud.gateway.globalcors`. Rejected for now because it requires the `OPTIONS` short-circuit above to be correct, and getting that subtly wrong produces a gateway that authenticates preflights — which fails in the browser while every `curl` test passes. That is an expensive class of bug to own before there is a deployment that needs it.

### `localStorage` for the token, with the trade-off named

The JWT goes in `localStorage` and is attached by an axios request interceptor.

*Why:* it survives a page reload, which matters when the whole point is a demo somebody clicks through. The alternative — token in React state only — logs the user out on every refresh, which reads as a bug during a demo.

*The trade-off, not hidden:* `localStorage` is readable by any script on the origin, so this is vulnerable to XSS in a way an `HttpOnly` cookie is not. Accepted deliberately for a local-only demo. The honest fix is an `HttpOnly; Secure; SameSite` cookie set by the gateway, which means the gateway becomes a session participant rather than a stateless token validator — a real architectural change, and not one to make for a demo. This is the frontend counterpart of the trusted-header trade-off already documented in the README: acceptable locally, must not survive contact with a shared deployment.

### React Query, because it is already installed

The board is fetched with `useQuery` rather than `useEffect` + `useState`.

*Why:* `@tanstack/react-query` is already a dependency, and `KanbanBoard` already accepts an `isLoading` prop that maps onto it directly. Reaching for an already-present dependency beats hand-rolling; hand-rolling would also mean writing the loading and error branches that `useQuery` provides.

*Honest counter-argument:* for exactly one query, `useEffect` is about ten lines and one fewer dependency. The tiebreaker is that the next phase adds drag-and-drop, which needs refetch-or-invalidate after a mutation — precisely what React Query is good at. If drag-and-drop were not coming, `useEffect` would be the right call.

### `projectId` is hardcoded

The board loads project `1`. No picker, no route parameter.

*Why:* a project picker needs a project list endpoint, a second screen, and a selection state, none of which prove anything the hardcoded id does not. It becomes a real question when there is a second project worth switching to.

*Made visible rather than buried:* the id lives in one named constant, not inline in a fetch call, so the point where it becomes dynamic is obvious.

### Seed data by documented command, not by a new seeder

The demo walkthrough includes the `curl` calls that create a few tasks.

*Why:* adding a task seeder means touching task-service, and this change's constraint is no backend changes. It also collides with an open question in the schema-migrations work about whether `DataSeeder` survives at all once tests own their fixtures.

*Revisit when:* re-seeding after `docker compose down -v` becomes annoying enough to notice. Then it belongs in task-service behind a profile, decided together with `DataSeeder`'s fate.

### Delete the unused dependencies rather than leaving them

`react-router-dom` and `lucide-react` are removed; `axios` and `@tanstack/react-query` move from `devDependencies` to `dependencies`.

*Why the move is a real fix, not tidying:* both are runtime imports sitting in `devDependencies`, so `npm ci --omit=dev` produces a build that fails on missing modules. It is latent breakage that only appears the first time somebody builds for something other than local dev.

*Why delete rather than keep for later:* an installed-but-unimported dependency is a standing claim that the project uses something it does not. Both come back in one command when there is a second screen or an actual icon to render.

## Risks / Trade-offs

- **`localStorage` XSS exposure** → Named above. Local-only demo; the cookie-based fix is an architectural change, not a tweak.
- **The proxy hides a real CORS requirement** → A production build will fail in a way local development never shows. Documented as the explicit trigger for adding CORS, in both the proposal and the README.
- **A hardcoded `projectId` looks like an oversight** → Kept in one named constant and stated in the walkthrough so it reads as scoped, not forgotten.
- **The demo depends on the whole stack being up** → Unavoidable; it is the point. The walkthrough points at the existing smoke test for getting there, and a failed board fetch shows an error state rather than a blank screen.
- **`POST /auth/token` returns a bare string, not JSON** → Easy to mis-handle by assuming `response.data.token`. Called out so the client reads `response.data` directly.
- **Deferring drag-and-drop leaves `moveTask` unused** → Deliberate, and the reason is in the proposal: two unproven things landing together makes both harder to diagnose. The backend reindex logic it would exercise is also currently untested.

## Migration Plan

1. Add the Vite dev proxy; confirm a same-origin `/api` request reaches the gateway.
2. Correct the dependency manifest and delete the dead files, so later steps build on an honest tree.
3. Add the shared axios instance with the `Authorization` request interceptor and the 401 response interceptor.
4. Add the login screen and token persistence; confirm a token is obtained and stored.
5. Wrap the app in a React Query provider and mount `KanbanBoard` behind the auth check.
6. Seed a few tasks by `curl`; confirm cards appear in the right columns.
7. Verify the 401 path by clearing or corrupting the stored token.
8. Document the walkthrough, the `localStorage` trade-off, and the CORS trigger.

**Rollback:** revert the commits. Nothing outside `jari-frontend/` and `readme.md` changes, so rollback cannot affect the backend.

## Open Questions

- **Does the login screen belong in this phase, or should the token be pasted in?** Resolved: a real login screen. Pasting a token would leave `POST /auth/token` still verified only by `curl`, which is the gap this change exists to close.
- **Should the board poll for changes?** Deferred. Polling has a real answer once there is a second client or the Phase 6 event flow makes staleness observable; adding it now would be guessing at an interval with nothing to observe.
