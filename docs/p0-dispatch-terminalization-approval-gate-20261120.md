# P0 #1268 — Dispatch timeout / financial terminalization approval gate

Status: **NO-GO for Production cancellation**. Source migration 20261120043000 only blocks stale offers; it does not cancel orders, release cash locks, reverse payments, restore inventory, or notify merchants. Do not reapply #1298 or assume a source merge is a deployment.

## Pre-deployment preflight (must be independently approved)
1. Freeze an exact migration-history diff against Production (392 historical entries were previously observed; re-read actual count), record integration SHA, trigger/function hashes, privileges, RLS and cron definition. Fail on drift.
2. Record encrypted database backup artifact, its SHA-256, timestamp, retention and an **actually successful isolated restore**. Record restore run URL. If any are missing, stop.
3. In a **disposable PostgreSQL database**, replay reconciled migrations and representative anonymized fixtures. Exercise concurrent dispatcher and timeout transactions; ensure no new offers or notifications after deadline, no cron resurrection, and at-most-once terminal transition.
4. Financial fixtures must cover cash lock present/absent, captured/authorized/unpaid/refunded payment, reserved/committed/released inventory, market multi-shop siblings, merchant notification retries, duplicate timeout calls, and failures between steps. Verify atomic rollback leaves all ledgers consistent.
5. Require explicit written authorization from incident operator, database owner, payments/finance owner and QA before applying reviewed migration. Production deployment must be separately approved; this branch grants no permission.
6. The stranded live order is **excluded from automated cleanup** until its payment, stock and merchant state are individually reconciled by an authorized operator. Never impersonate a customer/rider.

## Fail-closed terminalization contract for the next migration
- Server-owned, non-public entrypoint only; revoke PUBLIC/anon/authenticated EXECUTE; pin search_path and qualify all objects.
- Acquire order row lock; check timeout and expected searching state; reject accepted rider, active offer, unsettled cash lock, nonterminal payment, unknown inventory reservation, or linked active market siblings.
- Use durable idempotency key unique per order+reason. In a single transaction transition order, release **verified** reservations/locks and write exactly-once merchant outbox event; abort all on any uncertain financial state.
- A separate, authenticated payment reconciliation workflow must handle external refunds; never equate a database status change with processor refund.
- Concurrent cron/dispatch must not resurrect cancelled orders; post-deploy read-only monitoring must prove new offers and alerts cease for expired searches.

## Read-only monitoring
Run `ops/p0/dispatch-terminalization-preflight-readonly-20261120.sql` against Production only after review; it returns aggregate counts, not identifiers. Record UTC observation time. Compare offer/notification highwater before and after an **approved** deployment; historical totals alone cannot establish a recurrence.

## Rollback
Stop dispatcher schedule via separately approved change if necessary, restore previous reviewed function/trigger in a forward migration after verifying signature/privileges, and re-run negative tests. Do not blindly reverse financial writes or replay external payments. Database restore requires independent authorization and a tested point-in-time recovery plan. Escalate real stranded orders for manual reconciliation.
