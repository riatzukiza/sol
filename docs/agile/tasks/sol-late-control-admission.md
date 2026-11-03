---
uuid: "29ea92e6-0804-5097-badd-8985483e958e"
title: "Review lossless late-control admission for Sol turns"
status: "incoming"
priority: "P2"
points: 2
labels: "sol, turn-processor, cljs, planning, control-admission"
---

# Review lossless late-control admission for Sol turns

## Context

[Eta-mu issue 255](https://github.com/open-hax/eta-mu/issues/255) remains the original issue. Its current accepted Eta card is `kanban/tasks/sol-turn-session-late-control-messages.md`, with explicit canonical identity `sol-turn-session-late-control-messages`, Incoming / P2 / 2 points. Its exact original bytes are archived in this planning packet. This newly authored Sol Markdown has a distinct UUID and initial Incoming state: it is proposed review input for the extracted owner, not a transfer, alias, closure, operational admission, or status change of that Eta task. The cross-repository source identity is a body reference, not a fabricated same-board parent or hard dependency. Review must choose the lawful owning-board handoff before implementation; missing configured registration remains visible.

Accepted Sol main 1276955c86ff46936cd1ce7d81fbc790f7068e1e and personal main bf6c77b92246464b1e34177bc851d1ffc008c1b7 contain identical `infra/agent/turn_session.cljs` and its test namespace. The adapter clears both control queues in run-queued-turn!'s finally; enqueue-control! returns Promise.resolve(nil) while a turn streams. Existing mid-turn and idle tests do not exercise an arrival after the final drain and before settlement. This is a source-confirmed surviving gap, not a runtime reproduction or deployment claim.

## Outcome

Every steer! / follow-up! accepted by a Sol session is either delivered according to a reviewed turn policy or explicitly rejected to its caller. A control message must never be silently discarded after a successful-looking promise resolution. Preserve serialized turns, streaming state, history, subscriptions and current-turn post-settlement observations.

## Scope

The complete original requirements remain controlling:

1. Re-enqueue leftover queue contents as a fresh turn instead of clearing them, or reject the late steer! / follow-up! promise so the caller learns it was not accepted. Silently resolving nil is ruled out.
2. Deliberately decide and record the semantics of both late steer and late follow-up. The original note that a late follow-up may be safe to drop does not permit a successful discard: an intentional refusal must reject explicitly.
3. Directly test the race by queuing a control message after the final drain and asserting delivery to a subsequent turn or explicit rejection.

Proposed review decision: after normal completion, transfer undrained controls into the existing serialized pending-turn mechanism, preserving each queue's order and choosing/documenting cross-kind ordering. On failure, abort or a refused transfer, reject each affected undelivered control with an explicit reason. Do not start a fresh turn that bypasses an intentional cancellation. Acknowledgement timing must make a later discard observable; a prior Promise.resolve(nil) cannot be repaired by attempting to reject an already settled promise. Alternative rejection-only policy remains acceptable only if reviewed for both control kinds and preserves the whole contract.

Separate portable `.cljc` decisions for shaped queue/turn state, ownership/ordering and handoff-or-refusal from the existing CLJS atom/promise/AbortController/run-loop adapter. Use the existing IAgentSession and injected run-loop; do not create another runner. Review the exact consumption boundary: removal from a queue is not by itself proof that the run-loop delivered the message. The run-loop's abort-before-drain behavior and Sol's error/abort cleanup must agree.

## Non-goals

No runtime implementation in this PR. No provider/model requests, service deployment, persistence or restart protocol, Clio envelope changes, durable review-worker hosting, duplicate cancellation API, new board engine, or edits to Eta donor packages. Merged personal Sol worker/runtime/Clio work and accepted origin history remain intact. Later origin integration is separately qualified work.

## Acceptance criteria

- Both original Definition of Done requirements remain intact: **no code path resolves a steer! / follow-up! promise with the message discarded**, and **Sol test / lint:kondo gates are green**. Preserve original commands `pnpm --filter @eta-mu/sol test` and `pnpm --filter @eta-mu/sol lint:kondo` as historical monorepo verification, then explicitly map to standalone owner's actual package scripts without a gate waiver.
- Record reviewed policy for mid-turn, idle and late controls of both kinds, normal completion, failure and abort; pending-turn ordering, acknowledgement/rejection timing and actual delivery ownership are explicit. Existing public protocol behavior is preserved or a required compatibility change is reviewed before code.
- Meaningful RED uses a deterministic barrier after the last run-loop drains and before its return, not a sleep or generic mock that duplicates queue-clearing implementation. Exercise steer and follow-up separately and together; verify caller promise outcome, delivered message identity/history, number/serialization of turns and absence of duplicate delivery. Normal drain/idle controls are positive controls. Failure/abort, concurrent pending turns and refusal are negative controls.
- Pure queue/decision fixtures are portable JVM and compiled CLJS where practical; host integration uses actual TurnSession with injected documented run-loop seams. Verify abort-before-drain behavior against the pinned owning turn-processor; a test double alone is not proof of its current implementation. No real model/provider is needed for this race.
- GREEN follows reviewed laws/tests, then domain and outer adapter. Full owning gates include lint/kondo and contract guard, completed nonempty CLJS tests with zero compiler warnings, guard regressions, planning regressions, server build, existing native-Clio and isolated startup/SIGTERM checks wherever the current owner requires them. Preserve every existing test; no namespace exclusion, false-success exit or unrelated suite bypass.
- Planning review and a lawful native Rheos Ready handoff precede RED/implementation. The original Eta card remains unchanged and this proposed Incoming artifact has no native registration/readiness claim. Review point estimate 2 and split/re-estimate explicitly if policy or protocol scope grows; do not reduce acceptance to fit.

## Verification

Future proof matrix is in docs/design/late-control-admission-planning.md. This planning change verifies only exact source/issue/card provenance, preservation, receiving workflow draft boundary and canonical receipt/reflection admission. No compiler, runtime race, provider or deployment success is claimed.

## Risks

Early acknowledgements, abort races and cross-kind ordering are semantic choices, not only finally-block cleanup. Existing HTTP callers may depend on immediate control acknowledgement; review that compatibility. The personal default contains merged prerequisite work but does not confer approval to this plan. Current canonical merged-PR 3 intake failed HTTP500; retain the failure and do not infer native qualification. No configured Sol board was found in either exact Git tree; manual Markdown is supported input, not permission to invent board config or operational state.
