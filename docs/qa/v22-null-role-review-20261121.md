# P0: Null-role v22 cash guard – Review-only security proof

Observed Production function MD5 (READ ONLY, 2026-11-21): e62b8c1fd2193ea386aedcee96cfb6dd.

A manual negative test captured a **defense-in-depth gap**: the existing second cash-order guard correctly prevents an unauthenticated direct transition, but the `qg_v22_order_transition_guard()` check used `get_my_role()<>'admin'` and SQL NULL does not satisfy that predicate. This is not evidence that an external client can bypass the complete live guard stack.

This PR adds the exact source-based replacement changing just that predicate to `get_my_role() IS DISTINCT FROM 'admin'`. The captured source checksum is checked before replacing. The disposable PostgreSQL test reproduces the original gap with other triggers temporarily disabled in a ROLLBACK-only test, then confirms the candidate rejects it, and confirms the legitimate private Cron cancellation still cancels all eligible financial dependencies atomically.

No Production migration/deploy. Requires operator approval, migration parity/isolated restore, financial/RLS review and final integrated CI before live change. P0 #1268 remains open.
