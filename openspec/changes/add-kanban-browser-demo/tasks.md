## 1. Clear the ground

- [x] 1.1 Add a `server.proxy` entry to `vite.config.ts` mapping `/api` to `http://localhost:8080`.
  - **Also proxied `/auth`**, which the task omitted. `POST /auth/token` is equally cross-origin, and sending it as JSON makes it a preflighted request too. It is an open route at the gateway so `AuthenticationFilter` would not reject the preflight, but neither the gateway nor the identity service returns CORS headers, so the browser would block the response anyway. Login is unreachable without this.
- [x] 1.2 Change `API_URL` in `src/api/kanban.ts` to a same-origin `/api` default, keeping `VITE_API_URL` as the override.
- [x] 1.3 Confirm a browser request to `/api/...` reaches the gateway and returns 401 rather than being blocked as cross-origin.
  - A 401 is the success condition here: it proves the request arrived and was rejected on credentials, which is the next task's problem. A CORS error would mean 1.1 is wrong.
- [x] 1.4 Move `axios` and `@tanstack/react-query` from `devDependencies` to `dependencies`.
- [x] 1.5 Remove `react-router-dom` and `lucide-react`; delete `src/App.css` and `src/assets/react.svg`.
  - `src/assets/` is now empty and removed too. Lockfile regenerated; both packages confirmed gone from it.
- [x] 1.6 Confirm the dependency split is correct.
  - **This task was written wrong and is corrected here.** It originally said to run `npm ci --omit=dev && npm run build`, which cannot pass by construction: `tsc` and `vite` are build tooling and legitimately belong in `devDependencies`, so omitting dev dependencies necessarily removes the build itself. Running it fails with `'tsc' is not recognized`, which looks like a defect and is not one.
  - The check that actually proves 1.4 is two separate things, both now verified: (a) `npm ci --omit=dev` resolves every *runtime* import — `axios`, `react`, `react-dom`, `@tanstack/react-query` all present; (b) a full `npm ci` followed by `npm run build` succeeds, producing `dist/` at 190.60 kB (60.07 kB gzipped).

## 2. Authentication

- [x] 2.1 Create a shared axios instance with a request interceptor attaching `Authorization: Bearer <token>` when a token is stored.
- [x] 2.2 Add a response interceptor that discards the stored token on 401 and surfaces the unauthenticated state.
- [x] 2.3 Add a login component posting to `/auth/token`.
  - `POST /auth/token` returns a **bare JWT string**, not a JSON object — read `response.data` directly, not `response.data.token`.
- [x] 2.4 Persist the token in `localStorage` and read it on startup so a reload keeps the session.
- [x] 2.5 Point `src/api/kanban.ts` at the shared instance instead of bare `axios`.
- [x] 2.6 Verify login with the seeded `admin/admin123`, and that bad credentials show an error and store no token.

## 3. The board

- [x] 3.1 Wrap the app in a React Query provider.
- [x] 3.2 Gate `App.tsx` on the auth state: login screen when unauthenticated, board when authenticated.
- [x] 3.3 Fetch the board with `useQuery` and pass `data` and `isLoading` into the existing `KanbanBoard` unchanged.
  - Hold `projectId` in one named constant. Do not inline it into the fetch call; the constant is what makes the hardcoding visible and easy to replace later.
- [x] 3.4 Render an error state when the board request fails for a non-401 reason.
- [x] 3.5 Verify the board renders three empty columns before any task exists.
  - `KanbanService` returns `TODO`/`IN_PROGRESS`/`DONE` unconditionally, so empty columns are the correct first result, not a bug.

## 4. Data and end-to-end verification

- [x] 4.1 Create a few tasks by `curl` across at least two different statuses, with `projectId` matching the constant from 3.3.
- [x] 4.2 Verify each task appears as a card in the correct column, showing its key and summary.
- [x] 4.3 Verify the 401 path: corrupt or clear the stored token, then confirm the next request returns the user to login.
- [x] 4.4 Verify a hard reload keeps the user logged in and re-renders the board.
- [x] 4.5 Confirm no console errors and no failed requests in a clean run.

## 5. Document

- [x] 5.1 Add a demo walkthrough to `readme.md`: start the stack, run the frontend, log in with the seeded user, seed tasks, see the board.
- [x] 5.2 Add `.env.example` documenting `VITE_API_URL` and when it is needed.
- [x] 5.3 Document that the dev proxy is what makes browser requests work, and that serving the frontend from any other origin needs CORS or gateway-served static assets — neither of which exists yet.
- [x] 5.4 Document the `localStorage` token trade-off in Known Limitations, alongside the existing trusted-header entry.
  - Same shape of admission: readable by any script on the origin, so XSS-exposed; acceptable for a local demo, must not reach a shared environment. The `HttpOnly` cookie fix would make the gateway a session participant, which is an architectural change rather than a tweak.
- [x] 5.5 Update the roadmap: this phase complete, and note that drag-and-drop is the next frontend step.

## 6. File separately, do not fix here

- [x] 6.1 Raise `RouterValidator`'s substring auth matching as its own change.
  - `path.contains(uri)` over the open-endpoints list means `/api/tasks/1/swagger-ui` skips `AuthenticationFilter`, and task-service's `IdentityHeaderFilter` exempts `/swagger-ui` too, so it clears both gates. It 404s today because no handler matches — the boundary holds by accident. Fix is prefix matching.
- [x] 6.2 Note that the frontend `Task` type is missing `order`, and that it belongs to the drag-and-drop change where it becomes load-bearing.
- [x] 6.3 Record any other defect this demo surfaces as its own change rather than fixing it here.
  - **Bad credentials return HTTP 500, not 401 — a violation of an already-accepted spec.** `POST /auth/token` with a wrong password *or* an unknown username returns `{"message":"Bad credentials","status":500,...}`. Confirmed both through the gateway and directly against `:8082`, so it originates in the identity service, not in routing. `openspec/specs/identity/spec.md` requires 401 for both cases ("Unknown username", "Wrong password"), so the `identity` capability is not actually met as written.
  - Cause: `AuthService.login` calls `authenticationManager.authenticate(...)`, which throws Spring Security's `BadCredentialsException`; nothing maps it to a status, so it falls through as a 500.
  - Mitigating detail: the message is identical for both cases, so it still does not leak whether a username exists — the spec's non-disclosure requirement holds even though the status code one does not.
  - Found the way the proposal predicted defects would be found — by the demo exercising a path only ever checked by hand. Not fixed here, per this change's no-backend-changes constraint.
