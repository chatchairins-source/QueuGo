# QueueGo Pilot Security Audit — 7 October 2026

## Result

Code/database security audit: **PASS**

Platform Auth hardening: **BLOCKED separately** until Supabase leaked-password protection is enabled.

## Anonymous SECURITY DEFINER surface

Supabase Security Advisor initially reported 11 anonymous SECURITY DEFINER RPCs.

Two unnecessary anonymous endpoints were removed from anonymous access:

- `market_public_catalog()` — legacy catalog; production Customer uses `market_public_catalog_v2()`.
- `queuego_nearest_market(double precision,double precision,numeric)` — not called by the current public client; retained for authenticated/service use.

Nine anonymous RPCs remain intentionally exposed and are recorded in:

- `docs/security-anon-definer-allowlist.json`

They are limited to:

- public Market/storefront data,
- public promotions/reviews, or
- guest QR table ordering guarded by session/device binding, expiry, geofence, cart bounds, ownership and idempotency checks.

Live privilege verification confirmed all 9 allowlisted RPCs are executable by `anon`, while the two removed endpoints are not.

## Authenticated SECURITY DEFINER review

Authenticated SECURITY DEFINER functions were scanned for direct or delegated authorization controls.

All non-anonymous functions matched at least one reviewed authorization path:

- direct `auth.uid()` checks,
- `get_my_user_id()` / `get_my_role()`,
- `is_active_admin()`,
- POS ownership/permission helpers: `pos_allowed()`, `pos_my_shop()`, `pos_is_owner()`, or
- checkout delegation to `queuego_place_cash_order_core()`.

The POS helpers were inspected directly and bind access to the authenticated shop owner/staff record. The cash-order core checks authenticated active Customer ownership before any order mutation.

No authenticated non-anonymous SECURITY DEFINER candidate remained without an obvious direct or delegated authorization guard.

## UGC / chat safety

- Terms acceptance required before sending chat messages.
- Server insert guard enforces terms and block state.
- Customer and Rider can report/block.
- Admin has a moderation queue.
- Report evidence snapshot survives normal chat expiry.
- Account deletion clears UGC terms/blocks and removes personal moderation snapshot content.
- RLS-only helper was moved from `public` into `queuego_private`.
- Anonymous table privileges on `public.order_chat_messages` were revoked in migration `20261007023348_ugc_chat_revoke_anon_table_privileges`; authenticated CRUD privileges remain explicit and are still constrained by RLS.

Transactional smoke test passed for accept → report → block → unblock and was rolled back without leaving test data.

## RLS / performance hardening

- `auth_rls_initplan` findings: 13 → 0.
- Unindexed FK findings: 44 → 8 after indexing Pilot hot paths.
- UGC internal tables deny direct `anon` / `authenticated` table access.
- Live post-migration privilege verification confirmed `anon` has no SELECT/INSERT/UPDATE/DELETE privilege on `order_chat_messages`, while the authenticated role retains the required chat table privileges.

## Runtime verification

After the final anonymous surface reduction, the checked production window contained no Postgres ERROR and no Edge Function HTTP 4xx/5xx.

## Remaining external security blocker

Supabase Security Advisor still reports leaked-password protection disabled. This is a Supabase Auth platform setting and remains a separate Closed Beta hard gate:

- `release_gates.security_platform_auth = BLOCKED`

Do not change that gate to PASS until leaked-password protection is enabled and rechecked.
