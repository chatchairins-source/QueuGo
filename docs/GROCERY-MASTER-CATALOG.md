# QueueGo Grocery Master Catalog — Phase 1 candidate

Mission is independent of Rider Native Android. Web base: `7595d764dac8a14d28162248d33f640d0a614870` on main. Working branch: `work-grocery-master-catalog-20261012`. Main and the existing grocery starter branch were rechecked and still at that base. No Native/Rider files changed.

## Evidence and counts

`catalog/statistics.json` is generated, not an estimate:

- 1,109 factual research observations from 117 public source pages.
- 415 normalized package records; three repeated package observations deduplicated.
- 691 observations lack a clear name, explicit package size or an unambiguous package identity and remain research only.
- 45 category definitions, including the owner's 43 initial categories; 45 currently populated with normalized package records.
- Zero verified GTINs. No SKU/database/photo identifiers are relabelled as barcodes.
- Zero licensed/approved production images. All 415 package records have pending images.
- Zero records imported into Production; zero new products enabled there.

These counts do not mean 1,109 sellable SKUs or complete coverage of the Thai grocery market. All initial categories have source-backed coverage; coverage depth is uneven and this is an extensible first dataset, not a complete retailer catalog. Some names identify product families; the 691 incomplete observations must not be converted into arbitrary weights or artificial variants. Package material, domestic availability and sale-unit identity must be reviewed before activation.

Primary sources include Betagen, Ichitan, Ajinomoto, Foremost, Deksomboon, Hi-Q Food, ThaiNamthip's current public product names and explicit available-size lists, Tiparos, Morakot, Mitr Phol Sugar, CP Brand and Carabao. Sahapat's distributor product pages supply additional factual names and some explicit sizes. Selected factual package observations from Tops, Lotus's, HomePro, OfficeMate, Watsons and All Online expand domestic category coverage. One Big C toothbrush pack page is a factual reference only; no prose, photos or prices are included. Kao website terms require permission for commercial reuse, so it is excluded from the new collector/import input. Its generated record fingerprints are internal audit keys, never vendor SKUs or GTINs. No descriptions, prices or photos are copied into the sellable catalog. Big C is a reference source only: its terms restrict copying/downloading, so no Big C bulk scraping pipeline is included. The historical DIT 2020 promotion PDF was investigated but not used as current market data.

## Schema and existing flows

Migration: `supabase/migrations/20261012170449_grocery_master_catalog.sql` (created using Supabase CLI).

- `grocery_catalog_categories`: extensible, Admin-managed category definitions.
- `grocery_master_products`: canonical brand/name/variant/size/pack/unit identity, optional checksum-validated GTIN, active/merged state, image approval/evidence.
- `grocery_master_sources`: source URL/type/product reference, retrieved time and first-seen history. Admin-only access.
- Existing `products` is the Store Product layer, using `grocery_master_id`, `grocery_enabled`, `grocery_selling_price`, `grocery_promo_price`, existing `stock` and `grocery_stock_tracking`. Unique `(shop_id, grocery_master_id)` retains one store listing per master.
- No separate checkout, order state machine, GP wallet or cash-confirmation flow. Existing product IDs, GP trigger, order item snapshots and POS remain authoritative.
- Guard triggers derive identity/image/category from the master and sellability from enabled + positive store price + tracking/stock. A store cannot rewrite master identity through `products`.
- Tracked stock reservations execute inside existing order transactions, reject insufficient/fractional quantity, and restore once on cancellation/no-rider failure. Non-linked products immediately bypass the new stock trigger. Completed grocery items cannot be edited. Private reservation rows are not exposed to clients.
- Merge preserves historical product/order IDs and source references. If both masters already have listings in the same shop, merge rejects the collision instead of silently selecting a price/stock or discarding an order reference. The Admin picker shows product identity and store usage before merging; overlapping store listings are deliberately rejected with a clear message. Resolving overlapping listings requires store inventory review and must never silently select a price or add duplicate stock.

Read-only Production schema/policy preflight: `ops/grocery/preflight-schema-20261012.json`. Git backup: `backup-web-pre-grocery-master-20261012`. This is not a full database dump. No Production schema was changed; a complete current schema/storage/trigger backup is required immediately before a future rollout.

## Import pipeline

`catalog/normalize.js` is shared between CLI and Admin. It normalizes Unicode/Thai digits and sara-am, common unit names (ml/mL/มล., g/กรัม, L/ลิตร, kg), canonical size values, brand aliases and embedded display sizes. Coca-Cola/Coke/โค้ก aliases converge when the factual variant/size/pack/unit identity matches. GTINs are checksum-validated and padded to GTIN-14. Without a GTIN, brand + name + variant + size + pack + sales unit provide the canonical key. A row with a GTIN and an earlier matching row without one converge on one item; different GTINs for the same identity and conflicting identities for one GTIN reject for review. Dangling display-size labels are removed. Ambiguous identity conflicts require review.

`qg_grocery_import` recomputes the key on the server, checks Admin status, serializes imports/merge and preserves all source URLs and source record IDs through normalization/deduplication, including multiple source IDs on the same page. Reimport does not overwrite an Admin's reviewed attributes or create duplicate listings. New records always start inactive. Import cannot approve/copy an image.

Commands:

