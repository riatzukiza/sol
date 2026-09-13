# Sol — Agent Guidance

Eta-mu ClojureScript agent runtime backend: Node 22 + shadow-cljs + Fastify control plane.

## Quick commands

```bash
# Build (ESM server)
pnpm build

# Dev watch (hot-reload)
pnpm watch

# Start production
pnpm start

# Tests
pnpm test

# Lint (clj-kondo + contract boundary guard)
pnpm lint
pnpm lint:kondo

# Typecheck
pnpm typecheck
```

## Architecture

Namespaces under `open-hax.sol.*`:

| Layer | Purpose |
|---|---|
| `law.*` | Contract kinds, Malli validators |
| `shape.*` | Agent shapes, app route definitions, session persistence |
| `extern.*` | JS/Node/fastify/fetch adapters |
| `domain.*` | Agent, contracts, models, realtime, text, time |
| `infra.*` | Config, HTTP server, routes, agent runtime/sessions |
| `runtime.*` | Process-local state atoms |

## Dependencies

- Maven: malli, promesa, shadow-cljs, cider-nrepl, refactor-nrepl
- Git: katamorph v0.2.0, event-ledger (pinned SHA)
- Donor source: eta-mu CLI + turn-processor (via Git subdirectory from donor SHA)
- npm: fastify, @modelcontextprotocol/sdk, @fastify/*, typebox, ws

## Build contract

`scripts/contract-guard.mjs` enforces that only `extern.*` namespaces use raw JS interop. Included in the repo (was `../../scripts/contract-guard.mjs` in the monorepo).

## License

GPL-3.0-or-later
