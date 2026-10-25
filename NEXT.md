## 2026-10-25 Integrated nationwide web regression — DONE (local source/test)

- Commit f1531207: full npm test completed with exit 0 after restart; focused checks also passed. Temporary log from the interrupted session was unavailable, so the suite was rerun instead of inferring success.
- Native #710 at 2160f30e still calls Longdo from QueueGoApi.reverseGeocodeDestination. This violates the QueueGo-only direction and must be migrated to the shared authority; do not replace it with a Buriram-only reverse endpoint and claim nationwide coverage.
- Local git push failed because HTTPS credentials are unavailable. No remote publication, deployment or APK build occurred.
- NEXT: publish the tested integration through the GitHub connector with fresh branch checks; implement shared nationwide reverse lookup and migrate native destination lookup; then build all three apps. Production data/routing and end-to-end delivery remain incomplete.

## 2026-10-25 Three-app nationwide reconciliation — IN PROGRESS

- Integrated Places successor fe198b153fc7d02c226e8df5f8095c3edd70508a with current web main 0e6718cf0070a01cf6a52dcd589fad7edb47d7e8 using a forward merge. Preserved current compact addresses, rotation/tilt, PWA role icons and manifests alongside the shared Places RPC and nationwide registration. Combined both test lists without dropping either branch's checks.
- Focused evidence: shared search runtime 16, map UI 5, camera 77, Map View core 56, Map View search 10, nationwide registration 15 checks passed; PWA links/icons passed; JS syntax and diff whitespace passed. Full npm suite is still running; not certified yet.
- Latest native successor is PR #710 at 2160f30ef10d88f3f59acf86aa20b4a2ec414884; isolated worktree prepared without modifying its source. No APK build, Production migration, import or deploy in this checkpoint.
- NEXT: finish full regression on this integrated web tree, resolve its first failure, publish the forward successor, then reconcile native against the same search authority. Nationwide routing/vector data and Production Places/service-area release remain incomplete. Do not describe the apps as nationwide-ready.

# QueueGo Next Actions

## 2026-10-24 Native APK files and QueueGo Q role icons — DONE (test build/Web deploy)

- User clarified they need Android APKs. Latest successful Native Pilot run `40085133108` built Customer, Merchant and Rider debug APKs at Native source `8a9089d4fd87ab974da836cb0f3b6e61752cd027`; launcher asset-family verification passed. Artifact `12557006258` (`queuego-native-pilot-apks`) contains all three APKs; SHA-256: Customer `8a00886947e2350354d0d7c5b15375abcad83010f67152f171a01c7579578ca8`, Merchant `8acb51f3df1a40bca4b815dbdab1e6fb25359d0682dc8dbe80c8f321b37edb1f`, Rider `f803c0f31ac22bd0b719fae07f3098860063d54ec328fefff43e72ee4def6d34`.
- These are debug/test APKs, not Play Store production releases. Native icons are the established Q family: Customer Q+cutlery; Merchant Q+store; Rider Q.
- Web app icons now match the approved Customer/Shop Q artwork. PR #716 source `11a0d8efb14e082bb4176cb1a0e774bc7c0a798b`, merged `68968e74e83472119f91d7163c13b81925e6a9c2`; exact-head CI `40105477083`, main RC `40105786044`, Pages `40105786009` passed. Live 512px PNG icons and app links were verified.
- **NEXT:** owner-side install acceptance on the target Android devices; Native Play Store/release gate remains separate.


## Current Web PWA brand icon correction — IN PROGRESS

- Active branch `fix/web-pwa-icons-use-approved-q-20261024`, source commit `969eeb0f26a87d973f9626c1969e24b7034e82a1`, based on current Web main `2d3aa5ec203c873b69ec52e2dcd108dbd4ef688a`.
- Replace the previous ad hoc Web icons with the exact Customer/Shop Q role family from the latest Native successor. Include PNG manifest icons and iOS touch icons.
- **NEXT:** exact-head RC, merge, deploy Pages, verify public assets.


## Web app links and Customer/Shop icons — DONE (Web source/CI/deploy), 2026-10-24

