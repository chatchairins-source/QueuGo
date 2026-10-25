## 2026-10-25 Three-app nationwide reconciliation — IN PROGRESS

- Integrated Places successor fe198b153fc7d02c226e8df5f8095c3edd70508a with current web main 0e6718cf0070a01cf6a52dcd589fad7edb47d7e8 using a forward merge. Preserved current compact addresses, rotation/tilt, PWA role icons and manifests alongside the shared Places RPC and nationwide registration. Combined both test lists without dropping either branch's checks.
- Focused evidence: shared search runtime 16, map UI 5, camera 77, Map View core 56, Map View search 10, nationwide registration 15 checks passed; PWA links/icons passed; JS syntax and diff whitespace passed. Full npm suite is still running; not certified yet.
- Latest native successor is PR #710 at 2160f30ef10d88f3f59acf86aa20b4a2ec414884; isolated worktree prepared without modifying its source. No APK build, Production migration, import or deploy in this checkpoint.
- NEXT: finish full regression on this integrated web tree, resolve its first failure, publish the forward successor, then reconcile native against the same search authority. Nationwide routing/vector data and Production Places/service-area release remain incomplete. Do not describe the apps as nationwide-ready.

## 2026-10-24 Native APK files and QueueGo Q role icons — DONE (test build/Web deploy)

- User clarified they need Android APKs. Latest successful Native Pilot run `40085133108` built Customer, Merchant and Rider debug APKs at Native source `8a9089d4fd87ab974da836cb0f3b6e61752cd027`; launcher asset-family verification passed. Artifact `12557006258` (`queuego-native-pilot-apks`) contains all three APKs; SHA-256: Customer `8a00886947e2350354d0d7c5b15375abcad83010f67152f171a01c7579578ca8`, Merchant `8acb51f3df1a40bca4b815dbdab1e6fb25359d0682dc8dbe80c8f321b37edb1f`, Rider `f803c0f31ac22bd0b719fae07f3098860063d54ec328fefff43e72ee4def6d34`.
- These are debug/test APKs, not Play Store production releases. Native icons are the established Q family: Customer Q+cutlery; Merchant Q+store; Rider Q.
- Web app icons now match the approved Customer/Shop Q artwork. PR #716 source `11a0d8efb14e082bb4176cb1a0e774bc7c0a798b`, merged `68968e74e83472119f91d7163c13b81925e6a9c2`; exact-head CI `40105477083`, main RC `40105786044`, Pages `40105786009` passed. Live 512px PNG icons and app links were verified.
- **NEXT:** owner-side install acceptance on the target Android devices; Native Play Store/release gate remains separate.

## 2026-10-24 Web PWA brand icon correction — IN PROGRESS

- Active branch `fix/web-pwa-icons-use-approved-q-20261024`, source commit `969eeb0f26a87d973f9626c1969e24b7034e82a1`, based on current Web main `2d3aa5ec203c873b69ec52e2dcd108dbd4ef688a`.
- PR #715 added Web PWA artwork that did not match the established QueueGo Q brand. Use the latest Native successor's Customer Q+cutlery and Shop Q+store SVG/512px PNG assets, with iOS touch icons. Keep Rider's approved icon.
- No Production data changed. **NEXT:** exact-head QueueGo RC, merge after success, publish Pages and verify live icons.

## 2026-10-24 Web app HTML downloads and role icons — DONE (Web source/CI/deploy)

- PR #715 source `d9020643e050033d35640aaabeb952054b74ff0c`, merged into main at `beb7438a3e36afe9991a37689660fe469b26cb32`.
- User requests direct access to downloadable HTML for Customer/Shop/Rider and bold professional icons for Customer and Shop. Existing live app entry points are `/`, `/merchant/`, `/rider/`; HTML files require internet and their QueueGo services/assets.
- Customer and Shop now have distinct bold PWA SVG icons and manifests; all three apps declare a manifest. `/apps.html` lists launch links and same-origin HTML downloads. Downloaded entry pages need internet and hosted assets/services.
- Exact-head RC #3337 / `40103741633` and main RC #3338 / `40104022905` passed. Pages #1694 / `40104022142` succeeded. Public app links, Customer/Shop manifests and SVG assets were verified.
- No Production data changed. **NEXT:** optional owner-side PWA install/display acceptance on a physical device.

## 2026-10-24 Shared Web map camera rotation and tilt — DONE

- PR #714 source `2cfed544bf12caa5d967896f886953957b13ee5b` merged at `03ce3d5d4ebfff9f9aef150dec946ca12f54ecd6`.
- Exact-head RC #3335 / run `40103081891` passed all npm and mobile WebKit checks. GitHub Pages run `40103399673` succeeded; live Customer page opened.
- Camera rotation and tilt are available throughout Web Customer, Shop, Admin, Rider and map tools. Current OSM raster tiles cannot render 3D building geometry. Owner iPhone gesture acceptance remains a physical-device check.

## 2026-10-23 Nationwide Places/Search and operations mission

- Owner mission changes the technical boundary from Buriram Pilot to Thailand-wide maps, registration, search and QueueGo Places; order availability remains supply-dependent. Keep the current #695 map/search separation and all newer Merchant, Laundry and Rider successor work.
- Verified startup successor: PR #695 head `db3c75b6e7e5f8409bf4f83e39cd26bba49d306d`, descendant of main `c7090f1ced830e7203a719bea38db3ba636aee64`. During the work, PR #696 merged latest Map/Search separation into main at `0c22c1dc98ed6aa793483aa90ad8dfc8007486a8`; the local feature merged this current main forward at `04d037fcddccad8857a432b8c740d387d73e5215`. New local branch: `feat/queuego-places-search-foundation-20261023`.
- Latest implementation source commit: `2115bdfc625758ef88ce7599c96bd3b02dfc5395`, full `npm test` exited 0 on the implementation source; exact-head hosted CI and isolated Postgres migration validation remain pending.
- Production read-only evidence: one active `queuego_service_areas` row named `Buriram Pilot`, centered at 14.99292,103.10804 with 20km radius and 15km delivery cap. `queuego_assert_service_route` delegates to this circle gate. Existing voice session table and start/active/end RPCs are present; table direct-client RLS policy denies all, anon execute is false, authenticated execute is true. `qg_call_end` only marks `ended`; Rider order enrichment currently reads `users.phone`. No current no-show adjudication RPC was found during source/Production inventory.
- DONE (local foundation only): additive QueueGo Places schema with PostGIS point and polygon storage, Thai/English names, aliases, administrative hierarchy, source/license registry, source-record mapping, field-level provenance, canonical dedupe key, spatial/trigram/prefix indexes, and bounded `qg_search_places` RPC. The registry includes OSM with ODbL attribution and QueueGo verified operational records. Search filters out sources without storage or redistribution permission.
- IN PROGRESS (local implementation): `20261023195500_queuego_th_admin_source_registry.sql` records the reviewed Open Admin Data 2026.06 CC BY 4.0 source, attribution, update policy and both exact source file hashes. Verified root cause: the upstream README defines `all-flat.ndjson` as levels 1-2 only; subdistrict level 3 is a separate `all-subdistrict.json` file. The former importer expected 7,364 level-3 rows from the levels-1-2 file, so its input contract could never be met. The corrected importer requires both files from pinned commit `eef739a534e26181c86073d69e9aeb527110cf1a`, verifies Git blob SHAs `baa3eb18fb79412e85dcef08059d16f8cf47b317` and `bd5c49a7e1af578c9e34e32020ddd90eabdc167f`, and validates 77/928/7,364 counts, Thailand coordinates, unique admin codes and the full parent chain before rendering review-only SQL with per-field provenance. Exact bytes yield 8,369 rows. Villages are absent and no data has been imported.
- IN PROGRESS (local implementation): Customer, Merchant and Rider configure one `QueueGoMapWeb.search` adapter which calls `qg_search_places`; optional provider adapters are explicit and external results are non-persistable. Rider destination settings now searches outside its map and moves the existing map on result selection. No provider secrets beyond existing public publishable config were added.
- Verification: 55 focused schema-contract assertions PASS; full local `npm test` PASS on the exact reconciled source tree. Source only; no country data import, database migration application, Production data writes, user/account/order/payment changes or external hosting. Current environment lacks local Docker/psql, so real Postgres migration validation and exact-head hosted RC are still required.
- NEXT: validate the migration in isolated Postgres/hosted CI; add verified Thai admin hierarchy and open POI ingestion with pinned source/license provenance; wire the shared search RPC into Customer/Merchant/Rider; replace circular service-area gating with polygon/admin-area order authority that leaves map/registration available nationwide. Then implement secure 10-failed-call phone fallback and evidence-backed permanent cash no-show suspension with immutable audit. Do not claim QueueGo nationwide launch-ready before every owner Definition of Done gate is verified.

## 2026-10-23 Rider Web work preferences

- Owner requests Rider choice of accepting bundles, automatic/manual acceptance and service types, including visible Passenger Ride. Continue from latest Web main `6df56209021a0a47fce1928861a63472a00d3f6b`; numbered map stops stay intact.
- Evidence: Web profile only exposes availability, destination and Laundry; Native continuous-dispatch preferences are staged but not installed in Production. Existing Production Ride owner authorization and Ride dispatch already exist. Route Bundle global feature is currently disabled; Express lacks an installed action implementation. Do not enable global features or grant passenger permissions from a client checkbox.
- DONE (source/test/Production schema): add server-acknowledged per-Rider service/bundle/auto preferences, reusing the Native-compatible private dispatch preference table shape. Existing dispatch and offer readers filter chosen services; an assignment trigger enforces service and bundle choices across claim paths. Existing vehicle/weight/credit/lease/business rules are preserved by exact installed-source patch guards.
- Web profile shows explicit automatic acceptance (only while online/foreground), optional bundle discovery with per-offer manual income confirmation, service checkboxes and visible locked Ride/Express explanations. Laundry retains its existing mode. Unapproved Ride cannot be self-enabled. Automatic acceptance reuses the atomic/idempotent selected-offer claim, stops on offline/active work, and attempts each lease only once with account/session fences. Server read acknowledgement is refreshed at most15s; no local-only preference authority.
- Focused actual settings DOM/PGlite test39 PASS; full local regression PASS. Exact source `b7c8bb25b38888c2184253d564c93187be5a2647` RC `39922568410` SUCCESS, PR #668 merged `2acd75e5a4c8cce03042a90443d075d784149bde`. Initial source `24b4f88d` run `39921678927` was superseded during WebKit installation; it is not recorded as a completed pass. Production migration `rider_web_work_preferences` applied successfully after exact-head gates. Production readback: RPC exists, authenticated EXECUTE granted, anon denied, private table SELECT denied, RLS enabled, assignment trigger and dispatch filters installed, preference table empty (no QA Rider writes). No fake Production orders or notifications.
- DONE (deployment): Pages `39923174660` SUCCESS; Render deploy `dep-dbdheggae00c7387ecgg` LIVE on merge `2acd75e5`. Both origins return exact committed Rider HTML, preference JS and settings CSS bytes. Build `rider-20261023-workprefs1`; HTML SHA256 `c3c4f8c56b7dadcee9ff513370075ceb8ae15ae22033cf8a8a14f76f97b00f37`, preference JS `f1cba0d1e17eaa6caae1f655f69199636508705fefd5109f20a83fcb65965615`, CSS `977742a28af8ea39e113c7badf169bc13891b840eee1763ec4e4972b24e65296`.
- Advisor delta reviewed: private-table RLS with no policies is intentional (no direct client grants); authenticated SECURITY DEFINER RPC is intentional and calls the existing live-session/current-Rider ownership guard with an empty search_path. No anonymous access or new cross-account selector. Global Route Bundle remains false; Ride remains true with existing owner authorization, Express remains unavailable. Proposed new pricing from conversation has not been applied.
- NEXT: owner-account save/offer acceptance on an actual device; retain this deployed successor. Bundle preferences can be saved but actual bundle availability remains blocked by the existing platform feature gate; this change does not launch the proposed new pricing. Do not claim background acceptance while browser is closed, physical-device PASS, or Passenger Ride approval from UI visibility.

## 2026-10-23 Rider numbered map stop correction

- Owner screenshot shows a green pickup dot and red delivery dot without point1/2. Evidence-backed cause on latest main `79412b43`: `placeJobMarkers` constructs plain20px circles for ordinary jobs and Laundry; Market has tiny pickup digits only, no customer number. This is marker rendering, not route-service failure.
- DONE (source/test): replace only stop-marker presentation with readable numbered badges, shop/customer SVGs and point/action labels. Ordinary offer/active jobs show1 pickup,2 delivery; Market numbers remaining non-cancelled pickups then delivery; bundle numbers continue; Laundry icons follow pickup/return direction. Completed market pickups keep their number and use a muted color. Viewport alignment keeps labels upright in tilted maps, and stop markers stay above route/puck layers. Existing route authority, camera, cards/switches and order flow remain unchanged.
- Focused actual-function DOM/lifecycle replay:161 Rider map checks PASS,29 route-render checks PASS. Covers offer before acceptance, active refresh cleanup, multi-pickup/cancelled/done, multi-order numbering, both Laundry directions, icons/labels and null-coordinate rejection. Full local regression PASS; exact source `35c4cebbc9815f36cbb6bde97f2e58354a9d7467` RC `39919080167` SUCCESS. PR #666 merged as `64b530fa416f7184a92d5eb94ddb069e01ac79d6` with identical source tree.
- Native successor already contains navigationStopMarkerTitle point1 shop/point2 customer tests; this reported screenshot is Web. No APK or unrelated Native changes in this correction.
- DONE (hosting): Pages `39919562332` SUCCESS; Render deploy `dep-dbdgvqvavr4c73a24ua0` LIVE on merge `64b530fa`. Build identifier `rider-20261023-stops1`.
- DONE (public readback): both https://chatchairins-source.github.io/QueuGo/rider/?v=20261023-stops1 and https://queuego-web.onrender.com/rider/?v=20261023-stops1 return HTTP200 and match the exact committed Rider HTML bytes, SHA256 `44fd7432acc49d5e966203bb455de39d4459cc384ac5adcb57a9a3f188818229`.
- NEXT: owner-device visual acceptance during an actual offer; retain the numbered-stop successor. Local/CI DOM replay is not a physical phone screenshot. No real Production order or notification created for QA.

## 2026-10-23 QueueGo Render deployment

- DONE (Web source/test/public hosting): PR #662 source `6a17ee721c425ec4095935c649fbac111c8a5429`, merge `6e21b1668829d2819bfcd05c2d0002b6d32681b9`. Dependency-free `node ops/build-render-static.cjs` publishes 98 browser assets to `dist-render`; all 79 static HTML asset references resolve. Full local `npm test` PASS. Public readback of all 98 assets matches committed source bytes. Customer `/`, Merchant `/merchant/`, Rider `/rider/`, Admin `/admin/`, table ordering, privacy/account deletion pages and Rider install icon return HTTP200. Operational paths return404. UI is unchanged.
- Render workspace explicitly confirmed by owner: `tea-dbdejavavr4c739q33l0`. Static site `srv-dbdgi3mi0phs739nsmrg`, URL https://queuego-web.onrender.com ; initial deploy `dep-dbdgi3ui0phs739nso20` LIVE on `6e21b166`. Build command above, publish path `dist-render`, `SKIP_INSTALL_DEPS=true`, branch main. Supabase remains the existing Production backend; no database migration or sample orders/data.
- Push origin: Web PR #663 merge `c3bb6c7a42e949f35a65029eb30ee03c7c69a489`; Native PR #664 merge `78632dfa2a6bf8b3a81184a86037084672344622`. Only the exact Render origin is added, preserving each successor implementation. Production function patched from actual version24 to25, retaining all live files and verify_jwt=false with its existing internal authentication. Render/Pages OPTIONS204; untrusted origin403. Supabase Auth settings returns200 with Render origin CORS.
- Map service retained: `srv-dbden97avr4c739qh5mg`, https://queuego-map-buriram.onrender.com , source `8255033749aa5f805f1e7153e2096451eb90360a`. Environment `QUEUEGO_MAP_ALLOWED_ORIGINS=https://chatchairins-source.github.io,https://queuego-web.onrender.com` merged without replacing other variables. Deploy `dep-dbdgk2psrm7s73bmcv4g` LIVE. This environment override is authoritative for cross-origin access; do not remove it during future updates.
- Render public Git URL services require manual deploys according to current Render documentation even when autoDeploy reports yes. After future merged changes, inspect actual deploy SHA and trigger latest only if no automatic deploy exists. Never claim a queued trigger is live. Map is currently on the existing free plan; no paid upgrade performed.
- Hosted RC for exact packaging source `39916306963` was still running at this checkpoint; local full regression PASS, no hosted/physical-device acceptance claim. No authenticated order, notification, sale or payment was created during deployment QA.
- Map public post-rollout PASS: Render-origin health GET200, route OPTIONS204, route POST200 with actual route geometry; all include the exact Access-Control-Allow-Origin header.
- NEXT: retain these deployed successors; deploy latest final main checkpoint and confirm deployment.json SHA plus public role pages. Record subsequent real owner login/GPS journeys separately.

## 2026-10-23 Map-only release checkpoint

- Owner cancelled timestamp recovery and requests map changes only. Native restoration PR #658 is closed and must not be merged. Preserve current Rider UI; do not restart recovery.
- DONE (Web source/test/deployment): PR #659 source `f67f9a8638a391ecbdb6a6b9a7c0fe6fe32318f6`, merged `fe994fa0a7d0f93bc1d2e596285a3e9a06cbc43f`. Exact RC `39913291906` and `39913298092` SUCCESS; post-merge RC `39913807469` SUCCESS; Pages `39913807159` SUCCESS. Merge tree exactly equals the tested source tree.
- Public byte readback PASS: Rider build `rider-20261023-maponly1`; HTML SHA256 `39b7451dca46b4e37aa2842eff698f92bf3b0e10f2a05d5f506d29d0d7231312`; map JS SHA256 `0dcc1ea42b0a57def3ab83493bd46135306201e58cbf7f95c1f883276c427633`. Live URL: https://chatchairins-source.github.io/QueuGo/rider/?v=20261023-maponly1 . Both responses match committed bytes. All Rider CSS/non-map UI functions match pre-recovery current UI `59051ddf`; only online map follow and continuous speed zoom differ functionally from that UI baseline.
- DONE (Native source/test/build/emulator): PR #660 source `c0b2865e4c91a3b4a8ac7b02501d37cb0b360137`, merged `0981133bb384ce82adf727d9bcf2cd011f102e5f`. Exact Native Pilot `39913297922` SUCCESS: full regression, JVM/unit tests, three APK builds, 16KB/no-WebView checks and emulator launch/map/service lifecycle. Merge tree exactly equals the tested source tree. Separate map-display/navigation-map/maplibre runtime jobs were skipped on this source branch; do not relabel them passed. Read any new post-merge integrated results before further release certification.
- Native production scope is three map files only: online perspective, 25m camera/puck snap alignment, existing hosted QueueGo route URL default. Rider App UI/HUD/orders/API/proof/cash/auth unchanged. Web route provider returned HTTP200 with real road geometry (44 points, 1649m); no Production order or database writes.
- Exact Native build artifact `12483773589` (`queuego-native-pilot-apks`), SHA256 `0fadcf0ee7ec643f2437970e260fe68bdfa6ceceb92be239bb70458ba9ad5d8b`, belongs to source `c0b2865e`. This is CI build evidence, not installation on the owner's phone.
- BLOCKED (physical acceptance): real GPS/road smoothness, battery/foreground-background behavior and owner-device installation are not certified by CI/emulator. No physical-device PASS claimed.
- NEXT: retain these successors; read post-merge integrated map gates; address only evidence-backed map defects from actual device testing. Do not change cards/switches/branding/tabs or restore whole files.

