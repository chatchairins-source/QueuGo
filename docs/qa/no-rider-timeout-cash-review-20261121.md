# P0 no-rider auto-cancellation candidate — REVIEW ONLY
Date: 2026-11-21. Stack: this PR targets the branch of cash-helper PR #1343.

Owner rule: Rider must be assigned before 30 minutes (or 15 minutes via Admin). A server-owned per-minute scheduler must automatically mark an eligible unassigned order cancelled, never retry offers after deadline, and notify customer/merchant exactly once.

This SQL stage adds narrow postgres Cron-only exceptions to the actual captured Production cash order, payment and delivery guards; it does NOT bypass whole trigger/RLS checks. It handles **only simple single-shop CASH**, with exactly one uncaptured pending cash ledger, one pending delivery, no market siblings or inventory reservations/receipts/Rider advances/refunds. Unknown/paid/duplicate finance data fail closed for manual review. It does not move money or refund an external processor. Existing cash-order-lock markers and offer history remain. Cancellation, ledger status, live offer withdrawal, audit and notification are one transaction; duplicate Cron reruns do not repeat notifications.

The candidate exposes a PRIVATE `qg_private.cancel_rider_timeout_cash(integer)` with PUBLIC/client EXECUTE revoked, accepts only `session_user=postgres`, uses row locks/`SKIP LOCKED` and the existing cash AFTER trigger. It does NOT deploy a Cron, any migration, or make Production writes. Installation requires migration parity, finance/operator signoff, backup with actual isolated restore and full native/E2E test.

Source-only test: GitHub Actions PostgreSQL 17.6 uses fixtures captured from Production in PR #1343 and exercises 30/15m deadlines, idempotence, cash and delivery atomicity, unchanged money amount, paid-ledger rejection, preserved offer history, customer/shop notifications and denied authenticated RPC. This is still not a real Production trigger/cron security signoff.