- PR #715 source `d9020643e050033d35640aaabeb952054b74ff0c`, merged at `beb7438a3e36afe9991a37689660fe469b26cb32`. No Production data changed.
- Adds `/apps.html` with launch and HTML download actions for Customer, Shop and Rider, plus custom SVG PWA icons/manifests for Customer and Shop.
- Exact-head RC #3337 / `40103741633` and main RC #3338 / `40104022905` passed. Pages #1694 / `40104022142` succeeded; live app links, manifests and icon assets verified.
- Downloaded HTML files are web app entry points and require internet and hosted QueueGo services.
- **NEXT:** owner-side PWA install/display acceptance on a physical device, if desired.

## Completed Web map rotation and tilt — 2026-10-24

- Merged PR #714 at `03ce3d5d4ebfff9f9aef150dec946ca12f54ecd6`.
- Exact-head RC #3335 / `40103081891` passed full npm regression and mobile WebKit checks; Pages `40103399673` succeeded and the Customer site opened live.
- All Web maps now support rotation and tilt, with compass/pitch controls. Raster OSM basemaps show perspective only; extruded 3D buildings need vector/elevation tiles. Physical iPhone gesture acceptance remains pending.


Current snapshot: 2026-10-24 · Authoritative main: aad07e0aedf57c0b7992534bea5b1fa6cf24719e

## Active QueueGo continuation — 2026-10-24

- PR #704 branch `feat/queuego-places-search-successor-20261024`, latest verified head before this pointer update `ff0664638e51f77c962ce633afb3b66340c616a3`; current main `28a121178b6e15ceb0a7575452b5cb2a374f40a3`. Source head `e34c368716be12069fe1848e17c44a0c46800791` passed exact-head RC `40088891010`; documentation heads `779fa7b` and `ff06646` passed exact-head RCs `40089120095` and `40089392407` across npm and all configured mobile browser gates.
- Places/Search source remains unlaunched. Read-only QueueTech check finds no Places relation or `qg_search_places`, and only one active ordering area in Buriram. Existing call RPCs are authenticated-only with fixed search paths; phone fallback and no-show authority RPCs are absent. Advisor findings remain under review.
- DOPA registry contains 76 provinces, but no resource has verified payload bytes, checksum or row count. The public JSON mirror is incomplete/inactive and cannot establish source coverage. No Production write or deployment.
- **NEXT:** obtain the original official DOPA JSON and dictionary through an approved access path; verify raw bytes, SHA-256, province identity and row count before staging/import. Continue read-only security and service-area planning while access is unavailable. Keep PR #704 draft and QueueGo NOT DONE.

## Immediate map-layout checkpoint

- Customer location search, Customer map height/spacing and Admin location editor viewport fit are source complete on the #704 successor and covered by exact-head RCs `40088891010`, `40089120095` and `40089392407`.
- Physical iPhone Safari visual acceptance is still pending; CI does not establish device appearance. **NEXT:** owner-side review after any eventual deployment, then adjust only if the device shows clipping or overlap.


## Historical QueueGo owner mission — Nationwide Places/Search and operation

- PR #697 is superseded/closed; PR #704 is the active successor. Preserve newest main and all Merchant/Laundry/Rider work by forward merges only.
- Official Geofabrik Thailand PBF staging yields 163,924 candidate rows across 164 review SQL chunks. The latest aggregate report records category, geometry and address-hierarchy field presence without names or address values. No OSM SQL was executed.
- Open Admin Data hierarchy is now sourced from two pinned files at commit `eef739a5…`; exact hashes and all 8,369 hierarchy rows validate. Review SQL remains local and unexecuted.
- NEXT: follow the active QueueGo continuation at the top of this file. Continue source-only or read-only work while actual official payload and release gates remain blocked.
- Production Places/Search schema/RPC and imported nationwide data remain absent. DOPA GIS-01 is blocked on official payload bytes (HTTP 503). Buriram ordering/service-area gate remains. Rider 10-failed-call phone fallback and verified cash no-show suspension need server RPC/audit. Supabase advisor findings, Production E2E and Web Owner acceptance remain unresolved. No fake/demo data, Longdo/legacy map, Production writes/deploy, or physical-device PASS.
- QueueGo remains NOT DONE until the original mission Definition of Done is met. Keep cash-first.