## 2026-10-23 Current owner scope: map only; recovery cancelled

- Latest owner instruction: ไม่ต้องกู้แล้วปรับแค่เรื่องแผนที่. Do not continue whole-file/module timestamp recovery. Native recovery PR #658 is closed without merge. Initial recovery snapshots are historical references only.
- Web #657 had already merged at `2baf1a512eac574d3ca3095c581cc8f7589aaf43` before cancellation. The map-only successor removes its unintended UI/presentation changes by preserving the pre-recovery current UI from `59051ddfb89a03b7d34e3b5b94f80c62919f9e6a` in a new forward commit; no reset/revert.
- Evidence-backed map issue: Web qgNavigationCameraShouldRun excluded online Riders without a job, so live GPS updates could leave the map unfollowed and the puck near an edge. Online now retains the same perspective/heading follow as active navigation. Web speed zoom now interpolates continuously between the existing speed tiers; auto-pan recovery, 25m snap, sole QueueGo route service and current proof/cash/auth flow remain.
- Scope proof: all Rider stylesheet blocks and the existing non-map UI/rendering functions match the pre-recovery 59051ddf source. Compact status card, switch, branding, speed placement and tabs stay as in the owner screenshot. Only map activation, zoom policy and map asset/build identifiers change from that baseline.
- Focused validation: Rider route/GPS suite 145 PASS; map camera 72 PASS including zoom continuity across 5/11/18 m/s thresholds. Full local/hosted regression, Pages and public byte verification are NEXT; no physical road/GPS claim.
- NEXT: run exact-head full RC, merge the map-only successor and verify the live HTML/JS bytes. Never merge the cancelled Native recovery PR.

## 2026-10-23 Rider historical navigation recovery reconciled

- Owner requests Rider navigation from approximately 2026-10-22 18:00 Asia/Bangkok, and reports that the live screenshot still shows the newer layout. The initial recovery PRs were drafts, never deployed; do not describe the live site as restored before actual Pages readback.
- Historical source references: Web `7572146400f44bf66c96aca5ae5f84264fe4241b` (17:51:58), Native `db9bffdf0e77b2fdfc7b9321187e187e7c658d3b` (15:02:14). Source timestamps do not prove which APK was installed or which Pages deployment was active at that hour.
- Initial full source recovery is preserved at Web `d9682313c1a0750ac3c60f7d2ebd47cbb1574b08` / Android `8f355b0caf03e52fd568a774b5b8569d5d31d100`. Hosted first failure is retained: Web RC `39910241286` rider-runtime; Native Pilot `39910205915` scoped proof-recovery API contract. No failed run is relabelled as passed.
- Compatibility reconciliation preserves the timestamped navigation presentation/camera, while retaining current QueueGo-only route service and the current transaction/auth/proof-retry implementation. This final integrated source is NOT byte-identical to the historical whole file. No new map design, backend/schema/data changes or test removal.
- Web: original ready/status card and one switch, historical speed/branding/control layout, original online flat heading-follow and active-job perspective camera, speed-aware zoom and carriageway matching. Current 30m/3s + missed-turn reroute and durable single-proof/cash flow remain. Maneuver elements remain nonvisual as in the historical route-badge presentation.
- Local full `npm test` PASS; focused tests: Rider renderer/routes 136, camera 73, Rider runtime 114, durable proof DOM 36, route progress 22, missed-turn 7. Existing safety tests retained.
- STATUS: source/test ready locally; exact-head hosted RC, Pages deployment and public byte readback still OPEN. No physical road/GPS pass claimed.
- NEXT: exact-head RC; on success merge #657 using expected head, verify Pages and fetch public Rider HTML plus camera asset against committed bytes.

## 2026-10-16 Direct native category selection
- Owner screenshot/crop identifies the red เลือกหมวดหมู่ · ทุกหมวด intermediate button. Replace it with an always-visible native category dropdown so one tap opens options directly, in catalog and shop product views. Remove the extra reveal/hide handlers and red category-button styling. Preserve the30-category server filter, counts, search placeholder, fixed red add actions, draft exclusion and network behavior. No database change.
- Continue from main4a0d392e; PR363 merged as0a0042e0 after exact-headb5467f96 RC38831293929 (full regression, mobile WebKit, rendered components) and probe38831293865 passed. Pages38831582880 deployed successfully; public HTML/JS/CSS confirm visible native category selects and absence of intermediate category buttons, with direct-category1 cache versions. No database changes. Physical-device interaction is not separately certified. Android remains paused.

## 2026-10-16 Grocery category choices and fixed add buttons
- Owner additionally requires generic search placeholder: ค้นหาชื่อสินค้า ยี่ห้อ หรือบาร์โค้ด. Do not put Makro in the search field.
- Owner clarifies BOTH เพิ่มสินค้าเอง / เพิ่มสินค้าจากแคตตาล็อก buttons must stay fixed above bottom navigation while shop products scroll. Fix the specific high-priority sticky override for Grocery, reserve list space, and use explicit red/white primary buttons. Catalog row add buttons also need a higher-specificity red rule; the generic .qgg button white rule was winning.
- Source Add mode hid its entire category control. Expose a labelled category button/selector, populate counts from the entire active source catalog, and filter on the server BEFORE the51-row sentinel/offset. Extend the existing source search with a default-empty category argument; three-argument callers stay compatible. Category labels use real source paths rather than broad rice/sauce substring guesses; already-linked Makro shop products receive corrected category only.
- Actual Grocery shop has51 unpriced master-linked draft records among53 products. Do not delete records or invent prices: omit unpriced Grocery drafts from the shop product list while preserving catalog editing. Add shop product category filtering combined with existing search/availability state.
- Continue from main4edb6f9c, backup `backup/grocery-pre-category-dock-20261016`. Android remains paused. PR362 merged as8bc2d086 after exact-head82c1d2e4 RC38829329572/38829335545 and Makro probe38829335571 succeeded. Applied category migration after these gates; Pages38830105087 deployed successfully. Live owner-scoped readback:30 categories covering10000 active products,51 canned-fish results all correctly categorized,3-argument compatibility returns51 rows. Cold active count stays0. Public HTML/JS confirm categories1 assets and generic search placeholder. Mobile WebKit verifies fixed dock position before/after scrolling, final-row clearance, red buttons, category filtering and hiding unpriced drafts. Anonymous category RPC execution remains denied. No actual Merchant save or physical-device certification is claimed; Android stays paused.

## 2026-10-16 Grocery catalog connection and cold-product correction
- Continue from merged PR360 / main5fa3a941; backup `backup/grocery-pre-connection-recovery-20261016`. Web first; Android remains paused.
- Owner screenshot reproduces `column x.subcategory does not exist`. Latest deployed Merchant page RPC sorts by x.subcategory but omitted m.subcategory from its subquery. Add the missing projection in a successor migration; preserve ownership, sales channels, pricing and paging. SQL regression now loads the latest deployed override before testing, rather than only the older sales-channel function.
- Catalog reads retry once on transient transport/408/429/502/503/504 only; SQL/auth errors and writes are not automatically replayed. Failed loads show a retry button, disable saving and recover on online only when there is no draft/pending write. Owner/hash guards reject late work. Explicit manual-add/catalog entry labels and per-product POS/Delivery choices remain. Preserve other edited rows when adding one source product.
- Real source audit found95 cold rows in the deployed10000 despite the earlier zero-exclusion assertion. Source-category classification alone missed frozen-desserts and cold titles under flour/seasoning/etc. Correct crawler/import validation using title, deepest category and storage; add database active-row constraint and deactivate cold source rows without deleting facts or existing shop products. Do not represent the prior10000 as fully compliant.
- Refetched public Makro data:11414 eligible records after correction; generated a new exact10000 candidate snapshot outside the repository. After passing full regression/browser CI,95 validated source rows replaced the deactivated cold rows in Production; no existing shop products were deleted. No synthetic products, prices, barcodes, or image rights. Reference images remain Merchant previews.
- PR361 merged as fe2db0b2. Exact candidate2d080845 passed RC38827324936 and38827319972 (full npm, mobile WebKit, rendered Chromium) and Makro probe38827325166. Pages38827634637 deployed successfully. Both corrective migrations were applied after these gates. Production readback:10000 active,10000 reference images,874 brands,0 active cold rows,95 cold rows retained inactive. Owned authenticated-role server reads return51 master rows/51 source rows/51 Mama search rows and0 frozen search rows. No actual Merchant write was made during QA. Physical Merchant login/save and mobile network validation remain unverified and must not be claimed from isolated fixtures.

## 2026-10-15 Grocery catalog sales channels
- Owner removed the Grocery catalog promotion-price editor and requires explicit per-product choices for `ขายหน้าร้าน` and `เดลิเวอรี่ลูกค้า`. Continue from Web main `1efb918f434d60337baaf6776405cc669646caf4`; backup `backup-pre-grocery-sales-channels-20261015`; candidate `work-grocery-sales-channels-20261015`.
- Manual product editors on Web and latest Merchant Native successor already have separate POS/Delivery controls and GP-derived Delivery pricing, so do not duplicate or replace that flow. This change is scoped to the Grocery Master Catalog editor plus its existing RPC contract.
- Candidate removes `promo_price` from Merchant catalog payload/UI, persists `pos_available` and `delivery_available`, keeps one storefront price, and lets the existing GP price-lock derive Delivery price. Legacy Grocery promo values are retired by the staged migration; the private idempotent save receipt and ownership checks remain intact.
- Do not merge/deploy until Grocery SQL/UI checks and the full repository regression pass. Production mutation and authenticated release verification remain gated.

## 2026-10-15 Merchant catalog larger photos and compact prices
- Owner additionally requests smaller price-entry boxes and larger product images. Merchant-only card grid uses112x144 mobile/140x160 desktop packshots, two compact price/stock columns plus explicit POS/Delivery toggles and44px input/button targets; 16px numeric input text prevents Safari focus zoom. The promotion-price control is retired. Admin/Customer styling stays unchanged. Merchant CSS/JS cache versions are bumped together.

## 2026-10-15 Merchant catalog photo-first ordering
- Owner requests products with photos at the front/first pages and products without photos at the end. Implement globally in qg_grocery_merchant_page before LIMIT/OFFSET, including search/category/status filters, with deterministic name/id ordering within each group.
- Only approved nonblank images count as photos. Existing authorization, filters, 51-row sentinel and image approvals remain unchanged. Mixed-photo pagination regression runs in disposable PGlite only; never add synthetic photos to Production to demonstrate sorting.

## 2026-10-15 Grocery product photos: collection in progress
- Owner requests actual centrally supplied product photos for all 414 master products; merchants should not source photos individually. Continue from Merchant white-screen fix main1f983184/PR352 and preserve that work.
- Audited all414 imported records: every image is pending/null. Collected primary-source snapshots and a reproducible candidate review manifest. Current pass has147 label/size candidates,2 ambiguous,136 review and129 without a candidate; these are NOT visual approval or permission to publish.
- Downloaded146 unique actual distributor packshots; source URLs, original hashes and review copies retained. Additional official manufacturer candidates are kept separately. No generated packshots, retailer hotlinks, invented GTINs or invented reuse grants.
- Existing Admin image approval/upload and Storage/RLS remain authoritative; do not introduce a duplicate image system or weaken the approved-image constraint. Production image writes remain0 until actual identity and reuse evidence are verified. Completing the other267 matches and obtaining valid reuse evidence remain OPEN.

## 2026-10-13 approved category banners verified
- Final Web merge b5f9e4e3c84ca4e1daf6cb0ccd44d32cc38c07a7/PR181 has exactly tested3d29de55 tree. RC38419925893 and merged-tree RC38420111285 / Pages38420111247 SUCCESS. Actual public Shopping overview, six subcategories and Laundry images decode960x458; bright full photos/light text wash and original category icons verified.
- Native merge22a2a8b29b96ba555daa9aedcd5620e0b7d1e4cf/PR177 has exactly testedc01e91bf tree, preserving newest Rider4acbcda. Pilot38419935708 SUCCESS: full regression/source, all-role JVM, all3 debug builds,16KB/no-WebView and native-pilot launch/map/service lifecycle. Separate map-display-only/navigation-map-runtime jobs skipped for this branch; no new certification from those jobs.
- Internal APK artifact11825313667 ZIPsha256d5f0f441e0b7fba052e3f3280fe4266ab69ee3f0343535dd9b7b14df82b29ba4 verified; all8 packaged Customer nodpi images decode960x458 and match Web bytes. Owner freezes the seven selected category images; only Laundry gets the small local-shop adjustment. No further photo regeneration unless requested. No business/backend writes. Physical/authenticated E2E and final Release Gate remain OPEN; no release APK/AAB delivered.

## 2026-10-13 final selection freeze
- Owner says use the existing selected images for every category in BOTH Web and Android; make only a small ordinary-shop adjustment to Laundry. Freeze the seven selected category images; no further scene changes outside Laundry. Retain black collared shirts/red Q, bright full pictures, no dark panel/strip and original category icon tiles. Any format compression only keeps the selected picture.

## 2026-10-13 owner final banner scene correction: everyday Thai shops
- Owner requires ordinary community shops and normal working vendors, not luxurious/designer showroom scenes. Phone imagery must show a mall glass-counter kiosk. Other categories use practical local shops/stalls and simple shelves. Everyone wears a BLACK COLLARED SHIRT with a RED Q chest emblem, no apron.
- Keep bright shop details throughout the image, no black/dark left panel or strip. UI uses a soft light wash with readable dark text. Preserve category icons. Apply the same final artwork to Web and Native Laundry, Shopping and every Shopping mode. This instruction supersedes the intermediate luxury shop images; do not publish those variants.

## 2026-10-13 owner banner correction: black shirt, red Q, bright full image
- Latest explicit owner instruction: every depicted person wears a BLACK COLLARED SHIRT with a RED QueueGo Q on the chest. No white shirt or apron. The intermediate black-Q instruction was superseded by the explicit red-Q correction. Keep original category icon buttons.
- Owner rejects dark/black left panels or strips. New artwork fills the entire banner with a bright shop and relevant merchandise. Existing UI text uses a light translucent wash and dark type; remove the old black gradient. Apply the same photo bytes and presentation to Web and Native Customer for Laundry, Shopping and all Shopping modes.
- Web baseline503791f; preserve latest Native Rider manual-pan successor4acbcda. Native prior a07b8a0/Pilot38412835929 SUCCESS and APK artifact11822963430 ZIPsha2563d5522a362876bede047251773f1be4458d6c1f5c3866e43ffe5f914a62652a1 verified, but its dark/white-shirt artwork is superseded by this correction. New exact-head regression/build/runtime/publication remains required. Physical/authenticated Release Gate stays OPEN; no release APK/AAB delivery.

## 2026-10-13 complete Customer category banner artwork
- Owner requests matching Web/Native Android banners for Laundry, Shopping and every Shopping mode, while retaining the original category icon buttons. Continue Web31a4187 and Native56371ee; preserve Rider map/lifecycle/sequencing fixes and all unrelated successors.
- Existing shopping sprite is not a decodable WebP in the repository. Replace its use with individually verified WebP files: shopping/mobile/computer/automotive/fashion/toys/home and laundry, all under50KiB. Automotive car/motorcycle use the automotive family image. Artwork is category illustration, never a substituted real store/product photo.
- Both versions use identical image bytes; managed Admin banners/hide/link settings retained, bundled image remains visible while a remote banner loads/fails. Images belong in banners; category icons unchanged. No order/payment/state/backend changes. Source/CI and live publication checks pending; Native physical Release Gate stays OPEN.

# QueueGo project memory

## 2026-10-10 Native voice privacy disclosure
- Added factual privacy disclosures for Native optional microphone permission, order-scoped audio-only WebRTC transmission, no audio recording/storage, call status/timing/participant metadata, transient Supabase signaling and Firebase/Cloudflare infrastructure. No new order flow, UI redesign or permissions are introduced on the web.
- Backup before publication: backup-web-pre-native-voice-privacy-20261010 at a03eb794. Historical Capacitor Android release remains hard-blocked. Native app source of truth remains queuego-native-android-v1; physical push/voice, secret configuration, signing and Play Data Safety certification remain OPEN.
- Full web regression and live published privacy readback are required before calling publication verified. This disclosure does not certify physical audio, TURN or notification delivery.

Updated: 2026-10-06 (Asia/Bangkok).

## User instruction

Keep the navigation return feature below in project memory. Start implementing the Android return overlay only when the user explicitly instructs us to build an APK. Do not start it during web changes or unrelated development, and do not treat this memory as authorization to implement now. When the user requests an APK, include this feature in that work. The iPhone Live Activity remains a future option requiring a separate user request for iOS work. Both features are pending, not implemented.

## Pending: return to the active rider order while navigating

- Android installed app: add a draggable QueueGo Q overlay button while a rider has an active job and opens external navigation. Tapping returns to the same active order, preserving the session and current state. Request the OS draw-over-other-apps permission explicitly. Provide a notification return action when overlay permission is unavailable. Hide the overlay on completion, cancellation, logout, or loss of ownership. Implement in the native app, not only webpage CSS.
- iPhone installed app: use a delivery Live Activity and Dynamic Island on supported devices, with a link back to the same active order. Use notifications as appropriate for other devices. Do not promise an Android-style free-floating cross-app button on iOS. Requires native iOS integration; not implemented in the web app.
- Web/PWA: do not represent an in-page floating control as a cross-app overlay.
- Use the existing order and authenticated rider identity. Returning to the app must never claim another order or change order/payment state automatically.
- Navigation before pickup targets the shop. After pickup it targets the customer's actual delivery location. The authoritative order states remain unchanged. Rider verification is photo-only: one required pickup photo before leaving the shop and one required delivery photo before completion. These are delivery-safety proofs, not separate payment-confirmation states.

