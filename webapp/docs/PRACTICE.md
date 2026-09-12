# Practice curriculum — build the rest of this app

Work these **in order**. Each one builds something real against the running backend. For every
exercise: build it, then run the gate —

```bash
npm run check   # lint + typecheck + test + build
```

— and it isn't done until that's green.

The **links feature is the worked reference**: `src/features/links/` plus `src/shared/api/`.
Read it first (`docs/ARCHITECTURE.md` §7 gives the order), then copy its shape.

Legend: 🟢 Foundations · 🔵 Core · 🟣 Hard · ⚫ Stretch

---

## Track A — Orientation 🟢

- [ ] **A1 · Run it end to end.** Backend up (`docker compose up -d` from `code/`), `.env.local`
      copied, `npm run dev`. Confirm links render.
      _Done when:_ you can state, out loud, why the list renders **without logging in**.
- [ ] **A2 · Trace one request.** Follow a page load from the browser through the Next server,
      the edge, and into a gRPC service. Name every hop and what it adds.
      _Context: the browser only ever talks to Next. Next attaches a client token and calls
      `/api/links` on Envoy, which transcodes JSON to gRPC and forwards to shortener-service,
      which checks the `LIST_SHORT_URL` permission from the JWT._
      _Done when:_ you can point at the file doing each step.
- [ ] **A3 · Break the contract on purpose.** Rename a field in `shortener/api/shortener_api.proto`,
      run `npm run gen:proto`, then `npm run typecheck`.
      _Done when:_ the frontend fails to compile — then revert and confirm it passes. That failure
      is the whole argument for generating types from the protos.
- [ ] **A4 · Prove the boundary is enforced.** Add `import { something } from '@/features/auth/...'`
      inside `features/links` and run `npm run lint`.
      _Done when:_ lint fails with the "No cross-feature imports" message. Revert.

## Track B — Login 🔵

- [ ] **B1 · Session helpers.** Create `src/shared/auth/session.ts` (`import 'server-only'`) with
      `setSession`, `readSession` and `clearSession` over two `HttpOnly` cookies.
      _Context: options are `httpOnly`, `secure` in production, `sameSite: 'lax'`, `path: '/'`, and
      `maxAge` from the response's `expiresInSeconds`. Cookies can only be SET in a Server Action
      or Route Handler — never during a Server Component render._
      _Done when:_ a unit test asserts the cookie options, and nothing outside `shared/auth` reads
      the raw cookie.
- [ ] **B2 · The login action.** `src/features/auth/actions/login.ts` — `'use server'`: Zod-validate
      → `POST /api/login` via `callApi` → store the session → `redirect('/')`.
      _Context: `LoginRequestSchema`/`LoginResponseSchema` are already generated. Return field
      errors to the form with `useActionState`; map a 401 to "Invalid email or password" without
      leaking which half was wrong._
      _Done when:_ a wrong password shows an inline error and sets no cookie.
- [ ] **B3 · The form.** `features/auth/components/login-form.tsx` (`'use client'`) with
      `useActionState` and a pending state, plus `src/app/login/page.tsx` to host it.
- [ ] **B4 · Logout,** and a "signed in" indicator.
      _Context: the indicator reads the session, so with Cache Components it must live inside a
      `<Suspense>` boundary — reading cookies outside one is a build error. Keep it out of the
      layout's top level so it doesn't hold up the whole segment._

## Track C — Create a link 🔵

- [ ] **C1 · Repository write path.** Add `createLink(longUrl, accessToken)` to
      `features/links/server/link-repository.ts`, building the request with
      `create(CreateShortLinkRequestSchema, { longUrl })` and calling with `auth: { accessToken }`.
- [ ] **C2 · The action.** `features/links/actions/create-link.ts`: **authorize → validate → call →
      revalidate**, in that order.
      _Context: a Server Action is a public POST endpoint, so it re-checks the session itself
      rather than trusting the page that rendered the form. Use `updateTag` for read-your-writes._
      _Done when:_ creating a link makes it appear in the list, and calling the action with no
      session fails cleanly.
