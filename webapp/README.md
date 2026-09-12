# webapp — the browser front end for the gRPC URL shortener

A Next.js 16 app that talks to the Envoy edge (`:8080`) in front of the gRPC services. It is also
a practice project: one feature is fully built as a reference, and `docs/PRACTICE.md` walks you
through building the rest.

## Run it

```bash
nvm use                       # Node 24 (see .nvmrc)
cp .env.example .env.local    # dev values, matching compose.yaml's defaults
cd .. && docker compose up -d # the backend: services + edge on :8080
cd webapp && npm run dev      # http://localhost:3000
```

The home page lists every short link. It gets there without you logging in, because the server
authenticates as the `publicweb` **client** to read public data.

## Quality gate

Run this before calling anything done:

```bash
npm run check   # lint + typecheck + test + build
```

The build deliberately does **not** need the backend running.

## Contract types

TypeScript types come from the same `.proto` files the Java services compile:

```bash
npm run gen:proto   # buf + protoc-gen-es -> src/shared/api/gen/**
```

Generated output is committed, so a contract change shows up as a reviewable diff. Re-run it after
editing any `.proto` and let `npm run typecheck` tell you what broke.

## Where things live

| Path | What |
| --- | --- |
| `src/app/` | routing only: pages, boundaries, layouts |
| `src/features/<feature>/` | one folder per domain: `server/`, `actions/`, `schema/`, `components/` |
| `src/shared/` | cross-cutting: `api/` (edge client, tokens, generated types), `config/`, `observability/` |
| `docs/ARCHITECTURE.md` | how it's structured and why, with sources |
| `docs/PRACTICE.md` | the ordered task list — start here after reading the reference slice |

Start with `docs/ARCHITECTURE.md`, then read `src/features/links/` end to end, then open
`docs/PRACTICE.md`.