## Existing flow constraints

Customer order → PENDING → merchant accept → SEARCHING_RIDER → rider claim → RIDER_ASSIGNED → merchant preparing → PREPARING → merchant ready → READY → rider pickup → PICKED_UP → start delivery → IN_PROGRESS → complete → COMPLETED.

Shop arrival is an audited event (`rider_arrived_shop_at`), not a new order state. Manual payment confirmations were removed; existing backend cash/audit records are created by order actions. Keep this behavior.

## Verification needed before calling the pending feature complete

- Physical Android device: permission allowed/denied, Google Maps navigation, dragging/tapping overlay, active-order restoration, completion/logout cleanup, and app lifecycle.
- Physical iPhone: supported Live Activity presentations, tap to restore the correct order, lifecycle and completion cleanup.
- Customer/Merchant/Rider regression checks; preserve order ownership, state machine, notifications, and audit history.

## Last implementation reference

Commit `52c41aaf7863b916214c8baa63ffe374f8865afd` added a prominent customer navigation link after pickup and prevented market pickup coordinates from overriding the delivery destination. Cross-app overlay and iOS Live Activity remain pending.

## Rider web improvements implemented in this release

- Returning from external Maps restores the active job view after checking the current authenticated session; cached job details are marked stale and action controls stay locked until refreshed.
- Existing rider actions use `qg_rider_action_once` with a durable request ID and an owned receipt. Lost replies recover with the same ID rather than duplicating the action. Supabase remains authoritative.
- The job card shows the actual customer delivery address, order notes/landmarks, and customer phone.
- Opt-in Web Push connects the existing notifications table to a server-only outbox and Edge Function; chat messages to the rider generate notifications. Users enable it from Rider profile. iPhone requires a supported Home Screen web app.
- Quick rider issues use the existing support tickets with an automatic order link and a stable ticket ID on retries.
- Isolated regression tests cover action recovery, account races, quick ticket retries, and push-worker ownership. Physical-device push delivery and a complete live three-role delivery flow still require validation.
- The Android cross-app Q overlay remains deferred until an explicit APK request; iOS native Live Activity is also pending.

## Customer features restored after the cleanup audit

- Backup: `backup-pre-customer-function-restore-20261005` at `ad8c61ce8ad599cb826840df134a6413c53d467b`.
- Restore current Customer capabilities using `customer-features.js` and existing tables, with no legacy renderer or CSS overrides loaded.
- Tracking reads only an owned order through `qg_customer_order_context`; closed orders never return live Rider coordinates. Missing/stale GPS stays visible as missing/stale, with no invented movement.
- Chat uses existing `order_chat_messages`, durable per-user/order message IDs and the existing chat notification trigger. Customer and Rider may both continue the conversation for 30 minutes after completion; after that the backend denies Customer/Rider reads and writes and expired Customer/Rider messages are purged.
- Favorites, reviews, notifications, shop menu categories, search modes, support evidence, email signup, cart removal/merge and profile links are restored. Promotions show existing active shop campaigns; no coupon/discount model is added.
- Review ownership checks bind order, customer and shop; no financial/order transition RPC is replaced. Customer cancellation uses existing RPCs only.
- Manual payment-confirmation screens stay removed. The customer delivery-PIN flow is retired. Rider uses only a pickup photo and a delivery photo as required proof. Android overlay still waits for an explicit APK request.
- Full physical delivery, background mobile GPS, notification sound and push delivery still require device validation; unit tests do not certify these.

## Market approval continuity fix

- Admin dashboard has an explicit “อนุมัติตลาด” entry with a live pending count from existing market_requests and shop_profiles.
- Market approval uses the existing queuego_admin_review_market_request and queuego_admin_review_market_membership RPCs. New market approval queues the applicant shop for a separate membership review; it never auto-approves that shop.
- Failed request reads must show an error, never an empty approval queue. Realtime refresh also refreshes the market queue/count, with account ownership checks.
- Membership review displays existing shop location and front/stall evidence; no new approval tables or order/payment states.
- No actual pending request is approved as part of QA; the decision remains with Admin.

## Admin provider category labels

- Admin account approval, user details, shop control and market membership/request review display the shop's selected category (e.g. market → ตลาดสด), alongside the actual shop name and contact.
- Hydration prefers shop_profiles.public_category; older rows fall back to the saved category. Missing categories remain explicitly unspecified rather than inferred from membership.
- Role and permissions remain shop; the displayed business category does not create a new role or approval state.

## Admin market/shop master coordinates

- Admin can edit market coordinates from Admin → อนุมัติตลาด, and shop coordinates from the existing shop control list or pending market membership review.
- qg_admin_update_location checks the current active Admin in Supabase, validates coordinates and reason, locks the target and rejects stale expected coordinates. Retries of an already saved position are no-ops, without duplicate audit.
- Existing audit_logs records Admin identity, target, old/new coordinates, reason and timestamp atomically with the update. Related market suggestion distances are refreshed; existing membership/approval triggers remain.
- Existing order pickup/delivery snapshots and state machine are not changed. Coordinates are master data for subsequent orders.
- QA uses isolated fixtures plus real transactional checks with rollback; no actual market/shop pin is moved permanently by QA.

## Admin interface rebuild (2026-10-05)

- Replaced Admin presentation: one `admin/interface.css`, a responsive sidebar/mobile navigation, dashboard, shop/user cards, approval filters, announcement form and section selector. Removed all inline legacy style blocks, old panel injection, unused customer chat/tracking UI and the obsolete Admin market stylesheet.
- Longdo map selection replaces numeric coordinate entry and Google Maps links in Admin. Search, click/drop/center selection and optional GPS use the existing provider/key; an unavailable map locks saving. No default map center is saved without an explicit selection.
- Existing `qg_admin_update_location` remains authoritative with expected old coordinates, audit and retry behavior. Existing order coordinates and financial state transitions are untouched.
- Native prompts were replaced by labelled dialog forms for reasons, market radius and vehicle capacity. Tests cover validation, account races, map failure and retry payloads.
- Automated regression passes do not certify physical mobile GPS, live map tiles or real account approval. Local browser installation was blocked by the browser download returning HTML rather than a browser archive; do not claim visual browser/device certification from automated DOM tests.

- Publication authorized explicitly by the user on 2026-10-05. Rebased onto the current Admin redesign at `a4abadde6fd3f2a7b03e992f173fc7cbb00db2da`; backup before publication: `backup-pre-longdo-publish-20261005`.

## Merchant dashboard banner removal (2026-10-05)

- Removed the home dashboard photo banner and its dedicated CSS at the user's request. Shop identity/open toggle, profile photo management, order statistics and all data/actions remain in place.

## Merchant setup checklist (2026-10-05)

- Replaced the pending application screen with a Thai checklist reading the owner's current shop profile and products. Shows missing contact/address/category/photos, valid Longdo pin, at least one named priced product with image, actual account approval, and market membership when category is market. Does not invent a contract/signature completion.
- Dashboard shows setup while incomplete and normal summary when complete. Approved shops retain order access. Pending shops can edit profile/images/products and contact support through existing handlers; no approval or order state is changed. Failed reads show retry and account/navigation races discard results.
- Verified source parsing, Merchant routing/checklist and realtime fixtures; read existing products RLS to confirm owner insert/update policy. Physical mobile rendering and a real pending-shop submission remain unverified.

## Merchant market menu tile (2026-10-05)

- Moved the market stock entry into the existing main-menu grid as a matching icon card labelled “ตลาดและสต๊อก”. Removed the standalone wide red button and its layout CSS. Existing market category visibility and market-stock navigation are preserved.


## Merchant notifications interface (2026-10-05)

- Rebuilt the Merchant notifications presentation into a mobile-first card layout with a clear header, compact sound-test control, segmented category filters with counts, date grouping, category-specific SVG icons, readable message hierarchy, timestamps and link affordances.
- Removed reliance on unsupported glyph symbols that could render as black squares. Existing notification data, filters, mark-as-read behavior, navigation links, sound test, order state machine and backend writes are unchanged.


## Merchant order price display (2026-10-05)

- Merchant-facing order amounts now show product sales only, excluding delivery fees. This applies to the dashboard recent orders, order list, and order detail total.
- The order detail label is now “รวมค่าสินค้า”. Existing order totals and delivery fees remain stored unchanged for customer/rider/platform accounting; this is a Merchant presentation fix only.
- Legacy-safe calculation prefers order subtotal, then item totals, then total minus delivery fee when needed. No order state, payment flow, GP logic, or database schema was changed.


## Merchant single price + locked Delivery GP (2026-10-05)

- Merchant product editor now has one editable price: “ราคาขายหน้าร้าน”. The separate editable POS and Delivery price inputs were removed.
- Delivery price is read-only and displays the effective shop GP above it as “+ GP X%”. Current effective GP is loaded from `effective_gp_rate`.
- Delivery price formula is storefront price × (1 + GP%). Example at 10%: 100 → 110. The product save writes `price` and `pos_price` to the same storefront price.
- Supabase migration `20261005094500_product_delivery_gp_lock.sql` enforces the rule server-side. Direct attempts to overwrite `delivery_price` are replaced by the derived value, POS price edits also rederive Delivery, and current products were normalized.
- Product prices are repriced when current per-shop GP, legacy GP setting, or platform GP rule changes. Existing order/payment/state-machine behavior is unchanged.


## Delivery GP markup accounting (2026-10-05)

- The locked Delivery price model is now financially aligned end-to-end: merchant storefront price is the base, Delivery price = base × (1 + GP%).
- `orders.gp_rate` remains the configured nominal GP percent, while `orders.gp_amount` is now the actual markup portion embedded in the Delivery subtotal. Example: storefront 100, GP 10%, Delivery 110, GP amount 10.
- New Delivery orders calculate GP as subtotal × rate ÷ (100 + rate), so the merchant nets the storefront price after GP. DINE_IN/TAKEAWAY remain GP 0.
- Existing orders are not repriced or rewritten. If an existing order subtotal changes later (for example a safe recalculation), its existing GP/subtotal ratio is preserved so historical pricing rules are not silently changed.


## Product image upload feedback (2026-10-05)

- Merchant product editor now shows a visible upload-status card for the product image.
- Existing saved images are labeled as already stored in the system. Newly selected images show thumbnail, filename, file type, approximate size, and the status “เลือกแล้ว · จะอัปโหลดเมื่อกดบันทึก”.
- This changes presentation/feedback only; the existing Storage upload flow still uploads the selected product image when the merchant presses Save.


## Restaurant product categories (2026-10-05)

- For restaurant/cafe Merchant product editing, the free-text product category field is replaced by a required fixed selector.
- Categories are: เมนูแนะนำ / เมนูขายดี, อาหารจานเดียว, ข้าว, เส้น / ก๋วยเตี๋ยว, ของทอด, ของย่าง / ปิ้งย่าง, ต้ม / แกง / ซุป, ผัด, ส้มตำ / ยำ, กับข้าว, อาหารทะเล, ของทานเล่น, ของหวาน, เครื่องดื่ม, ชุดคอมโบ / เซ็ต, เมนูเด็ก, เมนูสุขภาพ / คลีน, เพิ่มเติม / ท็อปปิ้ง, อื่น ๆ.
- The Merchant catalog category buttons use the same restaurant list for food/cafe shops. Save validation rejects restaurant categories outside this list. Other shop types keep their existing category behavior.


## Restaurant menu options builder (2026-10-05)

- Merchant restaurant/cafe product editing now has a structured menu-option builder instead of the old comma-separated free-text field.
- Optional single-choice portion group supports “ธรรมดา” at base price and “พิเศษ” with an editable surcharge.
- Optional multi-select toppings support starter rows for ไข่ดาว, ไข่เจียว, เพิ่มเนื้อ and เพิ่มข้าว, with per-item enable toggles, editable names/prices, delete, and custom topping rows.
- Saved data uses the existing `products.variants` JSONB column with schema marker `queuego.menu-options.v1`; no database schema change or order/payment/state-machine change was needed.
- This step only creates and saves Merchant-side option configuration. Customer Delivery, POS and table-QR option selection/pricing are not yet wired to consume these structured variants and must be implemented before calling modifiers end-to-end complete.


## Restaurant catalog simplification (2026-10-05)

- Removed the duplicate restaurant/cafe category card grid from Merchant → Products. Restaurant/cafe merchants now go straight to their current menu list and use “เพิ่มเมนูใหม่”.
- Category selection remains only inside the menu editor, where it is required and uses the fixed restaurant category list.
- Retail/non-food shop catalog category cards remain unchanged.


## Merchant readiness check smoothing (2026-10-05)

- Approved merchants no longer see the blocking “กำลังตรวจสอบข้อมูลร้าน...” screen every time they return to Dashboard.
- A successful readiness result is cached per merchant for the current Bangkok business day. After that first successful check, Dashboard renders immediately.
- Readiness is revalidated silently in the background once per page load. The existing full merchant hydration/poll still refreshes account approval/suspension status, so Admin status changes are not ignored.
- Manual “ตรวจสอบข้อมูลอีกครั้ง” clears the readiness cache and performs a fresh check. Saving core shop profile/location data also invalidates the cache. Logout clears readiness cache entries.
- Incomplete/unapproved shops still see the setup checklist. Order state, payments, GP, approval authority, and backend security rules are unchanged.


## Buriram market picker + membership fix (2026-10-05)

- Merchant market membership now loads active Buriram markets with coordinates and automatically selects the market nearest the saved shop pin first.
- Merchant can override the automatic choice using a normal dropdown, or type a market name to get close-name suggestions; search results still show distance, verification state and whether the shop pin is within the market assignment radius.
- The same nearest/search/select behavior is available during first-time market-category shop registration. Existing server-side `queuego_submit_market_membership` remains authoritative and still rejects a shop pin outside the selected market radius.
- Fixed the market-membership runtime error `Can't find variable: qtSupabaseRpc` by using the existing `qtSupabaseTable('rpc/...')` data path with the current authenticated token.
- Market picker data comes from the existing `markets` table; it does not create duplicate markets. Current Buriram master data includes ตลาดสดเทศบาลเมืองบุรีรัมย์ and ตลาดสดสวายจีก. Unverified market coordinates remain visibly marked for Admin verification.


## Fresh market product categories (2026-10-05)

- Market-type Merchant product editing now uses a required fixed product-category selector instead of free text.
- Categories are: ผักสด, ผลไม้, เนื้อหมู, เนื้อวัว, ไก่ / เป็ด, ปลา, อาหารทะเล, ไข่, เต้าหู้ / เส้นสด / ลูกชิ้น, ของสดพร้อมปรุง, อาหารแช่เย็น / แช่แข็ง, พริกแกง / เครื่องแกง, เครื่องปรุง / ซอส, ข้าวสาร / ธัญพืช, ของแห้ง, อาหารปรุงสำเร็จ, ขนม / ของหวาน, เครื่องดื่ม, ของใช้ในครัวเรือน, ดอกไม้ / ของไหว้, อื่น ๆ.
- This selector applies to market/meat/fish/vegetable/fruit shop types in the normal Merchant product editor and in the dedicated Market stock product editor. Grocery keeps its existing flexible category behavior.
- Vegetable, fruit and fish shop types preselect ผักสด, ผลไม้ and ปลา respectively for a new product; merchants can change the selection before saving. Existing order/payment/GP behavior is unchanged.


## Market catalog simplification (2026-10-05)

- Removed the duplicate category-card grid from Merchant → Products for market/meat/fish/vegetable/fruit shop types.
- Market-type merchants now see search, current products and the add-product button directly; category selection remains inside the product editor using the fixed fresh-market category list.
- Restaurant/cafe category grid remains removed as before; grocery and other retail shop types keep their existing catalog grid behavior.


## Merchant authoritative category hydration fix (2026-10-05)

- Fixed a UI mismatch where Merchant could keep using a stale category from `users.metadata` even though `shop_profiles.public_category` had already changed (for example a real market shop still rendering grocery category cards).
- Merchant hydration now treats `shop_profiles.public_category` as the authoritative category and exposes it on the current shop user. Market membership status is hydrated alongside it.
- Merchant sync signatures now include shop category/membership fields, so a full profile refresh rerenders safe pages such as Products when the authoritative category changes. This makes the removed market category grid disappear without requiring a new login.


## Merchant order action reliability (2026-10-05)

- Fixed Merchant accept / start preparing / ready-for-rider / cancel actions that could require repeated taps or show an error even though the server had already committed the transition.
- Root cause: Merchant called `merchant_order_action` and then awaited a full database hydrate inside the same try/catch. A refresh/network failure after a successful mutation was incorrectly shown as an action failure, leaving a stale action button that invited another tap.
- Added `qg_merchant_action_once` with `qg_merchant_action_receipts`. Merchant now sends one durable request UUID; a lost HTTP reply can be retried with the same UUID without repeating the transition, audit entry or notifications.
- Merchant buttons disable immediately while saving. Successful server status is applied to the local order immediately, so the action button disappears without waiting for a full refresh. Full hydration runs afterward in the background and cannot turn a committed action into a false failure message.
- Transient transport failures retry once with the same request UUID and then reconcile the exact order status. If the outcome still cannot be confirmed, UI reports an uncertain connection state and refreshes automatically instead of claiming the order action failed.
- The same reliability path is used by the normal order detail screen and Kitchen mode. Existing rider-first state machine, `merchant_order_action`, delivery rows, notifications, audit logs, cash model and payment behavior remain authoritative and unchanged.


## Customer market shop visibility (2026-10-05)

- Fixed Customer → ตลาดสด so the market selector and shop directory no longer depend on having at least one sellable `market_products` row. Active markets come from a new public `market_public_markets_v1` RPC, and approved market-member shops come from `market_public_shops_v2`.
- `market_public_shops_v2` is now a safe SECURITY DEFINER public directory with explicit active-shop / active-user / approved-membership / active-market filters, so guest Customer access no longer trips RLS helper permission errors.
- Customer market page now shows a “ร้านค้าในตลาด” section even when a shop has no market-stock product ready yet. Shop cards distinguish shops that have not enabled Delivery from shops that are open but have no sellable market stock.
- Market selection uses the customer's current location when available to order markets by distance and preselect the nearest one; customers can still choose another market manually.
- Product checkout behavior remains strict: only products in the existing `market_public_catalog_v2` (approved shop, Delivery enabled, market stock configured and in stock) are orderable. This avoids showing a product that would fail Market Trip checkout.


## Customer market banner (2026-10-05)

