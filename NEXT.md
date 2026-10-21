# QueueGo Next Actions

Current snapshot: 2026-10-21 · Authoritative main: `c79640e0c896cc81961e8ba2dc69d336a9f519b0`

## Active chunk

**POS SKU, modifiers and generic inventory adjustments — source complete; exact merged-main RC required; production activation remains blocked.**

- PR #568 cash movement source was merged after exact-head QueueGo RC `39622237860` completed all 12 test/browser steps successfully. Merge commit: `c79640e0c896cc81961e8ba2dc69d336a9f519b0`; main now contains the audited cash-in/out UI and RPC source.
- PR #572 (`queuego-pos-sku-scan-pr`) was retargeted to `main`; inventory source commit `3ac73c2c1aa9221c392e5cfcaca5afd82653dd15`. Source RC `39633575108` passed before retarget. Status documents now record the merge and schema readback; fetch the live PR head before each continuation, then trigger a fresh exact-head RC after retarget.
- Read-only Production schema verification: migration history ends at `20261021133330`; `pos_register_cash_events` has RLS enabled, `anon SELECT=false`, `authenticated SELECT=true`, `authenticated INSERT=false`; it has no `event_type`/`idempotency_key` columns and `pos_record_cash_movement(...)` is absent. The merged cash feature is not live. The four PR #572 inventory migrations are also unapplied.
- Production Supabase advisors were read. Security lints include intentionally private tables with RLS/no policies and public/anon SECURITY DEFINER API warnings; review each finding against the intended endpoint policy before activation. Performance lints include unindexed foreign keys. No advisor-based change or database mutation is authorized by these findings alone.
- PR #572 source adds shop-scoped SKU/barcode, server-authoritative options/prices, opt-in generic inventory reservation across POS/QR/Delivery, manager-only idempotent stock adjustments and private audit history. Grocery Master and Market stock owners remain separate.

## Then, in dependency order

1. Verify fresh exact-head CI for #572 after retarget. If green, continue the authorized review; do not declare POS production-ready.
2. Review staged POS migrations against actual Production schema, roles, RLS/ACL, trigger order and forward-repair plan. Apply only through the controlled release process after the real cashier/user acceptance gate is available.
3. Complete authenticated cashier flows: shift open/float → sale → cash movement/refund → close/count/variance/history; then barcode/options/bill and inventory reservation/adjustment/cancel/refund across POS, QR and Delivery. Use authorized test accounts and avoid fake Production data.
4. After inventory release dependencies are satisfied, implement supplier directory + purchase order receiving through the same generic inventory adjustment/audit authority; do not duplicate Grocery Master or Market ledgers. Then cost history/COGS, item exchanges, hardware and offline recovery by dependency.
5. Continue the highest-priority unblocked North Star row from `FEATURE_GAP_MATRIX.md`, always checking current main and newest successor first. QueueGo and POS remain **NOT DONE** until role journeys and applicable release evidence pass.

## Resume protocol

After each bounded chunk, update this file and `CHECKLIST.md` with exact SHA, local/hosted tests, deployment/readback evidence, blockers, and one precise next action. A new session must begin from `WORKFLOW.md`, `CHECKLIST.md`, current main and the newest successor.
