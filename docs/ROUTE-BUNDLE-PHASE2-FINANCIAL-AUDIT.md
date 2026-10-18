# Phase 2 financial simulation dependency

Audited source: `945394e3bdb3dd1a43aa1f6e4e1ffe067eb92d8a` (PR #432). Read-only source audit; no provider calls, Production queries, claims, repricing or feature changes.

## Existing implementation to reuse

- `20261014234500_rider_continuous_dispatch_phase1.sql` supplies generalized `qg_private.route_bundle_simulate` for up to five Orders, but its financial quote is two Orders only. `20261015012500_merchant_readiness_dispatch_eta_funding.sql` renames that quote to `qg_route_bundle_quote_orders_without_readiness_v3` and wraps it with merchant-readiness checks. These are existing implementations, not missing services to recreate.
- `stack-planner.mjs` currently returns route distance/time/stops only. Its SQL input supplies status/sequence/created time, not committed prices, locked payouts, merchant readiness or quality baselines. A route result alone cannot certify financial eligibility.
- Live claim remains explicitly capped at two, including when configuration requests five. Preserve this restriction.

## Actual pair policy

| Existing rule | Quote fallback | Meaning |
| --- | ---: | --- |
| `route_bundle.max_detour_km` | 1.5 km | Additional route distance limit |
| `route_bundle.max_delay_minutes` | 10 min | Delay limit, reduced to at most five for food/cafe |
| `route_bundle.incremental_km_rate` | 5 THB/km | Additional-distance compensation |
| `route_bundle.pickup_cost` | 2 THB | Additional pickup handling |
| `route_bundle.dropoff_cost` | 2 THB | Additional dropoff handling |
| `route_bundle.minute_rate` | 1 THB/min | Additional-time compensation |
| `route_bundle.primary_savings_percent` | 50% | Two-way saving split, configuration guard allows 1–99 |
| `route_bundle.min_rider_extra_fee` | ignored / zero | Legacy wire field; no bonus/fixed-extra floor |

Fallbacks are source defaults, not evidence of deployed rule values. Pair incremental compensation is `ceil((distance cost + pickup + dropoff + time cost) * 100) / 100`. Available saving is `max(round(candidate current delivery fee - incremental compensation, 2), 0)`. Primary saving is `min(round(saving * split / 100, 2), max(primary current delivery fee, 0))`; candidate receives the residual saving rounded to two decimals. Each customer fee falls by their own saving. Both savings must be positive. Primary Rider payout is preserved from its locked payout (otherwise its current fee); candidate payout is incremental compensation. No bonus is introduced.

The exact-cent revenue/payout equality in the existing pair test uses primary payout equal to primary current fee. A prior locked payout can differ from the current discounted customer fee; therefore a generalized simulator must expose the existing funding difference rather than assert zero platform difference or silently lower payout. Likewise, a candidate has no committed Rider assignment yet: the existing policy does not promise that candidate's standalone fee as Rider payout. Preserve assigned payout floors; do not invent a new sum-of-solo-fees policy.

Readiness wrapper requires both merchant estimates and fresh exact-revision road evidence, and rejects pickup wait above five minutes. Generalized simulation must account for waits in arrival/delay/time cost rather than simply reuse a travel-only route duration.

## Smallest next step, pending a versioned business rule

Reuse existing exact-revision stack evidence and route planner. First define a versioned read-only allocation contract for **three to five Orders**: which prior Orders share a new saving, how the existing primary-percent split maps to more than two beneficiaries, deterministic residual-satang allocation, and funding of any gap between already locked payouts and currently charged delivery fees. No existing setting defines these choices; repeatedly applying pair quotes would reset baselines and can double count savings/costs. This is a code dependency requiring an agreed rule, not permission to deploy.

Once that contract exists, add a non-mutating simulation result to the existing planner path (no second pricing service). Its server-read input must include current customer-fee ceilings, previously locked per-Order Rider payouts, exact revisions, rule snapshot/version, readiness estimates, committed next stop and each delivery's quality baseline. Its output should include per-Order fees/savings/payout floors, incremental burden, route totals and explicit funding delta, with either eligible or a rejection reason. Keep money as integer satang and reject invalid/unrepresentable values. Reuse pair SQL as a two-Order parity oracle; do not rewrite it as live generalized pricing.

Required tests after rule agreement: pair parity including decimal/rounding boundaries; 2–5 Orders; each customer fee no higher than its current committed fee; every existing Rider payout retained; no bonus; exact satang conservation including explicit funding delta; deterministic residual allocation; no savings/negative budget rejection; readiness waits included in delays; missing/stale evidence rejection; unchanged Orders/payments/dispatch/feature flags. Source and isolated tests cannot certify physical or financial release readiness.
