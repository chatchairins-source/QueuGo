# P0 Rider claim deadline (2026-11-21) — review-only

Owner policy: if no Rider claims an order in 30 minutes (Admin can choose 15), automatically cancel it. This PR closes **one part** of that P0: a rider must not be able to claim a stale job during a scheduler race. The automatic cancellation scheduler, financial settlement and notifications remain **unimplemented** here.

Production 2026-11-21 READ ONLY observed checksums:
- public.rider_claim_order(uuid): b487a093bfeebf0bac165e87cb080f02
- public.market_rider_claim_group(uuid): 590c434cc0e3ae9cace06f70cc47360b
- Admin app.orderTimeout: 30 minutes
- Existing cron dispatch cadence: 5 seconds (username=postgres)

Both updated claim functions validate a real server search timestamp, reject malformed/future clocks, use the Admin-configurable 15/30 minute timeout, and reject at >= deadline while keeping idempotent retries for already assigned work. Market group claims check all non-cancelled siblings under existing row locks. Function signature, SECURITY DEFINER status, search_path and existing ACLs are unchanged.

The new GitHub Actions test executes the **captured live Production functions**, verifies the SHA-gated replacement and 30-minute/15-minute negative tests, fresh claim success, group atomicity and notification side effects on disposable PostgreSQL 17.6.

Do not run this review-only script on Production without owner approval, migration reconciliation, encrypted backup + independently verified restore, and full live RLS/trigger/race review. The P0 owner requirement is not satisfied until a separate trusted server-owned scheduler also transitions eligible unassigned searches to cancelled atomically, cleans verified pending finance and dispatch dependents and notifies users. No live order or payment has been changed here.
