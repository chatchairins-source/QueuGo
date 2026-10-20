# Rider and Merchant credit release gate

Source is staged on the Passenger Ride/Continuous Dispatch successor. Production remains unchanged. No provider API calls or test charges were made during implementation.

## Scope

One private ledger with distinct Rider and Merchant accounts; satang throughout. Merchant promotional credit is separate from paid credit, nontransferable/nonwithdrawable and consumed only for GP. New store trial is 1,000 THB, once per user after activation. Rider reserve/capture applies only to the already configured nonzero Ride platform fee. No Food Rider fee is invented.

GP collection uses credit only. Owner confirmed all prior Orders were simulated tests and rejected the legacy slip path. Old test Orders are not migrated or charged retroactively. Legacy GP RPCs, settlement writes/API, slip bucket access and UI are retired. GP-bearing new checkout fails closed while Merchant credit is disabled. The additive retirement migration follows the credit predecessor; do not skip migration ordering.

## Admin free Merchant credit

The owner authorizes Admin-issued promotional GP credit independently of paid topups. No Omise approval or payment is needed for a real, explicitly approved promotional grant. Use the existing promo ledger; never label the grant as cash or a paid bank transaction. Apply the Admin grant migration after its credit/retirement predecessors. The Admin UI must list active unarchived stores even if they have no ledger account, require amount and reason, persist a request before RPC, and retry ambiguous responses with the same UUID. Verify role/session/suspension, duplicate/mismatched requests, simultaneous grants and audit rollback on an isolated database before rollout. Do not seed every shop or enable the paid intake flag for this feature. Production schema deployment and Merchant GP activation still require their migration and Order-lifecycle checks.

## Activation prerequisites

1. Recheck remote successor, branch ancestry and exact-head CI. Apply predecessor staged migrations in order on an isolated DB first. Back up current schema/functions/financial data before approved Production apply; do not reset/revert newer work.
2. Verify that no other existing server/branch owns this credit ledger. The audit found no deployed adapter or wallet schema in the connected QueueGo project. Prior owner discussion identified Omise/Opn and secret names, not a verified installed payment endpoint. Reconcile any separately hosted implementation before activation.
3. Verify live Omise account approval and PromptPay capability outside chat. Secrets stay server-side: OMISE_SECRET_KEY and the Base64 Omise webhook signing secret as OMISE_WEBHOOK_SECRET; prior OMISE_PUBLIC_KEY is not required by this server-created source/charge path. QUEUEGO_CREDIT_LIVE_ENABLED must remain unset until operational approval. Current Omise documentation shows live secret keys in both `skey_live_...` and `skey_...` forms across current pages; the handler accepts either live-only form while explicitly rejecting `skey_test_...`, and SQL rejects livemode=false.
4. Deploy queuego-credit (JWT required) and queuego-credit-webhook (JWT off at Supabase, but fail-closed on Omise HMAC-SHA256 signature). Verify Omise-Signature over timestamp + "." + the exact raw body, accept dual signatures during secret rotation, reject timestamps outside the 5-minute window, then independently retrieve the charge from the fixed Omise host before applying credit. Handler uses dynamic webhook_endpoints[] for only these charges; do not overwrite another account webhook. Confirm the account API supports ref_id duplicate prevention and source[qr_settings][image_type]=png with the deployed API version. Consult official Omise documentation, not historical provider fees.
5. Verify an approved real topup end to end for each role: exact charged satang, correct owner/kind, confirmed paid status, live currency THB, credited amount, independently retrieved charge, duplicate/out-of-order delivery, timeout/retry, account disable and missing webhook reconciliation. Test charges must never credit Production.
6. Verify independent PostgreSQL duplicate-payment, concurrent-spend, duplicate-capture and disconnect-before-COMMIT gates, exact-head Android builds, native foreground/process-death request recovery, Web behavior and visual QR scan readability on physical phones. Source tests alone do not establish real-money readiness.
7. Apply the owner-directed credit-only retirement together with the reviewed credit rollout. Verify stale-app RPC, direct settlement write and old slip upload denial. No old test debt is carried forward, no old Order is backfilled or charged, and no balance is fabricated. New GP-bearing Orders reserve before preparation, resize on item edits and debit once at pickup. Validate each real market suborder separately.
8. Rider Ride licence/credit eligibility: insufficient credit excludes new nonzero-fee assignments; assignment locks the fee; pre-pickup requeue releases; onboard completion captures even if intake is later disabled. No nonzero-fee active Ride may predate its matching hold. Verify reassignments and two simultaneous requests.
9. Enable feature.credit_rider / feature.credit_merchant individually through the EXISTING reviewed platform rules only after the gate. feature.credit_merchant_promo is a separate opt-in; it grants only newly inserted store profiles, once per owner. No flag is seeded by this migration. Preserve ride/saver feature gates and other fare/dispatch policies.

## Recovery and accounting

- A topup request is persisted before a provider call. Repeat the SAME UUID/amount; ref_id prevents another charge. charge.create/complete independently retrieved webhooks can bind a response lost after provider acceptance.
- A provider error/timeout is uncertain, never proof of payment failure. Keep the SAME request UUID. If charge creation may have succeeded but its response/webhook was lost, retry first searches Omise charge metadata for that UUID, requires exact ref_id + queuego_credit_topup metadata, then retrieves the charge again before applying credit. Ambiguous or unavailable search remains uncredited for operator reconciliation; no silent new payment request is created.
- A successful topup is credited once regardless of webhook order. Charge id is globally unique. Successful state cannot be downgraded by a delayed pending event. Unknown topups, different charge bindings, mismatched amount/currency and test charges are rejected.
- Before-pickup cancellation releases held GP; it does not add paid balance. After-capture fee reversal is a new immutable ledger row, requires active Admin and a reason, and is once per captured entry. It is a credit reversal, not a bank refund.
- Pause new topups with QUEUEGO_CREDIT_LIVE_ENABLED; verified signed paid callbacks remain settleable. Disabling Merchant credit refuses new GP-bearing checkout; it never enables a legacy fallback. Existing genuine credit reservations retain their lifecycle. Do not rewrite immutable credit entries.
- Reconcile account cash/promo/held sums to immutable entry deltas. Reconcile successful topup charges against provider settlement/bank receipts. Operator must resolve every missing/stuck webhook before replacing an uncertain payment. Automated financial payouts/bank refunds are outside this credit rail.
- Admin list is bounded to 200 accounts with 20 recent entries each; owner history is bounded to50 entries/10 topups. Pagination/export beyond those limits is not implemented in this release.

## Evidence

Run npm test on a committed clean tree; credit-specific suites are included. Independent PostgreSQL CI database is loopback-only queuego_credit_gate. Native Pilot builds all three apps; no APK is delivered until the full existing three-role Release Gate passes.


### Later Ride guard compatibility (2026-10-20)
The Ride production migration20261017113000 redefines queuego_platform_rule_guard and drops earlier credit/continuous-dispatch keys. After reconciling the actual deployed history, include forward migration20261020091222_preserve_credit_feature_rule_guard.sql. It only restores validated key support; it does not turn on credit, dispatch or Ride. The rollout-chain regression now replays the later guard before this reconciliation, preserves tariff constraints and verifies rules/ACL unchanged. Do not apply the older13-file chain blindly over a newer Production schema: complete predecessor/function compatibility and provider/physical gates still apply.
