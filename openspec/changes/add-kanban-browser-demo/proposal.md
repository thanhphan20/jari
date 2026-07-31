## Why

The frontend cannot talk to the backend at all. `jari-frontend/` builds and serves, but `src/App.tsx` renders a single `<h1>` and nothing else. Every piece needed for a board exists and none of it is connected.

Four things block it, and the first is the one that looks solved and is not:

- **No CORS anywhere**, and adding it naively would not be enough. A browser preflights `OPTIONS /api/tasks/...`, preflight requests never carry `Authorization`, `RouterValidator.isSecured` treats `/api/**` as secured, and `AuthenticationFilter` returns 401 on a missing header. The preflight fails, so the real request is never sent.
- **The frontend sends no credentials.** `src/api/kanban.ts` issues a bare `axios.get`, so the gateway rejects it. There is no login screen and nowhere a token is kept.
- **`KanbanBoard` is orphaned.** The component is written and takes exactly the props it needs, but `main.tsx` → `App.tsx` never imports it.
- **There is no task data.** `DataSeeder` creates two users and no tasks, so a working board would render empty.

This matters beyond the frontend. Two phases have now shipped real behavior — identity propagation, token claims, Flyway-owned schemas — verified entirely by hand with `curl` and a hand-decoded JWT payload. A browser demo is the first artifact that shows the stack works without a developer narrating it, and it exercises the gateway's identity propagation through a real client rather than a crafted request.

## What Changes

- **The Vite dev server proxies `/api` to the gateway**, so the browser makes same-origin requests. This removes the CORS preflight problem without adding CORS configuration or touching the gateway.
- **A login screen** authenticates against `POST /auth/token`, stores the returned JWT, and an axios request interceptor attaches it as `Authorization: Bearer` on every `/api` call.
- **The Kanban board is mounted** and fed by `GET /api/tasks/kanban/{projectId}` through React Query, using the existing `KanbanBoard` component unchanged.
- **A 401 returns the user to the login screen**, so an expired token is an obvious prompt rather than a blank page.
- **Frontend dependencies are corrected**: runtime imports move out of `devDependencies`, and packages with no imports are removed.
- **The demo is documented**: how to log in with the seeded user, and how to create a couple of tasks so the board has cards.

## Capabilities

### New Capabilities

- `kanban-browser-demo`: A browser client authenticates against the identity service and renders a project's Kanban board from live backend data, through the gateway.

### Modified Capabilities

None. No backend contract changes and no backend code changes. The gateway, identity service, and task service are used exactly as they already behave.

## Impact

- **New:** a login component, an axios instance with request/response interceptors, a React Query provider, and a `.env.example` documenting `VITE_API_URL`.
- **Modified:** `vite.config.ts` (dev proxy), `src/App.tsx` (mount the board behind auth), `src/api/kanban.ts` (use the shared axios instance), `package.json` (dependency corrections), `readme.md` (demo walkthrough).
- **Deleted:** `src/App.css` and `src/assets/react.svg` (Vite template leftovers, imported nowhere); the `react-router-dom` and `lucide-react` dependencies (zero imports).
- **No backend changes.** If the demo surfaces a backend defect, that defect gets its own change.

## Out of Scope

Each of these was considered and deliberately left out, with the trigger for revisiting it:

- **Drag-and-drop.** `moveTask` already exists in `src/api/kanban.ts` and the backend reindexes the target column correctly, so this is tempting. It stays out because the board has never once rendered — shipping both means a rendering bug and a drag bug arrive together and neither is isolated. Add it once the board is proven, using native HTML5 drag events rather than a library.
- **Client-side routing.** `react-router-dom` is installed and unused. One screen does not need a router; add it at the second screen.
- **Gateway CORS configuration.** The Vite proxy covers local development completely. Add real CORS when the frontend is served from an origin that is not the Vite dev server — which is not the case today, and may never be if the frontend is eventually served through the gateway.
- **Project picker, task creation, notifications UI.** All need a screen each and none is required to prove the stack works.
- **Refresh tokens, logout, route guards.** The 30-minute expiry plus "401 sends you back to login" is sufficient and honest for a demo.

## Known Issues Recorded, Not Fixed Here

- **`RouterValidator` decides the auth boundary by substring.** `path.contains(uri)` over its open-endpoints list means `/api/tasks/1/swagger-ui` skips `AuthenticationFilter` entirely, and task-service's `IdentityHeaderFilter` exempts `/swagger-ui` too, so such a request clears both gates. It returns 404 today because no handler matches, so nothing leaks — but the boundary holds by accident rather than by design, and one added route changes that. Belongs in its own change, as prefix matching.
- **The frontend `Task` type has no `order` field**, though `TaskDto` returns one. Harmless while the board is read-only; it becomes load-bearing the moment drag-and-drop lands, so it belongs to that change.
