# AGENTS.md

Conventions for AI agents and human contributors working in this repository. Read [`readme.md`](readme.md) for architecture and [`spec.md`](spec.md) for the behavioural contract and known gaps first.

## What this project is

A **learning project** for distributed-systems patterns, not a product heading for production. That framing drives most of the conventions below: complexity that would be over-engineering in a startup is often the point here, and shortcuts that would be pragmatic elsewhere get recorded as known gaps rather than quietly taken.

The practical consequence: **do not "simplify" a deliberate pattern away.** Per-service databases, the separate `jari-security` module, and the gateway-as-sole-ingress design are all chosen on purpose. If something looks like unnecessary indirection, check `openspec/` before removing it — the rationale is usually written down.

## Before you change anything

1. **Read the relevant spec.** `openspec/specs/<capability>/spec.md` is the authoritative contract. If your change alters observable behaviour, it changes a spec, and that belongs in an OpenSpec change (below).
2. **Check the known gaps in [`spec.md`](spec.md).** Several "bugs" are deliberate and scheduled. Fixing one out of order is fine, but do it knowingly and in its own change — don't fold it into unrelated work.
3. **Grep for callers before editing a shared function.** `jari-common` and `jari-security` are used across services; a signature change there is a multi-module change.

## OpenSpec workflow

Non-trivial work is tracked as a change under `openspec/changes/<change-name>/`:

```
proposal.md    # what and why
design.md      # how, and what was rejected
tasks.md       # checklist, kept current as work lands
specs/         # delta specs for affected capabilities
README.md      # short orientation
```

Completed changes move to `openspec/changes/archive/<date>-<change-name>/`, and their capability specs are merged into `openspec/specs/`.

Slash commands for this workflow are available (`/opsx:propose`, `/opsx:apply`, `/opsx:archive`, `/opsx:sync`, `/opsx:explore`).

**Keep `tasks.md` honest.** A task marked done that isn't is worse than an open one — the archive is the project's memory of what was actually verified.

## Verification

Match what CI runs (`.github/workflows/ci.yml`) before claiming a change works.

### Backend

```bash
./mvnw -B -pl <module> -am compile
./mvnw -B -pl <module> -am test
./mvnw -B -pl <module> -am install
```

`-am` (also-make) is required — modules depend on `jari-common`/`jari-security`, and building one alone fails on an unresolved parent or sibling.

### Frontend

```bash
cd jari-frontend
bun install --frozen-lockfile
bun run lint
bunx tsc -b
bun run build
```

There is no frontend test script yet; CI skips it conditionally rather than failing. Do not add a bare `"test"` script that shells out to nothing — CI's guard exists because `bun run --if-present test` silently ran `/usr/bin/test` and passed.

### Full stack

`mvn`/`bun` checks do not prove the system works. For anything touching request flow, boot the stack (`docker compose up --build`) and run the **Smoke Test** in `readme.md`. There is no integration test harness yet (`add-integration-test-harness` is deferred), so the smoke test is the real end-to-end gate.

## Conventions

### Comments

**Default to no comments.** Well-named identifiers carry the *what*. Write one only when the *why* is non-obvious: a hidden constraint, a subtle invariant, a workaround for a specific bug, or behaviour that would surprise a reader.

Do not write comments that:

- Explain what the code plainly does.
- Narrate the change or its history ("added for X", "used by Y", "previously duplicated in Z") — that belongs in the commit message, and it rots as the code moves.
- Justify a design decision at paragraph length. If the rationale is that long, it belongs in `design.md`.

### Commit messages

Explain **what changed and why**. Put the reasoning here rather than in code comments — this is where it stays accurate. Reference the OpenSpec change when there is one. Do not add AI-tool attribution or co-author trailers.

### Backend

- Constructor injection via Lombok `@RequiredArgsConstructor`; no field injection.
- Service layer holds business logic; controllers stay thin.
- DTOs cross service boundaries, entities do not.
- `updateX` methods overwrite the full record rather than merging — callers must send the complete object. This is a real footgun (an omitted field nulls out) and is documented at each call site in the frontend API layer.
- Schema changes go through Flyway. `ddl-auto: validate` means entity drift fails startup by design; do not switch it to `update` to make a mismatch go away.

### Frontend

- One shared component per pattern. `Modal` and `Dropdown` exist because three near-identical copies of each did; do not reintroduce a fourth inline version.
- Task-field vocabulary (`TYPE_META`, `PRIORITY_META`, `STATUSES`, `STATUS_PILL`) lives in `src/types/kanban.ts`. Icons read `.color`, selects read `.label` — one source, no parallel copies.
- Icons come from `@phosphor-icons/react`. Do not hand-roll SVG paths, and do not use emoji as icons.
- Server state is TanStack Query. Do not mirror fetched data into `useState`.
- Mutations that the user should see immediately are optimistic, with rollback on failure (see the board's move mutation).

## Boundaries

- **Do not publish downstream service ports** in any non-local configuration. See Deployment Constraints in [`spec.md`](spec.md) — there is a working impersonation bypass this would expose.
- **Do not commit secrets.** `JARI_SECURITY_JWT_SECRET` comes from the environment; both services that need it fail to start without it, deliberately.
- **Do not weaken a security boundary to make a test pass.** Record it as a known gap instead.
- **Do not force-push shared branches** or amend published commits without asking.
