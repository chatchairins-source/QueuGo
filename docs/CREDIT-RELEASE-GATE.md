# Rider and Merchant credit release gate

Source is staged on the Passenger Ride/Continuous Dispatch successor. Production remains unchanged. No provider API calls or test charges were made during implementation.

## Scope

One private ledger with distinct Rider and Merchant accounts; satang throughout. Merchant promotional credit is separate from paid credit, nontransferable/nonwithdrawable and consumed only for GP. New store trial is 1,000 THB, once per user after activation. Rider reserve/capture applies only to the already configured nonzero Ride platform fee. No Food Rider fee is invented.

New Merchant delivery Orders snapshot their collection mode. Legacy Orders still use the existing GP slip settlement. Credit-mode Orders cannot enter the legacy GP amount/report path. Never change existing Orders' collection mode. The migration aborts if predecessor GP/report/rule functions have drifted.

## Activation prerequisites

1. Recheck remote successor, branch ancestry and exact-head CI. Apply predecessor staged migrations in order on an isolated DB first. Back up current schema/functions/financial data before approved Production apply; do not reset/revert newer work.
2. Verify that no other existing server/branch owns this credit ledger. The audit found no deployed adapter or wallet schema in the connected QueueGo project. Prior owner discussion identified Omise/Opn and secret names, not a verified installed payment endpoint. Reconcile any separately hosted implementation before activation.
3. Verify live Omise account approval and PromptPay capability outside chat. Secrets stay server-side: OMISE_SECRET_KEY and the Base64 Omise webhook signing secret as OMISE_WEBHOOK_SECRET; prior OMISE_PUBLIC_KEY is not required by this server-created source/charge path. QUEUEGO_CREDIT_LIVE_ENABLED must remain unset until operational approval. The handler rejects test-key configuration and SQL rejects livemode=false.
4. Deploy queuego-credit (JWT required) and queuego-credit-webhook (JWT off at Supabase, but fail-closed on Omise HMAC-SHA256 signature). Verify Omise-Signature over timestamp + "." + the exact raw body, accept dual signatures during secret rotation, reject timestamps outside the 5-minute window, then independently retrieve the charge from the fixed Omise host before applying credit. Handler uses dynamic webhook_endpoints[] for only these charges; do not overwrite another account webhook. Confirm the account API supports ref_id duplicate prevention and source[qr_settings][image_type]=png with the deployed API version. Consult official Omise documentation, not historical provider fees.
5. Verify an approved real topup end to end for each role: exact charged satang, correct owner/kind, confirmed paid status, live currency THB, credited amount, independently retrieved charge, duplicate/out-of-order delivery, timeout/retry, account disable and missing webhook reconciliation. Test charges must never credit Production.
6. Verify independent PostgreSQL duplicate-payment, concurrent-spend, duplicate-capture and disconnect-before-COMMIT gates, exact-head Android builds, native foreground/process-death request recovery, Web behavior and visual QR scan readability on physical phones. Source tests alone do not establish real-money readiness.
7. Confirm account creation/GP cutover with Merchant operations. Existing accepted/preparing Orders remain legacy; new credit-mode Orders reserve before preparation, resize on item edits and debit once at pickup. Existing processing/completed legacy settlements remain untouched. Verify mixed legacy/credit dates and multi-shop suborders separately.
8. Rider Ride licence/credit eligibility: insufficient credit excludes new nonzero-fee assignments; assignment locks the fee; pre-pickup requeue releases; onboard completion captures even if intake is later disabled. No nonzero-fee active Ride may predate its matching hold. Verify reassignments and two simultaneous requests.
9. Enable feature.credit_rider / feature.credit_merchant individually through the EXISTING reviewed platform rules only after the gate. feature.credit_merchant_promo is a separate opt-in; it grants only newly inserted store profiles, once per owner. No flag is seeded by this migration. Preserve ride/saver feature gates and other fare/dispatch policies.

## Recovery and accounting

- A topup request is persisted before a provider call. Repeat the SAME UUID/amount; ref_id prevents another charge. charge.create/complete independently retrieved webhooks can bind a response lost after provider acceptance.
- A provider error/timeout is uncertain, never proof of payment failure. Keep the SAME request UUID. If charge creation may have succeeded but its response/webhook was lost, retry first searches Omise charge metadata for that UUID, requires exact ref_id + queuego_credit_topup metadata, then retrieves the charge again before applying credit. Ambiguous or unavailable search remains uncredited for operator reconciliation; no silent new payment request is created.
- A successful topup is credited once regardless of webhook order. Charge id is globally unique. Successful state cannot be downgraded by a delayed pending event. Unknown topups, different charge bindings, mismatched amount/currency and test charges are rejected.
- Before-pickup cancellation releases held GP; it does not add paid balance. After-capture fee reversal is a new immutable ledger row, requires active Admin and a reason, and is once per captured entry. It is a credit reversal, not a bank refund.
- Pause new topups with QUEUEGO_CREDIT_LIVE_ENABLED. Pause new credit-mode Merchant Orders via its feature flag; existing obligations retain their original mode. Do not drop tables/entries or rewrite history to roll back financial data.
- Reconcile account cash/promo/held sums to immutable entry deltas. Reconcile successful topup charges against provider settlement/bank receipts. Operator must resolve every missing/stuck webhook before replacing an uncertain payment. Automated financial payouts/bank refunds are outside this credit rail.
- Admin list is bounded to 200 accounts with 20 recent entries each; owner history is bounded to50 entries/10 topups. Pagination/export beyond those limits is not implemented in this release.

## Evidence

Run npm test on a committed clean tree; credit-specific suites are included. Independent PostgreSQL CI database is loopback-only queuego_credit_gate. Native Pilot builds all three apps; no APK is delivered until the full existing three-role Release Gate passes.
