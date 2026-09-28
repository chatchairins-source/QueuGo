# QueueGo Market integration test report — 2026-09-28

## Source and scope
Current four role HTML files and `merchant/pos.js` from the live QueueGo repository were used. Market extends existing shops/products/orders/order_items/payments/deliveries. No market seller, catalog row, order, staff account or payment was left as test data.

## Verified
- Inspected live Supabase tables, columns, function signatures, RLS and existing order/cash/Rider lifecycle before migration.
- Applied four additive migrations: Base, Checkout, Vehicle, Vehicle Audit. Existing checkout and Rider claim/pool functions were retained and extended; no table was dropped and RLS was not disabled.
- Ran an authenticated transaction with a temporary Market category/product and the shared cash checkout RPC: stock 2 → 1; retry with the same order ID kept a single order and did not deduct again; cancellation restored stock 1 → 2. Entire test transaction was rolled back.
- Checked RPC grants: public catalog callable by anon/authenticated; stock edit, vehicle approval and Rider pool callable only by authenticated, with role/shop checks inside the functions. Private costs live in `market_products` with owner/admin read policy, not public `products`.
- Checked HTML inline JavaScript syntax across all four roles, plus new Market JS syntax with `node --check`.
- Checked Rider job list uses `get_rider_delivery_pool()` and server claim repeats vehicle eligibility and weight check. Admin vehicle approval writes before/after to `audit_logs` in the same transaction.
- Confirmed live DB currently has zero Market/Grocery category sellers and zero Market products. Guest catalog will correctly show an empty state until a real shop selects Market and adds stock.

## Not yet verified end to end
- Authenticated browser workflow for a real Market seller/customer/Rider/Admin on multiple devices was not completed. Browser runtime in this environment lacks a Chromium binary. Do not treat syntax checks or SQL transaction checks as mobile UI verification.
- Actual Rider vehicle documents and capacity approval have not been tested with a real rider. No active Market seller/product exists; therefore no live Market delivery was placed.
- GitHub Pages cache propagation and production click testing remain pending until the repository files are published and loaded from the deployed URL.

## Operational limits
- Checkout supports a single shop and integer quantities. Fractional weights are sold as a defined pack SKU (for example one 0.5 kg pack). Mixed shop carts and variable weighed-at-checkout prices are not offered.
- Product image entry in this first Market UI accepts a URL; existing merchant image uploader remains available in the main product editor.
- Food and POS flows should be regression tested on the deployed site after publication because their shared checkout/Rider functions were extended.
