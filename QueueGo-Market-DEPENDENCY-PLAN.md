# QueueGo Market — current dependency map and migration plan

Source of truth: the current `index.html`, `merchant/index.html`, `rider/index.html`, `admin/index.html`, `merchant/pos.js` and the connected Supabase schema inspected 2026-09-28. No older QueueTech UI is a source.

## Existing path

Customer Auth → `shop_profiles` (merchant-owned, `public_category` generated from metadata) → `products` → single-shop cart → `queuego_place_cash_order` → `orders` / `order_items` / `payments` / `deliveries` → merchant status RPC → `get_rider_delivery_pool` / `rider_claim_order` / rider action RPC → notifications → Admin.

The rider system requires `orders.order_type='shopping'` and uses statuses `pending`, `accepted`, `preparing`, `ready`, `assigned`, `picked_up`, `in_progress`, `completed`, `cancelled`. Therefore Market is identified separately from that lifecycle. POS uses `sales_channel='POS'`; Food/Market/Grocery delivery all use `sales_channel='QUEUEGO_DELIVERY'`.

## Reuse and additions

| Concern | Existing | Additive plan |
|---|---|---|
| Sellers / stalls | `users`, `shop_profiles`, profile media, hours/open state | Market-specific `metadata.category` values; no duplicate seller table or login |
| Catalog | `products` and shop relationship, categories, price, image, available | Sale unit and pack size fields; market stock extension keyed to existing product ID |
| Cost | No private cost field | Owner/admin-only `market_product_costs`; never put cost in publicly readable `products` |
| Stock | `products.stock` integer, no movement history | Numeric `market_product_stock`, `market_stock_movements`; server transaction for sale/cancel/adjustment, no client-side direct balance changes |
| Cart / checkout | Single shop, integer `qty` 1–99; server final price, stable order ID | Keep single shop and the same checkout RPC; market sellable packs are separate product rows for the first release, each with an explicit unit/pack size. Do not promise fractional cart quantities until shared checkout supports them safely. |
| Orders / money | `orders`, `order_items`, `payments`, `queuego_cash_order_locks` | Vertical marker on `orders` (Food/Market/Grocery), use same `shopping` lifecycle, cash and GP; reserve market stock atomically with checkout |
| Delivery | `deliveries` and existing merchant/Rider transitions | Carry market weight on existing order; filter capacity in Rider pool and enforce it in claim RPC before assignment |
| Vehicles | `rider_profiles.vehicle_type`, `vehicle_plate`, metadata | `saleng` type, name/capacity/status; restrict writable capacity to verified/admin-controlled value |
| Realtime / notifications / Admin | Orders and notifications, admin trace | Reuse existing subscriptions and trace; add Market labels and owner stock ledger; no parallel delivery job table |
| Multi-shop | Orders have one `shop_id`; checkout blocks multiple shops | Remain single-shop; do not alter the working cash and Rider transaction to pretend multi-shop works. A future parent group may reference multiple existing `orders`. |

## Security and migration order

1. Add safe Market classification/units and private stock/cost tables with foreign keys, RLS, explicit grants and shop-scoped RPCs. Keep existing `products.stock` for legacy Food; Market inventory uses numeric balance and movements.
2. Add a vertical marker and calculated weight to existing orders, and server-enforced inventory reservation/restoration around the existing cash order transaction. No client-authoritative price or stock.
3. Extend the existing Rider vehicle profile and claim checks; use server-side capacity for Market. Preserve Food eligibility and existing status codes.
4. Add customer Market route and seller stock controls to current UIs; surface vertical and weight in Rider/Admin. Ensure guest catalog reads never expose cost or private stock adjustments.
5. Rollback-transaction tests for checkout, retries, stock/cancel, capacity, role isolation, GP/cash, and live authenticated browser tests for all four roles. Do not claim end-to-end completion from UI alone.
