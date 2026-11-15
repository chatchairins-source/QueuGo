# QueueGo release evidence boundaries (review-only)

Snapshot: 2026-11-15. Base main: `369e4b49b20a0781f6d52be1c54aef5bd473609d`.
This document is **not** a release certificate. All evidence must be rechecked against the exact proposed release SHA. No Production writes are authorized by this checklist.

| Gate | Evidence that counts | Evidence that does **not** count | Status at snapshot |
| --- | --- | --- | --- |
| Code and CI provenance | Exact release commit SHA, required workflows completed successfully at that SHA, downloadable retained logs/artifacts | Success on ancestor PR or unrelated branch | NOT VERIFIED |
| Production role security | Current SQL definitions, grants/schema usage, RLS, authenticated/anonymous role exercise, signed review of all relevant SECURITY DEFINER functions | Function-name heuristics or historical advisor counts | BLOCKED: live delta not certified |
| Migration reconciliation | Source/production ledger comparison, reviewed forward-only remediation, independent readback | Merely counting migration files | BLOCKED |
| Backup and restore | Encrypted backup inventory, digest, independently executed isolated restore and readback | Static workflow test or successful upload | BLOCKED |
| Order and payments | Isolated authenticated Customer → Merchant → Rider → Delivered → Settlement test, consistency/idempotency and rollback evidence | Aggregate production counts or mock-only tests | BLOCKED |
| Map and routing | Exact-head browser/runtime and native offline routing acceptance, retained map source SHA-256 and storage provenance | Browser assertions alone or expiring CI artifact | BLOCKED |
| Android release artifacts | Three individually signed production APKs/AABs, app-specific SHA-256, certificate fingerprint and exact-source provenance | Debug APKs, workflow ZIP digest, or emulator-only compile | BLOCKED |
| Launch operations | Approved pilot geography, support/incident owner, escalation and rollback decision | Draft operational playbook | BLOCKED |

## Evidence sources and ownership

- [Project memory](../PROJECT-MEMORY.md) is historical context, not an authoritative live release verdict.
- [Backend aggregate payment audit PR #1113](https://github.com/chatchairins-source/QueuGo/pull/1113) is read-only triage; its counts do not prove concurrent payment idempotency.
- [Integration PR #1133](https://github.com/chatchairins-source/QueuGo/pull/1133) must be reconciled with current Android-specific CI ownership before promotion.
- [Buriram map artifact retention issue #1146](https://github.com/chatchairins-source/QueuGo/issues/1146) requires permanent, digest-verified source storage.
- [Pilot operations issue #1055](https://github.com/chatchairins-source/QueuGo/issues/1055) owns operational playbooks; do not duplicate them here.
- Worker 1 exclusively owns `native-android/**`, native regression tests and Android-specific build/export workflows. This document does not change them.
- Worker 2 owns Backend implementation and Supabase audit. Worker 3 owns Web/Admin/POS implementation. Worker 4 owns CI/release test infrastructure. This document only defines cross-stream evidence acceptance.

## Fail-closed decision

Do not promote a release when any required row is BLOCKED, PENDING or NOT VERIFIED. An evidence link must identify the exact source SHA, test environment, date, outcome and retained proof. Distinguish static checks, emulators, browser checks, isolated database tests, live read-only observations and real-device field trials. Never infer one from another. Do not merge to main, deploy, alter Production payments/data, or assert production readiness from this document.
