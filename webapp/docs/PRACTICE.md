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

- [x] **A1 · Run it end to end.** Backend up (`docker compose up -d` from `code/`), `.env.local`
      copied, `npm run dev`. Confirm links render.
      _Done when:_ you can state, out loud, why the list renders **without logging in**.
- [x] **A2 · Trace one request.** Follow a page load from the browser through the Next server,
      the edge, and into a gRPC service. Name every hop and what it adds.
      _Context: the browser only ever talks to Next. Next attaches a client token and calls
      `/api/links` on Envoy, which transcodes JSON to gRPC and forwards to shortener-service,
      which checks the `LIST_SHORT_URL` permission from the JWT._
      _Done when:_ you can point at the file doing each step.
- [x] **A3 · Break the contract on purpose.** Rename a field in `shortener/api/shortener_api.proto`,
      run `npm run gen:proto`, then `npm run typecheck`.
      _Done when:_ the frontend fails to compile — then revert and confirm it passes. That failure
      is the whole argument for generating types from the protos.
- [x] **A4 · Prove the boundary is enforced.** Add `import { something } from '@/features/auth/...'`
      inside `features/links` and run `npm run lint`.
      _Done when:_ lint fails with the "No cross-feature imports" message. Revert.

## Track B — Login 🔵

- [x] **B1 · Session helpers.** Create `src/shared/auth/session.ts` (`import 'server-only'`) with
      `setSession`, `readSession` and `clearSession` over two `HttpOnly` cookies.
      _Context: options are `httpOnly`, `secure` in production, `sameSite: 'lax'`, `path: '/'`. The
      ACCESS cookie's `maxAge` comes from the response's `expiresInSeconds`; the REFRESH cookie needs
      its own 7 days, matching `refresh-token-ttl` — reuse the access lifetime and every user gets
      logged out after 15 minutes. Cookies can only be SET in a Server Action
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

- [ ] **B5 · Registration.** `features/auth/actions/register.ts` → `POST /api/register` with
      `{email, password, firstName, lastName}`, then log the new user straight in.
      _Context: registration is NOT anonymous — `Register` is gated on the `REGISTER` permission,
      so the call needs `auth: 'client'` (the `publicweb` token), not a user token. This is the
      clearest case yet for why this app is a BFF: the browser cannot make this call itself,
      because it would need the client secret to get that token. A Server Action can._
      _Done when:_ signing up from the browser works, and `grep -r publicweb .next/static` is
      still empty.

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

## Track D — Edit a link 🔵

`UpdateShortLink` exists on the backend and nothing in this app calls it yet. It is the first
endpoint here that is **partial**: the client sends only what changed.

- [ ] **D1 · Repository write path.** Add `updateLink({ shortCode, longUrl?, status? }, accessToken)`
      to `features/links/server/link-repository.ts`. `PATCH /api/links/{shortCode}`, and parse the
      reply with `fromJson(UpdateShortLinkResponseSchema, …)`.
      _Context: the id goes in the **path**, the changes go in the **body** — destructure it out
      (`const { shortCode, ...changes }`) rather than serializing the whole message. The generated
      `UpdateShortLinkRequest` type has `shortCode: string` as required because that is the **gRPC
      message**, where a caller has no URL to put it in; over HTTP the transcoder fills it from the
      path instead. Send it in the body too and it is silently ignored — `PATCH /api/links/A` with
      `{"shortCode":"B"}` updates **A** and returns 200._
      _Done when:_ you can change a link's `longUrl` from a script and see it in the list.
- [ ] **D2 · Send only what changed.** `long_url` and `status` are proto3 `optional`, so an omitted
      field means "leave it alone" — not "set it to empty".
      _Context: this is why the request has no `FieldMask`. Explicit presence answers the same
      "absent vs zero" question per field, and Cognixus uses the same posture — `optional` plus
      narrow rpcs, no masks anywhere._
      _Done when:_ PATCHing only `longUrl` provably leaves `status` untouched — assert it in a test
      against a mocked `callApi`, the way `link-repository.test.ts` already does for the list.
- [ ] **D3 · The action and the form.** `features/links/actions/update-link.ts`, same order as C2:
      **authorize → validate → call → revalidate**. A `features/links/components/edit-link-form.tsx`
      pre-filled from the current row, submitting only the fields the user actually touched.
      _Done when:_ editing a link updates the list without a full reload, and the action rejects an
      unauthenticated caller on its own rather than trusting the page._