## Latest verified checkpoint
- Main: `2d0c6b58a45689585dc7653bd18bcf89dfd1e4c1`; PR #574 exact RC `39643279380` PASS; Pages deploy `39643901620` SUCCESS.
- Production remains unmodified. Migration history ends at `20261021133330`; inventory feature migrations are unapplied. Browser runtime readback was unavailable after environment disconnection.


## Completed successor: numeric quantity compatibility
- Read-only Production schema confirms `order_items.quantity numeric`; the PGlite inventory fixture previously modeled integer quantity.
- PR #576 merged to main as `a4f12dd39b751e7e05a2d37b54e47863d477f391`; exact RC `39647073978` passed, with fractional generic rejection and fractional Market ledger coverage. Post-merge RC `39647725266` and Pages deploy `39647725023` also passed.
- Production remains unmodified. Continue read-only migration/schema recovery review and keep cashier/device acceptance open.


## Latest verified checkpoint
- PR #576 merged to main at `a4f12dd39b751e7e05a2d37b54e47863d477f391`; exact QueueGo RC `39647073978` passed all 12 steps. Post-merge main RC `39647725266` passed and GitHub Pages deploy `39647725023` succeeded on the merge SHA.
- Production stays unmodified. Migration history ends at `20261021133330`; `products.inventory_stock_tracking` is absent until the inventory stack is deployed in order. No cashier/device or browser runtime acceptance is claimed.
- NEXT: continue read-only schema/trigger/ACL and controlled migration recovery review; preserve authorized cashier acceptance before Production activation. QueueGo/POS remain NOT DONE.


## POS migration preflight and main RC retry — 2026-10-21
- PR #577 merged at `e70961d5b70fa7f84519fbebf473659603e35132`; exact PR RC `39648478732` PASS. Main RC first attempt `39649126036` failed at Admin WebKit title-size read (`tests/admin-boot-browser.cjs:69`), then exact failed job retry passed all steps. Pages deploy `39649125931` succeeded on the merge SHA.
- Read-only Production catalog: latest migration `20261021133330`; inventory migration stack `20261021211800` through `20261021233300` absent, including reservation/adjustment/manager/Market-owner guards; cash-movement migration `20261021211709` also absent. No DB DDL/data write.
- Installed `order_items` triggers still include Grocery and Market stock owners; generic POS reservation/audit triggers are absent. Market table is RLS enabled and authenticated SELECT grant exists without policies, so migration 333's fixed-search-path definer mapping guard and post-330 overlap preflight are release-critical.
- Next: preserve controlled apply gate; before activation install migrations in order, run overlap preflight after migration 330, verify ACL/RLS/trigger definitions and test sale→cancel/refund/adjustment through authorized cashier and device. QueueGo/POS NOT DONE.


## Post-merge receipt — 2026-10-22
- PR #578 merged at `cc036ee001bf23425d055977f8330e3753749743`. Exact PR RC `39650817495` passed all 12 required stages. The PR stabilizes the existing Admin WebKit route assertion by waiting for the rendered heading, with no runtime UI change.
- Main push RC `39694314231` passed all stages on the merge SHA, including npm test, Admin WebKit boot/layout, Merchant mobile WebKit, Map Lab gestures, QueueGo Map search and Grocery mobile/tablet rendering. Pages deployment `39694313643` succeeded on the same SHA. Main ref verified at `cc036ee001bf23425d055977f8330e3753749743`.
- PR #578's change is test stabilization and documentation; Pages success is recorded as deployment evidence, not a claim of end-user UI behavior change. The prior main RC failure on #577 remains recorded as a separate earlier attempt.
- Production database remains unmodified. POS feature migrations remain unapplied; continue authorized cashier/device acceptance and controlled migration gates. QueueGo/POS are **NOT DONE**.

## Next action
Continue the POS migration recovery/compatibility review and verify the authorized cashier/device gate status. Do not apply Production migrations without the required acceptance; after that gate, proceed through ordered activation and readback. Then take the next highest-priority unblocked North Star gap. Keep QueueGo/POS NOT DONE until user and release evidence is complete.


