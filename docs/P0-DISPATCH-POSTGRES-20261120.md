# P0 #1268: real PostgreSQL concurrency evidence

The bounded-offer migration is still **not deployed**. This receipt covers the
offer/notification guard, not financial-safe order terminalization.

On 2026-11-20, `tests/rider-offer-deadline-postgres.cjs` passed **30 checks**
against disposable PostgreSQL **17.6**, using three independent real client
connections. No Production connection or data was used.

Verified behavior with the existing live-dispatcher snapshot and exact reviewed
`20261120043000_rider_offer_deadline_guard_p0.sql`:

- Concurrent cron calls create one eligible offer and one notification.
- Expired orders and attempt 121 create no offers/notifications/history growth.
- An uncommitted claim transition holds its order lock; dispatch skips it and
  cannot re-offer after commit.
- An uncommitted terminal transition is skipped; after commit, cron cannot
  resurrect it. Rollback preserves a valid order's eligibility.
- Anonymous/authenticated roles cannot invoke dispatch or insert offers;
  service-role dispatch can run its INVOKER trigger via the existing definer.
- Rolling back trigger/function DDL retains the guard and its behavior.

The test deliberately refuses non-loopback URLs, non-test database names,
nonempty databases and PostgreSQL majors other than 17. CI uses an isolated
official PostgreSQL 17.6 service pinned to the Docker manifest digest. No new
runtime npm dependency is required; it uses the repository's locked `pg`.

Run only against an empty, dedicated local cluster:

```sh
QG_DISPATCH_TEST_DATABASE_URL=postgresql://postgres:TEST_ONLY@127.0.0.1:55432/qg_dispatch_test_ci node tests/rider-offer-deadline-postgres.cjs
```

The claim/terminal race uses direct synthetic state transitions to isolate
PostgreSQL locking. It **does not** certify real claim RPC authorization,
financial guard callbacks, stock restoration, paid-order refunds, cash-lock
release, linked Market siblings or owner-approved stranded-order resolution.
Those remain release blockers alongside backup/restore and deployed readback.
