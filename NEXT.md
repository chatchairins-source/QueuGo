# QueueGo Next Actions

Current snapshot: 2026-10-21 · Authoritative main checked: `6669dc108137f8119c86d4de9709dacba7a301d7`

## Active chunk

**POS cash in/out shift ledger — staged source/test complete; release blocked.**

- Successor PR #568; branch `work-pos-complete-next-20261021`; exact HEAD `0f9af0cbc91af0bf7d964c9c52665ceb4e19cb9a`.
- Prior exact RC run `39617058579` passed at `119c68e`; updated exact-head run `39619648922` is in progress. Focused POS tests (36/73/18) and full `npm test` passed on the updated source tree.
- The latest change serializes concurrent retries by actor/shop/request key before receipt lookup; it does not replace the existing unique constraint or POS cash ledger.
- Cashier shifts, reconciliation, POS refund outflows and owner item reports are already deployed through earlier migrations. The cash-movement migration `20261021211709_pos_cash_register_movements` is absent from Production migration history.
- Do not implement a duplicate ledger or treat this slice as deployed. Follow `RELEASE_GATE.md` for the migration, UI activation and authenticated acceptance gate.

## Then, in dependency order

1. Re-fetch current main, related open PRs and branch descendants; verify whether #568 or a newer POS successor changed. Do not merge/deploy from this snapshot without that check.
2. Review `20261021211709_pos_cash_register_movements.sql` against actual Production grants, RLS, function ownership, table constraints and concurrent retry behavior. Run Supabase security/performance advisors and an isolated/concurrent transaction test where available. Record the evidence before any migration activation.
3. Complete authenticated owner/cashier acceptance for opening float → cash sale → cash in/out → refund → close/count/variance → history and retry. Use a dedicated authorized test identity and keep test data out of Production unless specifically permitted through the existing safe test process.
4. Continue the next highest-priority unblocked POS gap from `FEATURE_GAP_MATRIX.md` only after the shift-ledger release status is accurately recorded. Current likely discovery targets: scanner/modifier end-to-end path, supplier receiving/stock authority, offline recovery, and COGS/tax readiness.
5. Continue Delivery/Ride/Express/Laundry/Android release gates from `CHECKLIST.md` and newest successors. Physical GPS, real role accounts, provider secrets, tax policy, signing and Play Console remain blocked until available.

## Resume protocol

After each bounded chunk, update this file and `CHECKLIST.md` with status, exact SHA, tests/CI, deployment evidence and one precise next action. A new session must begin from `WORKFLOW.md` then `CHECKLIST.md` and fetch current GitHub state; this snapshot is not a substitute for a fresh ancestry check.