- Installed the user-supplied fresh-market banner above the market selector, with its full 16:9 composition and embedded WebP under 50 KiB, plus a green gradient background.
- Removed the market search input and label from production markup. Existing nearest-market selection, categories, shop product links, market cart and order behavior remain available.
- Backup: `backup-pre-market-banner-20261005-1853`.


## Notification read state + 30-minute Rider/Customer chat retention (2026-10-06)

- Customer notification center marks currently unread notifications as read when the user opens it. The local unread count is cleared immediately after a successful write so the red bell badge does not survive a read because a background notification refresh is already busy.
- Rider/Customer order chat remains available through the active delivery and for exactly 30 minutes after a completed delivery. Either side may initiate or reply during that post-completion window.
- Supabase is authoritative: `qg_chat_postjob_allowed` gates inserts and `qg_chat_read_allowed` gates reads. Completed chat uses the actual delivery/completion timestamp and closes after 30 minutes even if a client remains open.
- `queuego-chat-retention-30m` runs once per minute via pg_cron and removes expired Rider/Customer messages for completed orders. Admin access/audit behavior and unrelated shop/technician behavior are not broadened.
- Customer and Rider UIs hide or close expired chat threads and show the remaining post-completion window while it is active.
- Existing expired post-completion Rider/Customer chat was cleaned when the migration was applied. Order state, payments, delivery records, notifications and audit history are unchanged.
- Backup before this change: `backup-pre-notification-chat30-20261006-1233`.


## Rider professional flow — acceptance reference (2026-10-06)

The user-supplied Rider reference screens are the acceptance reference for workflow and information hierarchy. QueueGo keeps its own red brand and implementation; do not copy another service's branding/assets.

- Home/waiting: map-first Rider home, online/offline state, nearby-job readiness, messages, earnings, profile and laundry mode.
- Offer: show order code, shop, customer area, distance/ETA when available, Rider earning and explicit Accept/Reject. Never fake a countdown unless the server owns an expiry.
- Pickup: when the Merchant reaches READY, Rider opens a dedicated pickup screen, sees real order items/customer/note when available, takes exactly one required pickup photo, then taps “รับสินค้าแล้ว”. Do not add checkbox gates, customer PINs, or slide-to-confirm controls.
- Pickup confirmation records the existing cash handoff to the Merchant and moves a normal order through READY → PICKED_UP → IN_PROGRESS atomically. There is no separate payment-confirmation screen.
- Market pickup: every shop pickup also requires one pickup photo before that pickup can become PICKED_UP.
- Delivery: Rider map targets the Customer, keeps call/chat/details available, and uses the existing GPS arrival guard. Being outside the guard shows a warning rather than inventing a new order state.
- Completion: Rider takes exactly one required delivery photo and taps “จัดส่งแล้ว”. There is no customer handoff PIN and no optional second proof photo.
- Customer: customer UI must not generate, fetch, or show any Rider delivery PIN.
- Cash: pickup records Rider cash paid to Merchant; completion records Customer cash payment through the existing order/payment transition. No repeated “confirm money” step.
- Market completion validates every shop cash advance atomically, persists the delivery photo on suborders, writes completion audit history and sends one group completion notification.
- Layout: do not use elongated slide controls when a normal action button is sufficient. Do not stretch empty sections; render real data when present and omit empty detail blocks where possible.
- Earnings/cash ledger and profile/message/laundry surfaces remain part of the Rider experience. Rider/Customer chat remains available for 30 minutes after completion, then backend read/write access closes and retention cleanup removes expired Rider/Customer messages.
- Legacy full-screen Rider checklist, pseudo `arrived` order state, delivery PIN, slide-to-confirm pickup/delivery and direct client IN_PROGRESS → COMPLETED shortcuts must not return.
- Backup before this correction: `backup-pre-rider-photo-only-flow-20261006-1501`.
- Automated RC tests certify code/state/security contracts only. A physical-device three-role delivery is still required before claiming camera, GPS, route and mobile UX fully certified.


## Merchant preparation countdown inside ready button (2026-10-08)

- Owner requested the existing 20-minute preparation countdown inside the “สินค้าพร้อมส่ง” button. Removed the separate countdown card and its unused CSS.
- Countdown still derives from Production `preparing_at`; reopening the order does not restart it. At expiry it stays at 00:00 and the Merchant explicitly presses the ready action. The timer does not transition an order automatically or prevent an earlier ready action.
- No database, RPC, RLS, payment or order-state changes. Backup: `backup-pre-merchant-button-countdown-20261008-0856`.

## 2026-10-12 Grocery Master Catalog mission (independent of Rider)

- Owner authorized a real Thai Grocery Master Catalog with Big C as one reference, factual manufacturer/distributor sources, strict image-rights handling and batch idempotent import. Do not bulk-copy Big C or use retailer image hotlinks. No fake production catalog, no store-mandated master price, no Rider Native mission changes.
- Candidate branch `work-grocery-master-catalog-20261012`, base main `7595d764`; backup `backup-web-pre-grocery-master-20261012`.
- Candidate schema links master identities to existing `products` store listings and preserves checkout/GP/POS/state flow. Paged Merchant onboarding, Admin catalog/import/merge/image approval and Customer Grocery storefront are staged. New imports are inactive until review. Missing licensed images stay pending.
- Research: 1,109 factual observations, 415 normalized package records across all 45 populated categories; 45 category definitions. 691 package identities remain incomplete; no verified barcodes and no approved/licensed images. No Production migration/import/deployment occurred.
- See `docs/GROCERY-MASTER-CATALOG.md` and generated `catalog/statistics.json`. Release remains OPEN: authenticated Production/mobile E2E, restorable Production backup/rollout/advisors, multi-client stock races and Production 10k query evidence. The 691 incomplete observations are excluded from import. Overlapping store listing merges reject deliberately; inventory/price conflicts must be reviewed rather than guessed. Do not call this candidate a completed or released catalog.

- 2026-10-13 follow-up: corrected Thai substring classification and quarantined six ambiguous name/size observations; added 81 reviewed package observations, preserving source references. Actual 415-row isolated SQL import/reimport passes. Added barcode/no-barcode bridging and server identity-conflict/null-input denial checks. Local Grocery coverage: 66 SQL/normalization + 16 Merchant/Customer DOM + 11 Admin DOM + 48 rendered browser checks (320/390/768px). Admin merge target uses searchable product selection and requires confirmed server success.
- Backup access blocker: connected Supabase MCP has no backup tool; CLI linked dump returns ProjectRefNotLinkedError and no DB connection/token environment is configured. No current restorable DB backup obtained; no Production schema/data mutation permitted before that prerequisite is met. User authorized completion and rollout; do not ask for deployment approval again.

- 2026-10-13 Production Grocery checkpoint supersedes earlier no-mutation/backup blocker: private scoped application snapshot (82 tables, 1,860 typed rows, exact round-trip) saved before additive migration. Excludes Auth credentials and physical Storage bytes; not full disaster recovery. Installed Grocery schema and imported/reimported 414 real inactive masters across 45 categories, 415 source records, zero GTINs/approved images/store links. Existing 7 product row hashes unchanged. Web PR #168 remains draft, activation and authenticated E2E/release gate OPEN. Generic backup/management seed scripts contain no private snapshot data. User has authorized completion and rollout; no repeat permission needed.

- Latest published Grocery head e73cf77c4027ce38c2278a66ced8761848b1aaa6, RC CI 38334905956 SUCCESS (npm regression + rendered browser). Production no-session authenticated-role import/inactive master/source isolation passed. Timestamped non-private Production counters are separate from generated research statistics. Activation/authenticated web E2E/main publication remain OPEN.


## 2026-10-13 continuation — reviewed activation and advisors

Fetched current main and Grocery branch; no Grocery successor PR found. Exact head `f2f5740e5f3fea2618a1a2766cc6635784263eb7` RC CI run 38335174150 succeeded. Re-ran full npm regression and 48 Chromium component checks (320/390/768 px); these remain isolated tests, not authenticated E2E.

Production advisors identified three Grocery foreign-key indexes. Added and read back `grocery_master_merged_into_fk`, `grocery_stock_product_fk`, and `products_grocery_master_fk`; follow-up performance advisor reports no unindexed foreign keys. Two internal deny-all tables intentionally retain RLS with no client policies. Other system advisories are outside this mission.

Rechecked official manufacturer pages and activated ONLY Foremost plain UHT milk 225 ml, one carton (`https://www.foremostthailand.com/product/100-uht-plain-milk/`, explicit one-carton nutritional serving) and Roza salted pickled cabbage 170 g, one can (`https://www.hiqfood.com/product/salted-pickled-cabbage/`). Production readback confirms 2 active / 412 inactive masters, no store links. No prices, GTINs or image permissions invented. Total remains 414 / 45 categories / 415 provenance / zero barcodes / 414 pending images.

Release remains OPEN. Cloud Merchant shows the sign-in form and has no saved session. Real Merchant A/B, Customer and Admin web E2E cannot be certified without authenticated sessions. No main merge or web deployment. Concurrent stock gate must investigate the Production cash-order core's `FOR SHARE` product read followed by the Grocery trigger's `FOR UPDATE`: simultaneous transactions may require a lock upgrade and deadlock. This is a source-review concern, not a reproduced Production race; do not mark concurrency passed. POS readback uses order_items inserts and the new stock trigger, without a second explicit stock decrement in pos_edit_bill/pos_take_payment. Rider Android untouched.


Later in the same continuation, secure Merchant sign-in succeeded. The visible account page confirms a Grocery test shop. This supersedes the earlier no-Merchant-session observation; it does NOT certify candidate UI E2E, because main still lacks the PR's Grocery editor. Production currently has one non-archived Grocery shop, so two-real-store price isolation is still unavailable. Customer/Admin authenticated sessions and simultaneous stock connections remain unverified. Commit `7c0a485eff6d9a434a5da9f982449439a552ec02` RC CI 38335868729 succeeded. Gate remains OPEN; no main merge/deployment.


- 2026-10-13 Grocery concurrency continuation: confirmed Production cash checkout used product `FOR SHARE` before the Grocery stock trigger's `FOR UPDATE`. Deployed migration `20261013023547_grocery_cash_checkout_lock_order`: checkout now locks products `FOR UPDATE` in deterministic UUID order before `order_items` stock triggers. RC 38337527764 passed full npm + rendered Grocery browser checks before rollout. Post-deploy readback preserves SECURITY DEFINER/search_path and existing ACL (no direct anon/authenticated execute), confirms deterministic order/FOR UPDATE and removal of old FOR SHARE. Source deadlock hazard is closed, but real simultaneous authenticated Grocery checkout E2E remains OPEN. PR #168 stays draft; no main/web deploy; Rider Android untouched.

## 2026-10-15 Merchant white-screen root cause
- Continue Web main `9b8eeacf1f7cc0a801523c1095942d954b84295d`; inspected PR347/348/349/351 and open PRs. No newer open Merchant Web/Grocery successor was present. Backup `backup-merchant-white-screen-20261015` preserves that baseline.
- Reproduced on the actual authenticated Production path: Products → เพิ่มสินค้าจากแคตตาล็อก changes the hash but rejects with `ReferenceError: shopUser is not defined` in grocery-catalog.js. shopUser/profileId/layout were private to the Merchant IIFE, while the external Catalog renderer used them as globals. Previous component tests supplied replacement helpers, masking the missing integration. Router also ignored the returned async render promise.
- Export the existing three helpers; capture rejected async route/direct-click renders. Add an independent static recovery shell and empty-app guard, keep optional Map/Voice loading from blocking HTML parsing, and provide an AbortController timeout fallback for Safari without AbortSignal.timeout. POS cleanup must tolerate a missing optional POS script. No production database/auth/credit/business-rule changes.
- Add whole-document integration and mobile WebKit scenarios using localhost-only transport fixtures. These do not certify physical iPhone or live Supabase writes. Owner signed in through the secure browser flow; authenticated Production verification and a real Catalog save remain required after candidate CI/deployment. Keep the release gate OPEN until those checks have evidence.
- Mobile runtime investigation additionally reproduced unhandled refresh-timeout rejections from queued background hydration and a redundant post-restore router invocation after identity restore failed. Queued work now logs failures, failed restore keeps the existing Catalog shell, and unresolved shop identity presents a retry control with editing/save locked. Catalog JS cache version advanced with that change. Whole-document mobile WebKit covers optional SDKs hanging, absent POS, normal/expired sessions including missing AbortSignal.timeout, timed-out refresh, delayed Catalog, save/reload, reopen and rejected renderer recovery.