- [ ] **D4 · Map the three refusals.** This endpoint fails in three distinct ways, and they are not
      interchangeable: `NOT_FOUND` (no such code), `FAILED_PRECONDITION` (the new URL's host is on
      the blocklist — the audit re-runs on every URL change), and `INVALID_ARGUMENT` (you tried to
      set `LINK_STATUS_FLAGGED`, which is the audit's verdict, not a client's to write).
      _Done when:_ each renders differently, and you branched on `ApiError.grpcCode`/`httpStatus`,
      never on message text.
- [ ] **D5 · Expire, don't delete.** Add a one-click "expire" that PATCHes
      `{"status":"LINK_STATUS_EXPIRED"}`, and show expired links differently in `LinkRow`.
      _Context: `StatusBadge` already styles anything non-ACTIVE as amber — decide whether expired
      deserves its own treatment._
- [ ] **D6 · Concurrent edits.** ⚫ `updated_at` now exists on every link. Use it: refuse a write
      whose `updated_at` is older than the stored one, so two editors cannot silently clobber each
      other. Needs a backend change too — a precondition field on the request, or `If-Match` and an
      ETag at the edge.
      _Context: this is the concrete payoff of putting the resource in the URL, since conditional
      requests key on a resource URL._

## Track E — Token lifecycle 🟣

- [ ] **E1 · Refresh and retry.** In the create-link path: on `ACCESS_TOKEN_EXPIRED`, call
      `POST /api/token/refresh`, store the rotated pair, and retry the original call **once**.
      _Context: this lives in the action because only Server Actions can write cookies. Guard
      against retry loops — one attempt, then surface the failure._
      _Done when:_ with a deliberately short access-token TTL, creating a link after expiry
      succeeds without the user noticing.
- [ ] **E2 · Dead refresh token.** On any `REFRESH_TOKEN_*` reason, clear both cookies and redirect
      to `/login`.
      _Context: these come back as **400**, not 401 — that's what keeps a retry-on-401 client from
      looping on the refresh call itself. Refresh-token reuse is also detected server-side and
      revokes the whole family._
- [ ] **E3 · `proxy.ts`.** Redirect unauthenticated visitors away from `/links/new` by reading the
      cookie only.
      _Context: optimistic checks only — no network calls, no data reads. It runs on every request
      including prefetches, and it is **not** a security boundary: the action still re-checks._
- [ ] **E4 · ⚫ Verify the token yourself.** Fetch auth-service's public key and verify the access
      token's signature with `jose` before trusting its claims for UI decisions.

## Track F — Caching 🟣

- [ ] **F1 · Cache the list.** Add `'use cache'` + `cacheTag('links')` + `cacheLife` to the read.
      _Done when:_ you hit the wall — `next build` now needs the backend and a real client secret,
      because cached functions run at build time. Decide how to handle it (keep the read dynamic,
      or make the build tolerate an unreachable API) and write down why.
- [ ] **F2 · Invalidate it.** Have create-link refresh the list. Implement one `revalidateTag` and
      one `updateTag`, and write a comment on when each is right.
      _Context: `revalidateTag(tag, profile)` is stale-while-revalidate for the next visitor;
      `updateTag(tag)` gives the acting user read-your-writes immediately._

## Track G — Quality and shipping 🟣⚫

- [ ] **G1 · E2E.** Playwright: list → login → create → see it in the list.
      _Context: Vitest cannot render async Server Components, which is exactly what the list and
      page are — so this flow can only be covered end to end._
- [ ] **G2 · CI.** A GitHub Actions job on Node 24 running `npm run check`, plus a **codegen drift
      check**: run `gen:proto` and fail if `git diff --exit-code` shows changes.
- [ ] **G3 · Containerise.** `output: 'standalone'`, a Dockerfile, and a `webapp` service in
      `compose.yaml`. Remember `API_BASE_URL` becomes `http://edge:8080` on the compose network.
- [ ] **G4 · ⚫ Derive the URLs.** Read the `google.api.http` option off the generated method
      descriptors so repositories stop hard-coding paths — closing the one gap proto types have
      versus OpenAPI tooling.
- [ ] **G5 · ⚫ React Compiler.** Turn on `reactCompiler`, measure the build-time cost, and decide
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
- A client sends `PATCH /api/links/A` with a body of `{"shortCode":"B"}`. Which link changes, and
  why is the service unable to detect the mismatch?
- `UpdateShortLinkRequest` uses `optional` fields rather than a `google.protobuf.FieldMask`. What
  problem do both solve, and what would have to change about `ShortLink` to make the mask the
  better choice?
- A client may set a link to `EXPIRED` but not to `FLAGGED`. Why is that distinction worth
  enforcing in the service rather than in the UI?
- Changing a link's `long_url` re-runs the blocklist audit. What breaks if it doesn't?
