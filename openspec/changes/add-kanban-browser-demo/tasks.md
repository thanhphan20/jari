## 1. Clear the ground

- [ ] 1.1 Add a `server.proxy` entry to `vite.config.ts` mapping `/api` to `http://localhost:8080`.
- [ ] 1.2 Change `API_URL` in `src/api/kanban.ts` to a same-origin `/api` default, keeping `VITE_API_URL` as the override.
- [ ] 1.3 Confirm a browser request to `/api/...` reaches the gateway and returns 401 rather than being blocked as cross-origin.
  - A 401 is the success condition here: it proves the request arrived and was rejected on credentials, which is the next task's problem. A CORS error would mean 1.1 is wrong.
- [ ] 1.4 Move `axios` and `@tanstack/react-query` from `devDependencies` to `dependencies`.
- [ ] 1.5 Remove `react-router-dom` and `lucide-react`; delete `src/App.css` and `src/assets/react.svg`.
- [ ] 1.6 Confirm `npm ci --omit=dev && npm run build` succeeds.
  - This is what 1.4 actually fixes. Without it the correction is unverified.

## 2. Authentication

- [ ] 2.1 Create a shared axios instance with a request interceptor attaching `Authorization: Bearer <token>` when a token is stored.
- [ ] 2.2 Add a response interceptor that discards the stored token on 401 and surfaces the unauthenticated state.
- [ ] 2.3 Add a login component posting to `/auth/token`.
  - `POST /auth/token` returns a **bare JWT string**, not a JSON object — read `response.data` directly, not `response.data.token`.
- [ ] 2.4 Persist the token in `localStorage` and read it on startup so a reload keeps the session.
- [ ] 2.5 Point `src/api/kanban.ts` at the shared instance instead of bare `axios`.
- [ ] 2.6 Verify login with the seeded `admin/admin123`, and that bad credentials show an error and store no token.

## 3. The board

- [ ] 3.1 Wrap the app in a React Query provider.
- [ ] 3.2 Gate `App.tsx` on the auth state: login screen when unauthenticated, board when authenticated.
- [ ] 3.3 Fetch the board with `useQuery` and pass `data` and `isLoading` into the existing `KanbanBoard` unchanged.
  - Hold `projectId` in one named constant. Do not inline it into the fetch call; the constant is what makes the hardcoding visible and easy to replace later.
- [ ] 3.4 Render an error state when the board request fails for a non-401 reason.
- [ ] 3.5 Verify the board renders three empty columns before any task exists.
  - `KanbanService` returns `TODO`/`IN_PROGRESS`/`DONE` unconditionally, so empty columns are the correct first result, not a bug.

## 4. Data and end-to-end verification

- [ ] 4.1 Create a few tasks by `curl` across at least two different statuses, with `projectId` matching the constant from 3.3.
- [ ] 4.2 Verify each task appears as a card in the correct column, showing its key and summary.
- [ ] 4.3 Verify the 401 path: corrupt or clear the stored token, then confirm the next request returns the user to login.
- [ ] 4.4 Verify a hard reload keeps the user logged in and re-renders the board.
- [ ] 4.5 Confirm no console errors and no failed requests in a clean run.

## 5. Document

- [ ] 5.1 Add a demo walkthrough to `readme.md`: start the stack, run the frontend, log in with the seeded user, seed tasks, see the board.
- [ ] 5.2 Add `.env.example` documenting `VITE_API_URL` and when it is needed.
- [ ] 5.3 Document that the dev proxy is what makes browser requests work, and that serving the frontend from any other origin needs CORS or gateway-served static assets — neither of which exists yet.
- [ ] 5.4 Document the `localStorage` token trade-off in Known Limitations, alongside the existing trusted-header entry.
  - Same shape of admission: readable by any script on the origin, so XSS-exposed; acceptable for a local demo, must not reach a shared environment. The `HttpOnly` cookie fix would make the gateway a session participant, which is an architectural change rather than a tweak.
- [ ] 5.5 Update the roadmap: this phase complete, and note that drag-and-drop is the next frontend step.

## 6. File separately, do not fix here

- [ ] 6.1 Raise `RouterValidator`'s substring auth matching as its own change.
  - `path.contains(uri)` over the open-endpoints list means `/api/tasks/1/swagger-ui` skips `AuthenticationFilter`, and task-service's `IdentityHeaderFilter` exempts `/swagger-ui` too, so it clears both gates. It 404s today because no handler matches — the boundary holds by accident. Fix is prefix matching.
- [ ] 6.2 Note that the frontend `Task` type is missing `order`, and that it belongs to the drag-and-drop change where it becomes load-bearing.
- [ ] 6.3 Record any other defect this demo surfaces as its own change rather than fixing it here.
