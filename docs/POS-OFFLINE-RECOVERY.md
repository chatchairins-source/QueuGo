# QueueGo POS Offline and Recovery Design

Reviewed: 2026-10-22  
Status: recommended Phase 1 scope; no offline finalized sale is implemented or approved.

## Current QueueGo behavior

- `merchant/pos-table-v2.js` displays an offline warning; barcode lookup, payment, kitchen actions, and delivery actions explicitly require connectivity. Bill create/edit calls can still attempt their normal RPC path, but a new create persists one stable request ID in `sessionStorage` before the call and leaves it for online retry if the response is ambiguous. This is recovery for an attempted online request, not an accepted offline sale.
- An ambiguous online bill-creation request is kept in `sessionStorage` with a stable request ID. PR #580 added shop and cashier identity checks before replay. This recovers a response timeout; it is not a durable offline outbox and does not provide a full offline catalog.
- Cash movement has its own session-scoped idempotent retry. It also requires connectivity before submitting.
- Closing the tab can lose those tab-scoped pending requests. No offline sale queue, cross-terminal stock conflict process, or disconnected payment acceptance has been evidenced.
- Production remains unchanged. This document adds no schema, payment processing, tax behavior, or offline-sale authorization.

## Market review

QueueGo should reuse the general product lessons while keeping its own implementation and UX:

- Square lets owners configure whether offline acceptance is allowed, choose cash-only or cash plus offline card, set a per-transaction amount limit, and restrict acceptance by staff permissions. It labels captured-offline transactions as pending, warns about declined/expired/disputed payment risk, and only supports refund after reconnection and upload. Its supported offline payment queue has a defined upload window. [Square: Process offline payments](https://squareup.com/help/us/en/article/7777-process-card-payments-with-offline-mode)
- Shopify distinguishes offline actions from server-synced orders. It can hold offline card payments as pending, sync order and inventory after reconnect, provides per-device daily and per-transaction limits, and blocks some operations such as returns, exchanges, and voids while offline. [Shopify: POS offline features](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline/offline-features) · [Shopify: Offline card payments](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline/offline-payments)
- These products make connectivity, permission, pending status, limits, recovery, and transaction-risk boundaries visible. QueueGo has no approved provider/hardware integration or business risk limits, so card-offline behavior must stay out of scope until specifically approved.

## Recommended Phase 1: recoverable offline drafts

Phase 1 improves continuity without claiming that a disconnected transaction is a completed sale.

1. Keep the last valid catalog and working cart draft available in a durable, device-local store. A local draft is explicitly labeled **Not submitted**.
2. Store only the minimum needed to reconstruct a cart: shop ID, cashier actor ID, device/register ID, stable draft and request IDs, product and option IDs, quantity, and the displayed price snapshot/version. Do not store auth tokens or payment-card details. Avoid adding customer contact data or free-text notes to the offline store.
3. On reconnect, revalidate the current actor, shop, permissions, shift, item/options, price, availability, and stock through the existing authoritative server path. Changed or unavailable items become a review state; never silently substitute a price, product, or quantity.
4. Require cashier review of conflicts before submitting the normal online bill request. Use one stable idempotency request ID across retry attempts. Do not create a duplicate financial ledger or bypass current order/payment state transitions.
5. Show durable states such as `Draft (offline)`, `Needs review`, `Submitting`, `Submitted`, and `Rejected`. Do not mark a draft as paid, send it to kitchen, reserve inventory, issue a final receipt, or report it as a sale until the server accepts it.
6. Scope local records to shop + actor + device; block another actor from reading or replaying them. Do not delete an unsynced draft on sign-out, account switch, or transient error without recovery or an explicit manager action. Set a documented retention/cleanup policy before enabling this storage.
7. Preserve safe recovery after reload, browser restart, duplicate tabs, connection flaps, and server response loss. A local queue is not a cross-terminal lock: concurrent devices still rely on server-side stock and idempotency checks.

## Separate approval gate: completed sales while offline

A true offline sale changes what “sold,” “paid,” and “in stock” mean before QueueGo can validate the transaction. Do not enable it implicitly as part of Phase 1. Before implementing it, the business owner must choose:

- Whether disconnected checkout can finalize cash sales, or if it only saves a draft for online submission.
- Whether card payments are prohibited until a supported payment provider and target hardware explicitly support offline authorization. QueueGo must never collect or persist raw card data.
- Per-device and per-transaction limits, offline duration, and the operator permission required to enter or exit offline checkout.
- How stock is allocated across multiple disconnected terminals and what happens when devices later report competing sales.
- Whether pending local transactions appear in receipts, shift close/count, owner reports, tax/accounting exports, and customer notifications before server acceptance.
- How a customer is contacted and how staff recover declined, expired, duplicated, or unreconciled transactions.

Until those policies and provider/device requirements are decided, the safe default is recoverable drafts only; no offline completed-sale claim.

## Verification gates for Phase 1

- Unit/runtime: offline create never calls order/payment/kitchen RPCs; draft IDs remain stable; no duplicate replay; stale actor/shop is isolated; changed price, unavailable item, invalid options, and insufficient stock require review.
- Browser: disconnect during cart editing, reload, close/reopen, reconnect, resolve a changed item, submit once, and verify no phantom paid order. Exercise two tabs and account switching.
- Security/privacy: no token/card data in storage; scoped records cannot render to a different actor/shop; stored data has explicit retention and deletion behavior.
- Physical device: verify browser storage and recovery on supported cashier device(s); do not infer hardware reliability from DOM tests.
- Release: exact-head CI, authorized cashier workflow, server readback, real receipt/shift reconciliation, deployment, and regression. Production migration activation remains separately gated.

## Sources

- [Square Support — Process offline payments](https://squareup.com/help/us/en/article/7777-process-card-payments-with-offline-mode)
- [Shopify Help — Shopify POS offline features](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline/offline-features)
- [Shopify Help — Accepting card payments offline using Shopify POS](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline/offline-payments)