```bash
npm run catalog:build
node ops/grocery/import.cjs catalog/thai-grocery-import.json /tmp/grocery-validation.json
# Optional authenticated Admin apply (200-row batches):
# QG_SUPABASE_URL, QG_CATALOG_ADMIN_TOKEN and QG_SUPABASE_PUBLISHABLE_KEY
node ops/grocery/import.cjs input.json validated.json --apply
npm run test:grocery
```

CSV and JSON are supported. CSV requires the same source/name/brand/size/category/sale-unit fields as the JSON records. Malformed rows are reported before apply; credentials are environment variables and must not be committed. A partially completed batch run can be retried with the same input.

Research scripts are bounded factual collectors, not whole-retailer copies. `research-spc.py` collects names without images/marketing text. `extract-manufacturer-facts.py` consumes previously fetched official pages in scratch. Reviewed supplemental factual records remain separate in `manufacturer-extra-facts.json`, so rebuilding an extractor does not overwrite them. `build-research-catalog.cjs` separates size-qualified package records from incomplete observations. `curate-expansion.py` regenerates reviewed supplemental observations in `verified-expansion.json`. The classifier handles incidental Thai substrings explicitly (vinegar is not fruit juice; snack names are not milk). Adding another source requires its own reviewed adapter; do not infer package variants from known brand names.

## Merchant

Grocery Products and Catalog use one paged editor: 50 displayed rows, a 51st-row sentinel, server search/category/state filters. Existing background hydration skips full product snapshots on these grocery editor pages. On registration, Grocery copy explains selection from the master; successful background profile hydration routes the new owner to Products.

Store actions: selection, page bulk enable/disable, category-wide enable/disable, quick storefront/promo price, optional stock tracking and quantity. Unpriced enabled rows remain invisible to customers. Previously created unlinked Grocery listings remain editable through “จัดการสินค้าที่ร้านเคยเพิ่ม”; their records/order IDs are not deleted. Linking existing custom listings to an exact master remains a reviewed migration task, never an automatic name-only match. Dirty edits are protected from background rerender/page/filter changes; writes lock immediately, are ownership-scoped and repeat-safe. Store writes use a persisted request UUID and a private durable receipt, so a lost reply can be retried without resetting stock after intervening sales. Expected listing timestamps reject stale writes; confirmed writes reload the current listing. A user/route change discards late results.

## Admin

Dedicated Admin navigation and page: paged search, source references, barcode/name/category/size editing, active/disabled state, usage counts, CSV/JSON import, selected-row activation/disable and audited duplicate merge through a paged product picker rather than typing internal UUIDs. Merge success requires a confirmed server result. Image upload uses a dedicated Admin-only-write public Storage bucket and requires declared rights evidence; approval is explicit. Production URLs must be on QueueGo's Supabase Storage, never a retailer hotlink. Public manufacturer origin alone is not image permission.

## Customer

Grocery storefront uses existing approval/ownership RLS on `products`, positive effective store price and derived sellability. It loads 50 rows per page/category and uses the original cart/checkout product IDs and GP-derived Delivery price. Product information is synchronized from the master; price and stock stay with the shop. Other service storefronts keep their current flow.

## QA and release status

Local full `npm test` passes, including the existing Grocery/Market/POS/cross-role regression suite. New coverage: 66 isolated PostgreSQL/PGlite checks, 16 Merchant/Customer DOM checks and 11 Admin DOM checks covering import retry, unit/brand normalization, GTIN checksum/dedupe, store price isolation, enable/disable/unpriced state, tracked stock reservation/oversell rejection/cancel restoration, Admin merge retry, image hotlink rejection, source/role isolation, Thai/brand search, public storefront prices, bulk selection, duplicate tap and late owner/route results.

A disposable 10,000-row fixture catalog returned the bounded 51-row sentinel page in 88 ms on local PGlite; this is not a Production or device latency measurement. RLS role checks are cached per statement, and Thai/brand filters remain server-side. These are isolated checks, not authenticated Production E2E or simultaneous multi-connection PostgreSQL evidence. Package records are source-backed research, not fake demo inventory. Test-only fixture IDs/GTIN assignments are explicitly isolated and are not in the shipped catalog.

Rendered component QA now passes 48 checks across Merchant/Admin/Customer at 320, 390 and 768 px using Chromium 153.0.8010.0 and a Thai font. It verifies bounded pages, no horizontal overflow, actual source-backed names, store-save confirmation, image placeholders, Admin target selection and Customer lazy loading. The browser transport and account IDs are isolated fixtures; this is not authenticated Production or physical-device E2E. The RC workflow also runs this browser suite using the runner's Chrome. See `docs/qa/grocery-browser-20261013.json`.

**Release Gate OPEN.** Production migration/import/advisors, real store A/B onboarding → checkout → stock/cancel/POS regression, actual multi-client stock races and Production 10k-record query measurements remain unverified. Incomplete observations stay out of import and image approval remains pending. No Production deployment, main merge or release-ready claim is made.

Deployment blocker observed on 2026-10-13: Supabase MCP is connected for SQL, but provides no backup operation. The CLI `db dump --linked` fails with `ProjectRefNotLinkedError`; no database connection/token variables are configured in this workspace. No valid dump was produced. Before schema mutation, obtain a current restorable backup through the authenticated Dashboard or an authorized CLI connection; do not substitute schema metadata for the backup.