## 2026-10-16 QueueGo Map Lab — Buriram web-first prototype
- Owner requested the map/navigation data system be prototyped on Web before Android. Backup \`backup/web-before-queuego-map-lab-20261016\`; candidate \`work-web-queuego-map-lab-buriram-20261016\`, based on main \`a44bdb15c80d7216b82b1a6787775001dec1e70b\`.
- Added isolated \`/map-lab/\`: MapLibre map, Buriram working extent, OSM/Overpass traffic-signal + stop/give-way + railway-crossing + traffic-calming + speed-camera inventory, optional schools/hospitals, best-effort DRR FeatureServer road overlay, point-to-point routing prototype, route-near-signal/hazard audit and provenance-preserving GeoJSON export.
- Safety invariant: never fabricate a route when routing fails; never display a red/green countdown unless an authoritative time-synchronised controller/SPaT feed exists. OSM signal timing defaults to unknown. Public OSRM/Overpass/Nominatim endpoints are prototype dependencies only and must not become sole Production services.
- No Google Maps geometry/content is ingested or traced. OSM attribution/ODbL is visible. Each future merged road segment must retain source provenance and licensing metadata.
- Production target remains self-controlled QueueGo vector/offline tiles + versioned regional packages + routing graph + map matching + navigation safety gates. Evaluate OSM PBF with official DRR/DOH/MOT enrichment, MapLibre + PMTiles, and Valhalla/GraphHopper. Physical motorcycle validation is mandatory before sole-navigation release.
- This prototype makes no Supabase mutation and does not alter Customer/Merchant/Rider order flow, payments or Android. Web publication/release evidence remains required before calling it deployed.
- Successor expands the web prototype with the published NGIS national-highway ArcGIS layer for the Buriram envelope alongside DRR. NGIS/DRR stay isolated provenance-bearing verification overlays; endpoint/CORS failure does not fabricate roads or break the base map. Loaded NGIS features are included in provenance-preserving GeoJSON export.

## 2026-10-16 QueueGo Map Lab — Buriram web-first navigation research

- Owner requested QueueGo-owned map/navigation work on Web first before Android, using as much legitimate Thailand road/map data as practical and prioritizing navigation safety.
- PR #369 merged Map Lab v1 into main: isolated `/map-lab/` MapLibre surface; OSM/Overpass traffic signals, stop/give-way, level crossings, traffic calming and speed cameras; optional schools/hospitals; DRR rural-road + public bridge layers; NGIS national-highway overlay; A→B research routing; route-near signal/hazard audit; provenance-preserving GeoJSON export.
- PR #370 merged Map Lab v2 into main: optional OSM one-way/access/motor-vehicle/motorcycle restrictions plus `type=restriction` relation counts and route-near rule audit. Exact-head RC passed full npm regression, mobile WebKit Merchant boot and Grocery rendered checks before merge.
- Current main after v2: `070f0acc7fc292bf12ed6b7403baa3fb1c0c30fc` before this memory-only checkpoint.
- Safety rule: NEVER show a traffic-light countdown or claim future red/green timing unless QueueGo has an authoritative synchronized controller/SPaT feed for that intersection. Observed waiting time may inform ETA statistics only.
- Do not ingest/trace Google Maps geometry or proprietary navigation content into QueueGo map data.
- Public OSRM/Nominatim/Overpass and browser GIS services are research/prototype dependencies only; production QueueGo Navigation must move to pinned source snapshots, provenance/confidence per segment, validated topology/turn/access restrictions, self-controlled routing graph, map matching and versioned offline packages.
- OSM remains the routing-capable base candidate. DRR/NGIS/other official Thai datasets are verification/enrichment layers subject to per-dataset licence and redistribution review. Public road-accident/risk datasets are candidate safety enrichment; do not treat historical accident data as a real-time closure.
- Android navigation remains untouched by Map Lab work. Next phase should validate the web data layers on real routes in Buriram, then build the canonical QueueGo road ingestion/graph pipeline before porting the navigation experience to Rider Native.


## 2026-10-16 QueueGo Map Lab — Overture transportation
- Owner asked to search for dense open/global road datasets that include small roads/alleys and try them in QueueGo.
- Research identified Overture Maps transportation as the strongest additional global open road network: primarily OSM, enhanced with TomTom and local/regional authoritative sources; current release pinned to 2026-09-23.1 and transportation is ODbL.
- Candidate `work-web-map-lab-overture-transport-20261016` adds the official Overture transportation PMTiles archive as a subtle street-zoom road overlay without adding another front-page layer control. It is comparison/coverage intelligence, not a claim of complete alley coverage.
- Production plan remains: frequent Thailand OSM PBF (Geofabrik) + Overture transportation + NGIS/DRR + QueueGo verified field observations, preserving provenance and licence boundaries.

## 2026-10-18 owner website camera/single-proof deployment
- Owner provided road-video camera reversals/anchor drift and reported duplicate pickup/delivery photos from old ChatGPT/web files. Use bounded tested UI delta from newest source successor73c458c5, deployed onto current live main ded5dab4; do not merge staged backend or fake-map endpoint just to update Rider Web.
- Camera: optional null heading no longer north; same latest direction/overlap/progress guards, repeated off-route reroute, stale/duplicate GPS rejection and marker/camera duration. Proof: one photo+one action per pickup/delivery, inline cash, no second collection/slider page, retain preview/File/path through cancel/back/reopen/RPC retry, duplicate-tap/session fence and logout cleanup. Native existing one-photo/no-slider implementation unchanged.
- Focused tests camera65/proofDOM34/GPS9/live-route94/UI102/runtime83/source88 PASS; require full clean-tree npm, RC and Pages + public byte verification. Build rider-20261018-camera-proof1, script20261018-12. Existing route transport remains until QueueGo Map HTTPS gate; cap2/backend/providers unchanged. Physical Safari camera/GPS acceptance still requires actual owner test.

## 2026-10-18 public Rider repair VERIFIED
- Owner-authorized Web runtime f8986c1b8c9f9d86cf43c20fc1eded18b81f37e6/tree67b1058c075bfaccfda44ef1c3fab8f00f6a08fa: local full npm EXIT0, RC1869/39129222944, Pages1432/39129222693 and Chat134/39129222931 SUCCESS. Live HTML tag rider-20261018-camera-proof1 and script20261018-12 verified; shared JS exact bytes SHA256cef3140b63cbe3f8b332dfa2c49c79b7146d65f9c861366630e17d66628641d1.
- Pickup/delivery proof, GPS and puck function bodies exactly match strict staged PR436 d32fe66d. Main and Native source history diverged before this task; preserve bounded Web-only projection, not a full unrelated main merge over the newer staged services. No runtime backend/cap activation. Source Native3413/Map155 exact checks remain separate from public Web deploy.
- User may now physically test the versioned Rider URL. One pickup photo+one receive action; one delivery photo+one finish action; cancel/back/reopen/RPC retry retains proof and uploaded path. Physical camera/GPS acceptance is still open. Resume real evidence, do not bring back retired cash/collection slider screens or old files.

## 2026-10-18 route-progress display while retaining stable geometry
- The camera/proof repair keeps the road route instead of recalculating every10 seconds; preserve live distance/ETA presentation by reading accepted route-match progress against existing authoritative distance/duration. Time is labelled approximate; invalid/opposite/off-route evidence does not invent progress. Helper14/GPS9/proof34/camera65 PASS; full gate required. Build rider-20261018-camera-proof2; no backend/cap/pricing change or duplicate route engine.

## 2026-10-18 bold route from real20:19 recording
- Continue newest sourcec41453e9/livebad7dcba. Owner asks large visible navigation route. Web6px/no casing becomes12px red with16px white; Native active8 becomes12, future6 becomes8, casing4 wider. Same route geometry/camera/state/proof retained; current one-photo fix stays. Build rider-20261018-route-bold1; source/physical CI gates apply.

## 2026-10-18 real route disappearance and aligned arrows
- Owner20:43 recording plus actual MapLibre reproduction proves casing loading can suppress red fill via past load event. Existing helper now applies immediately and owns style.load retry/removal. Direction chevrons, displayed remaining-only route and same-point camera/puck road snap (<=12m, known accuracy<=35m, no opposite heading) preserve raw GPS/full route authority. Native reuses its hybrid signal pipeline; renderer alignment/chevrons only. Build route-arrow1; parente8d3716910d08a804c978bc738ab9ec021f0aa70; full exact CI and physical acceptance required.

## 2026-10-18 camera follows shared Rider course
- Owner asks camera behind/rotating with Rider arrow. Cache Web smoothed course once per fix for camera and puck; interpolate puck yaw with same easing. Native FOLLOW uses existing filtered course via existing camera policy option; keep unknown-heading, gesture, lifecycle and all route safety gates. Build route-arrow2; exact full CI and physical acceptance required.

## 2026-10-18 exhausted route lifecycle
- Endpoint replay exposes leftover red/casing when remaining geometry becomes empty. Reconcile renderer-owned IDs, remove exhausted/past multipart overlays and preserve later authoritative parts. Full road geometry/heading/financial authority unchanged. Build route-arrow3; require exact CI and physical acceptance.

## 2026-10-19 Web-first real places
- Pinned OSM3079 places/1284 business-service records via PR437; same shared asset/search/display with explicit source attribution and coordinate selection. Imported records are not merchants/orderable. Preserve current live routing/camera/proof and project only bounded UI/data. Full/exact gates required; native same-asset integration follows.


## 2026-10-19 Market Catalog continuation from PR441
- Verified latest successor `68b9b0b9` descends from current main `fa0b7ac9`; PR440 explicitly superseded. 128 sourced products, 84 approved images, 44 rights-pending candidates; Phase2 data exists. Do not recreate catalog or auto-approve retailer images.
- `6cfb031b` reuses the same image/credit/status rendering in list and add form, with explicit broken-image fallback; the Market gates now participate in npm test. Focused tests pass, full regression/exact CI and authenticated Merchant browser acceptance remain gates. Shared manual-editor kg presets and remaining rights resolution are next; no Production migration applied.

- Successor batch17 `c9c7c040` increases approved images to 88/128, 40 remain rights-pending. Published image-flow successor `840771cd` preserves it; full npm pre-batch17 passed, focused tests after integration passed. Local weight source `53097a3e` shares manual/catalog presets and custom metric synchronization; exact successor CI and authenticated browser acceptance remain required.


## 2026-10-19 — Actual SQL Market release gate
- IN PROGRESS; branch `work-market-master-catalog-successor-v2-20261019`; predecessor `83b7bac11b3478ad19d496acf774fc2d5c206f10` passed exact RC2008/run39233210924, including mobile WebKit. Local source commit `3336a6d1`.
- Actual migration execution found malformed rambutan slug in the staged phase2 seed. Production migration history confirms only Grocery grant `20261019051041` applied today, not these Market migrations. Corrected the unapplied seed to the canonical JSON/image slug; no applied history edited.
- Actual live read-only function audit confirms POS helpers exclude pending shops. New scoped owner-preparation helper reused by existing Market catalog page/add/manual-save functions allows only owned pending/active nonarchived Market/Grocery profiles; POS, approval, public shop visibility, checkout and financial triggers are unchanged. Owner-only stock/movement read policies enable pending-shop readback.
- TEST: full npm regression EXIT0; actual isolated SQL migrations + pending browse/add/readback/receipt replay/body mismatch/no duplicate/stock movement once/staff+archived+suspended+anon denial PASS19; Merchant DOM PASS42; source/data/image/category contracts PASS. SQL gate now included in npm test/RC. No Production writes.
- Counts unchanged: 128 sourced products, 88 approved images, 40 rights-pending. Browser binary download in this host returned truncated archives; use CI WebKit and do not claim signed-in live Merchant acceptance from isolated tests.
- NEXT: exact successor CI then release-gate review, remaining40 image rights and authenticated Merchant acceptance. No Production apply until exact successor passes.


## 2026-10-19 — Owner-authorized test shop and real backend acceptance
- Owner explicitly authorized creating a test shop. Created a dedicated test account through normal Auth signup and owner-scoped registration; marked test metadata, shop pending/draft, product Delivery availability false. No real customer account impersonation, approval bypass or public fake storefront.
- Pre-release function/policy backup committed on `backup/market-catalog-pre-release-20261019-e273cc59` at `aa57572411bfb5a9e12f65c4f068150dab035d64`. Exact HEAD e273cc59 passed RC2010/run39234648240. All 25 staged Market migrations passed a real-database transaction rehearsal and rollback before atomic activation. Original source migration receipts recorded with their versions to prevent CLI replay. Production readback:128 master rows,88 approved,40 rights-pending.
- Real authenticated test-shop REST PASS: paging128/no duplicate source IDs, category/search, approved+pending image fields, custom1.75kg, original receipt replay, new request/same master no duplicate, stock unchanged on replay. Actual GP trigger persisted storefront100 and Delivery110. Product availability false; shop remains pending/draft.
- Live frontend module with actual authenticated REST exposed a real optional-GP403 bug: Promise.all blocked the catalog even though browse/add were authorized. Shared nonblocking GP read now preserves unknown as null and shows server-calculated-after-save copy; no zero GP invented and no financial RPC grants changed. Manual stock and catalog reuse it. Versioned assets `20261019-market-gp5`.
- Local source/test commit25f8db44: full npm regression EXIT0; Merchant DOM45 and SQL19 PASS. Actual frontend module + real authenticated REST PASS:50 rows,1/2/3kg,custom2.25kg,double-submit1 write,readback correct. This is DOM-harness frontend acceptance, not a claim of physical-device or full signed-in-browser appearance certification.
- Opt-in live QA harness: `ops/market/live-catalog-qa.cjs`; requires explicit env opt-in, private session file and owned pending/draft shop with is_test metadata. Credentials/tokens never committed. Advisors reviewed: private receipts intentionally deny all direct access; authenticated definer warnings are intentional guarded RPCs, with owner/anon/staff/suspended checks tested.
- Image HTTP audit:79 unique URLs for88 approved rows;24 HTTP200 image responses,55 rate-limited429 during batch HEAD audit; no404 observed. Stop bulk retrying; keep explicit image failure UI and rights-pending labels. Remaining40 need exact reusable images/rights evidence, not a coding permission.
- NEXT: exact GP-fix CI, then merge/deploy Web and verify public gp5 bytes; keep test shop unpublished. Physical review and remaining image rights remain separate acceptance limits.


## 2026-10-19 — Market Catalog Web activation VERIFIED
- Branch: main; deployed runtime commit `532bb70d3c971effe0a07ab50175b6fbeadc8387`, merged PR441 from exact source `42ae26fd9390c8e54a01ff152d49bee53b8bf36f`. Source/test DONE and Web activation DONE for the stated data/add flow.
- Exact source RC2011/run39236477702 SUCCESS; merged-main RC2012/run39236885146 SUCCESS; Pages build/deploy39236883990 SUCCESS. Public Merchant HTML and actual browser reload expose `market-gp5` for CSS/Market/catalog scripts. Public JS exact-byte match with tested source:market.js SHA256986a44763cbf83b194eec4448ffee41bbd45f36c86903c92dd7107df80a8037b; market-catalog.js SHA2569ed01ab70446382aaf2b53dffd8ddf04bc2ba201f37597b49206781551aaa3b4.
- Backend activated25 tested source migrations; source-version receipts recorded atomically. Production readback128 total/88 approved/40 rights-pending. Backup aa57572411bfb5a9e12f65c4f068150dab035d64 retains pre-release functions/policies. POS/checkout/approval/public storefront conditions preserved.
- Owner-authorized test shop remains pending/draft and marked is_test. Real authenticated REST+frontend module acceptance PASS:search/category/paging,image states,presets,custom1.75/2.25kg,GP100→110,duplicate receipt/new request/double-submit1 write,authoritative stock readback. Actual manual stock screen reads all3 fixture products. Anonymous REST returns zero test products. No fake products exposed to customers; no actual orders/payment generated.
- This supersedes the previous login-blocked release checkpoint: authenticated backend and module flow is verified using the authorized test shop. Full signed-in-browser appearance/physical-device acceptance remains explicitly unclaimed; anonymous browser verifies deployed asset tags only.
- Remaining image work:40 retailer candidates still need exact reusable replacements or rights evidence; keep pending placeholders.79 unique approved-image URLs audited:24 HTTP200,55 HTTP429 batch-rate-limited,zero404; do not falsely mark these as deleted or auto-approve pending retailer art.
- NEXT: continue only remaining exact image/rights work from newest main descendant; keep test shop unpublished. Do not recreate catalog, migrations, presets, optional-GP fix or add receipts, and do not restart PR440/closed441.

## 2026-10-19 — Market image continuation after PR443
- Source main7959ede0 records live128 items/91 approved images/37 rights pending. Main CI and Pages both successful. Catalog, receipts, editable1/2/3kg, pending-owner and optional GP flow already implemented; continue image rights only.
- Batch20 stages two reviewed species/commodity images (banana prawn, white sand fish) with existing rights fields, taking staged counts93/35. Never infer retailer size/brand/pack from a generic photo. Rejected mixed empty-shell art for edible clam. NEXT: exact-source tests/CI, forward-only migration and live readback; do not claim signed-in physical-device appearance certification.

## 2026-10-19 — Market batch20 activated
- PR444 source20829ca2 passed RC2020/run39247512452 full regression/mobile WebKit; merged main f630086a. Forward-only batch20 image migration activated with original version receipt after rollback rehearsal. Live catalog128 total /93 approved /35 rights pending. Pending QA owner SQL RPC readback includes new fish/prawn image/source/license; no product, financial or stock changes.
- NEXT:remaining35 rights/exact-image replacements; preserve explicit placeholders. Research leads Pure_squid_meat.jpg and Oysters_at_LFS_Fish_Market_2013_(2).JPG need visual/cut review before approval. Empty mixed-shell art rejected. Supabase CLI telemetry was denied by automatic approval review; no retry/bypass. Use established connector for verified migration activation. Full signed-in browser/physical-device appearance remains unclaimed.

## 2026-10-20 Admin audit continuation
- Owner authorizes full Admin Web audit, performance and operational coverage with server-side role checks, audited financial idempotency, Web-first merge/Pages after green CI. PR445 is merged and must not be recreated. Base mainb8b9f988; backup/admin-before-overhaul-20261020.
- First scoped-loading checkpoint removes Admin entry into legacy full hydration, adds HEAD summary counts and section cache/coalescing, Users directory scope, Audit100, Notifications100 and stops hidden Rider5s polling. All APIs retain authenticated RLS/RPC enforcement. No Production schema/data writes.
- Full npm suite passed once; final exact-source/CI still pending. Executed new section runtime19 checks passed. Local WebKit binary is downloaded but shared libs unavailable; normal dependency install fails setgroups/seteuid. Existing hosted RC WebKit runner now executes whole Admin before/after fixture metrics. Do not claim physical/live authorization or Production timing from fixtures.
- Scope remains incomplete: permission matrix, existing staged controls/dependency integration, paged directories, subscription scopes, mutation/audit hardening, exact CI, merge/Pages/live verification. Continue on current Web successor; staged latest native source38baf74c is inspected without overwriting newer Web main.

## 2026-10-20 Admin performance slice merged
- PR457 source42fcd82449a7d02812059cc0f1ce39d8df756a01 passed RC2076/run39348758285: full npm, actual complete Admin mobile WebKit, Merchant mobile WebKit, map/catalog browser gates. Merge main827b34cd69ab25a93d4692b21a4ce816779edea7 has identical tree04584c3bff1fa89065d59e0d8e75fe99ccf75900.
- Controlled50ms localhost WebKit metrics: Supabase boot requests43→10; ready3392→828ms; shell478→329ms. Do not present these as Production latency/physical-device evidence.
- Pages1459/run39349127341 was in progress at checkpoint. Actual public predeploy Admin HTML matched baselineb8b9f988 SHA2569efbc5f5… exactly; live browser shows normal login and has no Admin session. No real authenticated operation/physical iPhone certification is claimed.
- Continue from newest main descendant, not PR445 or closed457. Permission coverage matrix remains honest about staged Credit/Admin Ride RPCs absent in Production and remaining operational APIs/health/pricing/control work. No Production schema/data changed. Do not call the whole mission DONE.

## 2026-10-20 Admin scoped operational readers checkpoint
- CURRENT BASE:9017567baff0b9fb325245578e3dbef5e7a0efea. PR458 sourceae0b438355b5684a466e742f4df575bfd2def25c passed RC39349869823; main CI39350311495 and Pages39350310925 SUCCESS. Full Admin mobile WebKit74 checks PASS. Latest controlled fixture25→10 requests /1933→762ms ready; prior baseline43 reflects legacy duplicate-boot timing variability, not Production latency.
- IMPLEMENTED: additive page-scoped shop overview overload (max200 IDs, invoker/RLS); bounded Dispatch projection (max100 each); notification outbox status aggregates. Private table grants unchanged. New private readers require active Admin and live session, wrappers authenticated only; no payloads/tokens or business mutations.
- TEST: full npm EXIT0; operational SQL25 and DOM24 PASS; whitespace PASS. SQL verifies anon/non-admin/suspended/session denial and limits. Production migration NOT APPLIED; exact-source hosted CI pending.
- REMAINING/BLOCKED: whole mission IN PROGRESS per audit matrix; Credit/Ride backend rollout, audited missing controls, further paging/abort work, authenticated and physical-device acceptance remain. NEXT: publish checkpoint, exact-source CI, rollback rehearsal before additive migration deployment, merge/Pages and byte verification.

## 2026-10-20 Admin readers deployed / Support successor
- DONE (PR459 slice): sourceccd9f421 passed RC2084/run39351062344 including full npm and mobile WebKit74. Maina1fb415b94a7ee4b55753c5f98199603ba8d4456 treefbdf1d5694894f044dabc3e96d2fc615fcc77bb2 equals tested source. Pages39351465709 SUCCESS. Controlled fixture43→10 requests; ready3427→754ms; shell435→254ms, not Production latency.
- Migration applied via connector version20261020010500; source CLI file20261020005115. Three public reader wrappers invoker; anon EXECUTE false; authenticated true with server role checks. Rollback rehearsal passed3 non-Admin denials; no business writes. Advisor warning counts unchanged; five new indexes appear as unused INFO immediately after creation (not evidence to drop them). Existing unrelated advisories remain.
- CURRENT BASE:a1fb415b. Support successor: inbox200/201 and per-shop conversation100/101 keyset pages; profile+thread reads parallel; Map grouping instead of repeated full filters; selecting shop only fetches that thread; actor/route fencing and text escaping. Dedicated visible/online60s fallback because live support table is not in Realtime publication; removed unrelated order-chat scope and duplicate shared Support loader.
- TEST: Support DOM18, section19, performance15, source95/82 PASS. Exact successor full suite/CI still required. REMAINING: financial/staged backend gates and all unresolved matrix controls. NEXT: exact Support CI/merge/Pages, then remaining permission and performance source work. Whole mission IN PROGRESS; authenticated/physical acceptance unclaimed.

## 2026-10-20 Admin stale-read checkpoint (mission IN PROGRESS)
- CURRENT BASE:b4b3f9f6f15c4b4b178d4fcaa57510cecc5882ae. PR460 source4982883c passed full RC39351721764/mobile WebKit74; Pages39352035615 SUCCESS. Support DOM22/full local npm PASS. No Production messages sent.
- IMPLEMENTED: section/session-aware GET/HEAD cancellation, shared directory scope reuse, pending-read coalescing beyond TTL and correct new-scope handling before hash event delivery. Mutations continue to completion; POST RPC reads are not yet cancelled. Role/RLS guards unchanged.
- TEST: section runtime34, Support22, interrupted network26, source95/82 PASS; exact full suite/CI pending. DONE only deployed slices459/460, not the mission. REMAINING: Rider queue paging, global search, financial reconciliation and unresolved feature matrix; scale indexes/aggregates and POST read cancellation. BLOCKED: live authenticated/physical acceptance; staged backend release gates remain separate. NEXT: exact-source CI, merge/Pages/live asset verification, continue remaining source work.

## 2026-10-21 Cash-based Admin finance scope
- Removed the obsolete Admin refund-request panel, handler, request read, and dormant finance-ledger status adapter. Cash settlement, GP, and credit tools remain. Admin has no refund workflow in QueueGo's cash-based scope. Merchant POS cash refunds remain a shop-side function and are separate from Admin.
## 2026-10-20 Customer saved-location coverage checkpoint
- CURRENT BASE:4ae1c89bb5f90f980033384b612988d9cd27791f (PR462 successor). Verified actual Customer source stores one location in users.metadata plus local cache, and immutable per-order delivery snapshots; live schema has no separate address table. Corrected initial inventory's assumed owner address policies.
- IMPLEMENTED: existing guarded Users detail shows saved delivery address, safely escaped; reuses existing read-only location map with validated coordinates. No additional queries, address table, customer edit, order-location rewrite or new privilege. Existing user_view audit retained; clearly distinguishes current account location from the order snapshot.
- TEST: Customer detail DOM11 and source95/82 PASS; full/exact CI pending. PRIOR PR461 passed RC39352282187 (full npm/mobile WebKit74) and merged main791a25340f87f7d47d55ab04569b36e4b2f7f4de; Pages verification pending. Whole mission remains IN PROGRESS per matrix.

## 2026-10-20 Server directory search checkpoint
- CURRENT BASE:283aec9bc977f93e4822e7e3033a96b66192a90c (PR463 successor). Newest main at start02d27a67c87a565fbf75f663032f018ed4f6e581; no reset/revert/cherry-pick. PR461 source3929a38c RC39352282187 PASS and PR462 source4ae1c89b RC39352428793 PASS (full npm/mobile WebKit74 each), merged791a2534/02d27a67 respectively. Pages/live verification follows deployed head.
- IMPLEMENTED: server name/phone search across accounts, max200 rows/201 sentinel, existing page-ID profile/readiness queries retained. Unicode Thai marks preserved, reserved filter/wildcard punctuation removed before building filter. Search resets cursor/history and cancels stale context reads; context-keyed directory coalescing never substitutes an older query. Old replies cannot overwrite current query. Own Admin cache retained for session authority but excluded from result cards when outside returned page.
- TEST: actual directory query16, section34, category22 and source95/82 PASS; exact full suite/CI pending. REMAINING: shop-name-specific global search, Rider queue pagination, search scale indexes/aggregates, unresolved backend/feature matrix and physical/authenticated acceptance. Whole mission IN PROGRESS. NEXT: exact successor CI, merge/Pages and actual deployed asset verification.

- Directory successor follow-up: scoped Realtime invalidates GET cache as well as section TTL; a change during an existing fetch queues exactly one fresh load after completion, avoiding indefinitely cached pre-event data. Unrelated events retain cache. Section orchestration37 and directory16 PASS; exact successor CI pending.


## 2026-10-20 Admin location scoped refresh continuation
- Status: IN PROGRESS; base main `92ef6ac8d56e2daf22840248171a9cb5579856d2`, branch `work-admin-location-scoped-refresh-20261020`.
- Evidence/root cause: admin/locations.js still called qtHydrateDatabase({light:false}) after the authoritative coordinate RPC, reloading unrelated system data despite the existing scoped directory loader.
- Change: governance coordinate saves now refresh only qtHydrateAdminDirectory(); Market retains its own existing loader. Navigation to unrelated routes causes no directory refresh. RPC payload, expected-coordinate conflict check, server role/audit authority remain unchanged. Versioned location asset updated.
- Focused tests: admin-locations 23/0 and shop mutation refresh 14/0. Full npm test EXIT 0 on this source; hosted RC/mobile WebKit required before merge/deploy; physical map acceptance remains unclaimed.
- NEXT: obtain exact-head RC/mobile WebKit, merge/Pages after green, then continue remaining Admin operations payload/coverage review.
- Rider evidence refresh: exact PR439 HEAD 38baf74c78a5881a225df5264a9fd28be485bf9b Native run39197043125 and Map run39197043133 are both SUCCESS. Do not repeat its previously pending CI repair. Authenticated operational/device gates still require separate evidence.


## 2026-10-20 Admin service job history
- Status: IN PROGRESS; successor of coordinate refresh PR487 / tested tree1350cbcfde365d2ec3aa266a6da00434c5fc60c1.
- Evidence: existing Ride/Express/Laundry/Dispatch jobs() used limit100 with no access to older jobs.
- Change: read-only 100/101 keyset pages ordered created_at DESC,id DESC, newer/older navigation, same-cursor retry and request/account/route fencing. Existing tables, service filters, RLS and trace actions remain authoritative; no job mutations or service activation.
- Dispatch 60s fallback does not replace an operator's historical job page. Explicit refresh can return to newest.
- Tests: operational runtime43/0 including 101-row boundary, cursor tie-break, service filters, next/previous, retry and historical fallback suppression; source integrity97 scripts/83 references PASS. Full regression/hosted mobile WebKit required before release.
- NEXT: exact successor RC, merge/Pages after parent green; then continue unresolved Admin matrix controls and service release evidence. Whole mission remains IN PROGRESS.


## 2026-10-20 Continuation evidence and remaining work
- DONE (coordinate source/test/deploy): PR487 source df5a3c58010ac510db41a388967cb282be1b57f9, exact RC39390289152 SUCCESS; merged main31d0842f428ec261b40d986b5db87255ce78c9b5, post-merge RC39390661600 SUCCESS, Pages39390661022 SUCCESS. Actual HTTPS200 location JS SHA256bb4396f6801027fcb9d16f9688198997c0f3e9198a1a539f1b20d3d483a0e9a6 and Admin HTML SHA256c787e6b6fb23e9badb0a4aa1e2dc6cf49a7ef7b2fb5462cb647546ac69b8fa22 match tested bytes.
- DONE (service-history source/test): PR488 source6c3acb9765d81bfade60287caf03b77e17ba9931, tree2f0d526f542f1ac992c4108c727193fbdbc9565c; full local npm EXIT0, operational43/0, exact RC39390622094 SUCCESS. Merge a7c1d2157b157cf84641533829992a0a311b002b has identical tree. Pages39391053622 SUCCESS; post-merge RC39391053747 remains in progress.
- DONE (Rider439 CI recheck only): exact38baf74c78a5881a225df5264a9fd28be485bf9b Native39197043125 and Map39197043133 SUCCESS. BLOCKED (operational acceptance): owner must use approved Rider on actual phone to verify ON/OFF, dispatch, destination save/reopen and background/process restoration; real GPS/road, FCM/voice, signing/AAB/Play remain separate gates.
- BLOCKED (Credit/Admin Ride backend activation): read-only Production catalog on QueueTech confirms no public queuego_admin_credit_accounts, queuego_admin_merchant_credit_targets, queuego_admin_grant_merchant_credit, queuego_admin_public_licenses, queuego_admin_configure_ride or qg_rider_dispatch_preference. No credit/topup-named public/qg_private tables. Public queuego_ride_config exists; its presence alone does not certify Admin or fee activation. NEXT: reconcile reviewed staged predecessor migrations on isolated DB and complete CREDIT-RELEASE-GATE before controlled Production rollout. No DB/payment/flag mutation performed in this continuation.
- IN PROGRESS (Market images): read-only live count rechecked: 128 total,93 approved,35 pending. Remaining exact image/source/rights work must continue from current catalog, not recreate catalog or add-flow. Physical Merchant browser appearance is separately BLOCKED pending actual owner device/session.
- IN PROGRESS (whole Admin mission): remaining matrix controls/performance/authority inventory in docs/ADMIN-OPERATIONS-AUDIT.md still applies. NEXT: verify the service-history deployment receipts, then review the next unbounded operational reader/control against newest main. Do not mark all workstreams DONE.


## 2026-10-20 Admin Rider presence keyset and Catalog correction
- Status: IN PROGRESS; branch work-web-admin-presence-keyset-20261020; base main5ccfc49dee17d292001a151674b3470d2591cfb9.
- Root cause: Rider presence page used OFFSET page*100 despite an ordered unique users.id key. Deep pages needlessly scan past previous rows.
- Change: stable id cursor history for next/previous and same-page refresh; cursor state resets on actor change. Retains 100/101 sentinel and exact-page profile reads, all existing online semantics/role fences.
- Test: presence runtime23/0; source integrity97/83; full npm EXIT0. Exact-head RC/mobile WebKit required before merge/deploy.
- Catalog correction: previous continuation conflated image_status with the Merchant catalog_preview_image field. Live read-only SQL now proves128/128 catalog previews populated; approved reuse images93/pending reuse35. Existing merchant/market-catalog.js renders catalog_preview_image and source tests lock that contract. Do not treat35 reuse reviews as35 missing catalog pictures or recreate image work. Customer storefront reuse approval remains separate.
- NEXT: exact RC -> merge/Pages/readback. Then remove the remaining all-shop applicant lookup in Admin Market by scoping it to visible request owner IDs.


## 2026-10-20 Admin Market scoped applicants
- Status: IN PROGRESS; successor of PR489 source3553f2665acc77ea49aabb067bcc65b987725b3e.
- Root cause: Market operations fetched all shop profiles only to label at most50 visible market requests, then repeatedly scanned them per row. Concurrent same-Admin loads could repaint an older queue.
- Change: lookup only unique valid requester account IDs from visible requests, bounded50; join labels by Map. A request generation fence rejects stale same-account responses. Existing mutations/RLS and queue limits are unchanged.
- Test: Market runtime19/0 with scoped applicant query and old/new queue race; source integrity97/83; full npm EXIT0. Hosted exact RC required before merge/Pages.
- NEXT: exact RC, merge after parent green, verify deployed bytes; continue Native Market catalog parity from newest source38baf74c without merging the Web-only tree over Native files.


## 2026-10-20 Admin Market pending-count HEAD reads
- Status: IN PROGRESS; branch work-web-admin-market-pending-counts-20261020; base mainb1243f3cbe589a613fce3373efd633fe56bfe478 includes PR489/490.
- Root cause: home Market badge fetched every pending request/member ID only to count rows, and could be truncated by API row caps. A focused count test additionally proved HEAD was classified as a mutation in qgAdminRead, disabling read coalescing.
- Change: badge uses the existing tracked HEAD/count=exact reader through qgAdminCountRead. HEAD joins GET/read-only RPC caching/cancellation; POST writes remain non-cancellable. Two concurrent same-owner count requests now send one network read.
- Test: section runtime59/0 proves HEAD/count=exact,1501-row total, coalescing and non-Admin denial; Market19/0; full npm EXIT0; source integrity97/83. Exact RC/mobile WebKit required before merge/Pages.
- Deployed proof for parents: PR489/490 exact RC green; mainb1243f3c Pages39395057654 SUCCESS and RC39395058304 SUCCESS. Actual HTTPS200 Admin HTML SHA25698feedd0222b659107d56c6a4f3442c21233fd798997057a9821013d12018719 and Market JS SHA256f8ff06a5bdd4d1fa0aa0d88e9c4bcdf9b66b93b4822351e949209f961909e24a match tested bytes.
- NEXT: exact RC/merge/Pages, then fix Admin Market Master image priority to use the dedicated official catalog preview like Merchant, preserving separate reuse status.
- Native Market parity is now PR491/a5c0bf8a7986d5510c33e5b93b5a5d0794d83188, strict descendant of green PR439/38baf74c; Native run39395017610 is running. Do not reimplement or merge the Web-only tree over native-android.


## 2026-10-20 Admin Market Master official preview priority
- Status: IN PROGRESS; successor of PR492/afd98c57c2e67ab7fb996d23a2e08c432aa9761a.
- Root cause: Admin Market Master preferred approved generic reuse art over the populated dedicated catalog_preview_image, while Merchant correctly used the clean catalog image. Thus Admin could still display the old commodity pictures despite all128 catalog previews being present.
- Change: Admin now prefers the same dedicated official catalog preview as Merchant; approved reuse art is only a fallback where the catalog preview is absent. Actual image errors show an accessible failure message.
- Test: workspace DOM21/0 proves official preview wins when both image fields exist, unsafe URLs remain rejected and broken-image state is visible; source97/83; full npm EXIT0. Exact hosted RC required before merge/Pages.
- NEXT: exact RC, merge after parent green, inspect actual deployed Admin image-priority bytes. Preserve image_status reuse approval and all source/RLS/data fields.


## 2026-10-20 Admin selected-shop products and stock inspection
- Status: IN PROGRESS; branch work-web-admin-shop-product-stock-read-20261020, successor of PR493/a5d4823b116fde71ba1789b58fa88411bab15673.
- Coverage gap: Admin shop detail exposed only one sample product, with no shop-specific product/stock browser. Existing Production SELECT policies already authorize Admin reads: products_admin_select and market_stock_owner_read; no new grant or RLS mutation is needed.
- Change: existing shop detail opens one read-only product/stock dialog for that selected shop. Uses50/51 ID keyset pages, server name search and same-page refresh; Market stock reads only visible product IDs (<=50). Market decimal stock/units/pack/minimum come from market_products; ordinary product stock remains products.stock. Missing values are shown as unknown.
- Safety: no POST/PATCH/DELETE, price/stock writes or owner impersonation. Text uses textContent, images require HTTPS, and close/search/account generations fence stale results. A session-storage actor change closes the view.
- Test: actual DOM/query runtime25/0 covers limits, selected shop and page stock IDs,4.5kg/2.25 pack, previous/next/refresh/search, unsafe image/text and close/account races; source98 scripts/84 references; full npm EXIT0. Exact hosted RC/mobile WebKit required before merge/Pages.
- NEXT: exact RC/merge/Pages and deployed bytes; continue the remaining Admin audit matrix. This closes the product/stock VIEW inventory gap without adding stock mutation authority.


## 2026-10-20 Admin GP mobile rate cards
- Status: IN PROGRESS; branch work-web-admin-gp-mobile-cards-20261020; newest main1b2240250686de5b32075ddf0d1044570b34a5e2 includes PR494.
- Evidence: supplied iPhone screenshot shows unseparated rate forms. The shipped qg-gp-rate-card defined only margin and inherited no card surface, border or padding. Date and numeric controls inherited automatic height.
- Change: add scoped white bordered cards and explicit44px date/numeric controls, preserving44px touch targets and existing rate save logic. Bump stylesheet URL to refresh cached clients.
- Validation: add whole-document mobile WebKit checks for real GP renderer/card surfaces, control heights and overflow at320/390/430px. Local full regression and exact hosted WebKit gate pending; physical iPhone not claimed.
- Deployment receipt: PR494 main1b224025 Pages39397596995 and RC39397597468 SUCCESS.
- NEXT: complete regression, exact hosted WebKit, merge and verify served CSS. Continue Native491 diagnostic CI separately; do not merge Web-only tree onto Native.


## 2026-10-20 Cross-stream verified checkpoint after GP mobile fix
- DONE (Web source/deployed): PR489 Rider presence keysets, PR490 page-scoped Market applicants, PR492 exact HEAD counts, PR493 official Market preview priority and PR494 selected-shop product/stock VIEW are merged. PR494 main1b224025 Pages39397596995 / RC39397597468 SUCCESS.
- DONE (GP mobile source/deployed): PR496 exactf8c3f8b6d327fed98845d6644088f898b2fb035a passed complete RC39399307537. Real whole-document mobile WebKit checks card surfaces,44px date/numeric controls and no horizontal overflow at320/390/430px. Main817fbe350acf4ba55a615251b43d49e633b77887 Pages39399766747 SUCCESS. Actual HTTPS200 HTML SHA256485ac0c4ac81853c5cd58f8cb05a11d94a410c932ca34b1f6e5768ac0e4a861c; interface.css SHA256f197f9ec9df50d7002dff16aa84ab3016448efada9dde965c8bd6f06f159ad6e match main. PR495 is superseded by496; no old implementation overlay.
- DONE (Native Market source/CI): PR491 exact64a8a2b689181d735a83699b20d0ff9a07231f20 passed Native39396673821 (JVM,3 APKs,16KB, Android launch/map/voice/system-kill recovery). Merged only into Native integrationbb9f41e6ccc99e9acd0e56744db050f4bf53182c, not Web main. Pilot APK artifact12251938112 / launch evidence12252428412. First run's Customer kill failure remains unexplained; diagnostics added without relaxing assertions.
- IN PROGRESS (financial source compatibility): Native PR497/fe3438a2046dcbd2b4f13f2fd5832d270990a129, treef56d790f394f2d8bcc13031311909eb93ad4e38a, restores credit/dispatch rule validation dropped by later Ride migration. Focused actual SQL and full clean local suite PASS; exact Native39399467533 running. Production NOT APPLIED. Preserve this Native successor; do not copy Web-only tree over native-android.
- BLOCKED (release/acceptance): real authenticated Admin/Customer/Merchant/Rider and physical iPhone/Android/GPS/road acceptance; signed AAB/Play; map HTTPS runtime hosting; full deployed migration/function reconciliation and approved payment-provider/credit-webhook/Express rollout. Source screens or emulator tests do not satisfy these. No fake balances/orders or service activation performed.
- NEXT: finish exact PR497 CI and Native integration39398702208/39398702254; merge only the green Native descendant. Continue unresolved Admin audit controls with authoritative policy/RPCs; do not enable unavailable financial/assignment controls. Whole mission remains IN PROGRESS.


## 2026-10-20 Parallel completion checkpoint
- Owner explicitly requested simultaneous completion of all backlog lanes. Independent isolated worktrees handle Native Rider, Credit/Ride/Express, Map runtime, Catalog/Laundry, Admin freeze, and three-app release evidence.
- PR508 merged44c97c04 after exact RC39420653591 SUCCESS; compact proof image/retake grid and camera normalization now preserve the existing server cap. Runtime normalization/upload scenarios added in5a34149c, actual proof/upload65/0; full regression/hosted successor CI pending.
- PR507 merged9bb6e436 preserving508/509; Pages39426694695 SUCCESS and actual HTTPS200 Admin hash23471ca18ac32835c0c2e0999846ae54c582142e22720e4b4ba51cfa194b60b9 matches. Postmerge RC39426694586 still running at checkpoint. Admin scope freezes after green; no new features.
- NEXT: finish exact regression/CI and release receipts without replacing newer descendant work. No physical/runtime/provider/Production/Play acceptance inferred from CI.


## 2026-10-21 Merchant Home cleanup / unified credit successor
- Status: IN PROGRESS (publication); source change removes the latest-orders heading, cards and unused Home slice. Orders remain accessible through existing Orders navigation; no order data is deleted.
- Source: PR525 branch work-web-credit-unified-merchant-rider-20261021, descendant of main60d5fa5a via97ace8fb. Preserves unified Merchant/Rider balance and finance-only Merchant credit access.
- Evidence: merchant-boot9 and credit-web31 PASS; git diff --check PASS. Full local regression and exact-head CI are required before merge/publication.
- Native: newest credit PR526 already has no latest-orders Home section; no duplicate Native implementation required.
- NEXT: finish full regression, exact-head RC/mobile WebKit, merge PR525 and verify successful Pages deployment plus served Merchant HTML. Native credit PR526 retains its separate Android release gate.


## 2026-10-21 Merchant Home publication receipt
- Status: DONE (Web source/test/hosting). Source59c1039f78b4896159d4d7a99a300f7e5d06054a; merge ede4914e8bc9b9aa8e0546d1efd8a98defa146ee.
- Evidence: full local TZ=Asia/Bangkok npm test EXIT0 on exact source tree9a93abdf; exact-head RC39520610148 SUCCESS including Merchant mobile WebKit; Pages39520939348 SUCCESS.
- Live Merchant HTML SHA2567133a3540e69401b0fdad4cf19a1e9897d57de5181063039480362a30d01e0cb equals tested/merged bytes. Latest-orders section and Home credit mount are absent. Existing Orders navigation and revenue/finance credit access remain. Shared Merchant/Rider Web credit shows one usable balance.
- Native credit PR526 HEAD021caf67fc422a3219c07ce2b4fba5ba3423272c: CI39520235790 IN PROGRESS at APK build; completed credit/continuous-dispatch/passenger/route-bundle race and navigation-map-runtime jobs SUCCESS. Skipped jobs are not certified.
- NEXT: verify postmerge RC39520939484 and exact Native39520235790 result; preserve latest release successor. Authenticated physical device, signing/AAB, Play Console and backend/provider activation remain separate gates. Do not repeat Home or credit implementation.


## 2026-10-21 Correct Merchant header credit removal
- Owner clarification: remove only the large Home credit card; retain the compact usable-balance badge beside the notification bell. Previous PR525 removed that badge too broadly.
- Restore existing shared mountBadge in Merchant Home header, with one unified available balance and existing revenue/finance destination. Large credit card and latest-orders list remain removed. No ledger, schema or order change.
- NEXT: run credit/merchant/source checks and full RC, publish and verify actual served header mount.


## 2026-10-21 PromptPay LIVE mission / current checkpoint
- Scope: existing Merchant/Rider credit ledger only. Public Web parent52597021; backend source021caf67 (Native PR526), integrated with public Web asfcad9069. No wallet replacement or fake credit.
- Production: queuego-credit v2 deployed with boolean-only startup readiness telemetry; queuego-credit-webhook v1 deployed with raw-body HMAC and provider retrieve. Readiness observed environment_ready=true, live_key_configured=false, webhook_signing_configured=false, live_intake_enabled=false. Required settings: OMISE_SECRET_KEY (LIVE), OMISE_WEBHOOK_SECRET, QUEUEGO_CREDIT_LIVE_ENABLED. Provider PromptPay LIVE capability/signing configuration remains unverified until those are supplied securely. No flags enabled and no real charge attempted.
- Production Admin Rider grant migration applied: same credit_accounts/credit_entries, active Admin/live-session check, integer satang, durable request UUID, account lock, actor/reason/audit. Rider Admin grants use existing cash balance because the original Rider account forbids promo balances; event source remains distinguishable in audit. No user balance was seeded or modified for testing. Anon/service ACL denied; rollback-only unauthorized call denied.
- Source tests PASS: Admin Rider20, Merchant38, Credit Edge82 (HMAC/raw-body/mode/currency/source/ref/metadata/recovery), Web37 (single balance/server pending recovery/duplicate click). Ledger59 and complete13-migration rollout chain PASS. These are isolated tests, not real bank settlement evidence.
- Web changes: same shared client; recover pending server UUID after storage loss, recheck pending via queuego-credit before authoritative summary refresh, PromptPay labels/amount/QR/status and Admin Rider form. Merchant compact header preserved; large card/latest orders stay removed.
- LIVE payment gate: BLOCKED on provider secrets/settings above. Feature flags remain off. Owner real Merchant/Rider bank payment, persistent post-login balance, and provider signed delivery are not certified.
- NEXT: finish exact Web full RC/mobile WebKit; publish and verify served bytes. Sync shared Native client only after Web source publication; build exact Native descendant. Keep payment LIVE BLOCKED until verified credentials, provider/signature/endpoints and actual successful paid charge evidence.


## 2026-10-21 Credit publication and Admin immediate refresh
- Web PR528 source22253875 merged cee5a58d; full local tests/RC39523018771/Pages39523292975 PASS. Actual shared credit and Admin credit files match tested served bytes.
- Root cause follow-up: qgAdminRpcRead caches account reads for5 seconds while direct mutation RPCs do not invalidate it. Clear the existing read cache after acknowledged grant/reversal, before reloading credit history; no financial mutation/authority changes. Rider durable-grant DOM5 and shared Credit Web37 PASS; full source RC required.
- Native current successor: PR529 HEAD2693396fa09e8432cf525a30c491f527ae53d3a5, strict descendant021caf67. Android sync uses same queuego-credit/ledger. CI39523534966 IN PROGRESS; credit, passenger, continuous-dispatch and stack SQL race jobs already SUCCESS. Do not use unpublished experimental public-Web/Native integrationfcad9069 as a source successor; no changes from that integration were published.
- Production readiness remains environment_ready=true/live_key_configured=false/webhook_signing_configured=false/live_intake_enabled=false; credit flags stay off. BLOCKED: OMISE_SECRET_KEY LIVE, OMISE_WEBHOOK_SECRET, QUEUEGO_CREDIT_LIVE_ENABLED and provider LIVE PromptPay/signing configuration.
- NEXT: publish the immediate audit refresh fix after exact RC; finish Native39523534966 compile/runtime gates and record receipts. Real bank payment and signed provider delivery remain uncertified until secure provider setup.


## 2026-10-21 PromptPay final source/build receipt (LIVE payment still BLOCKED)
- Web runtime: PR528 source22253875 -> mergecee5a58d; PR530 sourceac9c58d3 -> mergeb91d0ea8058d5a6fe2e19eade3b9f9b7619281bf. Full local Web regression EXIT0; exact RC39523018771 and39523725062 SUCCESS; postmerge RC39524163429 SUCCESS. Pages39523292975 and39524163519 SUCCESS; shared credit/Admin served bytes verified identical, including post-grant/reversal cache invalidation.
- Android active successor: PR529 HEAD2693396fa09e8432cf525a30c491f527ae53d3a5/tree69a1fb85891f31330d26c7b68a00eee394ec7cfe. Full clean local pretest/test/posttest EXIT0. Exact Native39523534966 native-pilot SUCCESS: complete regression, JVM, three APKs, 16KB/no-WebView, real emulator launch/map/voice lifecycle. Navigation-map-runtime and four required SQL race jobs SUCCESS. Skipped Express/map-display jobs are not additional certification. Native remains separate from public Web main, with same payment backend/ledger.
- Production: queuego-credit v2 and signed queuego-credit-webhook v1 deployed. Admin Rider grant RPC installed with active-Admin/live-session and immutable ledger audit; anon/service EXECUTE denied. No real user grant or bank payment was executed for evidence.
- Actual readiness remains environment_ready=true, live_key_configured=false, webhook_signing_configured=false, live_intake_enabled=false. Merchant/Rider feature flags both false. No fake key, QR, seed money, client ledger write or payment enablement.
- Browser acceptance: actual Merchant finance URL renders login, with no authenticated Merchant session available. No real-account topup, bank settlement, provider webhook delivery or post-login persistence certified.
- Mission: BLOCKED (payment-live only), NOT DONE. Required settings: OMISE_SECRET_KEY LIVE, OMISE_WEBHOOK_SECRET, QUEUEGO_CREDIT_LIVE_ENABLED and Omise LIVE PromptPay/signing configuration.
- NEXT: securely configure real LIVE provider/signing secrets; verify provider capabilities and dynamic webhook endpoint https://pkypiqhlrmzocysgeqew.supabase.co/functions/v1/queuego-credit-webhook; recheck readiness, then activate existing flags/intake and perform Owner Merchant and Rider paid20+ THB tests, duplicate/retry checks, refresh and logout/login. Do not duplicate or restart completed code.


## 2026-10-21 Verified existing Omise key is TEST (not absent)
- Owner confirmed Omise account already exists. Successful secure GitHub sign-in to Supabase verified the existing OMISE_SECRET_KEY and OMISE_PUBLIC_KEY entries were configured on11 October. Do not ask Owner to open/register Omise again.
- Root cause verified by boolean/classification-only server telemetry at2026-10-21T01:09:23Z: omise_secret_present=true, omise_secret_kind=test, outer_whitespace=false, wrapped_in_quotes=false. No secret values read, printed or copied. Earlier wording that the Omise key was absent was incorrect; the installed key is TEST.
- queuego-credit v3 logs only classification/presence flags, with unchanged authentication/payment gates. Intake and both credit feature flags remain off. No live charge or fake QR created.
- Secure Omise sign-in was attempted once; provider returned 'Request could not be completed due to an internal error'. No repeated credential submission or inferred account rejection. Automated sign-in stopped.
- NEXT: Owner completes Omise sign-in through the prepared secure browser/handoff, then configure real LIVE secret and webhook signing secret in existing Supabase project without sending values in chat; verify provider readiness/capability and signed endpoint before enabling intake/flags. All prior Web/Android green receipts remain valid for their exact source scope.


## 2026-10-21 Admin unified workspace continuation
- Owner reopens Admin scope for unified presentation and complete real-system access. Base main1f6bacbfc4b7588f5bf9ee50827483061ea928f7 contains final-freeze and visible-credit successors (verified git ancestry); no older branch overlay. Backup: backup/admin-before-unified-workspace-20261021.
- IN PROGRESS: grouped existing navigation into providers, services and finance/system; all existing destinations remain once in each menu. Shared light sidebar, readable type scale, standard page titles and44px controls reuse the existing shell and route authorities.
- Existing Laundry/Express pages read queuego_feature_enabled with p_feature only (server default time), show enabled/disabled/unknown separately and preserve paged history even when availability cannot be checked. No feature flags or balances changed.
- Production READ-ONLY: Laundry enabled=true, one active hub. Function signature includes p_at DEFAULT now(); previous main fixes avoid explicit null time. Six inspected Admin dispatch/count/notification/credit RPCs deny anon EXECUTE and allow authenticated (role checks remain server-side). This is not authenticated acceptance evidence.
- Test-harness repair: merchant-routing still expected removed revenue explanatory copy; current main uses automatic GP-credit copy. Updated the assertion to current text; no Merchant runtime changed.
- NEXT: full regression, hosted WebKit at320/390/768/1440, exact-head CI, merge then Pages byte verification. Local WebKit archive download truncated/503; hosted CI required. Authenticated real-account read/save/reopen and physical acceptance remain BLOCKED without an authorized Admin browser session. Payment provider, Ride/Express activation and physical release gates remain separate; do not claim whole-system done.

## 2026-10-21 POS future-readiness continuation
- Owner asks to grow QueueGo POS toward the POSPOS feature set for future needs. Keep shop POS separate from Admin and keep shop-side cash refunds in POS; do not add an Admin/provider refund flow or merge POS into Delivery GP.
- Existing POS covers dine-in/takeaway, QR tables, kitchen, bill/payment/history, staff roles, Delivery kitchen and printer paths. Grocery already decrements tracked stock through its existing order-item reservation trigger; do not create a duplicate grocery inventory ledger.
- PR #556 added cashier shifts: opening float, per-cashier active shift, payment-to-shift link, cash reconciliation, variance/notes, scoped recent history, and shop-cash refund outflow assigned to the shift where cash is returned. Payment/refund RPCs are authenticated and owner/role checked. It is merged at `ebdd250f60fa3d6be9bd28d294d5ecaca4f12a99`; hosted QueueGo RC run 39602602674 passed for the exact feature head. Production migration was applied and verified in the migration catalog as `20261021125213_pos_cash_register_shifts`.
- Production readback then found broad default table grants on the new tables and three missing FK-leading indexes. Production hotfix migration `20261021130147_pos_cash_shifts_acl_indexes` explicitly revokes direct table writes and anon reads, grants authenticated SELECT subject to RLS, and adds the missing indexes. Post-apply readback confirms both tables retain RLS, anon SELECT is false, authenticated SELECT is true, direct authenticated INSERT is false, and all three indexes exist.
- Web POS has a mobile-friendly `กะเงินสด` page with open/close forms, retryable current/history reads and empty/error states. POS CSS/JS asset versions advanced together. Offline sale, tax/E-tax provider, hardware certification and external bank/QR integrations are not claimed.
- Local PGlite coverage includes authorization, open/close/payment retry, cash refund reconciliation, cashier isolation, forced broad-grant revocation, authenticated read-only table grants and all three indexes. This does not prove authenticated merchant or physical till acceptance.
- Broader parity remains staged work: item-level reports, general stock/purchases and barcode workflows reusing existing product/Market/Grocery sources, loyalty/customer lookup, branches, offline queue, tax docs and hardware integrations. Do not claim the whole POSPOS feature set is implemented.

## 2026-10-21 POS inventory toggle authorization follow-up
- After PR #572 merged to main at `6050a51ab7cb06001fb4163b5ff88c890fb4950d`, reviewed Production `products_shop_update` RLS and found authenticated shop users can PATCH their own product rows. Existing migration guarded tracked stock quantity changes but did not require manager permission to enable/disable the inventory tracking owner flag (nor to insert a product with that flag set).
- Production `public.pos_allowed(text)` is a SECURITY DEFINER permission check: owner or active shop staff with the named capability. Used only read-only function definition; no user/product data read or write.
- Added successor migration `20261021233200_pos_inventory_tracking_manager_guard.sql`: the tracking guard now covers INSERT and UPDATE, requires authenticated `pos_allowed('manage_staff')` when tracking starts/is switched, preserves Grocery Master exclusion and open-order lock, and revokes direct trigger-function execution.
- Regression: `tests/pos-generic-inventory.cjs` denies unauthorized direct tracking toggle and tracked-product insertion; focused test passes 40 checks. Full `npm test` EXIT 0; exact-source RC `39638917216` passed all 12 steps; PR #573 merged to main at `ab36bfb9da66d5d4a43a83011e4a50fb08c27213`. Migration has not been applied to Production.
- NEXT: continue actual schema/trigger compatibility review for the five inventory migrations. Real manager/cashier and physical device acceptance remains blocked; do not apply Production migration or claim POS release readiness.

## 2026-10-21 POS Market stock authority isolation
- Read-only Production catalog inspection found `market_item_stock_guard()` decrements `market_products.stock_quantity` and appends `market_stock_movements`; it is the authoritative Market ledger. PR #572 generic reservation skipped only Grocery Master, so a Market-linked `products` row with generic tracking enabled could be double-decremented.
- Added `20261021233300_pos_market_inventory_owner_guard.sql`: migration preflight rejects existing tracked Market overlap; product toggle and Market mapping triggers lock/check `products` so ownership cannot be assigned concurrently; remaps lock both products in stable order and deletes serialize on the old product; generic order-item reservation rechecks Market mapping after row lock and leaves it to the existing Market trigger.
- Test `tests/pos-generic-inventory.cjs` simulates Market stock and a legacy overlap. Production RLS is enabled on `market_products` without authenticated SELECT policies, so `qg_inventory_tracking_guard()` is SECURITY DEFINER with fixed search path to see the mapping while the existing `pos_allowed` check reads caller identity. The regression runs migrations in timestamp order and simulates authenticated RLS. Focused inventory 47 PASS, adjustment UI 9 PASS, full `npm test` EXIT 0, diff check PASS.
- PR #574 source has been strengthened to lock both products in stable order during remap and serialize delete. Focused inventory test 47 PASS; current local full suite is rerunning. Exact-source hosted RC must be rerun for the final head. Previous runs on superseded trees do not count for current release gate. No Production migration/data write.
- NEXT: finish exact-source RC, then recheck latest main, open PRs, reviews and successor before merge. Continue schema/trigger compatibility review. Do not activate POS stock without authorized cashier acceptance.


## 2026-10-21 POS Market inventory post-merge checkpoint
- PR #574 merged to main as `2d0c6b58a45689585dc7653bd18bcf89dfd1e4c1` after exact-source QueueGo RC `39643279380` passed all 12 steps. GitHub Pages build/deploy `39643901620` succeeded on the same merge SHA.
- Browser runtime readback could not be completed because the browser environment disconnected. No Production migration/data write was made. Production migration history ends at `20261021133330`; `products.inventory_stock_tracking` is absent until prerequisite migration 330, so overlap preflight cannot be queried before ordered migration application.
- NEXT: continue schema/ACL/RLS/trigger-order/forward-recovery review. Keep Production activation blocked on authorized cashier/device acceptance. QueueGo/POS remain NOT DONE.


## 2026-10-21 POS numeric order quantity compatibility
- Production read-only schema: `order_items.quantity numeric`; `market_products.stock_quantity numeric`; Market relation is composite FK `(product_id,shop_id) → products(id,shop_id)`. Market and Grocery order-item stock triggers are SECURITY DEFINER.
- Identified regression fixture mismatch: POS inventory test had modeled item quantity as integer. PR #576 changed it to numeric and covers fractional generic rejection plus fractional Market stock movement without generic stock mutation. Exact RC `39647073978` passed; merged as `a4f12dd39b751e7e05a2d37b54e47863d477f391`; post-merge RC `39647725266` passed. No Production mutation.


## 2026-10-21 POS numeric quantity compatibility — merged
- Production read-only schema confirmed numeric `order_items.quantity` and `market_products.stock_quantity`, composite Market product/shop FK, and SECURITY DEFINER Market/Grocery stock triggers.
- PR #576 updates the inventory PGlite fixture to numeric; fractional generic stock quantities reject, while Market fractional stock changes only Market's ledger. Exact RC `39647073978` passed all 12 steps; merged to main as `a4f12dd39b751e7e05a2d37b54e47863d477f391`; post-merge main RC `39647725266` passed; Pages deploy `39647725023` succeeded.
- No Production DDL/data mutation. Production migration history ends at `20261021133330` and `products.inventory_stock_tracking` is absent. NEXT: continue ordered schema/trigger/ACL/recovery review; keep real cashier/device acceptance and controlled migration gates open. QueueGo/POS remain NOT DONE.


## 2026-10-21 POS migration compatibility preflight
- Read-only Production migration catalog ends at `20261021133330`; POS cash movement migration `20261021211709` and inventory chain `20261021211800`, `20261021223000`, `20261021233000`, `20261021233100`, `20261021233200`, `20261021233300` are absent. No DDL or data mutation.
- Read-only table/trigger catalog confirms current Grocery and Market order-item stock owners remain installed and new generic reservation/adjustment triggers are absent. Market products RLS is enabled; authenticated SELECT grant does not yield rows without a policy, so the fixed-search-path SECURITY DEFINER owner guard is necessary for internal mapping checks. Market overlap preflight can run only after 330 introduces `products.inventory_stock_tracking`.
- PR #577 merged `e70961d5b70fa7f84519fbebf473659603e35132`; exact PR RC `39648478732` passed. Main RC `39649126036` failed once on Admin WebKit title-size assertion line 69, then the exact failed job rerun passed all steps; Pages deployment `39649125931` succeeded. Record the intermittent failure for future browser-gate hardening.
- NEXT: continue controlled read-only review; do not apply migrations until authorized cashier/device acceptance. After approval, apply in timestamp order, run the overlap preflight, read back ACL/RLS/triggers and run authenticated sale/cancel/refund/adjustment flow. QueueGo/POS NOT DONE.


## Post-merge receipt — 2026-10-22
- PR #578 merged at `cc036ee001bf23425d055977f8330e3753749743`. Exact PR RC `39650817495` passed all 12 required stages. The PR stabilizes the existing Admin WebKit route assertion by waiting for the rendered heading, with no runtime UI change.
- Main push RC `39694314231` passed all stages on the merge SHA, including npm test, Admin WebKit boot/layout, Merchant mobile WebKit, Map Lab gestures, QueueGo Map search and Grocery mobile/tablet rendering. Pages deployment `39694313643` succeeded on the same SHA. Main ref verified at `cc036ee001bf23425d055977f8330e3753749743`.
- PR #578's change is test stabilization and documentation; Pages success is recorded as deployment evidence, not a claim of end-user UI behavior change. The prior main RC failure on #577 remains recorded as a separate earlier attempt.
- Production database remains unmodified. POS feature migrations remain unapplied; continue authorized cashier/device acceptance and controlled migration gates. QueueGo/POS are **NOT DONE**.


## POS ambiguous bill session-scope guard — 2026-10-22
- Read current main `6acd48ef3c130cc1c6b7f4c0958afcc228920414`, no newer POS successor was open. PR #580 guards the ambiguous bill retry with its captured shop and cashier identity; identity mismatch drops the tab-scoped pending request before any order RPC. Regression covers session change after the request is loaded.
- PR #580 exact RC `39695677982` passed all 12 required stages and merged as `fbdf63707a5eba1b9bf703891330c7b132e6bb54`. Post-merge main RC `39696051611` passed all stages. Pages deployment `39696050699` succeeded on the merge SHA.
- This recovers an ambiguous online create request safely; it is not an offline sale queue. POS offline sale/outbox/conflict/reconciliation remains unimplemented and unaudited. Production database remains unmodified; cash/inventory migrations and authorized cashier/device acceptance gates remain open. QueueGo/POS are **NOT DONE**.


## Latest main successor receipt — 2026-10-22
- PR #581 merged at `c740e9321ec5e3d1fb2d6acafc382180e7166121` after its exact-head QueueGo RC passed. Its immediate push RC `39696877230` was cancelled when main advanced; the descendant main `a71131aa0027b0616260ce6881ef708e99e9e214` added Admin exclusions for POS DINE_IN/TAKEAWAY orders in Admin order reads, count KPI and GP totals.
- Successor main RC `39697139447` passed all required stages, and Pages deployment `39697139101` succeeded on the current main SHA. The earlier cancellation is preserved as evidence; no failure was hidden.
- Current main therefore includes #580's cashier/shop-scoped ambiguous bill retry guard and the Admin POS-order reporting exclusion. Production remains unmodified. POS feature migrations remain unapplied, physical cashier/device acceptance is not claimed, and a durable offline-sale queue remains unimplemented. QueueGo/POS **NOT DONE**.


## POS offline recovery discovery — 2026-10-22
- Current source blocks offline sale writes. Ambiguous online bill creation and cash movement use tab-scoped idempotent retries; #580 added actor/shop protection for bill replay. These are not durable offline sale queues.
- Reviewed current official Square and Shopify POS offline documentation. Both distinguish pending offline work from server-accepted state and expose permission, risk, limits, recovery, or unsupported-operation boundaries. Research and the recommended QueueGo Phase 1 are in `docs/POS-OFFLINE-RECOVERY.md`.
- Recommended next implementation: durable, identity-scoped offline cart drafts that require server price/stock/permission validation and cashier review before online submission. Do not mark as paid, send to kitchen, or reserve stock while disconnected. Actual offline completed sales require owner decisions about payment, multi-terminal inventory conflict, limits, shift/accounting, receipts and customer recovery.
- No Production migration or data change. QueueGo/POS remain **NOT DONE**.


## 2026-10-22 verified continuation checkpoint: Admin cleanup and POS offline recovery
- Current main is `aeedb1df2ef4654ee68ed26b25b4f34a01e441de`.
- PR #585 documents POS offline recovery. Exact RC `39698519445`, main RC `39698920070`, and Pages deploy `39698919956` passed; merge `d67f8dd8f79bcc991c447376cdd838988c816884`.
- PR #584 removes the retired Admin GP/slip workflow and its loader while preserving GP deduction business logic. Exact RC `39699366405` passed on `9e9202bf3b688a816eef41cd3fe2eacd0e852562`; merge `aeedb1df2ef4654ee68ed26b25b4f34a01e441de`; descendant main RC `39699765279` and Pages deploy `39699764713` passed.
- The #584 Admin WebKit failure was a test fixture defect: legacy HTML was served with current section-loader/runtime assets. The harness now serves each baseline path from one commit; exact PR and main browser checks pass.
- Next: implement durable, shop/actor/device-scoped offline cart drafts. Keep the UI state explicitly Not submitted; after reconnect require current identity, permissions, shift, product/options, price, availability and stock validation plus cashier conflict review. No payment, kitchen, stock reservation, final receipt, or sale report while offline.
- Production migrations/data and real cashier/device acceptance remain gated. QueueGo/POS are NOT DONE.


## 2026-10-22 POS offline draft implementation checkpoint
- Current exact main remains `44094f6500f9a5075e3c781992e66aa3263e1f5c`; main RC `39700482606` and Pages deploy `39700482101` passed.
- Source inspection found `pos_edit_bill` appends lines without idempotency. Safe multi-line offline draft submission needs one atomic server RPC with a stable draft payload/request ID and authoritative price/option validation; the existing options RPC is idempotent per line but does not make multiple calls atomic.
- The required Supabase skill package was unavailable through `skills.read` in this runtime. No Postgres migration/RPC was written or applied. Recheck and read the skill before editing database source. Continue safe UI/store work only if it does not claim successful submission.
- Offline draft design and owner business-policy gates remain in `docs/POS-OFFLINE-RECOVERY.md`. QueueGo/POS remain NOT DONE.


## 2026-10-22 POS Kitchen shop-category gate

- Web POS reads `shop_profiles.public_category` (metadata fallback). The Kitchen screen and kitchen note/actions are available only to `food` and `cafe`; Grocery, Market and other categories show bills for direct checkout instead. Unknown/offline category state fails closed and hides Kitchen.
- Android Merchant uses the same category rule. Both clients keep the existing order, payment, cashier permission and cash-shift flow.
- Additive source migration guards kitchen transitions by shop category and permits payment without READY/SERVED only for non-kitchen categories, with existing status validation, shift association, payment ledger and function ACLs retained. PGlite behavior covers retail direct checkout and restaurant kitchen gating.
- This is source/CI work only. Do not apply the migration to Production until the POS release gate, authorized cashier acceptance and real device flow pass; Production schema/data remain unchanged.

## 2026-10-22 POS / Delivery separation and cancellation correction
- Owner reiterates POS storefront and Delivery are separate. Remove Delivery queries/actions/Kitchen tickets, setup tab, GP/Delivery report cards and history filter from POS. Retain food/cafe-only POS kitchen and non-food direct payment from existing PR595. Delivery continues in its existing Merchant order screen.
- Read-only Production audit: 5 POS bills, 0 nonzero GP/rate, 0 credit-GP-required bills, 0 GP credit holds. The existing order-shape CHECK enforces POS delivery_fee/gp_rate/gp_amount=0 and no rider/customer; no financial data rewrite is needed. Root misleading report: pos_owner_dashboard includes Delivery GP and bill count. Forward migration scopes that API to paid/completed POS DINE_IN/TAKEAWAY only.
- Separate Delivery policy: merchant may cancel after rider accepts only before preparation. UI guards canonical/legacy status aliases and preparation/ready timestamps. Forward migration patches the existing row-locked transition, preserves Market Trip/audit/notification/cash guards/ACLs, accepts legacy assigned only without preparation/ready timestamps, and rejects preparing/ready. No POS cancellation rule is changed.
- Local focused runtime/SQL tests added. No Production migration/deployment or authenticated live cancellation has been performed at this checkpoint; exact CI and controlled deployment remain required.

- Narrow Web hotfix is based on current main68fdb52d; it carries existing Kitchen rules from PR595 and these fixes. Offline draft source stays in PR595 pending its own release gates.

- Delivery cancellation boundary also runs in an INVOKER row trigger for direct shop UPDATE, because the existing cash-order guard permitted preparing/ready cancellation. It excludes POS and Ride; direct trigger execution is revoked from public/anon/authenticated.


## 2026-10-22 POS context hotfix deployment receipt
- PR598 merged as 15e7bd0726458a189df5fc85fc6827541ebccb9b; exact head4311be71 passed both hosted Web CI runs39740434026/39740439972. Pages39742256442 succeeded.
- Served merchant index blob84dfce7ac3f84e6804444f376a43c493015bf299 and POS script bloba326dd1c79e47e85023c14e4bb237c662b2a1446 match source.
- Food/Cafe retain storefront Kitchen; Grocery/Market no Kitchen. POS excludes Delivery and GP reports.
- Production applied kitchen/report/cancellation migrations at versions20261022073639/20261022073650/20261022073806. Repository filenames aligned with actual migration history to prevent duplicate future application.
- Cancellation retains Rider-assigned eligibility only before preparation/ready timestamps. Existing private transition ACL and public cashier/dashboard ACL preserved; direct merchant cancellation guard trigger verified. No business records mutated for verification.
- Android PR596 remains a separate build gate; no new APK acceptance claim. Offline PR595 must incorporate current main before release so older mixed POS/Delivery code does not return.


## 2026-10-22 offline successor reconciliation
- PR595 source reconciled with deployed main42e81f57, including actual Production migration filenames. Retains POS-only queries/history/reports and category-only Kitchen; no Delivery setup/actions or GP restored. Offline cache uses the reduced POS-only snapshot. Both offline and separation runtime checks pass.
- Offline draft remains distinct from a completed offline sale and is pending its own migration/deployment gates. Live Kitchen/separation hotfix is already deployed via PR598.


## 2026-10-22 offline backend deployment
- Exact Web successor0aed635c passed hosted RC39743999528; full local regression passed. Additional isolated pricing/draft tests46 passed using actual Production pos_edit_bill definition, preserving real bill identity/edit-state guards.
- Authorized live rollout: applied pos_menu_option_pricing20261022080039 then pos_offline_draft_submission20261022080048. Repository migration filenames aligned to actual versions; SQL unchanged. Both RPCs require authenticated POS receive_order/shop permission, receipts have RLS and no direct client grants, anonymous function execution denied. Readback: existing POS bills5, both receipt tables0; no fake sale/payment/cancellation created.
- Backend ready; Web publication and authenticated cashier acceptance remain separate receipts. Existing Kitchen/POS/Delivery separation stays live.


## 2026-10-22 latest-main POS regression continuation
- Fetched current repository `chatchairins-source/QueuGo` main at `1e5b54b64c946aacc37b140d71fe7f919b4d6536` (merge #611); reviewed the 100 most recently updated open PRs and POS-related branches. No open POS-specific PR is ahead of this baseline. The checkout was clean on latest main before this test correction.
- Full `npm test` first stopped in `tests/pos-product-codes.cjs`: current HTML linked POS CSS (`20261022-surfaces8`) and printer CSS (`20261022-light2`), while the assertion required the prior `20261022-pos-mobile1` cache token. The JavaScript bundle remains on its existing token and is linked. Updated asset assertions to verify each asset link without pinning a cache token; no runtime, business, or database behavior changed.
- Focused barcode/SKU SQL test passed 31 checks. Full `npm test` then exited 0 on this exact source checkout, including POS SKU scan, modifier pricing, offline draft submission/replay, cashier shifts, kitchen capability, Delivery/GP separation, and inventory guards.
- Read-only QueueTech Production catalog verification found the base POS tables/RPCs, cash shifts, menu option pricing, offline-draft submission, POS report separation, Food/Cafe kitchen guard and 10% Delivery markup migrations are present. The current RPC catalog does not include the source SKU/barcode save/lookup or POS cash-movement RPCs; `products.inventory_stock_tracking` and the generic inventory/adjustment/Market-owner migrations are absent. Production `pos_staff` and `pos_register_shifts` are empty. Do not activate barcode, cash-movement, or inventory flows until their source migrations are compatibility-reviewed and safely rolled out; the old source menu-option migration is superseded by the deployed `20261022080039` version and must not be applied twice.
- This checkpoint made no Production DDL/write, order/payment creation, browser publication, or physical device action. Release Gate remains OPEN for authenticated cashier and real device/tablet flows, receipt/printer verification, migration compatibility/rollout, and Production/browser readback. QueueGo/POS remain NOT DONE.


## 2026-10-22 POS latest-main continuation after #613
- Re-fetched `main` at `56a5c251920ce3a471e8aa3418ff24b0af4411f0`. PR #613's test-only change is merged; exact merge-main RC run `39758464905` and Pages deploy run `39758462798` both completed successfully. No open POS successor PR is newer. Named POS/Native branches checked are substantially divergent; do not merge them wholesale.
- Release review found a Grocery/Market Kitchen-role UI leak: the staff editor prompt always offered `KITCHEN`, even though the invite form already hid it for non-Food/Cafe. Updated the role editor to derive its choices from shop capability and reject an injected Kitchen invite selection before calling an RPC. Regression assertions and an isolated Market runtime check pass; no backend authority is claimed by this UI guard.
- Required backend follow-up: the deployed `pos_set_staff_role` and `pos_create_role_invite` must reject `KITCHEN` for non-Food/Cafe, and `pos_join_shop` must not accept a stale/incompatible Kitchen invite after shop category changes. The category migration currently only guards kitchen bill transitions and payment; it does not alter those role RPCs. Add an additive, compatibility-reviewed migration after the required Supabase skill is available; do not treat CSS/client checks as backend enforcement.
- Full `npm test` passed after this UI regression fix; the added Market runtime path passed 78 POS lifecycle checks, including rejecting the Kitchen role before any role RPC. Production schema/data have not changed. QueueGo/POS remain NOT DONE.


## 2026-10-22 POS backend Kitchen role capability guard
- Fetched `main` at `5bbc78ca82b0598faa7170026d856c96d5aaf130` after PR #614. No open POS pull request was found. Reviewed POS/native successor branch heads; the large successor branches have divergent histories and contain unrelated application changes, so none was merged wholesale. The current main Actions lookup reported no PR-triggered runs for this merge SHA.
- Read the live Supabase migration catalog and `pg_proc` definitions before editing. Production already has `shop_profiles.public_category/metadata`, the three existing role RPCs and `pos_allowed`; no new table/function is needed. There are currently zero POS staff and zero unused Kitchen invites. No Production data was changed.
- Added migration `20261022103505_pos_kitchen_staff_capability_guard.sql`, replacing only the established four POS functions. Kitchen permissions now require Food/Cafe even for an owner or a legacy Kitchen-role row; Kitchen invites, joining a stale invite after category change, and role assignment are rejected on retail categories. Existing security-definer ownership and execute ACLs are preserved by `CREATE OR REPLACE`.
- Added isolated PGlite coverage for Food/Market permissions, invite creation, staff role assignment and stale invite redemption. Both POS category tests passed (23 and 11 checks); the full `npm test` suite exited successfully and `git diff --check` passed. Migration is not yet deployed; PR/CI and Production readback remain required. QueueGo/POS remain NOT DONE.


## 2026-10-22 Production POS capability and cash-movement deployment
- PR #615 passed exact RC run `39764778696` (npm tests, Admin/Merchant mobile WebKit, Map Lab gestures/search and Grocery mobile/tablet) and merged as `7572146400f44bf66c96aca5ae5f84264fe4241b`.
- Applied Kitchen role capability migration version `20261022105255`; readback confirmed all four existing functions retain SECURITY DEFINER and authenticated EXECUTE while anonymous EXECUTE remains false. The live category source is `shop_profiles.public_category` with metadata fallback; no POS staff or unused Kitchen invites were present.
- Production already contained `pos_register_cash_events` with the refund-only schema and authenticated SELECT guard, but not `event_type`, `idempotency_key` or `pos_record_cash_movement`. Applied the merged source migration; Production recorded `20261022105630_pos_cash_register_movements`. Readback verified columns, RPC signature, fixed search path/security definer, authenticated-only execute and zero cash-event rows. No sale, payment, refund, drawer movement or staff data was created.
- Security/performance advisor counts remain at their preflight levels (21 security lint findings, 18 unindexed-FK findings); the migrations added no tables or foreign keys. Live cashier and physical drawer acceptance remain unverified. Product Code/SKU and generic inventory migration chains remain unapplied. QueueGo/POS are NOT DONE.


## 2026-10-22 POS Product Code audit preflight
- Re-fetched current main `4d93ad0f6540aa9f1b27c070cc8340a447f9f913`, checked open POS PRs and the SKU successor branch. No open successor PR was newer; the branch is older and has no relevant source diff.
- Production lacks the POS Product Code migration, table and RPCs. Existing `products` has the storefront `price` and `pos_price` fields used by the code lookup. Existing `audit_logs` is owned by postgres, RLS-enabled but not FORCE RLS; privileged writes remain within authenticated, fixed-search-path SECURITY DEFINER functions.
- Hardened existing Product Code save/remove RPCs to log actor, shop, product, code type and code in the same transaction. A same-payload retry returns the existing row without a duplicate audit event. Focused PGlite registry and UI tests passed (35 and 20 checks), followed by full local `npm test` and `git diff --check`. This migration has not been applied to Production.
- Next: exact-head CI, merge, apply source migration, verify grants/RLS/audit behavior and align the migration filename with the Supabase version actually recorded. No fake products or transactions. QueueGo/POS remain NOT DONE.


## 2026-10-22 POS Product Code audit and Production rollout
- PR #617 passed exact QueueGo RC `39769241578` and merged at `30865060c4238ecbaf2b2ece83a108caeab3b454`. Production applied source registry/audit migration as `20261022113556_pos_product_code_registry`.
- Post-deploy readback: zero code rows, RLS enabled, no direct anon/authenticated table grants, four SECURITY DEFINER RPCs with fixed search paths and authenticated-only EXECUTE; lookup reads `coalesce(pos_price,price)` and has no GP/Delivery markup reference. Save/remove write audit_logs in the same transaction; no fake product, order or payment was made.
- Production advisor identified missing child-side product_id/created_by indexes. PR #618 added both indexes with PGlite tests; exact RC `39772099278` passed and merge SHA is `bc8bdc173a8b798e84dee09833b76caf8b546614`. Applied as `20261022114328_pos_product_code_fk_indexes`; readback confirmed both. The remaining unused-index notice is expected while the registry is empty.
- NEXT: align both migration filenames with Production versions, then proceed through the remaining POS inventory and authenticated cashier/device gates. QueueGo/POS remain NOT DONE.
