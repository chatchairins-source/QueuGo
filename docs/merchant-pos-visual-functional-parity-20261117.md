# QueueMarket POS — visual reference vs. executable functions (17 Nov 2026)

**Source:** Owner-approved composite POS design image in this conversation. It is a visual target, **not** proof that the sample menu, receipt totals, orders, rewards, branch, profit, or online/offline indicators exist in Production. Product photos, mock prices, figures, badges and timestamps in the artwork are examples only: **never seed them into live orders or dashboards.**

**Code source:** Native Android Kotlin/Jetpack Compose at `native-android/merchant/src/main/java/com/queuego/merchant/`, integrated from PR #1072 and POS parity PR #1207. Use the exact latest SHA and CI results before asserting a feature has shipped.

| Reference feature | Source-backed existing behavior or observed gap | Current delivery decision |
|---|---|---|
| Android phone + tablet | Responsive product grid, 48dp navigation targets, tablet landscape rail/catalog/cart and portrait expandable bill in PR #1207 | Implemented in unmerged PR; visual/device validation still required |
| Product photo, name, price, plus | `PosCounterView` reads `PosSnapshot.products`, real photo URL, price and `onProduct`, with stock/permission guards | Existing actual POS operation, restyled |
| Search, filters, popular products | Product search, category filter and 7-day owner item sales in POS | Existing |
| Barcode/SKU | `MerchantPosApi.lookupProductCode` and USB/Bluetooth/keyboard scanner input; `MerchantPosCodesScreen` manages codes | Existing text/hardware entry; **in-app camera scanner not verified** |
| Dine-in, takeaway, table management | `PosCounterView`, `PosTablesView`, `PosKitchenView` and original onMode/onTable/QR action | Existing; **zone reservation and status codes beyond active/open bills not proven** |
| Delivery and online orders | Original `PosDeliveryView` plus host Merchant `orders` route | In PR #1207, POS rail links to host actual `orders`; not one invented shared order engine |
| Add/remove item, discounts, bill total | Existing POS RPC callbacks, selected paid/unpaid bill state, idempotent guards | Existing business actions, restyled |
| Payment methods and print receipt | `PosBillsView`, confirmed RPC payment, Kitchen/Receipt print bridge | Existing code; printer hardware/actual payment end-to-end acceptance still required |
| Stock management | PosProduct inventory flags; `MerchantMarketStockScreen` supports grocery categories | Host link only for stock-permitted shops; restaurant-wide procurement inventory not implied |
| Products & settings | Host `products`, `profile` | New POS sidebar links only to real authenticated routes |
| Sales reporting | `PosOwnerDashboard` totals, bills, hourlySales and itemSales | PR #1207 adds **real-data bar chart** and KPIs, never image sample data; actual profit unverified |
| Customer/member loyalty & points | No verified Merchant POS-native member/rewards production contract found in this audit | **Not implemented/claimed**; needs data model, RLS, earning/redemption/idempotency and privacy review |
| Store/branch selection | Artwork includes branch picker; single signed-in shop context in current POS snapshot | Multi-branch switch **not implemented/claimed** |
| Badge/notifications | Merchant home notifications exist; POS artwork has red badges | PR #1207 uses actual unread count, opens authorized Merchant notifications screen; no fake badges |
| Online/offline claim | Durable offline POS drafts and reconnect checks exist; full offline payment sync is not proven | PR #1207 shows live network validation; do **not** describe full offline POS/settlement as complete |
| KPI profit | Owner dashboard has gross sales, orders, cash sales, refunds, discounts; not verified net profit | Exclude profit card until server computes real cost-backed metric |

## Implementation boundaries

- Use the existing POS and Merchant API clients; avoid duplicate settlement, orders, inventory, profile or loyalty tables.
- **Never** display fake orders, sample sales or hard-coded promotional numbers as live data.
- Only enable navigation where the destination and current account permission/shop type are valid.
- Preserve retry/reconciliation and no-double-charge POS protections.
- Do not merge to `main` until the exact code SHA passes Native Android CI, role smoke checks, accessible layout checks and true-device screenshot review.

## Actual responsive acceptance checklist

1. Phone portrait (~360–430dp): 2-column cards, scrollable filters, persistent cart summary; checkout must not be hidden under system navigation/keyboard.
2. Tablet portrait (~600–840dp): at least 2 or 3 visible columns based on available width; no truncated tabs; expanded bill can be scrolled independently.
3. Tablet landscape (~1000–1400dp): dark navigation rail, catalog and persistent bill; all product and checkout actions remain reachable.
4. Increased font scale, long Thai product names, empty/no-photo products, out-of-stock and no-auth states remain readable and accessible.
5. Verify actual add/edit/remove product, table assignment, discounts, cash/electronic payment, receipt print, online order and stock decrease in a safe test shop. Do not treat static UI screenshots as financial verification.

**Next distinct engineering gaps after UI CI:** camera-scanning decision and permission path, loyalty/points backend specification, real online-order unread count, evidence-based reporting profit, multi-branch permission model. These are not silently treated as implemented simply because the image depicts them.

## Production tax backend verification — read-only audit, 17 Nov 2026

Read-only PostgreSQL catalog checks on the owner-specified Supabase Production project `pkypiqhlrmzocysgeqew` found:

- The `public.orders` table exists with RLS enabled, but no `pos_tax_*` / `pos_prices_include_tax_*` columns were present.
- `public.pos_tax_settings` and the `public.pos_save_tax_settings` RPC were absent.
- The existing `public.pos_take_payment` RPC exists; it must not be modified by the Native POS UI task.
- Existing general `queuego_credit_*` RPCs **do not prove** any POS-native customer loyalty, rewards, or checkout credit contract. Do not invent customer balances.

The repository includes two *root-level* draft SQL files, `QueueGo-POS-Tax-Foundation-Migration.sql` and `QueueGo-POS-Tax-Checkout-Integration-Migration.sql`. The latter adds a tax trigger and replaces an order-total integrity check. **Do not apply these to Production directly** or label POS VAT Production-ready. Backend Worker 2 must reconcile them with current migration lineage, actual checkout RPCs, existing constraints, RLS/GRANT and historical orders; use safe staging and migration review first.

Native POS follow-up in this PR is **display- and print-only, fail-closed**: read immutable per-bill tax snapshot fields, clearly flag missing tax data and failed settings loads, and never print fallback tax=0 or locally synthesized base/tender. The app must not imply VAT was successfully enabled while the backend lacks the schema.

Release acceptance: verify the complete safe migration and authenticated owner-only RPC in a test environment, then end-to-end create/discount/pay/receipt scenarios with both tax-inclusive and exclusive settings. Recheck Production only through the coordinated Backend release lane.
