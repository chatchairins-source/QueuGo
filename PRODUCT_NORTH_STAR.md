# QueueGo Product North Star

Last reviewed: 2026-10-21 · Repository: `chatchairins-source/QueuGo` · Main: `6669dc108137f8119c86d4de9709dacba7a301d7`

## Mission

Build QueueGo as one production-grade platform for local commerce and mobility: Delivery, Marketplace, Grocery, Laundry, Ride, Express, and Merchant POS. Product decisions should serve real customer, merchant, rider, cashier, and operator journeys. Do not wait for an owner to enumerate ordinary product requirements.

Each proposed capability must reuse and extend the current system of record. Do not create a second dispatcher, wallet, inventory ledger, route engine, refund authority, or order state machine when an existing one owns that responsibility. Preserve real-data, role, shop, payment, and release gates. Never conceal missing behavior behind a visual control or mark source work as a successful live flow.

## Operating loop

For each bounded capability:

1. Read `WORKFLOW.md`, `CHECKLIST.md`, this file, `FEATURE_GAP_MATRIX.md`, `RELEASE_GATE.md`, and `NEXT.md`.
2. Fetch current `main`, related open PRs, branches, and the newest descendant HEAD. Confirm ancestry before selecting a worktree.
3. Inspect current UI, backend, migrations, permissions, deployment and evidence. Research current official product documentation for relevant patterns.
4. Record the gap and evidence. Implement one coherent slice in the current owner implementation.
5. Run focused tests, then the required full suite and exact-head CI. Commit and checkpoint the exact SHA and next action.
6. Merge, deploy and verify served/runtime behavior only after the relevant gates pass. Keep owner-only, physical-device, credential, payment-provider and production cutover steps blocked until evidence exists.

## Product quality bar

- **End-to-end:** each role can complete the real journey, recover from interruption and understand the current state.
- **Authoritative:** server-side state, prices, permissions, inventory and money are validated atomically. Client UI is not authority.
- **Recoverable:** retries reuse durable request identities; ambiguous outcomes can be reconciled; offline behavior never silently duplicates or loses work.
- **Operational:** staff can see actionable exceptions, audit history, health and next steps without raw internal IDs or redundant controls.
- **Accessible and usable:** mobile-first layouts, readable Thai, clear focus and errors, large touch targets, sensible loading/empty states, and predictable navigation.
- **Secure and scoped:** actor, role, shop, service and location checks remain server-side; RLS and grants are tested; secrets stay server-side.
- **Measurable:** latency, failures, financial variance, inventory discrepancies and release evidence have explicit owners and thresholds.
- **Honest:** source, isolated tests, CI, hosted output, real accounts, physical devices and Production each have separate evidence states.

## Product-wide boundaries

- QueueGo Delivery and Merchant POS have separate ledgers and responsibilities. Shop POS refunds stay in POS; do not route them through Admin or Delivery GP.
- Customer-facing and merchant-facing prices, fees, refunds, promotions and taxes must be shown before confirmation and reconcile to an immutable server record.
- QueueGo keeps the current cash-first business rules unless an owner-approved requirement changes them. Payment providers remain disabled until credential, signature, settlement and replay gates pass.
- Route, ETA and tracking may display estimates, but must never invent location, route progress, orderability, product rights or live completion.
- Marketplace and Grocery inventory must reuse existing product/stock authorities and atomic checkout locks.
- Feature flags, route bundle limits, pricing and availability remain fail-closed until the relevant release gate is satisfied.

## External product research

Use official documentation to learn interaction patterns, not proprietary implementation or assets. Research refreshed 2026-10-21:

- [Shopify POS features](https://www.shopify.com/pos/features) documents multi-location inventory, scanned receiving/counts, purchase orders, returns/exchanges, staff attribution, hardware and checkout controls.
- [Shopify POS permissions](https://help.shopify.com/en/manual/your-account/users/roles/permissions/pos-permissions) separates sensitive order, return, inventory and offline actions, with manager approval options.
- [Shopify offline checkout](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline/offline-checkout) explains controlled offline mode and automatic sync after reconnection.
- [Square for Retail capabilities](https://squareup.com/us/en/retail/capabilities) and [Square purchase orders](https://squareup.com/help/us/en/article/8258-create-purchase-orders-with-square-for-retail) cover supplier ordering, receiving, offline payment and cash-flow management.
- [Lightspeed purchase orders](https://x-series-support.lightspeedhq.com/hc/en-us/articles/25533748112923-Creating-a-purchase-order) covers supplier-linked ordering and receiving; [staff roles](https://o-series-support.lightspeedhq.com/hc/en-us/articles/35765184351387-Managing-staff-access-with-user-roles) shows role-based separation of sales, stock and reporting.

QueueGo should evaluate these patterns against Thai merchant workflows, current schema, provider availability and operating cost before adoption.

## Current execution priority

1. Close the staged POS shift-ledger slice from the latest verified successor; its Production migration is not yet applied.
2. Keep the POS end-to-end / physical hardware / tax readiness gate explicit.
3. Continue the newest unblocked source/test stream by dependency from `NEXT.md`, without losing Delivery/Rider release evidence or opening unrelated visual work.

Current exact release and evidence state lives in `RELEASE_GATE.md` and `CHECKLIST.md`.