## POS ambiguous bill session-scope guard — 2026-10-22
- Read current main `6acd48ef3c130cc1c6b7f4c0958afcc228920414`, no newer POS successor was open. PR #580 guards the ambiguous bill retry with its captured shop and cashier identity; identity mismatch drops the tab-scoped pending request before any order RPC. Regression covers session change after the request is loaded.
- PR #580 exact RC `39695677982` passed all 12 required stages and merged as `fbdf63707a5eba1b9bf703891330c7b132e6bb54`. Post-merge main RC `39696051611` passed all stages. Pages deployment `39696050699` succeeded on the merge SHA.
- This recovers an ambiguous online create request safely; it is not an offline sale queue. POS offline sale/outbox/conflict/reconciliation remains unimplemented and unaudited. Production database remains unmodified; cash/inventory migrations and authorized cashier/device acceptance gates remain open. QueueGo/POS are **NOT DONE**.

## Next action
Implement the durable offline cart draft slice specified in `docs/POS-OFFLINE-RECOVERY.md`: cache only minimum catalog/cart data, persist drafts scoped to shop + actor + device, label them Not submitted, and require explicit cashier review plus server revalidation before online submission. Add reload/account-switch/duplicate-tab/price-change tests. Do not accept payment, dispatch kitchen, reserve inventory, or report a sale while disconnected. Keep the Production migration and physical cashier/device gates open.


## Latest main successor receipt — 2026-10-22
- PR #581 merged at `c740e9321ec5e3d1fb2d6acafc382180e7166121` after its exact-head QueueGo RC passed. Its immediate push RC `39696877230` was cancelled when main advanced; the descendant main `a71131aa0027b0616260ce6881ef708e99e9e214` added Admin exclusions for POS DINE_IN/TAKEAWAY orders in Admin order reads, count KPI and GP totals.
- Successor main RC `39697139447` passed all required stages, and Pages deployment `39697139101` succeeded on the current main SHA. The earlier cancellation is preserved as evidence; no failure was hidden.
- Current main therefore includes #580's cashier/shop-scoped ambiguous bill retry guard and the Admin POS-order reporting exclusion. Production remains unmodified. POS feature migrations remain unapplied, physical cashier/device acceptance is not claimed, and a durable offline-sale queue remains unimplemented. QueueGo/POS **NOT DONE**.

## Next action
Continue POS controlled migration recovery and authorized device acceptance tracking, then audit the retry/outbox boundary for offline operations without creating any payment or tax policy. Verify Admin's exclusion across orders, dashboard counts and GP after current deployment; keep POS sales reporting and offline queue distinct from platform marketplace metrics.


## POS offline recovery discovery — 2026-10-22
- Current source blocks offline sale writes. Ambiguous online bill creation and cash movement use tab-scoped idempotent retries; #580 added actor/shop protection for bill replay. These are not durable offline sale queues.
- Reviewed current official Square and Shopify POS offline documentation. Both distinguish pending offline work from server-accepted state and expose permission, risk, limits, recovery, or unsupported-operation boundaries. Research and the recommended QueueGo Phase 1 are in `docs/POS-OFFLINE-RECOVERY.md`.
- Recommended next implementation: durable, identity-scoped offline cart drafts that require server price/stock/permission validation and cashier review before online submission. Do not mark as paid, send to kitchen, or reserve stock while disconnected. Actual offline completed sales require owner decisions about payment, multi-terminal inventory conflict, limits, shift/accounting, receipts and customer recovery.
- No Production migration or data change. QueueGo/POS remain **NOT DONE**.

## Next action
Implement the recommended durable offline draft recovery slice from `docs/POS-OFFLINE-RECOVERY.md`, without accepting offline payment or treating the draft as a completed sale. Preserve the cashier/device and controlled Production migration gates.


## Active QueueGo continuation — 2026-10-24

- Nationwide successor PR #704 branch `feat/queuego-places-search-successor-20261024`; forward-merged source HEAD `e34c368716be12069fe1848e17c44a0c46800791`. Exact-head QueueGo RC `40088891010` passed all configured source and mobile WebKit gates.
- DOPA GIS-01 catalog metadata is available, but the official download has not yielded exact JSON bytes. The public mirror says its datastore is inactive and incomplete; it cannot support import or nationwide coverage claims.
- No Production data/schema change or deployment. Production Places/Search RPC remains absent, Buriram ordering gate remains active, and rider phone/no-show server authority and advisor remediation remain open.
- **NEXT:** retrieve the original licensed DOPA payload through an approved source path and verify its bytes, hash, province identity, schema and row count. Continue read-only release/security planning while blocked; keep QueueGo NOT DONE.