- [ ] **C3 · Error mapping.** Turn backend failures into UI: 403 → "you don't have permission",
      `INVALID_ARGUMENT` → a field error on the URL input.
      _Done when:_ you branch on `ApiError.reason`/`httpStatus`, never on message text.
- [ ] **C4 · Optimistic UI.** Add `useOptimistic` so the new link appears before the round trip and
      rolls back on failure.

## Track D — Token lifecycle 🟣

- [ ] **D1 · Refresh and retry.** In the create-link path: on `ACCESS_TOKEN_EXPIRED`, call
      `POST /api/token/refresh`, store the rotated pair, and retry the original call **once**.
      _Context: this lives in the action because only Server Actions can write cookies. Guard
      against retry loops — one attempt, then surface the failure._
      _Done when:_ with a deliberately short access-token TTL, creating a link after expiry
      succeeds without the user noticing.
- [ ] **D2 · Dead refresh token.** On any `REFRESH_TOKEN_*` reason, clear both cookies and redirect
      to `/login`.
      _Context: these come back as **400**, not 401 — that's what keeps a retry-on-401 client from
      looping on the refresh call itself. Refresh-token reuse is also detected server-side and
      revokes the whole family._
- [ ] **D3 · `proxy.ts`.** Redirect unauthenticated visitors away from `/links/new` by reading the
      cookie only.
      _Context: optimistic checks only — no network calls, no data reads. It runs on every request
      including prefetches, and it is **not** a security boundary: the action still re-checks._
- [ ] **D4 · ⚫ Verify the token yourself.** Fetch auth-service's public key and verify the access
      token's signature with `jose` before trusting its claims for UI decisions.

## Track E — Caching 🟣

- [ ] **E1 · Cache the list.** Add `'use cache'` + `cacheTag('links')` + `cacheLife` to the read.
      _Done when:_ you hit the wall — `next build` now needs the backend and a real client secret,
      because cached functions run at build time. Decide how to handle it (keep the read dynamic,
      or make the build tolerate an unreachable API) and write down why.
- [ ] **E2 · Invalidate it.** Have create-link refresh the list. Implement one `revalidateTag` and
      one `updateTag`, and write a comment on when each is right.
      _Context: `revalidateTag(tag, profile)` is stale-while-revalidate for the next visitor;
      `updateTag(tag)` gives the acting user read-your-writes immediately._

## Track F — Quality and shipping 🟣⚫

- [ ] **F1 · E2E.** Playwright: list → login → create → see it in the list.
      _Context: Vitest cannot render async Server Components, which is exactly what the list and
      page are — so this flow can only be covered end to end._
- [ ] **F2 · CI.** A GitHub Actions job on Node 24 running `npm run check`, plus a **codegen drift
      check**: run `gen:proto` and fail if `git diff --exit-code` shows changes.
- [ ] **F3 · Containerise.** `output: 'standalone'`, a Dockerfile, and a `webapp` service in
      `compose.yaml`. Remember `API_BASE_URL` becomes `http://edge:8080` on the compose network.
- [ ] **F4 · ⚫ Derive the URLs.** Read the `google.api.http` option off the generated method
      descriptors so repositories stop hard-coding paths — closing the one gap proto types have
      versus OpenAPI tooling.
- [ ] **F5 · ⚫ React Compiler.** Turn on `reactCompiler`, measure the build-time cost, and decide
      whether it earns its place.

---

## Interview drills

- Why does the link list render for a signed-out visitor, and what stops that visitor from
  creating a link?
- Where does the `publicweb` secret live, and why can it never be a `NEXT_PUBLIC_` variable?
- Why is a failed refresh a 400 and not a 401?
- Why can't a Server Component set a cookie, and what follows from that for token refresh?
- What exactly breaks if someone hand-writes a `Link` interface instead of generating it?
- `revalidateTag` vs `updateTag` — one sentence each.
- Why is the client token safe to cache in a module-level variable when a user token isn't?
