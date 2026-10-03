# Sol dependency and build evidence

Observed 2026-10-03 in the clean standalone fork at
`1276955c86ff46936cd1ce7d81fbc790f7068e1e`. No sibling package, credential,
production service or dependency manifest was changed for these probes.

| Probe | Actual result |
| --- | --- |
| `clojure -P` | Exit 0. Resolved `deps.edn`, including eta-mu `0ed56aa…` subdirectory Git dependencies, Katamorph `305a5e4…` and event-ledger `ada7374…`. No missing immutable Git dependency observed. |
| `clojure -M -m shadow.cljs.devtools.cli compile server` | Exit 0; 181 files, 165 compiled, **0 warnings**, 7.55 seconds. Generated ESM retains Node/npm imports; compile success does not prove runtime startup. |
| `clojure -M -m shadow.cljs.devtools.cli compile test` | Exit 0; 191 files, 190 compiled, **0 warnings**. **Autorun failed** with `Cannot find module '@modelcontextprotocol/sdk/client/index.js'`. Also reported missing `source-map-support`. No passing CLJS test counts. |
| `npm ci --ignore-scripts --no-audit --no-fund` | Exit 1, `EUSAGE`: package/lock mismatch. Locked shadow-cljs `3.4.4` cannot satisfy manifest `^3.4.10`; resolver reported `3.5.4` plus missing buffer/process/readline-sync/shadow-cljs-jar/source-map-support/which dependencies. Nothing was installed successfully. |
| `pnpm lint` | Exit 0. Kondo **0 errors, 0 warnings**; contract-guard OK. Kondo also emits inherited layering **info** diagnostics; they are not proof that layering is clean. |
| `node --test scripts/contract-guard.test.mjs` | Exit 0, 4 passed, 0 failed. Tests the existing guard, not the CLJS runtime. |

Tools were present: Node **24.14.1**, Clojure CLI **1.12.2.1565**, Java,
Babashka, pnpm, clj-kondo. This was **not** a Node 22 runtime qualification.
pnpm warned that the operator npmrc contains an unresolved `NPM_TOKEN`
placeholder; no secret value was printed or supplied.

The lockfile root still identifies `@open-hax/knoxx-backend-cljs` and includes
the older broad Knoxx dependency surface. The package manifest identifies
`@eta-mu/sol`. A reproducible package-manager/lockfile repair belongs in a
separate runtime prerequisite change, with Node 22 clean install, actual CLJS
test counters, server startup and both server/test compilation. Do not update
the immutable Git pins or pull local sibling sources merely to hide this npm
failure. Do not install an unrecorded floating npm graph as build proof.

The existing `scripts/run-shadow-tests-ci.mjs` refuses success when it cannot
parse CLJS result counters. The package `test` alias invokes shadow directly,
so its zero exit status alone is insufficient. This planning PR does not change
the runtime/test command; future qualification must preserve that honest gate.

The proposal's smallest runnable check is:

```bash
bb scripts/check-review-worker-plan.clj
bb scripts/check_review_worker_plan_test.clj
```

It reads EDN, requires the complete immutable Git dependency set from `deps.edn`
and an independently reviewed v1 source-file inventory, compares complete pin
fields, verifies namespace anchors, and ensures the proposal stays disabled.
The CLI regression fixtures prove that omitted/extra dependencies or integration
files, incomplete pins, duplicates and service activation are rejected.
It is only a structural/drift check. Canonical Katamorph resource validation,
Clio replay, Rheos-ready state and hosted MiMo/Kimi execution are still pending.
