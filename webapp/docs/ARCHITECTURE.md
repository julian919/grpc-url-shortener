# Architecture — how this app is put together, and why

Every decision below is traceable to a source, listed at the end. The short version: **`app/` is
routing glue, every real thing lives in a feature module, and secrets never leave the server.**

---

## 1. The shape

```
webapp/
├── src/
│   ├── app/                        ROUTING ONLY — pages, layouts, error/loading boundaries
│   │   ├── page.tsx                assembles the feature and owns the <Suspense> boundary
│   │   └── error.tsx
│   │
│   ├── features/                   ONE FOLDER PER DOMAIN
│   │   └── links/
│   │       ├── schema/             Zod — parses untrusted input at the boundary
│   │       ├── server/             `server-only` data access (the repository)
│   │       ├── actions/            `use server` mutations  (you build these)
│   │       └── components/         server + client components for THIS feature
│   │
│   └── shared/                     CROSS-CUTTING
│       ├── api/                    edge transport, token handling, generated proto types
│       ├── config/                 typed env
│       └── observability/          structured logger
│
├── buf.gen.yaml                    proto -> TypeScript codegen
└── docs/                           this file + PRACTICE.md
```

### The dependency rule

```
shared  ──▶  features  ──▶  app
```

Dependencies point one way. Features never import each other; `app/` composes them. This is
bulletproof-react's rule, and it's **enforced, not documented**: `import/no-restricted-paths` in
`eslint.config.mjs` fails the build on a violation. Try it — add an import from
`features/links` into `features/auth` and run `npm run lint`.

Next.js itself is "unopinionated" here and lists "split project files by feature" as one of its
strategies; the instruction is to pick one and stay consistent.

---

## 2. The security model: this app is a BFF

```
browser ──HTTP──▶ Next server ──HTTP/JSON──▶ Envoy edge :8080 ──gRPC──▶ services
                  (holds secrets + tokens)     (transcodes)
```

The browser never sees a token or a secret. Two rules make that true:

1. **No `NEXT_PUBLIC_*` variables exist, and none may be added.** That prefix inlines a value into
   the browser bundle. `PUBLICWEB_CLIENT_SECRET` is a real credential: RFC 6749 §4.4 says the
   client-credentials grant is for *confidential* clients only, and a browser is not one.
2. **`import 'server-only'`** marks every module that touches secrets, tokens or `process.env`.
   Importing one from a client component is a build error. Only `src/shared/config/env.ts` reads
   `process.env`, per Next's data-security guide.

### Two kinds of token

| | Client token (`publicweb`) | User token |
| --- | --- | --- |
| Represents | this application | the signed-in person |
| Obtained by | `POST /api/token/client` with Basic auth | `POST /api/login` |
| Grants | `LIST_SHORT_URL` | `CREATE_SHORT_URL` |
| Stored | in server memory, cached until ~30s before expiry | `HttpOnly` cookies (you build this) |
| Shared between users? | yes — it's app-level | **never** |

The client token is cached in a module-level variable with in-flight deduplication. That is safe
*because it isn't per-user*, and it mirrors what `ServiceTokenSupplier` already does in
user-service on the Java side. Per-user tokens must never be stored that way on a server.

It is also never used as a `use cache` argument or `cacheTag`, because cache keys and tags are
stored in plain text.

Cognixus's webapps make the same split explicitly: every repository call picks
`TokenCallerType.Client` or `.User`.

---

## 3. Types come from the contract

`npm run gen:proto` runs buf + protoc-gen-es over the **same `.proto` files the Java services
compile**, into `src/shared/api/gen/`. Nothing is hand-written, and nothing is duplicated.

Why the protos rather than the OpenAPI document:

- The OpenAPI doc is itself derived (proto → `protoc-gen-openapiv2` → Swagger 2.0), so typing
  against it puts two conversions between you and the source of truth.
- Envoy's transcoder speaks **proto3 canonical JSON**: int64 as strings, enums by name,
  lowerCamelCase fields. `fromJson` implements exactly that mapping, so the parse is the contract
  rather than an assumption about it.
- It's what cognixus does (protobuf-ts there, protobuf-es here — protobuf-es is actively released
  and is what Connect builds on).

**The trade-off we accepted:** OpenAPI tooling would also type the URLs and query params. With
proto types, each repository writes its own URL strings, exactly as cognixus's repositories do.
They're confined to one file per feature, and an E2E test covers them. Deriving them from the
`google.api.http` options in the generated descriptors is a stretch task in PRACTICE.md.

**int64 becomes `bigint`** (`ShortLink.createdAt`). Convert at the point of display.

---

## 4. Rendering: what's in the shell, what streams

`cacheComponents: true` makes a page a **static shell by default**. Anything that reads request
data has to sit inside `<Suspense>`; reading `cookies()` outside a boundary is a *build* error.

On the home page: the heading is static and prerendered, while the list is read at request time
behind a boundary and streams in.

The list is deliberately **not** cached yet. `use cache` would run at build time, which would make
`next build` require a running backend and a valid client secret. Making that trade consciously —
and dealing with the consequence — is exercise F in PRACTICE.md.

When you add sessions: a session read cannot be prerendered, so it belongs behind `<Suspense>`,
and per-user data uses `use cache: private` (browser-only) or passes a stable id into a plain
`use cache`.

---

## 5. Errors are machine-readable

The backend attaches a `google.rpc.ErrorInfo` to auth failures. `src/shared/api/errors.ts` parses
it into `ApiError { httpStatus, message, reason, grpcCode }`.

**Branch on `reason`, never on `message`** — the message is human-facing text the backend may
reword (AIP-193 exists precisely so clients don't parse prose).

The status codes are meaningful:

- **401** — the *access* token is missing, expired or invalid → refresh and retry once
- **400** with a `REFRESH_TOKEN_*` reason — the refresh token is finished → clear the session and
  send the user to log in. RFC 6749 calls this `invalid_grant`, and its being a 400 rather than a
  401 is what stops a retry-on-401 client from looping on its own refresh call.
- **403** — authenticated but lacking the permission → refreshing will not help

---

## 6. Conventions

- **Validate at every boundary.** Untrusted input (`searchParams`, form data) is parsed by Zod
  before the domain sees it.
- **Server Actions are public endpoints.** Each one re-checks auth itself. A page-level check does
  not protect the actions defined on that page, and `proxy.ts` is for optimistic redirects only —
  "not your only line of defense".
- **Colocate tests** next to the code as `*.test.ts`. Vitest cannot render *async* Server
  Components, so those get E2E coverage instead.
- **Structured logging only** — objects, never string concatenation.
- **`'use client'` is a decision**, pushed to leaves, not a default.

---

## 7. Reading order

1. `src/shared/config/env.ts` — what the server needs, validated once
2. `src/shared/api/edge-fetch.ts` → `client-token.ts` → `http.ts` — how a call gets authenticated
3. `src/features/links/server/link-repository.ts` — the data access layer
4. `src/app/page.tsx` — how a route assembles a feature and places its boundary
5. `docs/PRACTICE.md` — then start building

---

## Sources

- Next.js 16.3.4 docs: project structure, authentication, authentication with Cache Components,
  data security, `cookies`, `proxy`, Vitest guide
- bulletproof-react — feature folders and the unidirectional import rule
- RFC 6749 §4.4 (confidential clients), §5.2 (`invalid_grant`), §3.2 (POST), §5.1 (`no-store`)
- AIP-193 — `ErrorInfo`, and why clients shouldn't parse messages
- The cognixus monorepo — `repositories/`, `TokenCallerType`, proto-generated types
