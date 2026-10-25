## 2026-10-25 Coastal/island service-area audit — IN PROGRESS

- Recovered current main `a3949038`, published #704 head `0c3e9252` and local checkpoint `d119ceb7`; forward-merged the published head without replacing newer source. Exact-head hosted RC `40170835613` succeeded on `0c3e9252`.
- Read-only live public service-area RPC: 18/22 approximate locality routes accepted. Ranong, Ko Tao, Ko Phi Phi Don and Ko Lipe samples rejected. Deployed Natural Earth boundary has only 20 polygons / 3,317 vertices; offline source containment reproduces exactly the four rejected cases. Province-name search completeness does not certify country coverage.
- Added a review-only boundary audit with pinned geoBoundaries LFS SHA-256/byte length, explicit ODbL provenance, polygon/hole/edge checks, disconnected-island checks and a read-only live SQL generator. The candidate contains 70 polygons; 21/22 route samples are contained, including the four current failures. The Ko Lanta sample disagrees with the coarse boundary and needs precise land/address verification; approximate fixtures are not verified addresses.
- Focused boundary regression: 18 PASS; pinned full candidate audit succeeded. Independent Shapely/GEOS reports Valid Geometry (70 polygons / 93,940 vertices); this is not a Production PostGIS test. Full `npm test` completed with exit 0 on source commit `9ce5395f`.
- No Production mutation or deployment. Candidate is not approved for cutover: require PostGIS validity, accurate border/coast/island address fixtures, sea/foreign negative cases and ODbL attribution/derivative distribution review. Never fix this by buffering the whole country or removing the server guard.
- NEXT: resolve the Ko Lanta sample against a precise licensed land/address source, validate candidate topology and boundary semantics, then stage a forward-only replacement with complete provenance and audited rule revision. Continue shared nationwide native lookup separately; no native build or real delivery acceptance is claimed.

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

## 2026-10-24 Native APK files and QueueGo Q role icons — DONE (test build/Web deploy)

- User clarified they need Android APKs. Latest successful Native Pilot run `40085133108` built Customer, Merchant and Rider debug APKs at Native source `8a9089d4fd87ab974da836cb0f3b6e61752cd027`; launcher asset-family verification passed. Artifact `12557006258` (`queuego-native-pilot-apks`) contains all three APKs; SHA-256: Customer `8a00886947e2350354d0d7c5b15375abcad83010f67152f171a01c7579578ca8`, Merchant `8acb51f3df1a40bca4b815dbdab1e6fb25359d0682dc8dbe80c8f321b37edb1f`, Rider `f803c0f31ac22bd0b719fae07f3098860063d54ec328fefff43e72ee4def6d34`.
- These are debug/test APKs, not Play Store production releases. Native icons are the established Q family: Customer Q+cutlery; Merchant Q+store; Rider Q.
- Web app icons now match the approved Customer/Shop Q artwork. PR #716 source `11a0d8efb14e082bb4176cb1a0e774bc7c0a798b`, merged `68968e74e83472119f91d7163c13b81925e6a9c2`; exact-head CI `40105477083`, main RC `40105786044`, Pages `40105786009` passed. Live 512px PNG icons and app links were verified.
- **NEXT:** owner-side install acceptance on the target Android devices; Native Play Store/release gate remains separate.

## 2026-10-24 Align Web app icons with QueueGo Q brand — IN PROGRESS

- Evidence: Web app icons added in PR #715 did not match the established native QueueGo Q role family. The newest Native successor `work-native-rider-auth-review-20261024` includes Customer/Shop Q artwork and generated 512px PNGs; its launcher asset gate checks those assets.
- Source commit `969eeb0f26a87d973f9626c1969e24b7034e82a1` on `fix/web-pwa-icons-use-approved-q-20261024`, based on current Web main `2d3aa5ec203c873b69ec52e2dcd108dbd4ef688a`.
- Scope: use the approved Customer Q+cutlery and Shop Q+store icons in Web PWA manifests, include iOS touch icons, keep Rider's existing official icon, and preserve app HTML download links.
- No Production data changed.
- **NEXT:** run exact-head QueueGo RC, merge only after it passes, deploy Pages, and verify the live PWA PNG/SVG assets. Android icons/APKs are governed by the Native successor, not this Web branch.

## 2026-10-24 Web app entry downloads and QueueGo Q icons — DONE (Web source/CI/deploy)

- Scope: provide launch and HTML-download links for Customer, Shop and Rider; add distinct, professional Customer and Merchant PWA icons and install manifests.
- Evidence: the three live Web entry points are `/`, `/merchant/` and `/rider/`; Customer and Merchant did not declare app manifests. Rider already has its approved icon and manifest.
- PR #715 source `d9020643e050033d35640aaabeb952054b74ff0c`, merged to main at `beb7438a3e36afe9991a37689660fe469b26cb32`. No production data changed.
- Exact-head RC #3337 / `40103741633` and post-merge main RC #3338 / `40104022905` passed. Pages deployment #1694 / `40104022142` succeeded.
- Live `/apps.html` shows all three launch and HTML download actions; Customer and Shop manifests and both SVG icons were opened and verified on Pages. Downloaded HTML entry pages require internet and hosted QueueGo assets/services.
- **NEXT:** owner can install Customer/Shop as PWA or download the three HTML entry pages from `/apps.html`; physical device installation/appearance remains owner-side acceptance.

## 2026-10-24 Shared map rotation and tilt — DONE (Web source/CI/deploy)

- Cause: shared and direct MapLibre maps disabled camera gestures or clamped pitch to zero outside Rider navigation. Raster OpenStreetMap tiles support tilted perspective but do not provide extruded 3D buildings.
- Change: enable mouse drag and two-finger rotation/tilt on Customer picker/Ride, Merchant, Admin, Rider, QueueGo Map and Map Lab; show compass/pitch controls while preserving zoom buttons and Rider follow.
- Exact-head RC #3335 / run `40103081891` passed npm regression and all mobile WebKit gates, including Admin, Merchant, Map Lab, QueueGo Map and Grocery.
- Merged PR #714 as `03ce3d5d4ebfff9f9aef150dec946ca12f54ecd6`. GitHub Pages run `40103399673` succeeded; live Customer entry point opened at `https://chatchairins-source.github.io/QueuGo/`.
- Owner iPhone gesture acceptance remains a separate physical-device check.

## 2026-10-24 Customer Ride map flicker and Rider tracking — DONE (source/CI/deployment)

- Evidence-backed cause: the Ride screen's 5-second polling called `render()` for the route request page even when its data had not changed. `render()` removes the MapLibre instance, replacing the map tiles and causing the observed flash. Supply refreshes could also rebuild it.
- Fix on isolated latest-main branch `fix/customer-ride-map-follow-20261024`: polling now leaves the request page mounted when its ride state is unchanged and updates nearby supply markers/count in place. Nearby Rider clusters use motorcycle icons. On active trips the Rider marker uses a motorcycle icon, eases between fresh positions, and the map follows the Rider relative to the pickup point (or destination during the trip). Asset query is cache-busted.
- Source syntax (`node --check customer-ride.js`) and `git diff --check` pass. PR RC #3311 / run `40078362321` and post-merge main RC #3312 / run `40078632981` both passed all stages.
- PR #708 merged to `main` as `6a3267ae8a0cd2113c4d68ed7454431dea2bd133`. GitHub Pages run #1688 / `40078632889` succeeded. Public Customer HTML SHA-256 `edda958b4d06144cffce227f638809b8829ae241155431d5525961bb47207bcb` and `customer-ride.js` SHA-256 `14121b4988fccc492f46c077cae658d379f8172a5eea00a514b4368fca42dc06` match the deployed files.
- **NEXT:** owner can confirm no flicker and Rider movement on iPhone Safari during a live Ride. Release is deployed; no physical-device behavior is claimed from CI.


## 2026-10-24 Responsive map layouts — source/CI DONE, owner visual review pending

- Reviewed Customer address selection, checkout and order maps; Merchant shop location maps; Rider home, navigation and registration maps; Admin shop review and location editor; QueueGo Map and Map Lab. Full-screen Rider/Map surfaces and existing compact order/registration maps remain unchanged.
- Customer screenshot root cause: `#customer-location-search-results` had the `hidden` attribute but also an inline `display:grid`, leaving an empty bordered strip visible. The grid now applies only when results exist. Customer location map height is responsive (`clamp(236px, 33dvh, 285px)`); the heading/card/action spacing is tighter. Admin location editor map is responsive (`clamp(220px, 34dvh, 300px)`) and the dialog scrolls within `90dvh`.
- Source commits: Customer `c18ef5ceae5a0ed5339ab96dd945569daf64abd2`; Admin `3d693ed7b43e00f1d4be3c06d55d1eb91bd30935`. Active successor: PR #704, branch `feat/queuego-places-search-successor-20261024`; exact HEAD `3d693ed7b43e00f1d4be3c06d55d1eb91bd30935`.
- Exact-head QueueGo RC #3307 / run `40075979310` SUCCESS: full `npm test`, Admin and Merchant mobile WebKit, Map Lab gestures, QueueGo Map search, and Grocery mobile/tablet rendering. No Production deploy or data mutation.
- **IN PROGRESS (visual acceptance):** CI does not certify the screenshot on a physical iPhone/Safari. **NEXT:** after the successor is deployed, owner-side refresh and visual check of Customer address selection and Admin location editor; then adjust only if the device evidence shows remaining clipping/overlap.
- The available local checkout is the older POS branch `work-pos-next-from-main-20261022` at `56a5c251920ce3a471e8aa3418ff24b0af4411f0`, 206 commits behind main with unrelated uncommitted POS work. Preserve it; map changes are committed on the PR successor above.

## 2026-10-24 Rider call outcome authority audit — IN PROGRESS

- Read-only QueueTech catalog/RPC audit: `qg_call_sessions` RLS is enabled, anon/authenticated SELECT are both denied, and one deny policy exists. Seven `qg_call_*` RPCs were found; all are SECURITY DEFINER with fixed `search_path=public,pg_temp`, anon EXECUTE false and authenticated EXECUTE true. Definitions use active-session checks and participant/order scoping.
- Server call outcomes include `missed` on server-observed ring expiry, `declined` on callee rejection, and `ended` through `qg_call_end`. `qg_call_active`/`qg_call_incoming` transition expired ringing sessions to `missed`; `qg_call_end` transitions ringing or accepted calls to `ended`. Source scan confirms Rider order enrichment requests Customer `id,name` only; no direct Customer phone query or `tel:` fallback exists. The shared voice client calls `qg_call_end` after its no-answer wait and on transport/WebRTC failures. So `ended` conflates voluntary hangup, timeout cleanup and technical failures and is not a safe failed-call counter; no authoritative 10-failure threshold or phone entitlement RPC exists. Do not expose Customer phone from the client based on these statuses.
- Source/test checkpoint: code commit `6c809de9e57fe37222274fc41072ee4fd681b288`, tree `ae951a470a32580e84cba5dc7110c0369c0ebdb2`, full `npm test` EXIT 0; Thai hierarchy/PGlite regression 47 PASS. Current verified main `04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd`; PR #704 `2001e89bcddf491c1e33ca50c622d0071605968c`; local forward-merge HEAD `f67e43bdfdd1c148b16e5fc48af36acefb361e2c`; PR open/draft/mergeable, shared tree `1a906ae4acf11c9c215f5d726a6d75a1d69726af`. Full `npm test` exited 0 on the unchanged source tree at `97abbe5c3d5e3efdc63c616fe840984a88458a0f`; the later deltas are documentation only. Exact-head RC #3303 / run `40074137068` passed on PR commit `15c76b30ebbb8e7a1af78b0fb8a8e9d869ecf185`, including npm, Admin/Merchant WebKit, Map Lab, QueueGo Map search and Grocery mobile/tablet gates.
- Official Government Data Catalog read confirms DOPA GIS-01 covers 76 provinces, lists JSON resources, records source as Department of Provincial Administration, and specifies Open Data Common. Its linked official DOPA download for Buriram returned HTTP 502; the direct catalog endpoint retries for provinces 31/32 returned HTTP 503. No payload bytes/checksum were received, so none were staged/imported. Production Places/Search schema/RPC remains absent; Buriram is still the only active ordering area; verified-cash no-show authority, advisor remediation, Production E2E and Web Owner acceptance remain open. No Production writes/deploy; mission NOT DONE.
- **NEXT:** refresh main and PR #704, commit this DOPA catalog/download evidence, publish by expected-head CAS/`force=false`, forward-merge the published descendant locally and verify exact tree/hosted RC. Continue only source/read-only work until the approved migration/release path and exact DOPA bytes are available; no client-side phone fallback.

## 2026-10-24 QueueGo publication verification and DOPA endpoint retry — IN PROGRESS

- Latest source/test commit remains `6c809de9e57fe37222274fc41072ee4fd681b288`, tree `ae951a470a32580e84cba5dc7110c0369c0ebdb2`; full `npm test` EXIT 0, hierarchy importer/PGlite 47 PASS.
- PR #704 head `9a2cd9f5f91ed594ca7a210c7688b887ab369f74`, tree `8c0da9b7ec31b1d615a808715db1cf83a2aa9b4e`; local forward-merge HEAD `83776abfe3ba119e58d5c72b8db9364137ef8554` has that same tree. Main `04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd`. PR remains open/draft/mergeable.
- Hosted exact-head RC #3300 / run `40072083639` passed all 12 stages on `9a2cd9f5…`, including full npm and every Admin/Merchant/Map Lab/Map Search/Grocery browser gate.
- Repeated official DOPA download for province 32 (Surin) alongside province 31 (Buriram); both returned HTTP 503 and wrote no payload file. Current QueueTech Production read-only inventory confirms Places relation/search RPC absent, phone-fallback/no-show named RPCs absent, and only one active Buriram Pilot service area. No Production write, import or deploy.
- Read-only advisors: Security 25 no-policy RLS, 10 anon and 186 authenticated SECURITY DEFINER, 1 leaked-password; Performance 18 unindexed FK, 3 no-PK, 79 unused indexes, 57 multiple permissive policies. `qg_rider_mark_arrival` is guarded by active Rider/order/stage and fixed search_path but records client-supplied GPS without validation; this cannot establish no-show evidence. No grant or policy changes made.
- **NEXT:** refresh main and PR #704, publish this docs-only update with expected-head CAS and `force=false`, confirm tree equality and exact-head RC. Continue the next source-only/read-only audit; mission remains NOT DONE.

## 2026-10-24 Production capability inventory (read-only) — BLOCKED

- QueueTech read-only catalog query returned no `public.queuego_places` relation, zero `qg_search_places` routines and zero RPC names matching phone-fallback/no-show. `public.queuego_service_areas` still has exactly one active area: `Buriram Pilot` / บุรีรัมย์. This verifies the release blockers against current Production without changing it; name matching does not rule out a differently named routine.
- Source and local regression remain committed at `6c809de9e57fe37222274fc41072ee4fd681b288` (tree `ae951a470a32580e84cba5dc7110c0369c0ebdb2`): full `npm test` EXIT 0, Thai importer/PGlite 47 PASS. Exact-host RC for docs-only commit `c8060d1f34a30bc6f02a9b937d741db84017d587` is run #3299 / `40071834270`, currently in progress.
- No production import/write/deploy. **NEXT:** wait for #3299, then proceed only with source/read-only work until an approved migration path, official DOPA bytes/checksum, advisor remediation plan and owner release evidence exist.

## 2026-10-24 QueueGo advisor/Rider RPC read-only audit — IN PROGRESS

- Source/test checkpoint remains `6c809de9e57fe37222274fc41072ee4fd681b288`, tree `ae951a470a32580e84cba5dc7110c0369c0ebdb2`; full `npm test` EXIT 0 and `node tests/th-admin-import.cjs` 47 PASS.
- Hosted exact-head RC #3298 / run `40071527998` passed on PR commit `a54d37e53abb3cf84d1e357e11525ea1b97cc0af` (tree `2dbd67bdd03eac04e311785bb48545029fc5ec0c`), including full npm, Admin/Merchant WebKit, Map Lab, QueueGo Map search and Grocery mobile/tablet. Local forward-merge HEAD `da8ee276c89778e9e6f6b5f1606e5bd197ed1097` has the same tree.
- Read-only advisor snapshot at `2026-10-24T06:42Z`: Security RLS/no-policy 25, anon SECURITY DEFINER 10, authenticated SECURITY DEFINER 186, leaked-password protection 1; Performance unindexed FK 18, no-PK 3, unused indexes 79, multiple permissive policies 57. No blanket mutations: findings include intentional private tables and guarded RPCs; object-level remediation is still pending.
- Read-only Production `pg_proc` inspection confirmed `qg_rider_mark_arrival(uuid,text,double precision,double precision)`: `SECURITY DEFINER`, fixed empty `search_path`, owner `postgres`, anon EXECUTE false, authenticated EXECUTE true. Live definition matches the pinned migration: active Rider identity, assigned-order ownership and delivery-stage guards. It stores client-provided `p_lat`/`p_lng` in audit metadata without validating that GPS as proof; that timestamp/audit is not sufficient to adjudicate or suspend a Customer for a cash no-show. No change was made.
- DOPA GIS-01 retry still returned HTTP 503 with no payload. Production Places/Search migration/RPC/import remains absent; Buriram ordering gate, verified no-show and fallback authorities, advisor remediation, Production E2E and Web Owner acceptance remain blockers. No Production writes/deploy or physical-device PASS.
- **NEXT:** refresh main and PR #704, publish this read-only audit checkpoint with expected-head CAS/`force=false`, verify exact tree and hosted RC, then continue the next source-only/read-only task.

## 2026-10-24 advisor refresh and exact-head RC — IN PROGRESS

- Current source/test checkpoint: commit `6c809de9e57fe37222274fc41072ee4fd681b288`, tree `ae951a470a32580e84cba5dc7110c0369c0ebdb2`; full `npm test` EXIT 0 and hierarchy PGlite importer regression 47 PASS.
- Current refs before this documentation update: `main=04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd`; PR #704 `473123128cf179f074081efb13f906f55b02e39c`, tree `2e62f96aa20f225d6d17b622a1ec7ecef3a3be92`; local successor HEAD `5e62f1cdddcc9b909aabe929bd1e38af9708d4d1` has the same tree and includes the hosted commit via forward merge. PR remains open, draft, mergeable.
- Hosted exact-head RC #3297 / run `40071210025` passed on `47312312…`: `npm ci`, full `npm test`, Admin/Merchant WebKit, Map Lab gestures, QueueGo Map search and Grocery mobile/tablet all succeeded.
- Read-only QueueTech Supabase advisors observed at `2026-10-24T06:42Z`: Security counts RLS enabled/no policy 25, anon SECURITY DEFINER 10, authenticated SECURITY DEFINER 186, leaked-password protection 1. Performance counts unindexed FK 18, no primary key 3, unused indexes 79, multiple permissive policies 57. The unused-index count is one lower than the previous snapshot (80); the other counts match. Private-table RLS findings and guarded SECURITY DEFINER RPCs may be intentional; do not apply bulk changes. Object-level remediation remains unresolved. No QueueGo Places/Search schema/RPC is present in Production.
- Latest official DOPA GIS-01 retry still returned HTTP 503 with no payload file. Production database and hosting remain untouched. Buriram order-area, Rider 10-call fallback/no-show authority, advisor remediation and Production/Owner acceptance remain blockers; QueueGo is NOT DONE.
- **NEXT:** publish this docs-only checkpoint after refreshing refs, using expected-head CAS and `force=false`; verify tree equality and exact-head RC. Continue read-only advisor/object review or other source-only work; do not mutate Production.

## 2026-10-24 hosted RC and DOPA retry — IN PROGRESS

- Exact current source/test commit remains `6c809de9e57fe37222274fc41072ee4fd681b288`, tree `ae951a470a32580e84cba5dc7110c0369c0ebdb2`; full `npm test` EXIT 0 and `node tests/th-admin-import.cjs` 47 PASS. Search authority audit found Customer/Merchant/Rider and Map View consumers call the shared `QueueGoMapWeb.search()` path, whose only backend is `qg_search_places`; no separate provider fallback or Buriram search catalog was found.
- Latest hosted branch before this documentation update: PR #704 head `7c64748e80defef4ec50a3399bfb5a749ad40470`, tree `2ab8009e1cdad122887ba470c8333dda77d16839`; local successor HEAD `27b38412aeffd35d69779d4a7387a124ffdbafa3` has the same tree and includes the exact hosted commit by forward merge. Main remains `04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd`. PR is open, mergeable and draft.
- Hosted exact-head QueueGo RC #3296 / run `40070860574` succeeded on `7c64748e…`, with all 12 stages passing including `npm ci`, full `npm test`, Admin/Merchant WebKit, Map Lab gestures, QueueGo Map search and Grocery mobile/tablet. RC #3295 / run `40070748518` was cancelled when the newer docs-only checkpoint superseded it.
- Retried official DOPA GIS-01 province-31 acquisition into `/tmp/queuego-dopa-20261024-retry`; endpoint again returned HTTP 503 and no payload file was produced. No data import, Production write or deployment.
- Remaining blockers unchanged: Production Places/Search schema/RPC/import absent; Buriram ordering/service-area gate remains active in Production; Rider 10-failed-call fallback and verified-cash no-show suspension RPC/audit absent; Supabase advisor findings, Production E2E and Web Owner acceptance remain open. Do not claim mission DONE or device PASS.
- **NEXT:** publish this docs-only state with a fresh main/PR check and expected-head CAS/`force=false`; verify exact tree and exact-head QueueGo RC. Then continue on the next unblocked source-only task.

## 2026-10-24 QueueGo publication/tree reconciliation — IN PROGRESS

- Exact code evidence: source/test commit `6c809de9e57fe37222274fc41072ee4fd681b288`, tree `ae951a470a32580e84cba5dc7110c0369c0ebdb2`, full `npm test` EXIT 0 and Thai hierarchy importer 47 PASS. Docs checkpoint commits were published without code changes.
- Local successor `work/places-current-successor-20261024` was forward-merged with the published PR checkpoint; current local HEAD before this note is `f6e6dc1c4a29831b48340e639fae9b7bbbc14870`, tree `4c83bf8c003d03916e3bdf5e69a407de7d6664c6`. This merge retains both histories and makes the local tree byte-identical to the hosted PR tree.
- Rechecked refs before the last docs publication: main `04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd`; PR #704 was `5150a7bd3d89354f023e3fa8ef6222c91da09154`. Published documentation-only descendant via CAS/`force=false` as `5aa06859f602da2835b39d4b6aa9ed255362ecf4`, tree `4c83bf8c…`; then fetched and forward-merged that exact hosted commit. PR #704 remains open, draft and mergeable.
- Hosted RC #3293 / run `40070332412` passed all 12 stages on commit `5150a7bd…`. RC #3294 / run `40070653446` on `5aa06859…` was cancelled when a newer docs-only descendant arrived. The replacement exact-head RC #3295 / run `40070748518` is running on PR commit `1c151fd36f1f8e07d3a45597b576debb903bd77b`, tree `bce5c9274098306970fbacf0c2e35c64e9359502`; check its terminal status before considering the publication verified.
- Mission blockers remain unchanged: official DOPA village payload unavailable (HTTP 503); Production Places/Search migrations/RPC/data absent; Buriram ordering/service-area gate remains in Production; Rider fallback/no-show server RPC/audit absent; advisor remediation and Production E2E/Web Owner acceptance remain open. No Production write/deploy, fake/demo data or physical-device PASS.
- **NEXT:** verify RC #3295 on exact PR head, then proceed with the next unblocked source-only task without duplicating the completed importer fix. Keep #704 draft and preserve all newer Merchant/Laundry/Rider work.

## 2026-10-24 Thai hierarchy first-run source provenance — IN PROGRESS

- Evidence-backed root cause: generated review SQL inserted `queuego_places` in a data-modifying CTE, then joined the base table again in the same SQL statement to create `queuego_place_source_records`. PostgreSQL statement-snapshot visibility meant first-run places were not visible to that second read; a second import incorrectly appeared to repair the provenance rows. The source-record insert now joins the `upserted ... RETURNING place_id,admin_code` CTE directly.
- Added an isolated PGlite regression that applies generated three-level fixture SQL on a fresh schema, asserts all 3 source records exist on the first run with source IDs `10`, `1001`, `100101`, then reapplies and verifies place/source/provenance/alias counts remain unchanged. Focused importer suite: 47 PASS. Actual pinned full-hierarchy review SQL replay in isolated PGlite: first run places 8,369/source records 8,369/provenance 175,749; second run identical (idempotent). This mock does not certify PostGIS or Production schema behavior; SQL remains review-only and unexecuted in Production.
- Source fix commit `6c809de9e57fe37222274fc41072ee4fd681b288`, tree `ae951a470a32580e84cba5dc7110c0369c0ebdb2`; local docs checkpoint `e0c9d34af1400d1df643290b8dd2563b1cda7103`, tree `ab5e4d9f292c4a3fe9a189a53692669e8adbdc02`. Full `npm test` EXIT 0 on the exact source/test tree `ae951a47`; focused importer test 47 PASS; `git diff --check` PASS. No schema/data write or deployment occurred.
- Startup refs freshly checked immediately before publish: `main=04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd`; open draft PR #704 head `731d9deebb110d4fcda73ec68e0ebaa08479425a`; both were ancestors of the active local successor. Published via expected-head CAS with `force=false` as PR commit `5150a7bd3d89354f023e3fa8ef6222c91da09154`, parent `731d9dee`, exact tree `ab5e4d9f…`; verified PR #704 now points there and remains mergeable/open/draft. Hosted exact-head QueueGo RC #3293 / run `40070332412` succeeded on `5150a7bd…`: `npm ci`, full `npm test`, Admin/Merchant WebKit, Map Lab gestures, QueueGo Map search and Grocery mobile/tablet all passed. This certifies source/CI gates only, not production, device or owner acceptance.
- Outstanding blockers remain: DOPA GIS-01 official payload request returned HTTP 503; no Production Places/Search migration/RPC or hierarchy import; Buriram-only ordering/service-area gates still exist in Production; Rider failed-call phone fallback and verified-cash no-show suspension RPC/audit absent; Supabase advisor remediation and Production E2E/Web Owner acceptance absent. No fake/demo data, Production write/deploy, or physical-device PASS.
- **NEXT:** update the stale top-level NEXT snapshot and ensure local successor history records the published checkpoint without rewriting history. Then continue the next unblocked source-only task; do not reopen or duplicate the importer fix. Keep PR #704 draft.

## 2026-10-24 OSM staging privacy-safe coverage report — IN PROGRESS

- Added aggregate-only staging coverage counters to `osm-th-stage-report.json`: staged rows per QueueGo category, point vs boundary geometry, and presence totals for province/district/subdistrict/village/moo. No names, addresses or raw tag values are copied into the report. Regression locks counts and verifies values such as province/subdistrict/village names do not appear.
- Re-staged the exact licensed PBF snapshot from the prior OSM checkpoint into a fresh local review directory. Same hashes: PBF `a55012170da1eb3f48c6f98ae847d10cdb720a7f4fd4cf5c660edfe526dacaba`; GeoJSONSeq `7d9c6f7935ae03529349a168b534cbe4a0755c9b7e01964b4829f041ed3b754d`. Result remains 165,305 records, 163,924 staged candidates, 164 review SQL chunks; no SQL executed.
- Aggregate staged categories: place 97,572; food 32,360; shopping 20,228; grocery 11,617; market 1,487; laundry 660. Geometry: 113,343 points and 50,581 retained boundaries. Address field presence: province 15,453; district 14,663; subdistrict 13,712; village 3,322; moo 3. These are presence counts among staged OSM features, not guarantees that each administrative locality has an OSM place.
- Source commit `2136b4f61432bf10673294c39f6a5e7d18d1b6fa`, tree `26a3dab1959f3aba0363cf9d3cee49a8480a8efc`. OSM staging suite 76 PASS; full `npm test` EXIT 0; `git diff --check` PASS. Latest main `04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd`; PR #704 draft head `64e3bef1bbceddb0ef7af5ee5a64a9eb516bab68` before this change.
- **NEXT:** refresh main and PR #704, reconcile forward if either advanced, publish this tested tree with compare-and-swap/`force=false`, verify tree equality and wait for exact-head QueueGo RC. Preserve the outstanding Production/DOPA/Rider/security/E2E blockers below.

## 2026-10-24 Complete Open Admin Data hierarchy import — IN PROGRESS

- Root cause established against the upstream source repository: `data/all-flat.ndjson` is explicitly levels 1-2 only; `data/all-subdistrict.json` separately contains level 3. The exact pinned all-flat blob `baa3eb18fb79412e85dcef08059d16f8cf47b317` has 1,005 records (77 provinces + 928 districts), so the prior importer contract expecting 7,364 subdistrict rows from that file could never pass.
- Verified the same upstream snapshot at repository commit `eef739a534e26181c86073d69e9aeb527110cf1a`: all-flat blob SHA `baa3eb18fb79412e85dcef08059d16f8cf47b317`; all-subdistrict blob SHA `bd5c49a7e1af578c9e34e32020ddd90eabdc167f`. Both exact Git blob hashes were recomputed from retrieved source bytes. The two files validate as 77/928/7,364 rows, 8,369 total, with Thailand coordinates, unique codes and every province→district→subdistrict parent present.
+- Updated `scripts/import-th-admin-places.cjs` to require both exact pinned source files, verify both blob hashes and levels, validate the complete hierarchy, then emit review-only SQL. Updated the un-applied source-registry migration, tests and attribution/provenance documentation to record both files and the repository commit. CC BY 4.0 attribution remains in generated SQL; no dataset payload is committed.
+- Actual review artifact `/tmp/queuego-th-admin-2026.06.review.sql`: 11,504,089 bytes, validated counts `{"1":77,"2":928,"3":7364}` and both exact blob SHAs in its header. It was not executed and no Production schema/data changed.
+- Source checkpoint `61acdcc0a4bf6be69562bc0ad781dc26aa72cda9`, tree `1c6dd76037511c5d46f1d97764ad3e70ea9c213a`. Focused importer 41 PASS; full `npm test` EXIT 0; `node --check` and `git diff --check` PASS. Actual two-file import CLI run passed exact-hash/count/parent validation.
+- Startup refs for this source chunk: main `04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd`; PR #704 draft head `3e9185d6bd9853e3736640f1d4d6f10d7406c28a`; both were ancestors of the local successor. Published source checkpoint is PR head `64e3bef1bbceddb0ef7af5ee5a64a9eb516bab68` with exact tree `589e4d5ee322b6c13cb67debf82161696863114e`, matching local after forward merge. Hosted RC #3291 / run `40069117625` passed `npm ci`, full `npm test`, Admin/Merchant WebKit, Map Lab, QueueGo Map search and Grocery mobile/tablet on this exact hierarchy importer head. The newer OSM aggregate report change above still awaits publication/RC.
+- Mission blockers remain: DOPA official village payload still unavailable (HTTP 503); Production Places/Search migration/RPC and imported data absent; Buriram ordering gate remains; Rider phone fallback/no-show RPC+audit absent; advisor remediation, Production end-to-end and Web Owner acceptance remain open. No fake/demo data, Production writes/deploy, or physical-device PASS.
+- **NEXT:** refresh main and PR #704 again, reconcile forward if either advanced, publish this exact tested tree by expected-head compare-and-swap with `force=false`, verify the remote tree equals local, then wait for exact-head QueueGo RC. Continue unblocked source work after publishing; keep the PR draft.

## 2026-10-24 Thailand OSM extract staged for review — IN PROGRESS

- Startup recovery reread `WORKFLOW.md`, this checklist and `NEXT.md`; fetched `origin/main` at `04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd` and verified active draft PR #704 head `21d228dc1eeb4b9674b025d1c7905cc9174ea42f`. The local successor was already a descendant of both. No reset, revert, cherry-pick or conflicting-worktree edits.
- Acquired the official Geofabrik Thailand extract redirected to `https://download.geofabrik.de/asia/thailand-261022.osm.pbf`: 329,196,519 bytes; SHA-256 `a55012170da1eb3f48c6f98ae847d10cdb720a7f4fd4cf5c660edfe526dacaba`; retrieved `2026-10-24T05:25:08.954Z`; snapshot `2026-10-22`. Exported using isolated PyOsmium 4.3.2. GeoJSONSeq SHA-256 `7d9c6f7935ae03529349a168b534cbe4a0755c9b7e01964b4829f041ed3b754d`, 56,063,510 bytes, 165,305 records. Export callbacks wrote 113,977 named point candidates and 51,328 named area candidates; one invalid area geometry was counted and skipped.
- Actual local review staging succeeded with the pinned exact PBF URL/hash and export hash: 163,924 rows staged in 164 SQL chunks (maximum 1,000 rows each), 1 outside-Thailand feature and 1,380 unnamed/uncategorized features skipped. Output is isolated under `/tmp/queuego-osm-review-261022`; generated SQL remains review-only, all imported verification states remain unverified, and no database/Production write occurred. The PBF/export/review SQL are not committed.
- Root cause/fix: Node `readline` treated a valid U+2028 Unicode line separator in an OSM address value as a physical record boundary. The stager now streams RFC 8142 record separator `0x1e` boundaries directly; a staging regression includes U+2028.
- Source checkpoint `38b29ec1b47f00e9d4156c0205b2d40465840ea8`, tree `7c8e461fb1aafe6d306d9fcd8ae041a35d52a3e5`. OSM staging suite 72 checks PASS; full `npm test` EXIT 0 on the exact committed source tree; `node --check`, PyOsmium helper `py_compile`, and `git diff --check` PASS.
- Published the exact tree by compare-and-swap from PR #704 head `21d228dc1eeb4b9674b025d1c7905cc9174ea42f`, with `force=false`; published commit `26beb13118a108cd946f8f9842bd62c4ac019250`, tree `db18ee64b97ae05caeb2b7e18b7ba3b5eebb4cdd`, matches the locally full-tested tree. PR remains open/draft and based on main `04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd`. Local branch forward-merged the published head as `74b49b6d0d91517291ac2a86f05747f884c71cbf` with the same tree.
- RC #3289 / run `40067874552` on `26beb13118a108cd946f8f9842bd62c4ac019250` was cancelled when the documentation checkpoint superseded it. Hosted exact-head QueueGo RC #3290 / run `40067981147` then succeeded on PR head `3e9185d6bd9853e3736640f1d4d6f10d7406c28a`: `npm ci`, full `npm test`, Admin fast boot/layout, Merchant mobile WebKit, Map Lab gestures, QueueGo Map search and Grocery mobile/tablet all PASS. This RC predates the latest admin importer source change below.
- Existing blockers remain: Production Places/Search schema/RPC and nationwide hierarchy/places are absent; DOPA GIS-01 endpoint returned HTTP 503 and has no verified payload/checksum; Buriram order gate remains; Rider 10-failed-call phone fallback and verified cash no-show server RPC/audit are absent; Supabase advisor issues are unresolved; no Production E2E/Web owner acceptance exists. QueueGo mission remains NOT DONE. No fake/demo data, Longdo/legacy map, Production write/deploy or physical-device PASS is claimed.
- **NEXT:** continue from the newest admin importer source checkpoint at the top of this checklist. The OSM source tree has exact-head RC evidence above; keep all Production/release blockers.

## 2026-10-24 OSM named category coverage — IN PROGRESS

- Startup refs refreshed before edits: main 04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd; PR #704 draft head 25ba03b8cdce5ff81b47cb4d1c07b81fd7a1f649. Local branch work/places-current-successor-20261024 descends from both.
- Extended the OSM tag allowlist and controlled category map for named leisure, historic, craft, office, public-transport, railway station, bus-stop and airport-terminal POIs. Existing food/shop categories keep precedence; untagged/unnamed features stay excluded and source user/uid/changeset attributes remain dropped.
- TEST: OSM importer 58 checks PASS; full npm test exited 0 on source commit 868435e5b6ff2a2e932ace92f43a63ba36604c4e, tree adcbfe2fff552a15748ea04b3e599979ad6d3c00; node --check and git diff --check PASS.
- Exact-head QueueGo RC #3288 / run 40065755801 SUCCESS on PR commit 21d228dc1eeb4b9674b025d1c7905cc9174ea42f, tree d8b267f92eb3130b396ec920d244fe8dbf641824, matching local source tree. Full npm, Admin/Merchant WebKit, Map Lab, QueueGo Map search and Grocery mobile/tablet gates all passed. No PBF download, SQL execution, Production data/schema write, deployment or physical-device test.
- **NEXT:** refresh PR/main and investigate whether an exact, licensed Geofabrik Thailand PBF can be acquired through the permitted network for actual review-only staging. Verify local bytes against the official PBF SHA-256 before staging; if unavailable, record the endpoint failure and continue only with source-only work. Keep the PR draft and never run generated SQL in Production.

## 2026-10-24 OSM Thai address hierarchy normalization — IN PROGRESS

- Refreshed refs before edits: main 04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd; active PR #704 remains draft at bbfb12864c5a487e281acc1a4f64107a23c684e2. Local successor is work/places-current-successor-20261024.
- OSM staging now preserves explicit addr:subdistrict, addr:district and addr:province, plus Thai suffix variants that take precedence over generic tags. It retains addr:moo in the existing QueueGo Places moo field and records moo provenance. The existing public-tag allowlist remains enforced; no user/uid/changeset fields enter SQL.
- TEST: tests/osm-places-stage.cjs 56 checks PASS; full npm test exited 0 on exact source commit d461ac272d4a54d82a091bfc68d2108eb674fe95, tree 8e4fb1cca566e0ff1f824ff5f43ce573013a783a; node --check and git diff --check PASS.
- Exact-head QueueGo RC #3287 / run 40065287650 SUCCESS on PR commit 25ba03b8cdce5ff81b47cb4d1c07b81fd7a1f649, tree 40b72aa54d7fcd9df9110ed5a7bdddedd30b6432, matching local source tree. Full npm, Admin/Merchant WebKit, Map Lab, QueueGo Map search and Grocery mobile/tablet gates passed. No actual PBF retrieval, SQL execution, Production write/deployment, or physical-device validation.
- **NEXT:** extend the controlled OSM tag map for named leisure, historic, craft, office and transit POIs; refresh main and PR before editing, cover tag precedence/exclusions in focused tests, run full npm test, checkpoint and publish by expected-head CAS/force=false, then wait for exact-head RC. Keep PR draft and Production blocked.

## 2026-10-24 OSM areal POI coverage — IN PROGRESS

- Refreshed current refs before this chunk: main 04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd; PR #704 draft head e70b17d1699329c2dabb30f84c72e094549f722a. Both are ancestors of local branch work/places-current-successor-20261024.
- Extended scripts/stage-osm-places.cjs to accept Point, Polygon, and MultiPolygon GeoJSONSeq records. Polygons retain their source geometry as a candidate boundary; generated review SQL derives an interior search coordinate using PostGIS ST_PointOnSurface and stores the boundary in the existing multipolygon column. Polygon vertices/rings are validated, and all geometry coordinates must lie within Thailand bounds. Lines and unsupported geometries remain explicitly skipped.
- Updated the documented Osmium export to include point and polygon geometry. Source timestamps from Osmium @timestamp attributes remain interpreted as epoch seconds. The generated records retain stable OSM type/id, full-geometry payload hash, source license and extract/export provenance; verification stays unverified.
- TEST: OSM staging 49 checks PASS (point, polygon, multipolygon, invalid rings, Thailand bounds, interior-point SQL contract, provenance/chunking); QueueGo Places schema 79 PASS; full npm test exited 0; node --check and git diff --check PASS. Source commit c2f7c4769143990c6fa07cae282a7db2114fc55a, tree 3c0fcb2b6135dd17dc860a4128502a2e35d80072.
- Exact-head QueueGo RC #3286 / run 40064755457 SUCCESS on PR commit bbfb12864c5a487e281acc1a4f64107a23c684e2, tree 61a44072008391a291118c7176a71f4db63ed1b5, identical to local source tree. npm test, Admin/Merchant WebKit, Map Lab, QueueGo Map search and Grocery mobile/tablet gates all passed. No actual PBF was fetched, no SQL executed, and no Production data/schema/deployment or physical-device test occurred.
- **NEXT:** extend OSM administrative address-tag normalization for explicit Thai addr:subdistrict/district/province forms; refresh main and PR #704 before source edits, test, checkpoint, publish with expected-head CAS/force=false, and obtain exact-head RC. Keep PR draft. No Production writes/deployments or fake/demo data.

## 2026-10-24 QueueGo OSM Thailand source staging — IN PROGRESS

- Startup sequence reread WORKFLOW.md, CHECKLIST.md, NEXT.md, AGENTS.md, and docs/PROJECT-MEMORY.md; fetched main and the active PR successor before source changes and again before checkpoint. Current main is 04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd; active PR #704 is open, draft, mergeable, and remote head b51ed7af5ffb135729e62c840a5e4662339e7aa4. Local branch is work/places-current-successor-20261024, local HEAD e1f301574dd883fab420638ac8eada25d40b80f7, tree 06ba3565f7d00d1a1be94c79c196a8c019a097c7; both current main and PR head are ancestors. No other worktree was changed.
- Added scripts/stage-osm-places.cjs, a streaming, review-only GeoJSONSeq stager for the already registered openstreetmap ODbL source. It requires the official Geofabrik Thailand PBF URL, local exact PBF bytes matching the supplied SHA-256, retrieval time, snapshot date, and a fresh empty output directory; it hashes both PBF and export, keeps allowlisted public place tags only, uses stable type/id, preserves per-object timestamp/hash and attribution/license provenance, limits output to Thailand coordinates, chunks SQL, and never executes SQL.
- Scope is intentionally Point geometry only. Exported ways/relations that are lines or areas are skipped and counted; do not treat this as complete OSM coverage. Generated records remain unverified; SQL is a review artifact and requires the approved migration/release path before any import. data/maps/README.md documents the exact staging contract and ODbL obligations.
- TEST: node tests/osm-places-stage.cjs 35 checks PASS; node tests/queuego-places-search-schema.cjs 79 PASS; node --check scripts/stage-osm-places.cjs PASS; git diff --check PASS. Full npm test exited 0 on exact code commit e1f301574dd883fab420638ac8eada25d40b80f7; all suites completed, including nationwide registration 15, shared search 16, DOPA importer 43/fetcher 14, operational Places 46, and Merchant product reliability 90.
- Hosted exact-head QueueGo RC #3285 / run 40064212590 SUCCESS on remote PR commit e70b17d1699329c2dabb30f84c72e094549f722a, tree a03df0ff3690206559b6c3679f3084feeaff4131, which matches local source tree ef80b6d44f430507d841ee8da564d8ce19e18b28. npm test, Admin/Merchant WebKit, Map Lab, QueueGo Map search, and Grocery mobile/tablet gates all passed. No Production SQL/data write, import, deployment, physical-device validation or live end-to-end acceptance occurred.
- Existing release blockers remain: Production Places/Search schema/RPC and nationwide hierarchy/places are absent; DOPA GIS-01 payload is still HTTP 503 with no verified bytes/checksum/row count; Production order-area remains Buriram-only; Rider phone fallback and verified cash no-show server authorities/audits are absent; security/performance advisor findings are unresolved. Supabase migration/release approval remains blocked; do not bypass the CLI telemetry auto-review rejection.
- NEXT: extend the review-only OSM importer to support areal POIs using a bounded representative point and retained boundary, while keeping export/source provenance and review-only import gates. Re-fetch main/PR #704 first; forward-merge and test the newest descendant, commit and checkpoint, publish by expected-head CAS with force=false, then wait for exact-head RC. Keep PR draft. No Production writes, deployment, fake data, Longdo, or legacy map.

## 2026-10-24 QueueGo Places current-main reconciliation — IN PROGRESS

- Startup read `WORKFLOW.md`, `CHECKLIST.md`, `NEXT.md`, `AGENTS.md`, and `docs/PROJECT-MEMORY.md`. The active source is local branch `work/places-current-successor-20261024`, not the separate dirty POS/Rider worktrees.
- Verified PR #704 remains the active draft successor, remote head `5b383b7c7bc7ca15228db8247d5429e22fefda23`; it is an ancestor of the source branch. Re-fetched current `main` at `04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd` (advanced from the prior `fddf53d1` checkpoint), then forward-merged it without reset/revert/cherry-pick. Merge HEAD `8fc5d0a0a27ff37b914a80bde479784c4545b573`; merge touched only `merchant/index.html` (two latest Add Menu label lines), retaining the Places/Rider/Laundry/Merchant successor content.
- Full `npm test` completed on exact source HEAD `8fc5d0a0a27ff37b914a80bde479784c4545b573`; all suites, including Merchant product reliability 90, nationwide registration 15, QueueGo Places schema 79, shared search 16, DOPA importer 43, DOPA fetcher 14 and verified operational import 46, reported zero failures. `git diff --check HEAD^ HEAD` passed. No hosted RC or publication yet.
- Published exact-tree successor commit `40977f1da4b9beccdd2fbaa1fa45b6b34915e600` to PR #704 with expected head `5b383b7c7bc7ca15228db8247d5429e22fefda23`, `force=false`. PR remains open/draft, mergeable, based on current main `04aa8b3b51cf2b2549107bb67f101c3dd91ed1bd`. The published tree SHA `7013b3ea6f5aade7909581927046a36d9337b25e` exactly matches the local full-test tree.
- Hosted exact-head QueueGo RC #3283 / run `40062777292` succeeded on `40977f1d`: npm, Admin fast-boot/layout mobile WebKit, Merchant mobile WebKit, Map Lab gestures, QueueGo Map search mobile WebKit, and Grocery mobile/tablet render all completed successfully. This certifies the hosted source gates only.
- Fresh read-only Production audit: no QueueGo Places relation or `qg_search_places`/phone-fallback/no-show RPC found; `queuego_service_areas` contains one active Buriram row (20km radius, 15km max delivery). No Production writes occurred. Current advisor counts: Security RLS/no-policy 25, anon SECURITY DEFINER 10, authenticated SECURITY DEFINER 186, leaked-password protection 1; Performance unindexed FKs 18, no primary key 3, unused indexes 80, multiple permissive policies 57. These are not blanket mutation authorization and need object-level review.
- Official DOPA GIS-01 fetcher was retried for province 31 using its registered official URL; endpoint returned HTTP 503. No payload or sidecar was written, no SHA-256/row count exists, and import remains disabled. No physical-device or Production E2E evidence is claimed.
- **NEXT:** continue the next unblocked Places/Search ingestion/source task from the mission; preserve the DOPA payload blocker and approved Supabase migration path. Before any source write, refresh main and PR #704 again, keep draft, forward-merge only, and use expected-head CAS. No Production write/deploy or fake data.

## 2026-10-24 QueueGo operational Places and DOPA acquisition integration — IN PROGRESS

- Reconciled to current main `0e8ff9a1` and clean successor PR #704 (`b67afe42`, tree `50e9b41a`), which descends directly from main and preserves the latest #697 source tree. PR #697 is superseded; #704 is now the active line.
- Added `scripts/fetch-dopa-village.cjs` as the acquisition step feeding `scripts/stage-dopa-village-batch.cjs`: it fetches only the registered official HTTPS DOPA URL, rejects off-host redirects, enforces the 100 MiB cap, validates province/schema/coordinates and exact-byte SHA-256, then writes `<resource-uuid>.json` plus `<resource-uuid>.meta.json` expected by batch staging. It never modifies the resource manifest or import eligibility.
- End-to-end mock verifies fetched bytes flow to the existing batch stager as one review candidate, with the other 75 DOPA resources still missing and source manifest still import-disabled. Focused fetcher tests: 14; batch importer suite: 43. Network is mocked; no real DOPA bytes were retrieved.
- DOPA official endpoint still times out/502. QueueGo Places tables/search RPC remain absent in Production; Buriram order gate, phone fallback, no-show and approved migration path remain unresolved.
- - Forward-reconciled PR #704 through current PR #704 source head `c56304d6` (null-safe operational eligibility and bootstrap parity) at merge commit `4dfa9b71`; latest main `0e8ff9a1` and #704 head are ancestors of this tested source. PR #697 stays superseded.
- Integrated the official DOPA fetcher→batch-stager bridge (`40b8569f`) and privacy-filtered QueueGo shop/market export/import. Newest source makes runtime trigger and snapshot bootstrap use the same private upsert/retire authority, stable `shop:<UUID>` / `market:<UUID>` source IDs, `queuego:<source-id>` dedupe keys, category mapping, and map-coordinate eligibility. The importer remains bootstrap/audit only.
- Production read-only grant audit found `authenticated` currently has `USAGE` on `qg_private` and EXECUTE on 63 existing functions. Removed generated schema-wide revoke; only new private Places functions have EXECUTE revoked. No existing app privileges are changed.
- Exact tested source tree HEAD `4dfa9b71c885b6b54c668cdb4d5394a8fc0aea89`. Full `npm test` EXIT 0; focused QueueGo Places schema 79, verified operational importer 46, DOPA fetcher 14, DOPA importer 43 PASS; `git diff --check` PASS. Null onboarding/Market eligibility is now consistent between runtime trigger and bootstrap import. Export SQL is read-only and PII-filtered.
- Official DOPA GET returned HTTP 503; no exact payload/checksum/row count/import candidate. QueueGo Places is still absent from Production; no DB DDL/data/deploy was applied and exact-head hosted RC has not run.
- NEXT: refresh latest #704/main and successor status, forward-merge any newer commits, publish the exact tested descendant to #704 with expected-SHA protection, then obtain exact-head QueueGo RC. Keep PR draft; no data import or deployment.

## 2026-10-24 Merchant Add Menu compact public release

- ROOT CAUSE: moving `เพิ่มเมนู` to the bottom correctly fixed placement, but the inherited generic `.qgm-fab button` style kept it as a full-width solid-red primary CTA, making a secondary action visually dominant.
- DONE (source): food/cafe `เพิ่มเมนู` is fixed above Merchant bottom navigation as a compact 156px pill, 42px high, white background with red text/border. Grocery `เพิ่มสินค้าเอง` / catalog actions are unchanged.
- DONE (merge): PR #700 merged to main as `c757450189fd63e85f27f99f8a0c9cc38a272205`; Merchant blob `240b06faabb982ba9f72a9a4351aab37518b0580`.
- TEST EVIDENCE: post-merge QueueGo RC run `40057628775` / #3246 SUCCESS across npm, Merchant mobile WebKit, Admin mobile, Map Lab, QueueGo Map search and Grocery mobile/tablet gates.
- DONE (GitHub Pages): run `40057628235` / #1683 SUCCESS on the same merge commit.
- No Production database/order/payment mutation. Unfinished Nationwide PR #697 was not merged into main for this UI release.
- BLOCKED (owner visual acceptance only): confirm on the owner device that the compact outlined `เพิ่มเมนู` treatment is visually balanced.
- NEXT: preserve this compact treatment on every Merchant successor; do not restore the full-width solid-red CTA unless explicitly requested.

## 2026-10-24 DOPA GIS-01 deterministic batch staging — IN PROGRESS

- Active successor: PR #697 branch `feat/queuego-places-search-foundation-20261023`; source commits `b02917cf` (batch stager), `06809c99` (batch regression) and `a926c977` (evidence contract docs).
- Added `scripts/stage-dopa-village-batch.cjs`. It consumes only local exact payload bytes named by official resource UUID plus a sidecar containing the same UUID, exact registered `catalog.dopa.go.th` download URL and retrieval timestamp. Acquisition is a separate step in `scripts/fetch-dopa-village.cjs`; the batch stager itself never downloads data and never mutates the manifest.
- Each present payload is SHA-256 hashed and passed through the existing province/village parser; province code/name, village identifiers, names and Thailand coordinate bounds must validate before any SQL is written. Existing manifest checksum/row-count values, when present, are enforced.
- Output is review-only: per-province SQL plus `dopa-gis01-stage-report.json` containing checksum, validated row count and `import_candidate`. Missing files stay missing; missing/mismatched provenance or invalid bytes are blocked and emit no SQL. `import_candidate` is deliberately not `import_eligible`.
- Regression now covers one staged Buriram payload, a missing province, exact checksum/row count, no manifest mutation, official metadata matching and a blocked non-DOPA URL. README records the two-file evidence contract.
- Official Buriram resource acquisition remains blocked: current web fetch cannot access the DOPA catalog resource and an independent runtime probe cannot resolve `catalog.dopa.go.th`; no mirror bytes, checksum, row count or Production import is claimed.
- Latest main `c7574501` is already an ancestor through successor merge #701 / `d1025835`; this descendant retains that main update. Exact-head hosted RC after the DOPA source/tests remains required.
- NEXT: obtain exact-head RC for the integrated fetcher + batch-staging successor, then continue official DOPA payload acquisition. If DOPA remains unavailable, continue the next unblocked Places/Search ingestion task; Production DDL/data remains blocked by the approved migration/release gate.

## 2026-10-24 DOPA GIS-01 nationwide resource registry — IN PROGRESS

- Active Nationwide successor is PR #697 head `8e54805cd5e7d0f2c95b0b814d5df970e656b81a`, descended from main `c757450189fd63e85f27f99f8a0c9cc38a272205` via #701 merge `d1025835fee28449114a901c57aee6dad104a22d`. Exact-head QueueGo RC #3248 / run `40057854157` SUCCESS on `d1025835` before DOPA source/test additions; latest head still needs its own hosted RC.
- Reviewed public government catalogs for DOPA GIS-01. Dataset package `705402d4-2715-4ece-9166-22b54c6c3476` is the DOPA village-level JSON source under Open Data Common. Buriram resolves to official resource UUID `2e6697b4-6efe-46b5-940a-0d7b2caf771f`.
- SOURCE: `data/maps/dopa-gis01-resources.json` registers 76 province-specific DOPA resource UUIDs and official catalog download URLs. All 76 remain `import_eligible=false` with `sha256=null` and `validated_row_count=null`; catalog metadata is not treated as verified payload evidence. The dataset is published for 76 provinces; Bangkok is not represented in this DOPA village source.
- TEST: `tests/dopa-village-import.cjs` locks all 76 unique province/resource identities, official-host/UUID URLs, and requires every resource to remain disabled without exact bytes, SHA-256 and validated row count. DOPA importer suite: 31 checks PASS. `data/maps/README.md` documents the exact-byte gate.
- BLOCKED (payload only): reviewed DOPA download paths still return timeout/502, so no actual province bytes, checksum, row count or generated import SQL are claimed. No mirror bytes were substituted as authoritative source. No Production schema/data/deployment mutation.
- TEST: full local `npm test` exited 0 on the current DOPA registry/test source tree (including all shared Places/Search, nationwide registration, map, importer and Merchant regressions); `git diff --check` passed. This does not certify hosted RC, API/Production or a payload import.
- NEXT: obtain exact-head hosted RC for latest PR #697 head `8e54805c` (current remote docs checkpoint `67d7ec7d`). If green, continue acquisition of official DOPA province JSON through a working permitted path; compute SHA-256, validate rows with `scripts/import-dopa-villages.cjs`, and only then set that resource import-eligible/stage generated SQL. Keep Production DDL/data blocked until the approved migration/release path exists.

## 2026-10-24 QueueGo Places reconciled to latest main successor — IN PROGRESS

- Latest main `c757450189fd63e85f27f99f8a0c9cc38a272205` is now an ancestor of active Places PR #697 via successor merge commit `d1025835fee28449114a901c57aee6dad104a22d` (PR #701). The PR branch advanced during the local forward-merge; that newer descendant was fetched and verified before continuing. Its source tree matches local tested code for the Merchant `74px` content clearance and updated regression test.
- Local full `npm test` completed successfully on the same merged code tree; relevant suites include Rider registration map runtime (13), QueueGo Places schema (60), shared search (16), Thailand admin importer (29), DOPA importer (19), Map View search (10), and Merchant product reliability (90). `git diff --check` passed.
- Exact-head hosted RC #3248 / run `40057854157` on `d1025835` was in progress at this checkpoint. No Production schema/data or deployment change. Official DOPA and data.go catalogs list the GIS-01 village dataset with Open Data Common license, but resource payload and checksum have not been obtained.
- NEXT: read exact-head RC #3248. If successful, keep the PR draft and continue permitted data acquisition through an official catalog resource; if it fails, fix only the first regression. Do not route around Supabase CLI telemetry auto-review; Places deployment/import, order-area, Rider phone entitlement, and verified no-show remain blocked.

## 2026-10-24 QueueGo Places forward-merge of latest main — IN PROGRESS

- Refreshed `origin/main` after the prior checkpoint: `c757450189fd63e85f27f99f8a0c9cc38a272205` (PR #700). Active Places successor remains PR #697 at remote head `d88e4332f5b517a2330b2d3616c6a78040b9d55f`; no newer Places successor was found.
- Forward-merged latest main into the clean PR #697 checkout. The only conflicts were the Merchant Add Menu content clearance and its exact-pixel regression assertion. Resolved using the newest main value (`74px`) and preserved compact button behavior and all other PR #697 files.
- Full `npm test` completed successfully on the merged source tree, including registration map runtime (13), QueueGo Places schema (60), shared search (16), Thailand admin importer (29), DOPA importer (19), map UI (5), Map View Search (10), and Merchant product reliability (90). `git diff --check` passed. Prior exact-head RC #3245 / run `40057430678` passed on `d88e4332`; it does not certify this new forward-merge.
- No Production schema/data or deployment change. Existing Supabase migration auto-review block remains in force. DOPA GIS-01 catalog is searchable in official web index, but official catalog page fetch still times out; no province resource checksum/import is claimed.
- NEXT: commit this forward-merge with this ledger update, publish the descendant to PR #697, run fresh exact-head RC; continue unblocked ingestion/source work and keep schema-dependent phone/no-show/order-area authorities blocked until approved migration tooling is available.

## 2026-10-24 QueueGo Places successor forward-merge

- Current source base: PR #697 branch head `465005316bf5b513f4aa2671b553327d26240c55`; latest main `fdccc127caa80ce0cbd9897ef7bbec09570fa825` includes the public Merchant Add Menu change. Local merge `295916e5c452d3a4a921e66edde1ba1b2d337ea2` preserves PR #697, forward-merges current main, and resolves only Merchant button CSS/test conflicts.
- Resolution keeps the newer compact `เพิ่มเมนู` action (156px minimum, 42px tall, quiet white/red outline) while taking the newer 88px content clearance. Grocery add actions remain unchanged.
- Verification on the merged local tree: full `npm test` exit 0; focused Merchant product reliability 91 checks PASS; Merchant home layout 8 PASS; `git diff --check` PASS. Existing PR-head RC #3240 passed on `be7b1a6` before this forward-merge; a fresh exact-head RC is required after publishing this merge.
- No Production schema/data or deployment change. Places/Search still lacks Production tables/RPC; nationwide order coverage, 10-call phone fallback and verified no-show server authority remain blocked by the unreleased migration path. Keep the external schema gate; do not route around the Supabase CLI telemetry auto-review.
- NEXT: publish the forward-merge onto PR #697 from current remote head `46500531`, confirm main is an ancestor, then verify exact-head RC. Continue into the next unblocked QueueGo Places/Search backend or data-ingestion gap without changing role flows or map/search separation.

## 2026-10-24 Merchant Add Menu bottom action — DONE (source/test)

- Latest-successor integration commit: `be7b1a65b7f41512010e1f76f8f3886646f32c53` on PR #697. The exact PR #697 HEAD was re-checked before this checkpoint.
- Food/cafe product management now uses `เพิ่มเมนู` and keeps the action fixed at the bottom above Merchant navigation.
- Grocery remains product-specific with `เพิ่มสินค้าเอง` and catalog add actions unchanged.
- Exact-head QueueGo RC run `40055332378`: SUCCESS. npm test, Merchant mobile WebKit, Admin mobile, Map Lab, QueueGo Map search and Grocery browser gates all passed.
- DONE (source/test). No Production data mutation and no older main Merchant file was used or backported.
- BLOCKED (visual/release only): the owner-device screenshot cannot be re-accepted until the broader PR #697 successor is released/deployed.
- NEXT: keep this commit in every descendant of PR #697; after that successor is released, verify on the owner device that the button reads `เพิ่มเมนู` and sits immediately above the Merchant bottom navigation.

## 2026-10-24 Merchant Add Menu bottom action — integrated into latest PR #697

- Source base: exact latest PR #697 HEAD `261e0c2d919a01bbd7b18d7156ef5ae73f907118`; Merchant and regression files were re-read from this SHA immediately before write.
- Owner request implemented: restaurant/cafe product management action now says `เพิ่มเมนู` and is fixed at the bottom above Merchant navigation.
- ROOT CAUSE: the iPhone product CSS changed generic `.qgm-fab` to sticky, leaving the action immediately after product cards.
- Grocery behavior is unchanged: `เพิ่มสินค้าเอง` and catalog product actions remain product-specific.
- TEST EVIDENCE: exact-head RC `40054885493` passed on merge successor `a1df8864737a4f43ce3a168c489ccaa628a34b52`, including npm, Merchant mobile WebKit, Admin mobile, Map Lab, QueueGo Map search and Grocery browser gates.
- IN PROGRESS: PR #697 exact-head RC must validate this integrated merge commit.
- No Production data mutation.
- NEXT: verify PR #697 exact-head RC, then continue from its latest successor without reverting this change.

## 2026-10-24 Nationwide map POI authority checkpoint

- Source change: `queuego-map-web.js` no longer fetches or searches a bundled Buriram POI catalog. Map viewport POIs, shared text search, category lookup and nearby lookup now use only `qg_search_places`; map search overlay mounting returns null, leaving Search outside the embedded map. Removed `data/maps/buriram-places.json` and updated its README. Search fails closed when shared QueueGo Search is unconfigured; no local regional or unconfigured geocoder fallback.
- Successor PR #697 branch `feat/queuego-places-search-foundation-20261023`; latest source commit `174c852aa4fd586acb6b1bc9843a300c840a9784` locally; published source commit `a751de50ab41246d2f2151f865229705c5399f7a`. Latest PR head `75dcc3c6b2a51f14ae1c277af1785324b5dbfdc9` carries the full checklist; tree `e2bc10f777fa03622e368de115792abd749a69e2`, with equivalent local merge successor `f91758b230ad3d04ef4ff4fa76765b5d58dd7c16`. Exact-head RC #3238 / run `40055062868` was started on this head and cancelled when this checkpoint advanced; RC #3231/#3235/#3236 were cancelled as refs advanced. RC #3230 predates this source change.
- Verification on local exact source tree: focused map catalog/search/map UI/runtime checks PASS (4/8/5/16), `node --check queuego-map-web.js` PASS, full `npm test` exit 0, `git diff --check` PASS. These test mocked QueueGo Places; Production RPC/table availability remains absent, so live nationwide POIs/search are not yet available.
- Tile deployment review: client still uses OSM standard raster tiles with visible attribution. The official OSM tile policy documents best-effort service/no SLA, requires correct attribution, normal interactive-only requests and cache compliance, and recommends switching/self-hosting for unsuitable production use. No owned tile infrastructure or licensed service credentials are provisioned in this repo; do not bulk-download public tiles or silently introduce a paid provider.
- Mission remains NOT DONE. Nationwide Places/admin schema is source-only and not imported/deployed; actual DOPA village data/checksum remains unavailable; Buriram order-area gate remains in Production; server-authoritative phone fallback and no-show suspension are not implemented; exact-head CI/security advisors/live backend acceptance/Web deploy remain outstanding.
- NEXT: verify the exact-head RC triggered by this checkpoint, then continue from the newest PR #697 successor to the approved Supabase migration path and live nationwide Places/service-area authorities. Keep source ingestion blocked until a real licensed DOPA resource is retrievable and checksum-verified. Do not merge/deploy or claim full service readiness while those gates remain.

## 2026-10-23 Nationwide QueueGo Places/Search foundation

- Mission: make map, registration and place search nationwide while keeping order availability separate; build QueueGo Places/Search; remove the Buriram-only route gate; harden Rider phone fallback and verified cash no-show suspension.
- Correct successor at start: PR #695 branch `fix/map-search-outside-embedded-maps-successor-20261023`, HEAD `db3c75b6e7e5f8409bf4f83e39cd26bba49d306d`, descendant of main `c7090f1ced830e7203a719bea38db3ba636aee64`. While work was in progress, main merged PR #696 at `0c22c1dc98ed6aa793483aa90ad8dfc8007486a8`; feature branch merged that current main forward at `04d037fcddccad8857a432b8c740d387d73e5215` and retains #696's Customer/Merchant map/search separation. Current local branch `feat/queuego-places-search-foundation-20261023`, exact HEAD `2115bdfc625758ef88ce7599c96bd3b02dfc5395` (parent `28b35873b614a23ae9ad2b9127e2bbb7cfbfef7f`); this successor is not pushed and has no PR yet.
- Production read-only evidence: PostgreSQL 17.6; `queuego_service_areas` has the single active Buriram Pilot circle at 14.99292,103.10804, radius 20km, max delivery 15km. `queuego_assert_service_route()` calls this authority and rejects out-of-area checkouts. `qg_call_sessions` exists with direct client RLS denied; `qg_call_start`, `qg_call_active`, and `qg_call_end` are SECURITY DEFINER with authenticated-only EXECUTE. Existing call-end marks calls ended without a failed-attempt classification. Rider Web currently reads `users?select=id,name,phone` for customer IDs.
- IN PROGRESS (local source/test): additive migration `supabase/migrations/20261023144405_queuego_places_search_foundation.sql` introduces source/license registry, Thailand Places and admin-hierarchy fields, source-record merge identities, field-level provenance, aliases, PostGIS point/polygon columns and GiST indexes, exact/prefix/fuzzy/nearby `qg_search_places`, RLS and explicit grants. OSM/ODbL attribution and QueueGo verified records are registered; no place/catalog data is imported. No Production schema/data was changed.
- IN PROGRESS (local source/test): migration `20261023195500_queuego_th_admin_source_registry.sql` registers Open Admin Data Thailand Administrative Divisions release 2026.06 under CC BY 4.0, records attribution/update policy and the reviewed `all-flat.ndjson` Git blob fingerprint, and enforces one field provenance row per source record. `scripts/import-th-admin-places.cjs` validates the pinned snapshot hash, expected 77 province / 928 district / 7,364 subdistrict counts, Thailand coordinate bounds, unique admin codes and valid parent hierarchy before emitting repeatable upsert SQL with source-record hashes, bilingual names, postcodes where present and field provenance. Source has no village records. Focused importer tests 23 checks PASS; no dataset was imported and no Production schema/data changed.
- IN PROGRESS (local source/test): `QueueGoMapWeb.search()` now calls the same public `qg_search_places` RPC for all configured app roles, normalizes place coordinates/provenance, and allows only explicitly configured fallback adapters; external fallback results are marked non-persistable and the direct Nominatim search endpoint is removed. Customer, Merchant and Rider share one Supabase search config. Rider's existing destination preference adds a functional search field outside its map; selecting a result moves the existing QueueGo Map center and label. Focused shared-search test 16 PASS; Rider destination runtime test 41 PASS; map search/UI tests 20 PASS.
- Evidence: `npm test` exited 0 on the exact implementation source committed at `2115bdfc625758ef88ce7599c96bd3b02dfc5395`; latest focused checks pass for schema (60), importer (25), shared search (16), Rider settings (41), and `git diff --check`. Supabase docs confirm extension placement in `extensions`, geography, GiST and `ST_DWithin` pattern. Production extension list showed `pg_trgm` installed and PostGIS available but not installed. The local environment has no `psql` or Docker/Supabase runtime, so migration SQL execution and exact-head hosted CI are still required; Production was not used to test DDL.
- Blocker (remote delivery): exact source is committed locally at `2115bdfc625758ef88ce7599c96bd3b02dfc5395` with checkpoint docs at `cf95f3371b1e69a9271d47bb75be332d71cdf7d0`, but `git push` to GitHub failed because this checkout has no HTTPS Git credentials (`could not read Username`); therefore no hosted PR/CI can be opened from this environment. Local work continues on the same newest successor.
- Blocker (schema tooling): `npx supabase@latest migration new qg_rider_phone_fallback` was rejected by automatic approval review because the CLI contacts a PostHog telemetry endpoint and may transmit local command/environment metadata. No DDL was applied; its empty generated file and transient cache were removed. Per the reviewer, do not route around this rejection. The Rider ordinary order payload no longer selects Customer phone; focused voice-contract, Rider runtime/chat and diff checks pass. Server fallback RPC/audit and no-show schema work remain blocked pending an approved migration path.
- Latest verification: full `npm test` exits 0 on exact local HEAD `5293ab7cbff50b7fe605d6baee9319db68544298`; this includes the phone-free ordinary Rider enrichment contract, Places/search/importer tests, and current regression suite. This is local evidence only: no hosted RC, isolated execution of the new Places migrations, Supabase advisors, or Production deployment is implied.
- Nationwide registration/discovery root cause: Rider Web's required province `<select>` listed only 16 provinces; both Merchant Market signup/membership flows filtered `markets` by `province=eq.บุรีรัมย์` and prefixed new-market requests with Buriram. Successor commit `9106c597040a174dc85ccae5292c574b5cf935d0` changes Rider province/district to bounded free-text address fields and removes the province filter/default from both existing market discovery flows; distance ranking remains based on the submitted shop GPS point. `tests/nationwide-registration.cjs` checks Phuket Rider metadata and Phuket-nearest market ordering while confirming that Buriram rows remain in the all-province results. Focused tests (15/117/130) and full `npm test` exit 0 on this source. No signup, market submission, Production mutation or deployment was performed.
- Scope limit: Rider signup now accepts any province, but still initializes rider profile coordinates as null; a Rider QueueGo Map pin flow remains to be implemented. Market enrollment still needs an active nearby market and verified radius; removing province filtering does not create missing market supply or bypass market proof.
- Status: **IN PROGRESS**, not DONE. The reviewed administrative importer is ready but no nationwide hierarchy dataset has been imported; shared RPC integration is local source only and not deployed; Production still has the old Buriram route gate and old phone payload; no verified no-show flow exists yet. Rider signup accepts any province and Merchant market discovery no longer filters to Buriram on this successor, but Rider coordinate pinning is still absent. Exact-head hosted CI, isolated Postgres execution and Supabase advisors remain pending.
- NEXT: continue the unblocked source lane by adding a Rider QueueGo Maps pin during registration and persisting the selected coordinate in its existing rider profile fields, with map/search separation and regression coverage. Then resume source-only importer/search review. Schema-dependent next steps (Places migration execution/import, nationwide ordering-area authority, voice fallback, and no-show suspension/audit) remain BLOCKED until an approved migration path is available; do not route around the CLI auto-review rejection.

## 2026-10-23 Embedded map search separation — latest successor

- PR #696 merged to main as `0c22c1dc98ed6aa793483aa90ad8dfc8007486a8`, based on main `c7090f1ced830e7203a719bea38db3ba636aee64`, including the shared Laundry storefront and its latest cache-bust.
- Customer/Merchant embedded location search is outside the map; selecting a result recenters/pins QueueGo Maps. Shared QueueGo embedded maps no longer create search overlays by default.
- Merchant menu/product search remains functional; the add-menu/add-product action is compact directly beneath search.
- Tested predecessor `7a36490584b9f6eeec4f2d7e892f0e24bc069b0c`, RC `39969847191` SUCCESS across npm, Merchant mobile WebKit, Admin mobile, Map Lab, QueueGo Map search and Grocery browser.
- This successor reuses identical tested Merchant/map/test blobs and reapplies only the Customer location-search changes onto latest `index.html`.
- Google Places POI data is not copied onto QueueGo/OSM maps; current Google Places policy requires Places results displayed on a map to use a Google Map.
- No Production mutation.
- NEXT: retain #696's separated map/search behavior while integrating the shared QueueGo Search backend; do not restore provider-specific UI search or an embedded map overlay.

## 2026-10-23 Merchant shopping subcategory compact mobile picker

- Owner screenshot shows Shopping subcategory choices occupying excessive vertical space on Merchant Account/Edit Shop mobile.
- ROOT CAUSE: Merchant CSS forced `.qgm-shopping-subcategory-grid` to one column below 430px, while the checkbox inherited generic `.qgm-field input` min-height/padding because only `accent-color` was overridden. This produced oversized checkbox controls and one tall full-width row per subcategory.
- DONE (tested source): tested successor `929454bd690780d8a3728d6bc7609f6cd890fc7c` keeps a two-column mobile grid, ~46px label touch targets and explicitly constrains native checkboxes to 18px without changing stored values/save behavior. Exact-head RC `39964095616` SUCCESS: full npm regression, Merchant mobile WebKit, Admin mobile, Map Lab, QueueGo Map search and Grocery mobile/tablet gates passed.
- RECONCILED: while CI ran, main advanced from `badd6f1c` to `433771eecddd1557c1eac7804f6f35614d933639` only through Laundry `CHECKLIST.md` commits. This successor starts from that newer main and reuses the exact tested Merchant/test file contents; no Laundry runtime file is overwritten.
- IN PROGRESS: successor source `0fbf5dd40c610ec12d4ea34ac827f6614e34532a`; no Production data mutation.
- NEXT: obtain exact-head CI for the reconciled successor, merge to main, deploy the proven merge to Render, then require owner-device visual acceptance before claiming physical mobile PASS.

## 2026-10-23 Customer Laundry shop photos

- Stream: display each registered Laundry shop's real profile cover, falling back to its profile logo. The catalog returns shop identities, so the page fetches only the profile photo fields for catalog shop IDs under the authenticated session.
- ROOT CAUSE: publicPhotoUrl used a regex character class containing a doubled backslash before s, rejecting the letter s and filtering Supabase Storage URLs. Fixed with browser URL parsing and HTTPS-only validation.
- DONE (source/test): PR #688 merged as badd6f1c3645a7a456ca08fcea2b212d84fdbe29. Exact-head QueueGo RC run 39961728447 SUCCESS on 823284b3a4b91d72db334e702e4a514126b5b087; npm test and mobile WebKit checks passed. Regression covers a Supabase Storage HTTPS URL and HTTP rejection. No schema or Production data changes.
- DONE (GitHub Pages): run 39964233648 SUCCESS for main commit ecc0b8044e19dcc5af7d7b7eaaccb32ac769b6f5.
- DONE (Render): after the owner delegated workspace choice, selected the sole available workspace My Workspace (tea-dbdejavavr4c739q33l0). Deploy dep-dbdmbenavr4c73aj2f4g reached LIVE on the same main commit ecc0b8044e19dcc5af7d7b7eaaccb32ac769b6f5 at https://queuego-web.onrender.com.
- IN PROGRESS (main RC): run 39964233691 on ecc0b8044e19dcc5af7d7b7eaaccb32ac769b6f5. Exact PR-head CI is already SUCCESS; no failure reported on this main run.
- NEXT: owner visual acceptance on a signed-in browser/device: open the Laundry shop list and confirm a registered shop photo appears. Do not claim visual acceptance from CI/deploy status alone.


## 2026-10-23 Customer Laundry registered directory + Render release

- Owner reported that Customer Laundry still showed the legacy QueueGo Laundry Hub and required registered Laundry shops only, while Laundry shops must stay off Customer Home.
- ROOT CAUSE (source): the old Customer Laundry page read raw Laundry hubs/settings, while Customer Home did not exclude public_category='laundry'. PR #685 replaced that client-side discovery with the authenticated `queuego_laundry_customer_catalog()` authority, restricted the catalog to active registered Laundry shops with enabled settings, active service and accepted active Rider, reconciled qualifying disabled shops, and excluded Laundry shops from Home.
- DONE (source/test): PR #685 source HEAD `28dfa3d77b99c61d8c6df5460164c0c6d86a9bb7`; exact-head RC run `39951223902` SUCCESS. Merged to main as `e74f384ce32ccd0cfb993ac74bdf48c729906901`. Post-merge RC push run `39951714290` SUCCESS and GitHub Pages run `39951713397` SUCCESS.
- ROOT CAUSE (Render stale Production): `queuego-web` is configured for branch `main` with autoDeploy enabled, but no Render deploy was created for commits after `000ac0ba3c04bce23900cc6fc67f5df14e6305c9`; Production therefore remained on the pre-#685 bundle even though GitHub main/Pages were newer.
- DONE (Render deployment): forced the missed release path only after confirming Render had not auto-created a deploy. Deploy `dep-dbdli7e7bikc73e1vsu0` built commit `e74f384ce32ccd0cfb993ac74bdf48c729906901` and reached LIVE. The Render packaging script includes `laundry/`; committed Laundry HTML uses `queuego_laundry_customer_catalog`, no longer performs the old raw `laundry_hubs?select=id,name,shop_id` read, and Customer route cache-bust is `20261023-dir1`.
- No mock/demo/fake Production data was created. No Production order/payment/auth mutation was used for this release validation.
- NEXT: owner-device browser acceptance only: open Customer Home and confirm Laundry shops are absent from the nearby-store list, then open ฝากซัก and confirm only the actually registered Laundry shop(s) appear. Do not claim physical-device/browser acceptance until that observation is provided. Future Laundry edits must retain `e74f384c` or a proven descendant and must not restore the legacy raw-hub directory.

## 2026-10-23 Rider automatic route + simplified pickup flow

- Owner requires continuation from newest Rider Web successor only. Remove the manual navigation CTA in the normal shop/customer active-order flow because QueueGo route guidance starts automatically after claim. Remove the redundant “ได้รับอาหารแล้วใช่ไหม?” yes/no confirmation after the required pickup receipt photo.
- DONE (source/test): exact source HEAD `705e82c470b06249677b75d5ed18aeeee943f2d4` on `fix/rider-remove-nav-and-pickup-confirm-20261023`. Active food/customer card no longer exposes `primary-nav-`; it exposes a stage action and goes directly to “ถึงร้านแล้ว” / “ถึงลูกค้าแล้ว”. Arrival still persists through the existing authenticated 200m advisory guard. Pickup still requires the existing single receipt photo and photo-backed pickup RPC; only the extra yes/no overlay was physically removed. Route geometry, rerouting, camera/follow, numbered stops, cash responsibilities, delivery proof, work-preference switches and Production data are unchanged. Build `rider-20261023-flowtrim1`.
- CI first failure on predecessor `12fc38981e32be514d4bdbdd424709835d7a19dc`: RC `39927104730` failed only at `tests/cross-role-order-status.cjs` because the test still expected the retired local manual-navigation flag. Successor `705e82c4` updated that assertion only. Exact-head RC `39927284818` SUCCESS.
- DONE (merge/deployment): PR #673 merged as `cf82b8a1113c3cc9c2ac7290c4ed4912179dd69e`. GitHub Pages run `39927661060` SUCCESS. Render deploy `dep-dbdi0d5g1s2s73bittpg` LIVE on the same merge. Post-merge RC `39927661814` SUCCESS and Chat Retention `39927661833` SUCCESS.
- NEXT: retain `cf82b8a1113c3cc9c2ac7290c4ed4912179dd69e` or a proven descendant as the Rider Web successor. Owner-device visual/road acceptance is still required; do not claim physical-device PASS. Future Rider edits must not restore the manual navigation CTA or the redundant pickup confirmation overlay.

## 2026-10-23 Rider switches and right-side map HUD

- Owner requires continuation from the newest Work successor only; never restore older Rider files. This stream started from main `edac2cdf5e68cb3c81af49331b1ad85ee2a25723`. Concurrent Work commits `73574832d20cac59767d38547980822349d9ed97` (autosave work switches) and `77f484d8e70f25c24feb57ac44ecd2f30ced836a` (autosave tests) were detected on the active branch and retained as newer successor work; they were not reverted or replaced.
- DONE (source/test): PR #670 exact source HEAD `273924008e934b13938481d8bdd85a758ae22991`. Existing Rider work-preference checkbox inputs are presented as QueueGo toggle switches and save automatically on change; the separate save button is removed by the retained Work successor. Server acknowledgement, service/bundle authorization and assignment enforcement remain authoritative. Rider Web speed is moved from the top header into the right-side map tool stack, and the existing MapLibre navigation compass is moved from top-left to top-right. Route geometry, numbered stops, rider puck, rerouting, camera/follow and assignment logic are otherwise unchanged. Build `rider-20261023-switchhud1`.
- Exact-head RC `39924716374` SUCCESS: npm regression, mobile WebKit Admin/Merchant checks, Map Lab gestures, QueueGo Map place search and Grocery mobile/tablet rendering all passed. PR #670 merged as `0175359263ed4a237977f3539f6e0e8aece39407`. Post-merge RC `39925078609` SUCCESS and Chat Retention `39925078601` SUCCESS.
- DONE (deployment): GitHub Pages run `39925077934` SUCCESS on merge `0175359263ed4a237977f3539f6e0e8aece39407`; Render static-site deploy `dep-dbdhm6vb9u1s739occg0` is LIVE on the same merge. No Production database/order/payment mutation was used for this UI change.
- NEXT: retain merge `0175359263ed4a237977f3539f6e0e8aece39407` or a proven descendant as the Rider Web successor. Owner-device visual acceptance is still required for the switch appearance and the right-side compass/speed stack; do not claim physical-device PASS from CI alone. Any further Rider changes must reconcile from the newest descendant and must not restore pre-#670 files.

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

# QueueGo Active Work Checklist

Last governance checkpoint: 2026-10-23; latest verified main `9eb3dac112ab0003c5b36dd844c3abbcff1decac`

This checklist is the repository continuation ledger. Read `WORKFLOW.md` first.

## Current verified Rider Web checkpoint — 2026-10-23

- Current Web main: `9eb3dac112ab0003c5b36dd844c3abbcff1decac`, merged from successor PR #636 (`fix/rider-web-benchmark-main-reconcile-v3-20261023`, exact head `e36b2a49f6877d18212b98caa14511dfe52a62f5`). PR #629 and #632 are superseded and closed; do not merge or restart from them.
- Exact-head QueueGo RC `39890314962` passed before merge. GitHub Pages deployment `39891035608` passed on merge SHA `9eb3dac112ab0003c5b36dd844c3abbcff1decac`. Post-merge main RC `39891036067` has already passed `npm test`; remaining browser checks were still running when this checkpoint was written.
- Rider Web keeps QueueGo Map-only route authority, 30 m / 3 s generic off-route policy, immediate missed-turn reroute guard, 25 m route snap, maneuver HUD, 8 s post-pan follow recovery, durable pickup/delivery proof, cash confirmation, and Ride/Express modules. No Longdo/legacy route fallback is permitted.
- Release status remains **BLOCKED** only for evidence that CI cannot certify: physical Rider road/GPS behavior and related device runtime acceptance. Do not claim physical-device PASS from Web/CI evidence.
- **NEXT:** finish/read back post-merge main RC `39891036067`; if green, retain `9eb3dac1…` as the deployed Rider Web baseline. Any later Rider change must branch from current main and preserve this successor instead of reviving #629/#632.

## Current verified POS checkpoint — 2026-10-23

- Web POS category filters remain deployed and byte-verified from `be33b8318967ad6105222efa8305fec8005a5335` (PR #631). Exact RC `39878988663`, main RC `39879361494`, Pages `39879361545` succeeded. This checkpoint changes documentation only.
- Native PR #633 exact `d98ecdbf423e34b41e4fde3062d7b0d5db6750f0` passed both requested runs `39881729771` and `39881729809`; merged only into Native successor at `0a9c17dd774f59781de0565e9fa313da5ee955b8`.
- Native PR #635 fixes Android cached null-category conversion and tests null/named/literal-null/legacy catalogs on Android itself. Exact source `639bedea25710988c5f446ba6c16d1c0d3072a7d` passed Native Pilot `39882985832`: regression, Kotlin/unit tests, APK/16 KB checks and emulator instrumentation. Log confirms `QUEUEGO_MERCHANT_POS_CATALOG_PASS`. Merge `1f71c351b88d635de810fccb8e82bc447de37390` has the identical tested tree; Native successor documentation head is `382a746ad9176a03c4b7beafdfd1dbc2dad8b9ce`.
- Full clean local source-equivalent regression passed at `4b2c56c0` (map asset-fetch pretest skipped; existing assets reused); source contracts 313 and migration mirror PASS. Hosted CI included full setup. Emulator evidence artifact `12471178946`, SHA-256 `7d794e8f91cdfbdc90e0dea4b5ca7eae913f3f0522360c61808c816d78fbddb8`.
- Existing dirty POS/Native worktrees were preserved. No Production schema/data or authenticated sale changed. BLOCKED (release): authorized owner/cashier/staff journeys, physical phone/tablet/scanner/printer/drawer, and real-register offline/reconnect/retry evidence. Emulator success is not physical-device acceptance. Overall POS remains **NOT DONE**.
- **NEXT:** complete the authorized real cashier/device Release Gate and record evidence on these same successors. Do not restart from an older checkout or merge the divergent Native code into Web main.

## 2026-10-22 current head and next slice

- Current main `aeedb1df2ef4654ee68ed26b25b4f34a01e441de`: PR #584 merged after exact-head RC `39699366405` passed on `9e9202bf3b688a816eef41cd3fe2eacd0e852562`. Main RC `39699765279` and Pages deployment `39699764713` passed on the merge SHA.
- PR #585 POS offline recovery design merged at `d67f8dd8f79bcc991c447376cdd838988c816884`; exact RC `39698519445`, main RC `39698920070`, and Pages deployment `39698919956` all passed.
- Active next action: implement Phase 1 durable local cart drafts only. Submission needs an atomic/idempotent server RPC because standard `pos_edit_bill` is not idempotent.
- Current environment blocker: `skills.read` cannot load the required Supabase skill package. No schema/RPC code has been written. Reload the skill before any Postgres change; do not substitute a client-only unsafe multi-line submission. Cover scope isolation, reload/reopen, account switch, duplicate tabs, changed price/availability, and idempotent online submission. Do not create offline completed sales or touch Production schema/data.
- Release blockers remain: authorized cashier/device acceptance, full authenticated real-user journeys, Production migration approval/apply/readback, and deployed runtime acceptance. QueueGo/POS remain **NOT DONE**.

## 2026-10-21 POS scan continuation checkpoint

- **POS SKU/barcode registry and scan-to-bill — source/test complete locally, release BLOCKED.** Branch `queuego-pos-sku-scan` is based on the merged latest main and the current #568 POS successor tree; pre-change HEAD `a0b3109a3f8ae6970f2e865e0760b7e8ddef4e3d`. Verified latest main `643a115129b0bf1d6b05de261baa0aa959310a1b`; checked open PR #570 overlap, POS asset references unaffected.
- Added shop-unique product code table, guarded manager CRUD/cashier lookup RPCs, scanner UI and queued existing bill flow. Added server-validated menu option selection, price snapshot, total recalc and replay receipt. Migrations `20261021211800_pos_product_code_registry` and `20261021223000_pos_menu_option_pricing` are staged; neither is in Production. The option SQL test executes the existing Grocery Master stock trigger and verifies no double deduction on retry/price update.
- Verification: POS runtime 73, cash shifts 36, item sales 18, registry SQL/security 31, scan-to-bill UI 11, option pricing/security/replay 27; Security Definer review, source integrity and full `npm test` PASS on local source commit tree. CI pending publication. No Production mutation, cashier acceptance, or physical scanner certification.
- **NEXT:** publish exact branch and open a dependent PR against #568; local Git push currently cannot authenticate (`could not read Username for https://github.com`), so an authenticated GitHub write path is required. After publishing, run hosted CI; then review migration/security and apply through release gate; authenticated cashier scan→options→bill→kitchen→payment→receipt and physical scanner acceptance. Variant price/snapshot is now source complete. Next inventory audit: generic non-Grocery-Master stock tracking across POS/Delivery; keep Grocery Master on its existing reservation trigger. Keep cash-movement PR #568 acceptance separately open.

## 2026-10-21 North Star recovery checkpoint

- Initial verified `main` at this recovery checkpoint: `6669dc108137f8119c86d4de9709dacba7a301d7` (superseded by merged North Star docs commit `643a115129b0bf1d6b05de261baa0aa959310a1b`).
- This checkpoint adds `PRODUCT_NORTH_STAR.md`, `FEATURE_GAP_MATRIX.md`, `RELEASE_GATE.md`, and `NEXT.md`; these are the mission, evidence matrix, launch gate and precise continuation pointer. Older snapshots below are historical evidence; fetch current main and the relevant newest successor before resuming.
- **POS cash in/out shift ledger — source/test IN PROGRESS, release BLOCKED.** Open PR #568; branch `work-pos-complete-next-20261021`; exact HEAD `0f9af0cbc91af0bf7d964c9c52665ceb4e19cb9a`, based on the verified latest main. Prior RC run `39617058579` passed at `119c68e`; updated exact-head RC run `39619648922` completed SUCCESS. Focused POS tests (36/73/18) and full `npm test` passed on the updated tree in this session. Latest change adds transaction-scoped serialization for same-key retries.
- Production migration read-only check: history currently ends at `20261021133330_pos_owner_item_sales_report`; staged `20261021211709_pos_cash_register_movements` is absent. Do not present cash in/out as live. Review schema/ACL/concurrency and run the relevant release process before activation.
- Whole-platform status: NOT READY. Authenticated role E2E, current-device acceptance, printer/scanner/drawer validation, Android signing/Closed Testing, and other feature-specific gates remain separate; see `RELEASE_GATE.md`.
- NEXT: re-fetch main/open PRs and the newest POS descendant; inspect the staged migration against Production grants/RLS and concurrent retry behavior; then finish authorized cashier journey and migration gate. Continue other unblocked product rows by dependency without resetting or duplicating implementations.

## Dependency order

### 0. Work continuity / anti-repeat process
- Status: DONE
- Branch: `main`
- HEAD/commit: `9439569c15f72568b3398a1072f6d2285c8e7ce7`
- Evidence: `WORKFLOW.md` exists on main and defines startup, checkpoint, successor, release and interruption rules.
- NEXT: keep this checklist updated after every bounded chunk.

### 1. QueueGo Map / Buriram routing core / unified route authority
- Source/test status: DONE
- Release status: BLOCKED
- Active integration PR: #417
- Branch: `work-queuego-map-route-authority-unified-20261017`
- HEAD: `de5c206fb0798115ef1bff8052ddc22358a2d33f`
- Evidence:
  - QueueGo RC Tests run 39043592024: SUCCESS
  - QueueGo Map Engine Buriram run 39043592049: SUCCESS
- Release blockers:
  - Production/public hosting not approved
  - physical Buriram road/GPS certification not complete
  - Android full offline routing runtime not complete
- NEXT: do not claim launch-ready; continue unblocked dependent source work while these remain blocked.
- Superseded/ancestor examples: PR #379 and earlier Local Roads PRs are not restart points when #417 contains their successor integration.

### 2. Rider Web navigation / QueueGo sole route authority
- Source/test status: DONE
- Release status: BLOCKED
- Active PR: #413
- Branch: `work-web-rider-queuego-route-authority-20261017`
- Previous failing HEAD: `ece9fd795e5e965db9c1c3fbf4cafb27cad4f591`
- Current HEAD: `08e5af506468e08e5d815bc1d8bfc6e5e2b69611`
- Current CI: QueueGo RC run 39049849304: SUCCESS on `08e5af506468e08e5d815bc1d8bfc6e5e2b69611`; previous run 39043362341 failed on stale policy assertion
- Fix commits: `8a933b998fb3e4d6441ea99557ae8ba279272ead`, `08e5af506468e08e5d815bc1d8bfc6e5e2b69611`
- Root cause: stale regression assertion still requires Android navigation to force the Google Maps app package, conflicting with the newer QueueGo in-app navigation authority policy.
- Evidence: job 117427127897 fails with `Android navigation must force the Google Maps app package`.
- Release blocker: authorized QueueGo Map runtime plus physical road/GPS certification inherited from Map release gate.
- NEXT: no more source rework unless a newer successor/regression appears; do not reintroduce automatic Google Maps navigation.

- 2026-10-18 runtime blob verification: PR #413 and unified PR #417 have identical `rider/index.html` (`fd412052...`), `queuego-map-web.js` (`d48480fe...`) and `tests/rider-map.cjs` (`e9614e3b...`). #413 carries the newer corrected action regression test; no runtime merge/reimplementation is required.
- OWNER ACTION TO UNBLOCK RELEASE: approve/host the QueueGo Map runtime and provide physical Rider Web GPS/road evidence for the Buriram route/camera/reroute acceptance. Until then release remains BLOCKED despite source/test DONE.
- NEXT: no code work on Rider Web unless a new regression or newer runtime successor appears; continue to Passenger Ride source/test gate.

### 3. Rider Android navigation / QueueGo route authority
- Source/test status: DONE
- Release status: BLOCKED
- Active PR: #414
- Branch: `work-native-rider-queuego-route-authority-20261017`
- Current HEAD: `0360622b086ab4e1bd844cbe5310a7716d04dfa6`
- Recovery checkpoint lineage:
  - `297f9e4a918b8bdab86cc4abebb38d3176ab1842` restore current Grocery stylesheet
  - `735cdf215ffe0334839e31f00172116080dcc950` + `7826b019e89847d8cdeae17b3faa63fdd7a3d3cb` forward-port Passenger Ride production backend delta while retaining the newer env-only quote handler
  - `7d0aa2bdeee087685cc350996b70b88dc9abe8f7` restore current Grocery normalize helper
  - `992d117cb2ec88da0ad4e04cf90262596154a9c7` restore current Admin Grocery catalog asset
  - `55ea1afd4bad60c48e5524762039fdedbdff667b` restore current Customer Grocery catalog asset
  - `59a32dd7cfba0b540280ae085e1f5d0a0a79e5c8` add pinned Map asset generation before regression
  - `4bd4825d01f724882403e9a3514569d48b78bacb` lock Playwright runtime for Map validation
  - `d335a75d4a9e4825bda5c31cc61e6884802d3d49` restore current Merchant Grocery catalog asset; the immediately preceding successor also restored Merchant Grocery image preview
  - `9905f53f7f75cd2237f17271e842946c3406ff5e` restore credit-only Merchant GP retirement on current successor
- Established CI root causes and evidence:
  - Earlier source-integrity failures were caused by long-lived branch integration drift plus missing generated Map asset lifecycle setup. Unified #417 generates pinned/checksummed `map-assets` in `pretest`; #414 originally did not.
  - After `59a32dd...` and Playwright lock, source-integrity advanced to 94 scripts / 80 local references / 0 failures.
  - Exact-head run 39050648816 on `d335a75...` then failed at `Retired GP slip reporting must have no post-commit path`.
  - #414/current main had reintroduced legacy Merchant GP slip mutation/view paths after the credit-retirement successor; credit #332/#333 has zero `report_shop_gp`, `qgmSubmitGpSlip`, `settlements?on_conflict`, `qgmOpenGpSlip`, `data-gp-slip` and `gp-slips` runtime paths.
  - `9905f53...` forward-ports only the credit-retirement delta onto the latest #414 Merchant implementation: removes settlements write path, GP slip support link/viewer, GP slip submit RPC path and restores automatic-credit revenue copy.
- Verification before commit `9905f53...`: the same forbidden runtime contract used by `tests/legacy-gp-retirement.cjs` was checked and no forbidden Merchant GP mutation/view path remained.
- Current CI: QueueGo Map Engine Buriram run `39050895186` FAILED on exact HEAD `9905f53f7f75cd2237f17271e842946c3406ff5e`.
- First failure root cause (job `117449078394`): `tests/network-timeout.cjs` invoked `qtSupabaseAuth()` without loading its helper dependency `qtRequestTimeout` into the isolated VM. Merchant runtime already defined the helper. Fix commit `44487d60765cb821d867877369723332b5bc73b7` forward-ports the already-green main/unified test harness behavior that loads `qtRequestTimeout` when present.
- Main-sync safety: backup `backup/rider-route-authority-before-main-sync-20261017 @ 992d117cb2ec88da0ad4e04cf90262596154a9c7`; PR #420 main→#414 was conflict-only, never merged, then closed. No force/reset/revert used.
- Passenger Ride integration note: #414 is a descendant of #326; PR #387 later production-backend delta was forward-ported via its two new migrations while retaining the newer env-only quote handler.
- Latest exact-head failure checkpoint (2026-10-17): PR #414 HEAD `9905f53f7f75cd2237f17271e842946c3406ff5e`, QueueGo Map Engine Buriram run `39050895186` FAILED after source-integrity/realtime/post-commit/Rider checks had already passed.
- First actual failure: `tests/network-timeout.cjs` expected a timeout-style rejection but received `ReferenceError: qtRequestTimeout is not defined` from `qtSupabaseAuth`.
- Evidence-backed root cause: `tests/network-timeout.cjs` extracts/evaluates only the target request functions (`qtSupabaseAuth`, `qtSupabaseTable`, etc.) inside an isolated VM and does not load their helper dependency `qtRequestTimeout`. The actual Merchant runtime on exact HEAD does define `function qtRequestTimeout(ms)` before `qtSupabaseAuth`; therefore this CI failure is an incomplete isolated test harness scope, not a missing helper in live Merchant runtime.
- NEXT: in the next bounded chunk only, inspect the current HEAD definition/import scope for `qtRequestTimeout`, fix that single missing-scope/runtime issue, run the relevant test/CI, commit, then stop and report.
- Release blockers after source gate: Native Pilot on exact successor, physical GPS/road/navigation acceptance, QueueGo Map runtime/offline release gates.
- Superseded: PR #412 is an ancestor; do not restart there.

- Exact-head run `39051786664` on `bb837b4f14a478bba568933f00be9e7908ab6642` passed Merchant routing with `96 checks / 0 failures`, then failed first in `tests/rider-runtime.cjs` with `0 !== 1`.
- Root cause: the #414 Rider runtime regression harness was missing the current `syncRiderNavigationCameraMode:()=>false` VM stub already present in `main`/unified, so refresh-path execution diverged from the current runtime contract.
- Fix commit: `698d58f1cdb3ea4a3b0603bdba72b98875c1b263` forward-ports the current Rider runtime test harness only; Rider runtime implementation was not changed.
- NEXT: obtain exact-head CI for `698d58f1cdb3ea4a3b0603bdba72b98875c1b263`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39051907077` on `698d58f1cdb3ea4a3b0603bdba72b98875c1b263` passed Rider runtime, then failed first at the legacy PixelCopy snapshot memory assertion in `tests/native-three-app-stability.cjs`.
- Root cause: commit `38a3dcbb915d4e07bdcd3a5b3ef3c5cf8d3f3ab1` replaced the legacy renderer with MapLibre; continuity now retains the MapView/holder through `RiderNavigationMapRegistry` and no longer allocates PixelCopy snapshot bitmaps. Reintroducing the old snapshot path would revert the newer renderer architecture.
- Fix commit: `ef56ec4ee64a7bdba61c422ad09af62bb82dbd13` updates only the stability gate to require retained MapLibre lifecycle continuity and forbid PixelCopy/Bitmap snapshot allocation.
- NEXT: obtain exact-head CI for `ef56ec4ee64a7bdba61c422ad09af62bb82dbd13`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052159790` on `ef56ec4ee64a7bdba61c422ad09af62bb82dbd13` passed the retained MapLibre continuity gate, then failed first at the legacy bitmap-cache recycle assertion.
- Root cause: the assertion still described the retired renderer's `markerBitmaps`/`releaseBitmapCaches` lifecycle, while the MapLibre successor owns native marker/polyline refs and tears them down through `resetSurface()` + `MapView.onDestroy()` with no app-allocated bitmap caches.
- Fix commit: `fc83355fde1f87f4cfad96e2e4b5e312a576fb54` updates only that stability assertion to require animator cancellation, marker/route ref clearing, map/view release, and absence of legacy bitmap allocation.
- NEXT: obtain exact-head CI for `fc83355fde1f87f4cfad96e2e4b5e312a576fb54`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052279381` on `fc83355fde1f87f4cfad96e2e4b5e312a576fb54` passed the MapLibre dispose gate, then failed because `tests/native-three-app-stability.cjs` still tried to read removed `LongdoRiderMap.kt`.
- Root cause: MapLibre successor moved camera persistence into `RiderNavigationIntegration.kt`, where camera load/save is gated by `persistenceHydrated` and executed via `withContext(Dispatchers.IO)` against `SessionStore`.
- Fix commit: `9da1b59415740a2e63be33746394691a9f4f4ce1` updates only the first retired Longdo camera persistence gate to the current MapLibre integration path; the following legacy marker-cache assertion is intentionally left for the next exact-head failure if reached.
- NEXT: obtain exact-head CI for `9da1b59415740a2e63be33746394691a9f4f4ce1`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052425725` on `9da1b59415740a2e63be33746394691a9f4f4ce1` passed the MapLibre camera persistence gate, then failed first at the immediately following legacy Longdo marker-bitmap recycle assertion.
- Root cause: the stability test still required `clearPin()/markerBitmaps/stopBitmaps/bitmap.recycle()` from the retired Longdo renderer, while the MapLibre successor owns markers in the renderer and releases app references via `stopMarkers.clear()`, `demandMarkers.clear()`, `riderMarker = null` and `MapView.onDestroy()`.
- Fix commit: `d80f373aa016efba65c544cdb9bb2ff1a87ce7ec` updates only that memory-safety gate to forbid the deleted Longdo map/cache path and require MapLibre marker/view release.
- NEXT: obtain exact-head CI for `d80f373aa016efba65c544cdb9bb2ff1a87ce7ec`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head Native Pilot run `39052528115` on `d80f373aa016efba65c544cdb9bb2ff1a87ce7ec` passed native stability with `106 checks / 0 failures`, then failed first in `tests/native-release-evidence-bundler.cjs` because the generated `map-assets/` directory made the checkout dirty before gate-envelope validation.
- Root cause: #414 inherited Map unified `pretest` generation of pinned web assets but not the unified `.gitignore` rule `/map-assets/`; the evidence bundler correctly requires a clean source checkout and therefore stopped on generated, non-source files.
- Fix commit: `1b1692c2627e2ecb6cfabfd9f6b99f5f3986aec4` adds only `/map-assets/` to the existing #414 ignore rules, matching unified Map provenance behavior without weakening source cleanliness for other files.
- NEXT: obtain exact-head CI for `1b1692c2627e2ecb6cfabfd9f6b99f5f3986aec4`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052635608` on `d80f373aa016efba65c544cdb9bb2ff1a87ce7ec` passed native stability `106 checks / 0 failures`, then failed in `tests/native-release-evidence-bundler.cjs` because the checkout was dirty before the intended missing-gate assertion.
- Root cause: `npm test` pretest generates pinned `map-assets/` via `ops/maps/fetch_web_assets.py`, but #414 `.gitignore` did not ignore that generated directory; `finalize-native-release-evidence.py` correctly rejects any dirty source checkout.
- Fix commit: `1a580fbc7b6aa6aaac55a416354fa5fff0b4d632` ignores only generated `map-assets/`; release-evidence cleanliness enforcement remains unchanged.
- NEXT: obtain exact-head CI for `1a580fbc7b6aa6aaac55a416354fa5fff0b4d632`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052777401` on `1a580fbc7b6aa6aaac55a416354fa5fff0b4d632` passed Native release evidence bundler (`28 checks / 0 failures`) and then failed first because `tests/native-rider-route-bundle-parity.cjs` still read removed `LongdoRiderMap.kt`.
- Root cause: route-bundle stop planning moved to `RiderNavigationIntegration.riderNavigationStops()` under the QueueGo Map/MapLibre successor, while the parity test remained coupled to retired Longdo marker internals.
- Fix commit: `e8cf3f4df2fc72f7b0e76043afed08a16b512eef` updates only route-bundle parity assertions to the current QueueGo navigation stop-plan contract; runtime implementation was not changed.
- NEXT: obtain exact-head CI for `e8cf3f4df2fc72f7b0e76043afed08a16b512eef`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052949572` on `e8cf3f4df2fc72f7b0e76043afed08a16b512eef` passed Native Rider route-bundle parity, then failed first in `tests/rider-return-to-sender.cjs` because current `admin/index.html` no longer contained `qg_admin_delivery_return_cases_v2` / return actions.
- Root cause: Return-to-Sender backend/Rider contracts remained present, but later Admin history overwrote the Admin Ticket integration originally added by `e739722dcecd55de550d722bee444fdd946c3245` and upgraded to multi-stop v2 by `b1933b37b781739b552e209002ca363b09ffe90e`.
- Fix commit: `38b9e1c591693437509d776b61d0ac3e70ce4516` forward-ports only the historical Tickets/Return-action block from the v2 Admin commit into the current Admin file; newer Admin code outside that block is preserved.
- NEXT: obtain exact-head CI for `38b9e1c591693437509d776b61d0ac3e70ce4516`. If red, fix only the new first failure; if green, continue the #414 gate.

- After `e8cf3f4...`, concurrent successor commit `38b9e1c591693437509d776b61d0ac3e70ce4516` restored Admin multi-stop delivery return controls (`qg_admin_delivery_return_cases_v2`, `return_to_shop`, `resolve_no_return`). A subsequent overlapping patch accidentally duplicated only `qgAdminReturnAction`.
- Corrective commit: `e306bc6c5b9a6aabef941a13c54c8e55625727cc` removes the duplicate handler while preserving the newer Admin return-case implementation from `38b9e1c...`.
- NEXT: obtain exact-head CI for `e306bc6c5b9a6aabef941a13c54c8e55625727cc`; if red, read and fix only the new first failure. Do not reapply the return workflow from an older branch.

- Exact-head run `39053216685` on `38b9e1c591693437509d776b61d0ac3e70ce4516` passed Return-to-Sender, MapLibre runtime/module and optional Google Maps return overlay, then failed first in `tests/native-map-marker-design.cjs` because route-stop assertions still read `QueueGoRiderApp.kt`.
- Root cause: `riderNavigationStops()` and bundle ordering ownership moved to `RiderNavigationIntegration.kt`; the marker-design regression test still looked for those symbols in the app shell.
- Fix commit: `ef03f11a1d20363a8c45b8a3025437ae590d403c` updates only the test ownership path; Native runtime code is unchanged.
- NEXT: obtain exact-head CI for `ef03f11a1d20363a8c45b8a3025437ae590d403c`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39053349534` on `ef03f11a1d20363a8c45b8a3025437ae590d403c` passed marker parity and MapLibre runtime/module contracts, then failed first because `tests/native-android-source.cjs` still read removed `QueueGoLongdoMap.kt`.
- Root cause: Customer/Merchant shared location picking migrated to `QueueGoMap.kt` / `QgLocationPickerMap` backed by MapLibre; the source regression still referenced retired Longdo picker lifecycle strings.
- Fix commit: `478a7dfc9d973dc3a859bdc9b15f60b1da7576d8` updates only the shared picker lifecycle gate to current QueueGo MapLibre ownership/destroy semantics; native runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `478a7dfc9d973dc3a859bdc9b15f60b1da7576d8`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head successor `478a7dfc9d973dc3a859bdc9b15f60b1da7576d8` updates only `tests/native-android-source.cjs` to replace retired `QueueGoLongdoMap.kt` / `QgLongdoLocationPickerMap` ownership with the current shared `QueueGoMap.kt` / `QgLocationPickerMap` MapLibre lifecycle contract.
- Current exact-head CI: QueueGo Map Engine Buriram run `39053521816` is IN PROGRESS on `478a7dfc9d973dc3a859bdc9b15f60b1da7576d8`.
- NEXT: read the first completed result/failure from run `39053521816`. If red, fix only the new first failure. If green, mark this source gate passed and continue the next #414 gate.

- Exact-head run `39053722416` on `ebf65e2b717a55547b8f61a3f0080eb42733d9f3` advanced past the removed Rider map file path, then failed first at the stale `native Rider must include Longdo map SDK host` assertion.
- Root cause: current Rider host is `QueueGoRiderNavigationMap` backed by Gradle module `:rider-navigation-map` and MapLibre `android-sdk-opengl:13.5.3`; the test still required retired `RiderLongdoMap/MapGLSurfaceView/LongdoLayer` symbols.
- Fix commit: `93cd3ee2388ba63c4087a93bc7a9495cc2324bc8` updates only that host assertion; runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `93cd3ee2388ba63c4087a93bc7a9495cc2324bc8`; fix only the new first failure.

- Exact-head run `39053841854` on `93cd3ee2388ba63c4087a93bc7a9495cc2324bc8` passed the Rider MapLibre host gate, then failed first at a Customer manifest assertion still requiring `longdo.map.key`.
- Root cause: Customer location picking now uses QueueGo MapLibre and the manifest declares `queuego.map.service.base_url` plus Android location permissions; the Longdo Customer key is retired.
- Fix commit: `f6ee1afb93470a8685618044f4419c6edd40d7b3` updates only the Customer map manifest assertion to the current QueueGo Map service contract.
- NEXT: obtain exact-head CI for `f6ee1afb93470a8685618044f4419c6edd40d7b3`; fix only the new first failure.

- Exact-head run `39053930825` on `f6ee1afb93470a8685618044f4419c6edd40d7b3` passed the Customer QueueGo Map manifest gate, then failed first at a stale Rider GPS/pin assertion still requiring Longdo `setLocation/ldmap.removePin/pushPin` symbols.
- Root cause: GPS ownership moved to `RiderNavigationLocationService` (`FusedLocationProviderClient` -> `NavigationMapFix`) and marker ownership moved to MapLibre `RiderNavigationMap.kt` (`stopMarkers` / `riderMarker`).
- Fix commit: `d5fe917369260ed3f828f9072379adbad1f34bdf` updates only that GPS/marker source gate; runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `d5fe917369260ed3f828f9072379adbad1f34bdf`; fix only the new first failure.

- Exact-head run `39054055959` on `d5fe917369260ed3f828f9072379adbad1f34bdf` passed the Rider fused-GPS/MapLibre marker gate, then failed first because Merchant setup regression still required `QgLongdoLocationPickerMap`.
- Root cause: Merchant shop setup already uses shared QueueGo MapLibre `QgLocationPickerMap` while retaining the same GPS and save flow.
- Fix commit: `03e99642794cebb3c1dd3c4a5f94000d101ea866` updates only that Merchant picker assertion; runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `03e99642794cebb3c1dd3c4a5f94000d101ea866`; fix only the new first failure.

- Exact-head run `39054161358` on `03e99642794cebb3c1dd3c4a5f94000d101ea866` passed the Merchant QueueGo Map picker gate, then failed first at a Merchant manifest assertion still requiring `longdo.map.key`.
- Root cause: Merchant native setup now uses QueueGo MapLibre and the manifest declares `queuego.map.service.base_url` plus location permissions; Longdo Merchant key is retired.
- Fix commit: `b48df16cda889b5200fc1e12d7257e877cef7e14` updates only the Merchant map manifest assertion to the current QueueGo Map service contract.
- NEXT: obtain exact-head CI for `b48df16cda889b5200fc1e12d7257e877cef7e14`; fix only the new first failure.

- Exact-head run `39054253085` on `b48df16cda889b5200fc1e12d7257e877cef7e14` passed the Merchant QueueGo Map manifest gate, then failed first at a Rider lifecycle assertion still requiring Longdo `map_*_failed` diagnostics.
- Root cause: MapLibre successor uses disposal/lifecycle guards (`disposed`, view identity, guarded async callbacks, `MapView.onDestroy`) instead of the retired Longdo diagnostic/fallback model.
- Fix commit: `c9b9c420fdd20d78001cb028fd66b8f6d3beddc4` updates only that lifecycle safety assertion to the current MapLibre guard model; runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `c9b9c420fdd20d78001cb028fd66b8f6d3beddc4`; fix only the new first failure.

- Exact-head run `39054387407` on `c9b9c420fdd20d78001cb028fd66b8f6d3beddc4` passed the full native source gate (`272 checks / 0 failures`), then failed first at a Route Bundle numbering assertion that still read renderer-owned Longdo fields.
- Root cause: Route Bundle stop planning/numbering moved to `RiderNavigationIntegration.kt`; current logic preserves pickup-first ordering by `bundleSequence`, drop ordering by `dropRank`, and drop numbering offset by pickup count.
- Fix commit: `b0d4ad83ad27ccb15ed93cd00788de6ec9c8ed7d` updates only that Route Bundle numbering assertion to the current ownership path.
- NEXT: obtain exact-head CI for `b0d4ad83ad27ccb15ed93cd00788de6ec9c8ed7d`; fix only the new first failure.

- Exact-head run `39054514459` on `b0d4ad83ad27ccb15ed93cd00788de6ec9c8ed7d` passed native source (`272/0`), MapLibre marker/overlay, route-bundle and semantic marker contracts, then failed first because `tests/native-rider-road-display.cjs` still read removed `LongdoRiderMap.kt`.
- Root cause: Rider road-display test fixture still pointed at the retired Longdo renderer; current renderer is `rider-navigation-map/.../RiderNavigationMap.kt`.
- Fix commit: `1223300c6a130cb08f0a86b3d7f7534a2f22f783` changes only the renderer fixture path; downstream road-display assertions are intentionally left for exact-head CI one failure at a time.
- NEXT: obtain exact-head CI for `1223300c6a130cb08f0a86b3d7f7534a2f22f783`; fix only the new first failure.

- Exact-head run `39054636207` on `1223300c6a130cb08f0a86b3d7f7534a2f22f783` passed native source/marker/route-bundle contracts, then failed first because `tests/native-rider-road-display.cjs` still required legacy `roadDisplay.update()/pause()/dispose()` calls from the retired Longdo renderer.
- Root cause: current MapLibre route lifecycle is owned by `RiderNavigationMap.renderRoutes()` and releases renderer polylines through `removePolyline()` + `routeLines.clear()`; the old `RiderRouteDisplay.kt` remains legacy source but is no longer the active renderer owner.
- Fix commit: `b526021a3cb920d2588681cb0bdfd8de0f3d2cb1` updates only that lifecycle assertion to the active MapLibre renderer contract.
- NEXT: obtain exact-head CI for `b526021a3cb920d2588681cb0bdfd8de0f3d2cb1`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39054874086` on `b526021a3cb920d2588681cb0bdfd8de0f3d2cb1` passed native source `272/0`, Route Bundle and MapLibre marker gates, then failed first at `clearPin()` teardown assertions in `tests/native-rider-road-display.cjs`.
- Root cause: the remaining assertion still described Longdo pin cleanup after dispose; active MapLibre teardown uses `resetSurface()` to clear stop/demand markers, null the Rider marker, destroy the MapView and clear the view reference, with no `clearPin()` API.
- Fix commit: `08165c620acced0e0f1ab4f278eecaf355520b73` updates only that teardown assertion; runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `08165c620acced0e0f1ab4f278eecaf355520b73`; if red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39082580957` on `08165c620acced0e0f1ab4f278eecaf355520b73` passed road-display, voice, continuous-dispatch and cancellation-funding gates, then failed first because `tests/rider-planned-stops.cjs` sliced from removed `qgBundlePlannedStops()` and therefore never loaded current `qgBundleNextOrder()`.
- Root cause: Web Rider no longer owns the retired planned-stop function; current bundle selection is `qgBundleNextOrder()`, ordering pickup states by `route_bundle_sequence`, picked-up drops by `_bundleDropRank`, and filtering cancelled/completed orders.
- Fix commit: `827cf08bdd1a1ef8eddeaded7507413cc512c1bf` updates only that regression test to the current bundle selector contract; Rider runtime is unchanged.
- NEXT: obtain exact-head CI for `827cf08bdd1a1ef8eddeaded7507413cc512c1bf`; if red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39082702403` on `827cf08bdd1a1ef8eddeaded7507413cc512c1bf` passed current bundle selector `8/0`, then failed first in `tests/rider-saver-availability.cjs` because Admin had lost the dedicated Saver status/control DOM and scoped RPC handler.
- Root cause: historical commit `fefd09a19d53cb118bb07420b3e636c6f6f47a72` implemented the owner-directed Saver OFF-until-ready control using existing `feature.route_bundle` plus `queuego_admin_set_saver_enabled`; later Admin history overwrote that UI block and reverted to a generic platform-rule save that could also persist pricing.
- Fix commit: `1f745e0981315a0ef6fe1db22849a9297c9594d2` forward-ports only the scoped Saver UI/RPC block into the current Admin file and removes `feature.route_bundle` from the generic price-save payload; other current Admin code is preserved.
- NEXT: obtain exact-head CI for `1f745e0981315a0ef6fe1db22849a9297c9594d2`; if red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39082825269` on `1f745e0981315a0ef6fe1db22849a9297c9594d2` passed Saver `29/0`, Passenger Ride `93/0`, Credit ledger `59/0`, Credit Edge `77/0` and Credit Web `9/0`, then failed first in `tests/legacy-gp-retirement.cjs` because current Admin still contained legacy settlement persistence/review paths.
- Root cause: later Admin history had overwritten PR #332 credit-retirement integration: `credit.js`/`admin-credit` routing were absent while `qt_settlements`, GP slip review and `admin_review_gp` remained. Merchant and Native Merchant were already clean.
- Fix commit: `e8446b540566d012d83acf8520578bdbd4f16ce0` forward-ports the Admin-only retirement delta from PR #332 onto the current Admin file: loads credit center, removes settlement hydration/persistence and GP slip/review UI, redirects `admin-gp` to credit, preserves Saver and newer Return-to-Sender blocks.
- NEXT: obtain exact-head CI for `e8446b540566d012d83acf8520578bdbd4f16ce0`; if red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head QueueGo Map Engine Buriram run `39083016044` on `e8446b540566d012d83acf8520578bdbd4f16ce0`: SUCCESS. Dependencies/regression, pinned OSM/vector package, normalized database/routing tiles, real graph/restriction evidence, browser-with-external-requests-blocked, offline package/rollback, candidate offline container, loopback staging contract and release-gate report all completed successfully.
- Source/test conclusion: DONE for the current #414 successor. This does not certify physical GPS/road behavior, real-device navigation continuity, signed release or Production/public hosting.
- Release blockers: physical Rider device/GPS/road certification in Buriram, exact-device background/foreground navigation acceptance, and the parent QueueGo Map runtime/offline release gates.
- OWNER ACTION TO UNBLOCK RELEASE: install the certified Rider build for this exact successor on a real Android device and perform the recorded Buriram GPS/road/navigation acceptance route; provide physical evidence/logs. Production/public Map hosting remains separately gated.
- NEXT: continue to the next unblocked source/test workstream in dependency order; do not modify #414 again unless a newer successor or a new regression appears.

- Successor `43325ab6899b5d4dc08e67215abf7eadd03694ad` expanded Native Pilot triggers to shared Web sources. Exact-head Native Pilot run `39083523167` then failed first in `tests/native-release-gate.cjs` at `Native pilot CI must pin the observed Longdo SDK SHA-256`.
- Root cause: workflow commit `0b181932df2cd774b93ae8d533b81f10524193e6` intentionally removed the retired Longdo Android SDK download/SHA gate when Native Pilot switched to QueueGo MapLibre, but the release regression from 2026-10-10 still required the deleted Longdo artifact.
- Fix commit: `575a10a1e1d05e6f437dbfe7ca5002f0e9967d3a` updates only the release test: it requires exact active MapLibre `13.5.3`, requires QueueGo MapLibre display/lifecycle CI gates, and forbids reintroducing the retired Longdo SDK download/integrity path.
- NEXT: obtain exact-head Native Pilot and Map Engine results for `575a10a1e1d05e6f437dbfe7ca5002f0e9967d3a`; if red, fix only the new first failure. Do not resume Passenger Ride until #414 exact-head gates are green.

- Exact-head Native Pilot run `39083719573` on `575a10a1e1d05e6f437dbfe7ca5002f0e9967d3a` advanced past the MapLibre release-gate regression, then failed Kotlin compilation. The first chronological compile failure was `RiderNavigationMap.kt:465` because MapLibre `cameraPosition.target` is nullable and the code dereferenced `latitude/longitude` directly.
- Fix commit: `0360622b086ab4e1bd844cbe5310a7716d04dfa6` changes only `publishCamera()` to return when `camera.target` is null before constructing `NavigationMapPoint`. Later Customer/Merchant compile failures from the same run are intentionally deferred until the next exact-head first failure.
- NEXT: obtain exact-head Native Pilot and Map Engine results for `0360622b086ab4e1bd844cbe5310a7716d04dfa6`; if red, fix only the new first failure. Do not resume Passenger Ride until #414 exact-head gates are green.

### 4. Rider online / deployed-schema compatibility
- Source/test status: DONE on its scoped compatibility fix
- Release status: BLOCKED
- PR: #365
- Branch: `work-native-rider-online-production-compat-20261016`
- HEAD: `e2f643605124008b311c47ca3c43706bead7d990`
- Root cause already recorded in PR: deployed Production orders schema lacked optional `ride_distance_km`, causing initial Rider snapshot failure and disabled online switch.
- Release blocker: authenticated real-device online confirmation remains owner acceptance.
- NEXT: do not reimplement compatibility logic; physical authenticated verification when owner/device evidence is available.

### 5. Customer Android location/address runtime
- Status: BLOCKED
- PRs: #366/#367 ancestry
- Latest listed dedicated location-runtime head: `4d65d54b9aacdad74d139c80498d592edb7c9102`
- Remaining evidence: authenticated address/shop switching, process restoration, physical-device crash reproduction/acceptance.
- NEXT: no claim of device completion until physical evidence exists; avoid reopening older camera/location implementations unless a newer successor proves a regression.

### 6. Passenger Ride
- Source/backend status: DONE for scoped production-compatible backend
- Release status: IN PROGRESS / BLOCKED where physical evidence is required
- PR: #387
- Branch: `work-ride-production-enable-20261017`
- HEAD: `d460c7fed64438f1c9fdacae0c2ef5647405ad5f`
- Evidence: QueueGo RC run 39004653410: SUCCESS
- NEXT: verify newest descendant integration with current Rider navigation/dispatch; complete unblocked source integration before physical Customer↔Rider E2E. Physical trip/GPS/device evidence remains BLOCKED.

### 7. Continuous dispatch / multi-stop stack
- Status: IN PROGRESS
- PR: #325
- Branch: `work-native-rider-continuous-dispatch-20261015`
- HEAD: `42a789642bc04f02e99400653f54291ee12ce7fa`
- Known boundary: Saver remains OFF; live route-bundle cap remains 2; Passenger Ride production enablement and nonzero-fee paths have release dependencies.
- NEXT: determine whether a newer descendant already contains this work before any modification. Do not restart #325 if credit/Ride/native successors already descend from it.

### 8. Merchant/Rider credit, top-up and GP replacement
- Source/test status: DONE for staged source on current credit successor chain
- Release status: BLOCKED
- Successor chain: #331 -> #332 -> #333
- Latest listed head: PR #333 `b3d61be03f2150181dbdb6c253b25c785759a243`
- Evidence in PR chain: credit ledger/retirement/Admin promo tests and race gates pass.
- Release blockers: `docs/CREDIT-RELEASE-GATE.md`, reviewed migration rollout, live provider verification where paid topups are enabled, authenticated physical-device evidence, controlled Production activation.
- NEXT: inspect for newer descendant before edits; no Production financial activation without the gate.

### 9. Merchant Grocery catalog
- Web source status: DONE for deployed web-first 414-image/catalog work as separately validated.
- Native source/test status: DONE for scoped catalog port
- Release status: BLOCKED
- PR: #364
- Branch: `work-native-merchant-grocery-approved-web-20261016`
- HEAD: `95ef486d5c7d801c0b76cd82080c2549a4c4c57a`
- Release blockers: physical-device catalog acceptance and signed-release certification.
- NEXT: do not confuse 10,000-item source/test coverage with launch readiness; reconcile only through newer native successor if one exists.

### 10. Customer + Merchant Play Store release
- Status: BLOCKED
- PR: #384
- Branch: `work-native-playstore-customer-merchant-20261017`
- HEAD: `3dfb9e757121d13d9e9ea24e92f5d800e1eec7b4`
- Blockers: signed release AAB/signing, Play Console Closed Testing and physical acceptance.
- NEXT: source-side release readiness can be inspected, but publication cannot be marked DONE without signed/Play evidence.

### 11. Express
- Status: IN PROGRESS
- Discovery result: no `express` branch/commit and no Express/parcel/courier implementation string was found in the current Rider/Customer successor files inspected (`Models.kt`, `QueueGoApi.kt`, `QueueGoRiderApp.kt`, Customer app, Web index).
- Dependency: must branch from the newest green native successor that already contains Continuous Dispatch + Credit + Passenger Ride + QueueGo navigation. Current candidate is PR #414 after its exact-head CI turns green.
- NEXT: after #414 is green, create one Express successor from that exact HEAD and add the minimum real 1-pickup/1-drop backend/domain contract first; do not invent a second dispatcher.

### 12. Laundry
- Status: IN PROGRESS (release-line reconciliation)
- Older dedicated PR #141 exists, while newer Rider navigation/dispatch successors contain Laundry handling.
- NEXT: determine the newest descendant containing shop-managed Laundry rules before edits; treat #141 as historical unless proven otherwise.

## Physical / external blockers owner must eventually supply

These are not code-completion claims:
- real-device authenticated Rider online confirmation;
- physical GPS/road/navigation test in Buriram, including parallel roads/U-turn/reroute/background-foreground;
- authenticated Customer↔Rider Passenger Ride physical E2E;
- physical Merchant catalog acceptance;
- signing credentials/process and signed release AAB;
- Play Console Closed Testing;
- explicitly approved Production/public QueueGo Map hosting;
- controlled Production credit/payment cutover and provider verification where applicable.

## Superseded rule

Do not treat every open PR as active work. Before touching any older PR, compare ancestry against the newest relevant successor. If newer work contains or replaces it, record it as superseded and continue only from the newer HEAD.

## 2026-10-18 live Rider Web camera / one-photo repair
- Stream: owner-authorized website camera and single-proof correction, deployment branch `work-web-rider-camera-single-proof-20261018`, base current public main `ded5dab4dc663c389810c007764d92cbbb3ce369`. Bounded delta comes from newest strict source successor of73c458c5; no old PR reset/revert and no staged backend/service/cap changes are published for this repair.
- Evidence: owner road recording shows camera reversals and anchor drift. Source regression reproduces missing heading becoming0°/north. Current public main lacked newer direction/overlap/continuity and repeated off-route guards. Fix deploys those existing guards, preserves unknown optional GPS values, rejects stale/duplicate callbacks and shares marker/camera timing.
- Evidence: real production-script DOM before-fix test fails because cancelling retake clears the selected proof. Same-order reentry previously rebuilt the page; Web retained obsolete separate cash/collection screens. Fix makes one pickup photo+one action and one delivery photo+one completion action, with cash shown inline. Back/reopen/cancel/retry retains the File/preview/uploaded path; double tap/session scope guarded; logout releases proof memory. Backend proof requirement and audited RPCs remain unchanged.
- Local focused tests PASS: camera65, actual proof DOM34, GPS9, live-provider route/cache94, UI102, runtime83, source88 scripts/73 local refs. Full clean main-deployment-tree npm regression REQUIRED before publish. New tests are part of npm test. Native source already has one photo/one simple action/no slider and passed inherited contracts; not rebuilt/replaced.
- Build marker: `rider-20261018-camera-proof1`; cache-bust shared script `queuego-map-web.js?v=20261018-12`.
- STATUS: IN PROGRESS pending full test + Pages deploy. NEXT: commit tested Web checkpoint, full npm test, publish only bounded Web tree to latest main, inspect RC + Pages build, fetch public HTML/JS and verify exact build/one-proof/missing-heading guards. Do not call deployed until public bytes verify.
- Existing live route transport remains unchanged until separate QueueGo Map HTTPS release gate. This repair does not publish a nonexistent /map-api endpoint, enable Route Bundle3–5, change Production DB, create test orders or add a dispatcher/payment/proof system. Physical Safari/GPS/camera road acceptance remains BLOCKED until owner tests the new build; source DOM is not physical certification.

## 2026-10-18 Rider Web deployment VERIFIED
- Stream: camera course/GPS/puck + single-photo pickup/delivery. Runtime Web HEAD `f8986c1b8c9f9d86cf43c20fc1eded18b81f37e6`, tested tree `67b1058c075bfaccfda44ef1c3fab8f00f6a08fa`; Pages1432/run39129222693 SUCCESS; RC1869/run39129222944 SUCCESS; Chat Retention134/run39129222931 SUCCESS. Full local clean-tree npm test EXIT0 before publication.
- Public URL verified: https://chatchairins-source.github.io/QueuGo/rider/?v=rider-20261018-camera-proof1 . HTML returns the exact build tag, script20261018-12 and proof resume helper; obsolete cash-confirm function is absent. Public shared-camera JS bytes equal the tested file, SHA256 cef3140b63cbe3f8b332dfa2c49c79b7146d65f9c861366630e17d66628641d1.
- Exact production-script parity verified between live Web projection and source PR436 for proof workflow, GPS callback and speed/marker functions. Strict source branch `work-native-route-bundle-web-camera-20261018` at `d32fe66dd87e934133352e4e287805c6c4905996`; Native3413/run39129128696 and Map155/run39129128721 are its separate exact gates. Main and staged Native history are divergent; do not full-merge unrelated older main implementations over this strict successor. Preserve the bounded Web projection and both lineages.
- STATUS: SOURCE DONE / WEB CI DONE / WEB DEPLOYED. This documentation-only receipt changes no public runtime. Physical Safari camera/GPS acceptance remains BLOCKED until owner confirms the new build. No database, live cap, wallet, proof authority or staged service was activated.
- OWNER ACTION REQUIRED: on iPhone Safari open the versioned URL; during an authorized order take one pickup photo then press receive, and one delivery photo then press complete. Cancelling retake, back/reopen and retry must retain preview without recapture. Record exact build/order/device and screen recording; for GPS compare on a safe real route with observer. Do not substitute this source test for road certification.
- NEXT: inspect this docs-only receipt's automatic RC/Pages run; no further runtime fix is required unless real evidence shows a remaining fault. Continue staged source only from PR436/newest descendant after exact CI, never by deploying its gated backend to static Pages.

## 2026-10-18 bounded remaining-route HUD correction
- Camera route reuse must not freeze the distance/ETA card. Store existing authoritative route distance/time with its cache; read the accepted route match on each fresh GPS fix and render remaining road distance plus explicitly approximate ETA. Unknown, opposite, off-route or inaccurate fixes do not fabricate progress. Geometry, route authority, quote/pricing/payout and business states unchanged.
- Tests: production helper14/0, GPS9/0, proof DOM34/0, camera65/0 and existing Rider route/source suites PASS. Build rider-20261018-camera-proof2; one-photo fix retained. Full clean-tree regression REQUIRED before this runtime checkpoint is published.
- STATUS: IN PROGRESS until exact CI/public bytes verify. NEXT: publish tested bounded progress delta and verify build2 on the public Rider URL; physical road/camera acceptance remains separate.


## 2026-10-18 owner video 20:19: bold navigation route
- Stream: Rider Web/Android route visibility. Branch: main; verified parent HEAD `bad7dcbae235c86a7185efa7f7acabaf3fc25d5b`. Source parent exact Native3414/run39129868715 and Map156/run39129868717 SUCCESS; live parent RC1871/run39129856790 and Pages1434/run39129856588 SUCCESS. Local backup backup/route-stroke-before-20261018 preserves source parent.
- Evidence: owner real iPhone Safari video20:19 shows a thin red stroke on broad pale roads; existing Web uses6px with no casing, Native active8/future6. This bounded visual revision uses Web12px red/16px white casing and Android active12/future8 with4 wider white casing, same authoritative geometry and cleanup owner. Build rider-20261018-route-bold1. No route engine/camera/GPS/proof/backend/pricing/cap change.
- TEST: focused source Rider121/0, live Rider96/0, camera65/0, proof DOM34/0, progress14/0, source integrity PASS; Native road-display/navigation/marker/source contracts PASS. Runtime confirms shared casing/fill geometry and both clean up during reroute/dispose. Full clean-tree regression and exact successor CI REQUIRED.
- STATUS: IN PROGRESS. NEXT: run relevant/full checks, publish bounded Web for owner review and run exact successor Native/Map gates. Physical turn/GPS/camera and visual acceptance remain BLOCKED; see existing owner account/device/action/evidence table. Route cap remains2.


## 2026-10-18 route disappearance / puck alignment / direction arrows
- Stream: Rider Web and Android; branch main; verified parent HEAD `e8d3716910d08a804c978bc738ab9ec021f0aa70`, source Native3415/run39131732532 and Map157/run39131732523 SUCCESS, live RC1872/run39131720597 and Pages1435/run39131720132 SUCCESS. Backup backup/route-arrow-before-20261018.
- Real owner20:43 recording shows no active route initially, then a distance badge without visible red line. Actual runtime reproduction on parent fails: adding casing starts source loading; isStyleLoaded=false defers red fill to an already-fired load event. Fix existing line helper with owned style.load replay and immediate idempotent application; removal cancels pending replay. Direction chevrons use a local canvas glyph, no remote glyph/font dependency.
- Presentation trims only the displayed route to the interpolated puck; full authoritative road geometry/ETA/reroute remains unchanged. Web confidence-bounded road alignment requires known accuracy<=35m, match<=12m and no opposite heading; camera/puck use the same presentation point while raw GPS stays untouched. Native reuses existing signal/hybrid snap authority and trim helper; active route head moves with existing puck animator, white direction chevrons share route cleanup. No second engine/dispatcher/GPS service or backend/cap/proof/payment change. Build rider-20261018-route-arrow1 / map asset20261018-13.
- TEST: parent red-fill runtime FAIL reproduced. New actual renderer runtime22/0, camera65/0, Rider source121/live96, GPS9/0, progress14/0, one-photo DOM34/0 and Native source/navigation/road contracts PASS; full clean regression and exact successor CI REQUIRED.
- STATUS: IN PROGRESS. NEXT: full regression, publish tested Web first, verify public HTML/JS and exact source CI. BLOCKER: physical acceptance remains required. OWNER ACTION REQUIRED: approved Rider on real iPhone Safari/Android opens route-arrow1 during approved active route; observer records device/OS/build/time/order, route line at animated puck, direction through turns, parallel-road/off-route recovery and background/foreground. Do not fabricate a route without a destination or activate cap3-5.


## 2026-10-18 shared Rider heading follow
- Stream Rider Web/Android camera. Parent HEAD `b00c575794e4aa1b968da97596933f0e6532d293`; source branch work-native-route-bundle-web-camera-20261018 / live main. Previous route visibility/alignment/arrows checkpoint passed full local regression; its exact CI may be superseded by this requested camera successor.
- Owner requests camera follow Rider arrow. Existing Web camera uses road look-ahead yaw while puck smooths GPS separately. Use one cached smoothed heading per accepted fix for camera and puck; marker rotation interpolates the same shortest angle/easing. Gesture/overview ownership, unknown-heading and opposite-road guards preserved. Native FOLLOW explicitly prefers existing filtered signal/puck course while retaining fallback/quality gates and existing camera policy; added actual Kotlin policy test. Raw GPS/route authority unchanged. Build route-arrow2 / map asset20261018-14.
- TEST: renderer runtime23/0, camera65/0, GPS9/0, Rider source121/live96, proof34/0 and Native navigation/road/source contracts PASS; new Kotlin shared-course case requires CI. Full clean regression/exact HEAD Native/Map/RC/Pages REQUIRED. STATUS IN PROGRESS. NEXT publish tested successor and verify public bytes plus exact CI; physical road/heading/parallel-road/foreground acceptance remains BLOCKED using owner action table above.


## 2026-10-18 completed/multipart route overlay cleanup
- Stream Rider Web renderer; branch main, parent HEAD 2f778b6f85e312d8bea07ea94c2860d5dc76a9e5. Parent full local regression PASS; public arrow2 HTML/JS byte-match verified; RC1874/run39134784093 and Pages1437/run39134784706 SUCCESS. Native3417/run39134801387 and Map159/run39134801473 were still pending at review.
- Actual helper replay at the route endpoint reproduces a stale red/casing tail: zero remaining lines skipped the update loop without deleting owned layers. Multipart render copies could also leave obsolete parts. Reconcile exact owned IDs on each valid render, delete exhausted parts, retain only the matched remainder and subsequent authoritative parts. Only first part is anchored to the animated puck; later parts stay untouched. No route/heading/financial/GPS authority change.
- TEST: endpoint before-fix assertion FAIL (layer remains true); renderer now29/0 incl exhausted cleanup, no jitter regrowth and multipart retention. Camera/Rider/GPS/proof + full regression and exact successor CI REQUIRED. STATUS IN PROGRESS. Build route-arrow3 / asset20261018-15. NEXT publish tested bounded renderer repair and follow exact current CI; physical acceptance still BLOCKED.


## 2026-10-19 public Web places projection
- Stream business places Web; live main parentf836f05da7a3aa5465f9648e7d83997e32fe406b, source strict successor PR437/data29f135a3. Pinned real OSM catalog3079 records/1284 business-service categories, Oct15 snapshot, original identity/source/ODbL/known metadata retained. No imported business becomes a merchant or orderable.
- Project only shared Web helper, exact catalog, picker/search hooks and existing Map-view local-first search/nearby onto current live Web. Existing live route transport and newer camera/proof fixes retained; do not copy staged backend/hosting configuration.
- TEST catalog24643/search13/DOM7/camera65/lifecycle28 PASS; full clean regression and exact main RC/Pages REQUIRED. STATUS IN PROGRESS. NEXT publish tested Web and verify local search/place dots/labels/details; then continue native shared asset/picker. Physical entrance/contact freshness remains BLOCKED, no Production DB/provider/cap activation.

## 2026-10-19 Rider Home Web review projection
- Based on current public main4b3915dec74b20c4e592b92f4a1e0c0cb3630873, preserving current route transport and all newer real-place assets. UI-only projection of source PR438: one Ready switch, no top duplicate/large buttons/stats, calmer readable profile/settings rows, normal idle camera, red existing puck unchanged.
- Switch uses existing rider_profiles.metadata online/available; writes serialized/acknowledged/session-fenced, late polling responses fenced after writes. No approval bypass or new availability schema; existing active-work restriction retained.
- Full TZ=Asia/Bangkok npm pre/test/post exit0. Rider runtime83/map96 and actual projected Home DOM27 PASS (isolated, no live account/order writes). Build rider-20261019-ready1. Native candidate/build evidence tracked separately in PR438.
- Web review deployment requested by owner. Physical/authenticated switch/dispatch/reconnect acceptance, screenshots and Web preference parity remain pending; no release/DONE claim. No staged Native backend/schema/config/financial deployment.

## 2026-10-19 Rider Web My Destination settings review
- Current main baseline e37dc61cd37ec6f24460d69f12dd17b8d2ba72e3; source successor187ac19d0f7acdd7e3633fda8831bba77930dd90/tree536479b995bdaf7e4157e1e7e9e0615e3cc5c960. Existing Home Ready card, red marker and live routing transport preserved.
- Profile Settings adds My Destination via existing installed destination read/write RPCs and shared map/search/GPS helpers. Saved preferences load from backend; only matching acknowledgement or confirmed lost-response reconciliation shows saved. No new backend/schema/availability/dispatch source or real account/order write in QA.
- Actual Settings DOM36 PASS covers read/write/duplicate-submit/late response/account fencing/invalid coordinates/GPS disposal. Full TZ=Asia/Bangkok npm pre/test/post EXIT0 on Web projection; same source full suite EXIT0. Production read-only ACL audit confirms anon execute denied, authenticated wrappers call existing live-session/active-Rider guards.
- Native9e actual three APK/JVM/16KB/full source and five SQL races PASS; containing runtime final result still required. Public Web review activation is this bounded projection only, with existing map transport retained. Authenticated destination save/reopen and phone visual/GPS checks remain BLOCKED by missing usable Rider browser session/physical device; no credential bypass or fabricated Home screenshot.


## 2026-10-19 — Market Master Catalog successor v2 (PR #441)
- Branch: `work-market-master-catalog-successor-v2-20261019`
- Base: latest main `fa0b7ac941311772b24af8af6565dde7ed9a3f11`; PR #440 is superseded and closed
- Recovered the existing Market catalog work from `work-market-master-catalog-20261015` onto a clean descendant of current main; the stale branch itself was not reused.
- Added source-backed Thai Market master identities, authenticated Merchant catalog browse/add flow, idempotent add receipts, GP-derived Delivery preview, separate manual add, and pending-Merchant access for catalog preparation.
- Staged new migration as `20261019114500_market_master_catalog.sql`; no Production apply in this checkpoint.
- Source contract gate: `tests/market-master-catalog.cjs`; Merchant DOM flow gate: `tests/market-master-catalog-ui.cjs` (browse/category/image-status/1-2-3 kg/add/reload/no double add); exact-head full CI still required.

- Fresh-source expansion: added 33 current public product facts from Makro PRO and Lotus's covering pork, chicken, duck, beef, mushrooms and common vegetables. Retail prices are intentionally not imported.
- Weight-selling UX: catalog items sold by kilogram now expose 1 kg / 2 kg / 3 kg quick presets while keeping custom quantity input.

- Market catalog source coverage now: 25 starter + 33 fresh Makro/Lotus + 70 seafood/fruit/veg = 128 sourced catalog identities.
- Image-review coverage: 128/128 items have a source/review record; 121 have verified direct product-image candidates, 7 retain verified product/source pages pending an exact reusable image, and 0 remain family-reference-only. Approved Production images remain 0 until reuse-rights evidence is documented; no retailer art is published as QueueGo-owned.
- Added sourced กุ้งแชบ๊วย and ปูทะเล variants; phase-2 migration `20261019150000_market_phase2_chaebow_mudcrab.sql` stages facts only, no retail price/stock.
- Code checkpoint before this checklist update: `a5c758e4174d5d9587f0629e5948c3b3b03708d3`. Fixed the first exact-CI source-contract failure (invalid decimal slug), retained successful catalog-add authoritative reload/no-double-add behavior, and continued exact image-candidate verification.
- NEXT: exact-head CI on this successor; fix the first failure only. Continue resolving the 7 remaining source-page-only images, then authenticated/physical Merchant Web acceptance before any Production apply.



## 2026-10-19 — Market Catalog image/source checkpoint (PR #441)
- Branch: `work-market-master-catalog-successor-v2-20261019`; current code checkpoint before this checklist update: `945bbe4ad54e698786b10777c516a8c4862cf5a6`; base main: `fa0b7ac941311772b24af8af6565dde7ed9a3f11`.
- Catalog coverage: 128/128 sourced identities with direct image evidence. Approved open-license/public-domain images: 28; retailer/direct candidates still rights-pending: 100; source-page-only: 0; family-reference-only: 0.
- Approved batches now include commodity staples plus exact Nam Dok Mai mango, Red Lady papaya, Monthong durian and Shine Muscat; Merchant renders approved image attribution and never renders rights-pending retailer art as approved.
- Phase 2 remains 70 seafood/fruit/vegetable identities (ปลา 13, กุ้ง 8, ปู 7, หอย 9, ปลาหมึก 4, ผลไม้ 16, ผักสด 13); 1/2/3 kg presets + custom quantity retained. No Production migration/apply performed.
- TEST: exact-head RC for `945bbe4...` pending GitHub scheduling. NEXT: if RC fails, fix first exact failure only; if green, continue open-license replacements for the remaining 100 rights-pending images and authenticated/physical Merchant Web acceptance before Production.


## 2026-10-19 — Market Catalog image batch 9 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 9 test checkpoint: `753adb4fe41bf3fb89caf30f0695fbd92b6b1939`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased from 33 to 46, rights-pending retailer candidates decreased from 95 to 82; source-page-only 0, family-reference-only 0.
- Batch 9 adds cabbage (2 catalog identities), pork belly, raw chicken drumstick, eggs (2 sizes), enoki (2 identities), dried shiitake (2 sizes), tomato, long bean and mackerel using Commons licensing/attribution evidence.
- Staged migration: `20261019174500_market_open_license_images_batch9.sql`; no Production apply. Merchant approved-image attribution rendering remains required.
- NEXT: exact-head RC; fix first failure only. If green, continue open-license replacements for the remaining 82 rights-pending images, then authenticated/physical Merchant Web acceptance before Production.


## 2026-10-19 — Market Catalog image batch 10 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 10 test checkpoint: `d51d1bce8bcb2f6a0c4591870c8d6318caf773c7`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 54, rights-pending retailer candidates reduced to 74; source-page-only 0, family-reference-only 0.
- Batch 10 approves choy sum (2 identities), peeled garlic, banana bunch, watermelon, orange, pineapple and dragon fruit with Commons source/license evidence.
- Staged migration: `20261019180000_market_open_license_images_batch10.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue open-license replacements for the remaining 74 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 11 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 11 test checkpoint: `17042a52251c48f9850cf6d562df018fd6f1a1f8`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 59, rights-pending retailer candidates reduced to 69; source-page-only 0, family-reference-only 0.
- Batch 11 approves fresh shiitake, white shimeji, beef shank, pork loin and fresh lemongrass with Commons source/license evidence.
- Staged migration: `20261019181500_market_open_license_images_batch11.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue open-license replacements for the remaining 69 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 12 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 12 test checkpoint: `7bcd9b3f44c0a02927e5f3f2780de3ff3dca44dc`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 64, rights-pending retailer candidates reduced to 64; source-page-only 0, family-reference-only 0.
- Batch 12 approves Nile tilapia, snakehead, giant river prawn, whole barramundi/seabass and blood cockle using verified Commons license evidence.
- Staged migration: `20261019183000_market_open_license_images_batch12.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue open-license replacements for the remaining 64 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 13 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 13 test checkpoint: `3e717b0122b387910eb05ce1f991efa202e89ea4`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 67, rights-pending retailer candidates reduced to 61; source-page-only 0, family-reference-only 0.
- Batch 13 approves raw pork shoulder, raw bone-in beef short ribs and raw beef round/top-round for the matching shoulder/rib/hip catalog identities; cooked pork-shoulder/tenderloin candidates were explicitly rejected as misleading.
- Staged migration: `20261019184500_market_open_license_images_batch13.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue exact raw/open-license replacements for the remaining 61 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 14 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 14 test checkpoint: `dea3647634cf21b65e882b6d25c3aac653094e76`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 75, rights-pending retailer candidates reduced to 53; source-page-only 0, family-reference-only 0.
- Batch 14 approves kale (2 catalog identities), celery, half pumpkin, key lime, lychee, rambutan and cantaloupe using verified Commons licensing/attribution evidence.
- Staged migration: `20261019190000_market_open_license_images_batch14.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue exact open-license replacements for the remaining 53 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 15 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 15 test checkpoint: `5c8881a28733d35d4cda77b84f9b415a4c92be86`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 81, rights-pending retailer candidates reduced to 47; source-page-only 0, family-reference-only 0.
- Batch 15 approves king oyster mushroom, whole king mackerel, whole white shrimp, frozen squid rings, chilled squid tentacles and whole blue crab using verified Commons license/attribution evidence.
- Staged migration: `20261019191500_market_open_license_images_batch15.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue exact open-license replacements for the remaining 47 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 16 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 16 test checkpoint: `069e3c3eb61b5fcb96d85f91a030cf7462f8cd66`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 84, rights-pending retailer candidates reduced to 44; source-page-only 0, family-reference-only 0.
- Batch 16 approves raw chicken leg quarter, sliced catfish and raw soft-shell crab on ice with verified Commons license/attribution evidence.
- Staged migration: `20261019193000_market_open_license_images_batch16.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue exact open-license replacements for the remaining 44 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 17 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 17 test checkpoint: `1e6dbe80898e6b9b581dc6401622f09c4abe23d1`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 88, rights-pending retailer candidates reduced to 40; source-page-only 0, family-reference-only 0.
- Batch 17 approves dried bamboo shoots, fresh tofu skin, textured vegetable protein and peeled ginkgo nuts using verified Commons licensing/attribution evidence.
- Staged migration: `20261019194500_market_open_license_images_batch17.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue exact open-license replacements for the remaining 40 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market image add-form verification
- IN PROGRESS; branch `work-market-master-catalog-successor-v2-20261019`; base `c9c7c040917867e86978e975dcf7ba87ec3a8ddc`; local source/test commit `6cfb031b3214f717c4b5c046dccea35b91dbef85`.
- Reused image/credit/status renderer for list and add form, accessible broken-image feedback; versioned assets. Market data and DOM tests now execute in npm test/RC.
- TEST: full npm test EXIT0 on pre-batch17 UI tree; focused source integrity PASS (90 scripts/77 refs), catalog contracts PASS, DOM 29 checks PASS. Exact successor CI required.
- Counts inherited from batch17: 128 items; 88 approved images; 40 rights-pending direct candidates. No Production apply or real Merchant save certified.
- NEXT: exact successor CI; remaining image rights, shared manual editor weight presets and authenticated Merchant Web acceptance.


## 2026-10-19 — Shared manual/catalog Market weight presets
- IN PROGRESS; branch `work-market-master-catalog-successor-v2-20261019`; published predecessor `840771cdb6b9b23b1665800affd4559930bc9963`; local tested source `53097a3e`.
- One shared implementation serves manual add/edit and catalog add: 1/2/3kg updates unit, pack and kg weight; custom quantities remain editable, gram/hectogram packs synchronize kg weight. Asset versions updated together. Existing add receipts and source image rights guards preserved.
- TEST: full npm test EXIT0 on integrated batch17 source plus weight changes; focused DOM 42 checks PASS, catalog data/image/category/access contracts PASS, source integrity PASS. Exact successor CI still required.
- Catalog: 128 sourced identities; 88 approved images; 40 rights-pending direct candidates; Phase2 70 identities retained. No Production apply, authenticated Merchant save or physical-device certification claimed.
- NEXT: exact successor CI, remaining image rights; authenticated Merchant browse/filter/add/custom weight/shop-list acceptance before release. No need to repeat already-tested catalog/category implementation.


## 2026-10-19 — Actual SQL Market release gate
- IN PROGRESS; branch `work-market-master-catalog-successor-v2-20261019`; predecessor `83b7bac11b3478ad19d496acf774fc2d5c206f10` passed exact RC2008/run39233210924, including mobile WebKit. Local source commit `3336a6d1`.
- Actual migration execution found malformed rambutan slug in the staged phase2 seed. Production migration history confirms only Grocery grant `20261019051041` applied today, not these Market migrations. Corrected the unapplied seed to the canonical JSON/image slug; no applied history edited.
- Actual live read-only function audit confirms POS helpers exclude pending shops. New scoped owner-preparation helper reused by existing Market catalog page/add/manual-save functions allows only owned pending/active nonarchived Market/Grocery profiles; POS, approval, public shop visibility, checkout and financial triggers are unchanged. Owner-only stock/movement read policies enable pending-shop readback.
- TEST: full npm regression EXIT0; actual isolated SQL migrations + pending browse/add/readback/receipt replay/body mismatch/no duplicate/stock movement once/staff+archived+suspended+anon denial PASS19; Merchant DOM PASS42; source/data/image/category contracts PASS. SQL gate now included in npm test/RC. No Production writes.
- Counts unchanged: 128 sourced products, 88 approved images, 40 rights-pending. Browser binary download in this host returned truncated archives; use CI WebKit and do not claim signed-in live Merchant acceptance from isolated tests.
- NEXT: exact successor CI then release-gate review, remaining40 image rights and authenticated Merchant acceptance. No Production apply until exact successor passes.


## 2026-10-19 — Market catalog exact-code release verification
- Source/test status: DONE for code `e37d3546aff1a1f02f696a165ce8ba4967c95b8a`; exact RC2009/run39234168435 SUCCESS, every step including npm regression, complete Merchant mobile WebKit boot and visual component gates passed.
- Branch: `work-market-master-catalog-successor-v2-20261019`; 128 sourced products, 88 approved images, 40 explicitly rights-pending direct candidates. No source/image/category/preset implementation restart is needed.
- Release status: BLOCKED by authenticated actual Merchant add/shop-list acceptance. Live browser inspection at the public Merchant URL showed the login form, with no usable signed-in Merchant session. Real merchant-selected price/cost/stock must be used for a live save; no fabricated Production fixture or account bypass is permitted.
- Remaining rights work: obtain documented retailer reuse permission or an exact open-license image for the 40 pending rows. Current fallback/status is intentional and approved-image rendering is covered by DOM tests; do not relabel these rows as approved merely to clear the count.
- Production remains unchanged: Market catalog tables do not yet exist; read-only migration history confirms staged Market files have not been applied. No merge, Production migration, real product mutation or physical-device acceptance is claimed.
- NEXT: authenticated Merchant acceptance on an authorized review environment, then apply the staged files in order and verify counts/ownership/public asset versions before public activation. Use secure browser authentication if the owner elects to supply a test session; never request secrets in chat. This is a concrete release dependency, not a new coding permission request.


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

## 2026-10-19 — Image batch18 review checkpoint
- Branch: work-market-image-rights-20261019; base HEAD 76f472a3b9da9e679b8a4bb8eedc520c1e30272a. Pre-edit checkpoint branch backup/market-images-before-batch18-20261019 at the same SHA.
- Added CC0 raw whole-duck photo after visual review and license verification; generic whole-duck only. CP S/M/XL remain pending. Rejected Commons Tenderloin_packages.jpg for pork: source categorizes it as beef. Cooked pork tenderloin photos also excluded.
- Staged catalog128 / approved89 / pending39; production remains128 / 88 / 40 until this new migration is activated. No new products, prices, stock, weights, schema or add-flow implementation.
- SQL integration now checks every manifest status and every approved image/source/license against actual migrated rows, replacing fixed approved88 assertion. Validation PASS: full npm test EXIT0; Merchant DOM45; actual SQL236; source/category/image contracts128; git diff --check clean.
- NEXT: full npm regression, publish exact commit/PR, CI; then apply only new batch18 migration and verify count/readback.

## 2026-10-19 — Batch18 activated; batch19 pork review checkpoint
- Branch work-market-image-rights-20261019; verified remote source d2e96ee1b1ac7312404f9c77a735a3b41a711428; merged main 2cf31f6883e1e6eac01d30770f6d149d28b3e5b5. Exact source RC2015/run39240924481 SUCCESS including mobile WebKit. Git-fetched remote tree matches locally tested source byte-for-byte.
- Batch18 real SQL transaction rehearsal/rollback PASS, then new migration activation PASS with original version receipt recorded. Production readback128 total /89 approved /39 pending. Whole-duck prior image null/status pending; prior retailer image source retained in original source migrations. No product/stock/price writes.
- Batch19: reviewed original Julo raw pork tenderloin JPEG2048×1536 and Commons public-domain grant. Approve exact tenderloin and generic lean-pork commodity only; reject beef/cooked substitutes. Pre-edit backup branch backup/market-images-before-batch19-20261019. Staged128 /91 approved /37 pending; production still89/39.
- New authenticated read-only REST recheck unavailable: previous private temporary QA session file is absent in current runtime. Do not claim this recheck passed; earlier authorized live add-flow evidence remains recorded above. No replacement account or products created in this batch.
- Validation PASS: source integrity, catalog128/image91/37 contracts, SQL238, Merchant DOM45, diff whitespace. NEXT: publish batch19 exact HEAD for full CI, then activate only new batch19 migration.

## 2026-10-19 — Image batches18/19 live verification
- Main source HEAD ec765be8366d845e672d60d71eaa316061801c30; merged PR443 exact source 3e4b767c6499547f242aa946883bcb7e3efaa792. Exact source RC2017/run39241481156 SUCCESS (full npm and mobile WebKit). Prior batch18 main RC39241254291 and Pages39241254137 SUCCESS. Both published source trees verified identical to local test commits via git fetch/diff.
- Activated only two new forward image migrations after real transaction rehearsals/rollback; original versions20261019201500 and20261019203000 recorded to prevent replay. Production readback128 total /91 approved /37 pending. Generic whole duck, raw pork tenderloin and lean pork gain licensed commodity illustrations; source product facts retained, no brand/size/pack/weight claims added.
- Local validation: source integrity90 scripts/77 references; full npm PASS for batch18; focused batch19 source/image/category contracts128, SQL238, Merchant DOM45 PASS; complete full regression and WebKit PASS in exact-source CI. No price, stock, order, payment, shop approval or public-sale changes.
- Live owner-scoped RPC rechecks PASS under existing pending test-shop identity via transaction-local authenticated SQL role/claims: whole-duck and pork tenderloin return approved image, source and credit; chicken tenderloin remains pending with no leaked retailer art. This is live SQL RPC permission/readback evidence, not a new signed-in-browser or REST authentication claim; private temporary QA session was unavailable. Earlier full add/idempotency acceptance remains recorded above.
- NEXT:37 exact image/rights replacements remain; keep their explicit pending placeholders. Full signed-in physical-device appearance is not newly certified. Do not duplicate catalog/add/weight/GP logic or replay already applied migrations.

## 2026-10-19 — Image batch20 species review checkpoint
- Branch work-market-image-review-20261019-v3; base HEAD7959ede06024059244b08380999c90f7ccf221ff, latest main descendant after merged PR443; no newer Market successor found. Pre-edit checkpoint backup/market-images-before-batch20-20261019. Latest main RC39241958948 and Pages39241958648 SUCCESS.
- Visually reviewed original raw whole banana prawn and whole Sillago sihama images; reuse existing prawn license and verify fish CC BY3.0. Department of Fisheries page45 confirms Thai ทรายขาว species name. Pictures illustrate commodity/species; retailer count/weight facts remain unchanged. Reject empty mixed-shell Bivalvia.jpg for fresh clam.
- CLI generated new batch20 migration, then renamed only new unapplied file to20261019204500 so it sorts after main's20261019203000. Staged128 total /93 approved /35 pending; Production remains91/37 until verified activation. No schema, price, stock, add flow or weight logic changes.
- Validation PASS: source integrity90 scripts/77 references, source/category/image contracts128, SQL240, Merchant DOM45, diff whitespace. CLI generated file successfully, but automatic approval review rejected subsequent PostHog telemetry with unknown payload; no retry or telemetry bypass. NEXT: commit/exact-source full CI; transaction rehearsal then activate only batch20 through established connector.

## 2026-10-19 — Image batch20 live verified
- Main source HEAD f630086a3d229ea8a1f5c3352dd6d10a0a4763f1; PR444 exact source20829ca22b48cc117f876bb2cccda5db77cea265. RC2020/run39247512452 SUCCESS, including full npm and all mobile WebKit gates. Git-fetched remote tree matches locally tested source e64f8d49.
- Transaction rehearsal/rollback PASS; batch20 applied through established connector with original version20261019204500 receipt. Live readback128 total /93 approved /35 pending. Owner-scoped catalog RPC under existing pending QA identity returns both new approved images, Commons sources and credits correctly. No price, stock, unit/weight, order, approval or public-sale writes.
- CHECKLIST and project memory updated; all128 retain direct reference or approved image state. Signed-in physical-device appearance remains unclaimed; this session's browser has no signed-in tab. No credentials, new account or extra fixture products created.
- NEXT:35 exact image/rights replacements remain. Unapproved research leads (not visually reviewed, do not auto-approve): Commons File:Pure_squid_meat.jpg (CC0; check whole peeled squid versus cut meat) and File:Oysters_at_LFS_Fish_Market_2013_(2).JPG (CC BY-SA3.0; check fully shucked meat versus half-shell). Mixed empty-shell Bivalvia.jpg rejected. CLI telemetry remains rejected; do not retry its PostHog egress.

## 2026-10-20 Admin overhaul — checkpoint 1 (not mission complete)
- CURRENT BASE: b8b9f9883b50d7da3efbd0d37d1e0080bc4b158e; backup/admin-before-overhaul-20261020. Working HEAD: see this checkpoint commit. Main fetched; PR445 already merged. Divergent fix-admin branch repeats the same fast-path concept and is not a newer main descendant. Native/settings successor38baf74c remains separate staged source, not silently merged into Web.
- IMPLEMENTED: Admin hydration dispatches by visible section; overview HEAD counts only; directory coalescing/30s cache; Users joins directory fast path; Audit100; Notifications100; no hidden Rider5s poll; fallback poll throttled60s. Existing RLS/RPC authority unchanged. No Production mutation.
- TEST: full baseline-change npm suite EXIT0 before adding runtime test; executed section runtime19 PASS. Final exact-source npm and CI required. WebKit downloaded but local launch lacks shared libraries; normal dependency install failed setgroups/seteuid. Added whole-document mobile WebKit before/after request/timing gate to existing RC runner (which already installs dependencies).
- REMAINING: scoped directory pagination/summary RPC optimization; broad realtime table subscriptions; full permission inventory and missing operation controls; staged Credit/Ride/Express dependencies; hardened existing mutations/atomic audit; authenticated database permission evidence; exact-source CI/merge/Pages/live readback. No request reduction number or real latency claim until measured.
- BLOCKED: local WebKit runtime dependencies; physical iPhone and live authenticated operation verification not yet available. Source/UI/test work continues.
- NEXT: finish inventory and controls on same successor, run exact CI, repair from its first failure, merge only green Web then verify Pages. Do not mark full Admin mission DONE.

## 2026-10-20 Admin checkpoint 2 — scoped sockets, bounded directory and existing operational surfaces
- BASE:47ead7ca (PR457 checkpoint1). Preserves mainb8b9f988 and existing PR445 changes.
- IMPLEMENTED: account keyset pages200/201 sentinel, profiles restricted to page IDs; realtime visible-section subscriptions with channel retirement; short coalesced GET cache; initial verification shell; real audit reader; existing Credit and Ride modules reused from newest inspected38baf74c, with actor/status fencing; separate Dispatch/Express/Laundry/System and Rider review pages using existing tables/RPCs. No Production write/feature activation.
- EVIDENCE: live read-only ACL/function/trigger audit and rollback-only synthetic authenticated non-Admin denial test pass for summary and shop approval. Live Credit/Admin Ride config functions absent, exposed modules fail closed. Full coverage matrix:docs/ADMIN-OPERATIONS-AUDIT.md, including genuine missing source work.
- TEST: operational DOM24, realtime50, section19, interface28, category22, network26, source94/81 pass. Full exact-source npm still running. Checkpoint1 hosted npm passed; WebKit failed because shallow checkout omitted pinned baseline, now fixed with full-history checkout.
- REMAINING/NEXT: inspect new CI WebKit failure if any; capture before/after metrics; finish server filtering/paging and missing operational controls in matrix. Merge/Pages only green candidate, then actual deployed asset/readback. Whole mission remains IN PROGRESS.

## 2026-10-20 Admin checkpoint 3 — page correctness and broadcast recovery
- BASE:b0abefb9. Fixes a pre-existing out-of-scope desired variable after confirmed shop open/close write. Governance now inserts only its selected panel into DOM, retaining the existing section actions.
- Orders:100-row pages/101 sentinel, deterministic order, server status filter, exact-page Shop/Customer ID reads and Map lookups; page search explicitly labelled. Broadcast no longer depends on the200-user directory cache: reads recipients in200-row server pages, persists notification IDs before each bulk write, uses ignore-duplicates on lost-reply retry and retains cursor until confirmed. No real broadcast sent.
- TEST: new actual Orders/Broadcast runtime13 PASS; Admin interface28/category22/source94/81 pass. Full npm running. Hosted b0abefb9 npm passed; mobile WebKit revealed a test race (directory navigation not awaited before request snapshot). Test now waits for actual directory page completion and reports exact unexpected requests instead of loosening assertion.
- NEXT: exact successor CI; inspect request/timing evidence, repair failures, publish green Web slice. Full permission/missing-control mission remains IN PROGRESS per audit matrix; no financial/backend activation implied.

## 2026-10-20 Web-first receipt / successor workspace split
- DONE — first Web slice only: PR457 source42fcd824 passed exact RC2076/run39348758285 including full npm and complete Admin mobile WebKit. Merge main827b34cd69ab25a93d4692b21a4ce816779edea7 has identical tested tree04584c3bff1fa89065d59e0d8e75fe99ccf75900. Pages1459/run39349127341 SUCCESS.
- PUBLIC VERIFIED: HTTP200 Admin HTML, section-loader, operations, credit, passenger-ride and role-realtime all byte-match main827b34cd. Public browser renders login and loads the new versioned scripts. No signed-in Admin session exists in this browser; no live user mutation or physical-device acceptance claimed.
- MEASURED: complete-document mobile WebKit, same50ms localhost fixture: boot requests43→10; ready3392→828ms; shell478→329ms. These are controlled test values, not Production latency.
- CURRENT SUCCESSOR BASE:827b34cd. Separate18-section navigation, Content/Pricing/Catalog workspaces reusing existing managers, server-filtered50-row Market Master browse, stricter Ride config validation and coalesced section renders. Expand hosted WebKit to every main route and governance section. No new Production schema/data or privileges.
- TEST: workspace DOM18, existing Admin interface28/category22/source95/82 PASS. Full npm first run found an old Shopping banner contract tied to inline Admin calls after Content moved to workspace.js; updated the contract to verify the actual loaded workspace + manager. Final full suite/CI still required.
- REMAINING: scoped readiness RPC internal scan, server-paged Rider review queues, missing audited operational mutation/health APIs, some pricing/eligibility controls, live authenticated/physical acceptance. Coverage matrix remains IN PROGRESS; first successful deployment is not full mission completion.

## 2026-10-20 Admin scoped operational readers checkpoint
- CURRENT BASE:9017567baff0b9fb325245578e3dbef5e7a0efea. PR458 sourceae0b438355b5684a466e742f4df575bfd2def25c passed RC39349869823; main CI39350311495 and Pages39350310925 SUCCESS. Full Admin mobile WebKit74 checks PASS. Latest controlled fixture25→10 requests /1933→762ms ready; prior baseline43 reflects legacy duplicate-boot timing variability, not Production latency.
- IMPLEMENTED: additive page-scoped shop overview overload (max200 IDs, invoker/RLS); bounded Dispatch projection (max100 each); notification outbox status aggregates. Private table grants unchanged. New private readers require active Admin and live session, wrappers authenticated only; no payloads/tokens or business mutations.
- TEST: full npm EXIT0; operational SQL25 and DOM24 PASS; whitespace PASS. SQL verifies anon/non-admin/suspended/session denial and limits. Production migration NOT APPLIED; exact-source hosted CI pending.
- REMAINING/BLOCKED: whole mission IN PROGRESS per audit matrix; Credit/Ride backend rollout, audited missing controls, further paging/abort work, authenticated and physical-device acceptance remain. NEXT: publish checkpoint, exact-source CI, rollback rehearsal before additive migration deployment, merge/Pages and byte verification.

- Reader checkpoint follow-up: rollback-only live-schema rehearsal passed all three non-Admin denials and anon ACL checks; no persisted change. Live column/index review confirmed report compatibility. Added matching global sort/recent-date indexes after finding existing history indexes lead with order_id and cannot support dashboard order. Exact successor CI required.

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


## 2026-10-20 Admin Rider review pagination checkpoint
- BASE: PR465 merged to main `7ba59d04d556f2c1360eff2729f63a86ea7872ab` after RC run `39357745885` SUCCESS. Admin global directory now includes bounded current shop-name/phone search without full hydration.
- PRODUCTION BACKEND: migration `20261020021801 admin_rider_review_pagination` applied after BEGIN/ROLLBACK rehearsal. One centralized private reader pages identity/inspection/vehicle/document queues; legacy four queue RPCs delegate to it for compatibility. Public wrapper is SECURITY INVOKER; anon/PUBLIC EXECUTE denied, authenticated allowed; post-apply advisor has no finding for the new reader.
- WEB: Rider review requests 50 rows/page from `qg_admin_rider_review_page`; next/previous navigation and post-action refresh remain on the current page. Removed browser-side full-queue read followed by `.slice(0,200)`.
- TEST/GATE: source migration contract and Admin operations DOM regression added. Exact successor RC/Pages still required before this slice is DONE.
- REMAINING: search-scale indexes/aggregates, audit/notification paging and remaining safe matrix controls. staged Credit/Ride cutover, and authenticated/physical-device acceptance remain separate release gates; do not bypass them.

- SCALE SEARCH follow-up: Production migration `20261020022140 admin_directory_search_trgm_indexes` applied after rollback rehearsal; existing `pg_trgm 1.6` reused. Four partial/full GIN trigram indexes now cover account name/phone and current shop name/phone contains-search. Production currently has 35 users / 10 current shops; indexes are capacity work, not a claim of current latency improvement. Repo regression locks all four index definitions. Search-scale text indexes are no longer a remaining item.


## 2026-10-20 Admin audit / notification paging checkpoint
- BASE: exact descendant of PR466 source `004743628cba7410b9d7b5769356749d90b25aa6`; PR466 RC #2123 SUCCESS and merged main `cd857a2eea894b791f4397ce253aea08f4bc61fb`.
- IMPLEMENTED: Audit Log and Admin's notification history now read 101-row sentinel pages and render at most 100 rows. Older pages use a deterministic `(created_at,id)` keyset cursor instead of growing OFFSET scans; previous-page history is kept only in-session. Cursor identity is part of the read scope so an older in-flight page cannot overwrite the active page. Page changes refresh only the active section; no full Admin hydrate.
- PRODUCTION DB: migration `20261020022520 admin_audit_notification_paging_indexes` applied after rollback rehearsal. Added `audit_logs(created_at desc,id desc)` and `notifications(user_id,created_at desc,id desc)` indexes matching page filters/order. No RLS/grant changes.
- TEST: section-loader regression now asserts 100/101 keyset queries, no OFFSET paging, older/newer cursor navigation, stable order, page metadata/functions, and both migration indexes. Exact successor full RC/mobile WebKit/Pages still required.
- REMAINING SOURCE: read-only POST/RPC cancellation and audited safe controls that already have authoritative backend. Credit/top-up/Ride cutover, worker/device delivery evidence and physical authenticated acceptance remain Release Gates, not bypass targets.


## 2026-10-20 Admin shop category editor checkpoint
- BASE: PR467 keyset history successor; intended base is merged main `e3c914428b1d86a459a20b313da76eb54a12f41b`.
- PRODUCTION BACKEND: migrations `20261020023357 admin_shop_category_editor` and `20261020023435 admin_shop_category_invoker_hardening` applied. Final RPC `qg_admin_update_shop_category` is SECURITY INVOKER, requires live session + active Admin, anon/PUBLIC EXECUTE denied, authenticated EXECUTE allowed, and the post-hardening security advisor has no finding for it.
- CONTROL: existing shop-detail dialog now exposes one audited category editor. It reads only the selected current shop, enforces fixed category/subcategory allowlists, requires a 3–500 character reason, requires at least one shopping subcategory, and writes a server Audit Log with before/after values. It does not alter shop approval/open status.
- TEST: dedicated source regression covers the UI control, selected-shop read, server guards/allowlists/audit, explicit grants and final SECURITY INVOKER hardening. Exact RC/mobile WebKit required before merge.


## 2026-10-20 Admin read-only RPC cancellation successor after category-editor sync
- BASE: current main `b11d6767d06340dd771f2f080710f458df80b18c` includes merged PR #471 audited shop-category editor and its Production-backed SECURITY INVOKER RPC.
- IMPLEMENTED: read-only Admin POST/RPC work (System live counts, Dispatch overview, notification health, Rider review paging, Ride config/licence reads, Credit account/merchant-target reads) joins the existing route/account AbortController lifecycle. Mutations continue on normal RPC calls and are never route-aborted.
- FAILURE/FIX EVIDENCE: RC #2195 found a cross-module helper-name collision; successor helpers are module-scoped/uniquely named. RC #2205 then found a stale Rider-review regression requiring direct `qtSupabaseRpc`; the test now requires the cancellable read helper and forbids the old direct read path.
- PRE-SYNC GATE: RC #2209 on `30a327f76293f587a7ed1defaa2c0c69ca3ee25a` passed full npm, Admin mobile WebKit, Merchant, Map and Grocery gates.
- FORWARD SYNC: #471 files were preserved on this successor without reset/revert/force; Admin category UI/migrations/tests and read-cancellation changes coexist.
- NEXT: exact-head RC after the forward sync is required before merge. If green, merge this slice and continue the next authoritative Admin control; external provider/physical release gates remain blocked and must not be bypassed.


## 2026-10-20 Admin Rider Map Feedback / Road Alerts checkpoint
- BASE: stacked on clean RPC-cancellation successor PR #472; this keeps read-only queue RPCs on the cancellable Admin read layer while review/publish/disable remain mutation RPCs.
- REUSED BACKEND: Production migrations already exist as `20261014074523 rider_map_feedback_v1`, `20261014110355 rider_road_alerts_v1`, and `20261014110429 rider_road_alerts_fk_index_v2`. Their exact historical Production-aligned source mirrors were restored to the repository; Production was not re-applied.
- IMPLEMENTED: Rider Admin section restores the existing map-feedback review flow from PR #261 and Road Alert controls from PR #320. Pending reports can be accepted for review or dismissed with reason. Accepted road_closed/bad_road/unsafe_turn reports can publish bounded Road Alerts and active alerts can be disabled.
- MAP AUTHORITY: “ดูบนแผนที่” reuses existing `qgAdminViewLocation()` / QueueGo Map read-only dialog. No Google Maps URL or duplicate map component.
- SECURITY: browser reads only public guarded RPCs; it never reads `qg_private.rider_map_feedback` or `qg_private.rider_road_alerts`. Existing wrappers are SECURITY INVOKER, deny anon, and private implementations require live session + active Admin.
- TEST: dedicated source regression guards RPC/read-vs-mutation wiring, QueueGo Map reuse, no Google Maps fallback/private-table browser access, bounded queue/duration contracts, privilege revocations, and restored migration mirrors. Exact stacked RC/mobile WebKit required before merge.
- NEXT: merge PR #472 first if its exact gate is green, retarget this successor to main, then obtain exact-head RC before merge. Physical navigation/GPS acceptance remains a separate release gate.


## 2026-10-20 Admin Rider Programs successor
- BASE: merged PR #473 / main `93837a8c99b2bb4318ddd1fb122265111b3946d2`; successor branch `work-web-admin-rider-programs-v2-20261020`.
- IMPLEMENTED SOURCE: restores exact Production-aligned source mirrors for Rider Missions, Insurance and Benefits. Adds one separate Admin workspace for real Missions / Benefits / Insurance using the existing public RPCs only.
- READ/WRITE BOUNDARY: list RPCs use the scoped cancellable `qgAdminRpcRead` layer; mutations remain direct `qtSupabaseRpc` so navigation never aborts a committed write.
- AUDIT: new redacted atomic audit trigger records Admin mutations without policy number, support contact/link, coverage text or benefit URL in `audit_logs`.
- SAFETY: no mock program rows, no automatic Mission bonus/credit payout, no direct `qg_private` browser access, no service-role key, and no Credit/Ride/provider Release Gate bypass.
- PRODUCTION: rollback rehearsal passed with insert/update/delete + redaction sentinels; migration `20261020030951 admin_rider_program_audit` is applied. Three triggers exist; direct anon/authenticated/PUBLIC EXECUTE on the private trigger function is denied; Missions/Benefits/Insurance remain 0 real rows.\n- NEXT: run focused/full tests + exact-head RC, then merge/Pages verification. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin Rider online-readiness explanation
- BASE: merged PR #474 / main `c1742d8aaa5d4bd7cccc300ae62a44497e080f3f`; branch `work-web-admin-rider-online-readiness-20261020`.
- IMPLEMENTED SOURCE: existing Rider review queues now state whether each row is a blocker for the existing server online guards: identity re-verification, vehicle inspection, selected vehicle approval, and expired national ID / driver licence / selected-vehicle PRB.
- NO NEW AUTHORITY: reuses `qg_admin_rider_review_page`; adds zero queries, zero polling, zero table/RPC/schema changes, and no `set_rider_availability` / force-online action.
- DEVICE BOUNDARY: Network, Firebase, notification permission, GPS/location service, Google Maps and overlay-return readiness remain device-side evidence and are explicitly not claimed by Admin.
- SERVICE BOUNDARY: Ride / Express / Laundry can have additional service-specific release gates; this view does not bypass them.
- TEST: dedicated jsdom regression locks server-blocker labels, 50-row paging reuse, no private-table access and non-Admin denial.
- NEXT: exact-head RC/mobile WebKit, merge/Pages, then continue remaining safe matrix gaps. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin Rider presence paging
- BASE: merged PR #475 / main `76ba873fdcccb324e45d98e50240658e062affff`; branch `work-web-admin-rider-presence-page-20261020`.
- ROOT CAUSE: Rider status re-read all Rider users + all Rider profiles + up to 2,000 historical presence events even though the Admin directory was already scoped and Production records every `metadata.online` change via trigger `qg_record_rider_presence`.
- CHANGE: presence screen now reads Rider users at 100+1 rows per page, fetches profiles only for those page IDs, and renders the current `rider_profiles.metadata.online` value. Prev/Next preserve access beyond the first page.
- REQUEST/PAYLOAD: removes the `qg_rider_presence_events?...limit=2000` request entirely and removes unbounded Rider user/profile reads. No schema/RLS/polling/service change.
- CONSISTENCY: Production trigger evidence confirms each change of `metadata.online` appends the matching presence event; profile current state and event history are therefore not competing authorities for the current switch.
- SAFETY: actor/page generation fencing prevents a stale response from another page/account repainting the panel.
- TEST: dedicated jsdom runtime regression locks 100+1 paging, page-ID profile lookup, no presence-history query, Prev/Next and account-race fencing.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC, then continue remaining Admin performance/coverage gaps. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin scoped shop overview only
- BASE: merged PR #476 / main `0e03fcd1a57a5e2b31177fbd5cefeffab0ce9285`; branch `work-web-admin-scoped-shop-overview-20261020`.
- PRODUCTION AUTHORITY: `queuego_admin_shop_overview(p_user_ids uuid[])` is SECURITY INVOKER, active-Admin guarded and rejects more than 200 account IDs. The legacy no-arg overload is SECURITY DEFINER, computes readiness/order counts for all shops, and Supabase Security Advisor flags its authenticated exposure.
- CHANGE: Admin directory fast path now calls only the scoped overload with the current page IDs. The legacy PGRST202/function-missing fallback to the no-arg overload is removed; an unreachable old no-arg Admin hydrate call is removed too.
- FAILURE MODE: missing scoped RPC now fails closed and surfaces the load error instead of silently falling back to an unbounded all-shop computation.
- TEST: existing Admin directory runtime now verifies every overview call carries `p_user_ids`, caps at 200, simulates PGRST202 and proves no second/unscoped call occurs.
- NO DB MUTATION: no grant/RLS/function/schema change in this source-only checkpoint. Legacy backend function retirement remains a separate compatibility/security decision after deployed callers are proven absent.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC, then continue Admin performance/permission coverage. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin Orders keyset paging
- BASE: merged PR #477 / main `d6caeebb5ccf0608707ea391be0035e148b04dd6`; branch `work-web-admin-orders-keyset-20261020`.
- ROOT CAUSE: Admin Orders used `offset = page * 100`; database work grows with page depth even though rows are already sorted by `created_at desc, id desc`.
- CHANGE: paging now uses a stable keyset cursor `(created_at,id)` with 100+1 sentinel rows. “ก่อนหน้า” stores cursor history; status-filter changes reset cursor/page; refresh stays on the current cursor.
- CONSISTENCY: visible-page shop/customer names are still fetched only for the 100 visible orders. Trace/delete/status filtering and local within-page search are unchanged.
- RACE SAFETY: request-version + Admin-owner fencing prevents a stale old page/filter response from repainting a newer page/account.
- INDEX EVIDENCE: Production already has `idx_orders_created_at (created_at desc)` and `orders_pkey(id)`; no schema/index mutation is required for this checkpoint.
- TEST: Admin order runtime rejects any `offset=` order request and verifies next cursor, previous cursor, filter reset, exact-page parties and existing broadcast behavior.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC, then continue Admin performance/coverage. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin Support fallback refresh scope
- BASE: merged PR #478 / main `0f84fe969b75f3029fad13577c2c4e9ec4b27be2`; branch `work-web-admin-support-fallback-scope-20261020`.
- EXISTING BACKEND: `shop_support_messages` still has no Realtime publication, so the bounded 60s visible-page fallback remains necessary.
- CHANGE: fallback refresh now runs only on `#admin-support` when the tab is visible, browser online, Admin approved, inbox/thread are both on the newest cursor, and the reply draft is empty.
- NO HISTORY JUMP: old inbox/thread pages are never auto-refreshed; manual paging stays under operator control.
- NO TYPING INTERRUPTION: a non-empty reply draft suppresses the timer; explicit refresh and post-send refresh still work.
- REQUEST BOUNDS UNCHANGED: inbox 200/201 + selected thread 100/101 keyset pages; no extra table, no new poller, no interval reduction, no full hydrate.
- TEST: existing `admin-support-runtime.cjs` now locks history/draft suppression, newest-idle fallback, hidden-route suppression and stale-actor fencing.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC, then continue remaining safe Admin source gaps. Whole Admin mission remains IN PROGRESS.

## 2026-10-20 Admin Support global inbox index
- BASE: stacked successor of PR #479 HEAD b232595212a3dcf817ee503f879fd781ff517e43; branch work-web-admin-support-inbox-index-20261020.
- QUERY CONTRACT: Support inbox reads newest 200/201 rows globally with ORDER BY created_at DESC, id DESC; thread paging already has a shop-leading index.
- PRODUCTION BEFORE: indexes are PK(id), sender_user_id, and (shop_user_id, created_at); there is no global recent-inbox order index. Current table is tiny and planner correctly uses Seq Scan+Sort, so this is scale hardening rather than a claim of current latency gain.
- SOURCE: additive migration 20261020041534_admin_support_inbox_paging_index.sql matches live Supabase version 20261020041534 and adds shop_support_messages_recent_idx(created_at DESC,id DESC); no RLS/grant/data/poller change.
- ROLLBACK REHEARSAL: Production subtransaction created a temporary equivalent index and deliberately rolled it back; verification returned rehearsal_index_left=0.
- TEST: existing Support runtime locks the migration/index name and exact column order.
- NEXT: parent PR #479 is merged/Pages/post-merge green; live migration/index are verified. Run exact-head RC on the version-aligned source, then merge/Pages/post-merge RC.

- PRODUCTION APPLY: Supabase migration tool applied version 20261020041534 / name admin_support_inbox_paging_index successfully.
- VERIFY: pg_indexes now reports shop_support_messages_recent_idx as btree (created_at DESC, id DESC). No data/RLS/grant change occurred.
- RELEASE STATE: source migration filename is aligned to 20261020041534_admin_support_inbox_paging_index.sql so repository and live migration history match.


## 2026-10-20 Admin complaint ticket keyset paging
- BASE: merged PR #480 / main `a9a6a2271ea0c48bff79d890467e4c002025cf16`; branch `work-web-admin-tickets-keyset-20261020`.
- BEFORE: complaint modal read a fixed newest `limit=200` page with no way to navigate older records.
- CHANGE: complaint tickets now use stable `(created_at,id)` keyset paging with a 100/101 sentinel, explicit “ใหม่กว่า/เก่ากว่า” controls, and stale-account/request-generation fencing.
- MUTATION: existing `qg_admin_ticket_action` is unchanged; after a decision the modal refreshes the same current cursor instead of jumping to newest.
- SECURITY: direct table read still depends on existing authenticated RLS `qg_tickets_read` (own ticket OR active Admin). No new write authority, no bypass and no UGC changes.
- SCALE SOURCE: live/source-aligned migration `20261020042804_admin_ticket_paging_index.sql` adds only `qg_support_tickets_recent_idx(created_at DESC,id DESC)`.
- CURRENT DATA: Production has 0 complaint tickets at this checkpoint, so this is capacity/UX hardening rather than a claim of current latency gain.
- TEST: new jsdom runtime regression locks 100/101 paging, no OFFSET, older/newer cursor behavior, same-page mutation refresh, stale-account fencing and the index contract.
- PRODUCTION: rollback rehearsal passed; migration `20261020042804 admin_ticket_paging_index` is applied and `pg_indexes` confirms `qg_support_tickets_recent_idx(created_at DESC,id DESC)`. Parent main Pages #1479 and post-merge RC #2323 are green.\n- NEXT: rerun exact-head RC on version-aligned source, then merge/Pages/post-merge RC. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin UGC moderation keyset paging
- BASE: merged PR #481 / main `c7d9373504b56eda3f3bdf842c43f997c4fa50d2`; branch `work-web-admin-ugc-keyset-20261020`.
- BEFORE: Admin UGC modal called legacy `qg_admin_ugc_reports(p_status)`, which returns at most 300 rows and has no older/newer navigation.
- SECURITY BOUNDARY: `public.qg_ugc_reports` remains client-table denied. Candidate adds `public.qg_admin_ugc_reports_page` as SECURITY INVOKER over `qg_private.admin_ugc_reports_page`; private reader requires `assert_live_session()` + active Admin and caps pages at 100.
- UI: modal uses cancellable `qgAdminRpcRead`, 100-row keyset pages ordered `created_at DESC,id DESC`, explicit newer/older navigation, and stale-account/request fencing.
- MUTATION: existing `qg_admin_ugc_report_action` remains direct/non-cancellable; after moderation the same cursor page refreshes.
- EVIDENCE SAFETY: existing strict snapshot-image data URL allowlist and escaped text rendering are retained; browser never reads `qg_ugc_reports` directly.
- SCALE SOURCE: live/source-aligned migration `20261020044510_admin_ugc_report_paging.sql` adds `qg_ugc_reports_recent_idx(created_at DESC,id DESC)`.
- CURRENT DATA: Production currently has 0 UGC report rows at this checkpoint; this is operator-history/scale hardening, not a present-latency claim.
- TEST: dedicated jsdom/source regression locks guarded reader wiring, 100-row paging, cursor history, same-page moderation refresh, stale-account fencing, wrapper/private privileges and index contract.
- REHEARSAL: Production rollback-only execution passed with a real active Admin/live session fixture; page JSON contract was valid, partial cursor returned 22023, anon EXECUTE remained denied, authenticated wrapper EXECUTE was present, and rollback left no reader/index objects.\n- PRODUCTION: migration `20261020044510 admin_ugc_report_paging` is applied. Public wrapper is SECURITY INVOKER; private reader is SECURITY DEFINER; anon/PUBLIC EXECUTE are denied on both; authenticated EXECUTE is present; `qg_ugc_reports_recent_idx(created_at DESC,id DESC)` is live.\n- NEXT: rerun exact-head RC on version-aligned source, then merge/Pages/post-merge RC. Whole Admin mission remains IN PROGRESS.

## 2026-10-20 Admin GP scoped daily payload
- BASE: merged PR #482 / main b4284e3526923426e620500596d04cfae942978b; branch work-web-admin-gp-scoped-shops-20261020.
- BEFORE: selecting a GP day fetched all shop_profiles plus all settlements for that date, even though the UI only renders shops that have qualifying orders on that day.
- CHANGE: first read the same qualifying daily orders, derive unique shop IDs, then fetch shop_profiles and settlements only for those IDs in batches of at most 200.
- FINANCIAL SEMANTICS: subtotal/GP/count math, eligible order statuses, settlement review controls, slip handling and admin_review_gp mutation are unchanged.
- RACE SAFETY: Admin owner + selected date + request-generation fencing prevents an older day/account response from repainting a newer view.
- NO DB/RLS CHANGE: source-only payload reduction; no credit/top-up/provider behavior and no financial write authority change.
- TEST: tests/admin-gp-scoped-runtime.cjs uses 201 unique shops to force two bounded batches, locks totals, rejects unscoped profile/settlement reads and verifies stale-date suppression.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC, then continue remaining Admin loader payload review. Whole Admin mission remains IN PROGRESS.

## 2026-10-20 Admin shop mutation scoped refresh
- BASE: merged PR #483 / main beb7e48a91d34a860652e3397925ff4dd5e5b9f3; branch work-web-admin-shop-mutation-directory-refresh-20261020.
- ROOT CAUSE: successful unopened-shop deletion and shop archival still called qtHydrateDatabase({light:false}), reloading unrelated system data after a directory-only mutation.
- CHANGE: both paths keep their existing authoritative RPC mutation, then refresh only qtHydrateAdminDirectory() and re-render the Shops section.
- FAILURE SEMANTICS: failed server mutation still does not refresh or acknowledge success.
- NO AUTHORITY CHANGE: queuego_admin_delete_unopened_shop / queuego_admin_archive_shop are unchanged; no RLS/grant/schema/financial/service change.
- TEST: tests/admin-shop-mutation-refresh.cjs calls both real client functions, forbids any full hydrate, requires one directory refresh per successful mutation, and verifies failed archive causes no refresh.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC, then continue remaining Admin post-action/full-hydrate and loader payload audit. Whole Admin mission remains IN PROGRESS.

## 2026-10-20 Admin GP language/UI polish v2
- BASE: merged PR #484 / main `a67783798c94a3678174535baf40760da9acd930`; successor branch `work-web-admin-gp-ui-language-v2-20261020` supersedes diverged PR #485.
- WORDING: main page uses “ค่าบริการร้านค้า (GP)”, “ยอดขายร้าน”, “ค่าบริการที่ต้องชำระ”, “ออเดอร์ที่เข้าเกณฑ์”, and readable settlement labels instead of ambiguous/raw GP wording.
- RATE PAGE: uses “อัตราเฉพาะร้าน / สิ้นสุดแล้ว” instead of promotion semantics and separates system default vs shop-specific rate clearly.
- UI: summary metrics, shop cards, status chips, compact slip actions, responsive desktop/mobile layout and cleaner rate cards.
- SAFETY: GP math, qualifying statuses, <=200 scoped shop/settlement batches, slip signing, `admin_review_gp`, rate writes, RLS/grants are unchanged.
- PRESERVED #484: unopened-shop delete and archive still refresh only `qtHydrateAdminDirectory()`; no `qtHydrateDatabase({light:false})` remains in Admin source.
- TEST: `admin-gp-scoped-runtime.cjs` locks scoped batching/math/stale fencing plus terminology/status/slip actions/layout; existing shop-mutation regression remains in the full suite.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC. Whole Admin mission remains IN PROGRESS.


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


## 2026-10-20 Admin source-freeze candidate
- OWNER SCOPE: no new Admin features after this checkpoint. Finish only correctness, security, performance, CI/Pages and existing-control defects; then move engineering focus to Android / Play Store.
- BASE: current main `0ce02788416f88965e4b0636c6fca727f7b0e45e`; branch `work-web-admin-freeze-shop-state-scope-20261020`.
- CLEANUP: removed dead `qgRefreshAdminShopStates()` legacy code. It had no callers/current markup target but still contained unbounded all-current-shop + all-open-state reads. Current shop open/close control remains `qtAdminToggleShop` with the existing selected-shop reads/mutation authority.
- PERFORMANCE REGRESSION: `tests/admin-performance.cjs` now forbids the removed refresher and both legacy full-table shop-state query strings. Admin full-hydrate legacy code remains unreachable for Admin because `qtHydrateDatabase()` returns through `qtAdminRefreshScoped()` before the non-Admin hydrate body.
- SECURITY RECHECK: Production public Admin RPC inventory has anon/PUBLIC EXECUTE denied for inspected Admin functions. Legacy SECURITY DEFINER functions are still generic-advisor findings, but inspected endpoints enforce active-Admin role/status through direct checks or `get_my_role()/is_active_admin()`.
- ROLLBACK-ONLY GUARD EVIDENCE: with a real active non-Admin auth subject, `admin_suspicious_orders()` returned zero rows and both `admin_delete_user()` / `queuego_admin_approve_shop()` denied with 42501. Transaction rolled back; no user/shop/order data changed.
- FREEZE BOUNDARY: Merchant/Rider Credit/Top-up backend rollout, Ride/Express controlled activation, worker/device-delivery evidence, physical GPS/map/browser acceptance, signed AAB and Play Console/Closed Testing are release gates outside the frozen Admin feature scope. Existing Admin surfaces must remain fail-closed where their authoritative backend is absent.
- NEXT: exact-head full RC + Admin mobile WebKit; merge only green; verify Pages/post-merge RC and served bytes. If green, mark current Admin source set FROZEN and move to Android Play Store readiness. Do not open new Admin feature work unless a regression/security defect is discovered.


## 2026-10-20 Parallel completion: Rider proof and Admin freeze
- DONE (Rider Web source merge): PR508 exact f1359784406cf6a625455fbcf213ac49bbc7de90 passed RC39420653591 and RC39420648835; merged as44c97c04384fcdb93be1801687a30c71bcd6a4e3. Camera-image normalization retains the Storage5MB cap; selected proof preview uses compact two-column image/retake controls.
- DONE (Admin source merge/hosting): PR507 exact9726fbdefff5226530e12cf283756ff49be0eb09 passed full RC39418117904 and RC39418123012 including mobile WebKit. Merge9bb6e436aee941e1223ae3d941b40c53d4c27f8c preserves508 and509. Pages39426694695 SUCCESS. Actual HTTPS200 Admin HTML SHA25623471ca18ac32835c0c2e0999846ae54c582142e22720e4b4ba51cfa194b60b9 matches that main; dead full-shop refresher absent. Postmerge RC39426694586 still running at this checkpoint.
- DONE (focused uploader runtime test): commit5a34149c exercises actual support uploader with safe PNG passthrough, HEIC, blank-MIME HEIF, oversized JPEG, source rejection, decode failure and Storage rejection. Combined actual proof DOM/upload suite65 checks/0 failures. Decode and transport are isolated substitutes; no physical iPhone or live upload is certified. Full local regression running before publication.
- IN PROGRESS: Native Rider latest descendant CI, shared release manifest cleanup, Credit/Ride/Express reconciliation, Map runtime transport and Catalog/Laundry checks run independently. Shared branches are not overwritten while other writers advance them.
- NEXT: complete local full suite and exact hosted CI for the uploader test successor, merge and verify Web bytes; record final Admin RC receipt. Continue newest Native descendant and each unblocked source lane. Physical GPS/authenticated-device, approved hosting/provider/Production release and signed AAB/Play gates remain separate.


## 2026-10-21 Admin final freeze
- Component: Admin Web
- HEAD: main `2ef2ac18b23a14b5ad8e79d6bc2b0abad3f999e4`; frozen Admin implementation merged via `9bb6e436aee941e1223ae3d941b40c53d4c27f8c` from exact source `9726fbdefff5226530e12cf283756ff49be0eb09`.
- Build/Test: exact source RC `39418123012` SUCCESS; post-merge RC `39426694586` job `118641612148` SUCCESS; Pages `39426694695` SUCCESS.
- Integrity: main is 9 commits ahead of the Admin merge checkpoint with no changes in `admin/`, `tests/admin*`, or `role-realtime.js`.
- Release blocker: none in Admin source. External gates remain for Credit/Top-up rollout, Ride/Express activation, authenticated/physical-device acceptance and Android/Play release.
- Status: DONE / SOURCE FROZEN. Do not reopen superseded Admin branches without a reproducible regression/security defect.
- NEXT: resume Android Customer/Merchant/Rider Google Play Closed Testing Release Gate from newest release Source of Truth.


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


## 2026-10-21 QueueGo Web design language (PR #566)
- Source/test status: DONE. PR #566 merged to main as `beac57b9c66972fcaeb4253e2c05116c7617b30f`.
- Branch checkpoint: `29a7a1f9004e25fdd5112e4b791f32f64d0e5345`; exact-head RC `39614031789` SUCCESS, all15 steps completed, including full regression and mobile WebKit.
- Scope: QueueGo wordmark Queue dark / Go red across Customer, Merchant, Rider and Admin. No Production data, schema or business-flow changes.
- Web deployment: Pages build/deploy run `39614671640` SUCCESS on merge commit. Main Customer asset blob `f7c4082c0d211a93df003ae3473fac181e6178ce` has the tested singular cache keys; Admin CSS contains the shared wordmark colors.
- Owner visual acceptance: BLOCKED until Owner opens the deployed site in iPhone Safari; this environment could not read the live Pages response to independently compare served bytes.
- NEXT: Owner reviews QueueGo in iPhone Safari. After review, record any requested visual corrections; only then decide on Android sync.


## 2026-10-21 QueueGo POS generic inventory continuation
- Implemented on the existing dependent POS successor: opt-in generic inventory tracking and private reservations across shared POS/QR/Delivery order items. Grocery Master products are explicitly excluded to preserve their existing single stock authority. A constraint prevents enabling both tracking owners.
- Generic product editor marks the field as sellable stock and blocks tracking-mode changes while an open order contains the SKU. POS displays available units and disables a tracked SKU at zero. Order item changes are serialized on the order and product rows; insufficient-stock writes fail transactionally; pre-completion cancel releases once while completed refund remains sold until a separately authorized physical return/restock flow.
- `tests/pos-generic-inventory.cjs` 22 PASS; POS option 27, SKU SQL 31, scanner 11, cash shifts 36, Merchant product reliability 72; full hosted `npm test` EXIT0. Shopify official inventory-state docs informed the available/committed label. Isolated PGlite tests do not certify production concurrency/RLS or real register behavior.
- Migration `supabase/migrations/20261021233000_pos_generic_inventory_reservations.sql` is staged, not in Production. No migration applied and no real product/order writes made. Latest main snapshot `643a115129b0bf1d6b05de261baa0aa959310a1b`; PR #568 head `a1592df31c41cbb6bbfdf13e166d977c9bcf901c`; tested source head for PR #572 `e40dcd17480cd6068d2eda4ef9d04bdc67f6771f`. Exact tree source is verified against local tested commit `1eafe37ed1eb19ec6d017a0ace9219cfe0b85659`. Hosted RC run `39630180090` PASS includes full tests, WebKit install, Admin/Merchant mobile checks, Map Lab/search and Grocery rendering. PR #572 remains stacked and no merge/deploy/readback/cashier acceptance is claimed.
- Next: resolve #568 release gate, rebase/retarget #572 onto merged main and rerun exact-head RC; review/apply migrations through release process and run authenticated boundary sale/cancel/refund acceptance. Then build stock adjustment audit/history and supplier purchase receiving. QueueGo/POS remain NOT DONE.


## 2026-10-21 POS inventory adjustment audit/history continuation
- Scope: extend the same generic inventory owner used by POS/QR/Delivery. Grocery Master and Market inventory ledgers remain untouched.
- Migration `20261021233100_pos_inventory_adjustments.sql`: manager-only idempotent stock adjustment RPC; append-only movement rows with actor/reason/note/before/after; opening balance; linked order reservation/release/cancellation events; tracked stock direct-write guard; shop-scoped paginated history RPC. Four migrations in this dependent POS stack remain unapplied in Production.
- Merchant product editor: tracked quantity is read-only on general save; dedicated mobile dialog requires integer delta, reason and note. Ambiguous retries reuse the same session request key; successful change refreshes product cache and history. History output is escaped and keyset-paginated.
- Tests: generic inventory/adjustment SQL 38 PASS; adjustment UI runtime 9 PASS; Merchant product reliability 79 PASS; full `npm test` EXIT0; `git diff --check` PASS. Exact-source hosted RC `39633575108` PASS on `3ac73c2c1aa9221c392e5cfcaca5afd82653dd15`, including WebKit, Admin/Merchant, Map and Grocery checks.
- Release gates remain BLOCKED: #572 is stacked on open #568; Production schema review/apply, live concurrent boundary, manager adjustment, authenticated cashier acceptance, deployment/readback and real register verification remain open. No live stock writes or Production migration performed.
- NEXT: review CI evidence and resolve #568 before rebasing #572 onto main; rerun exact-source RC on the merged-main descendant. Production migration review and authorized live cashier/device acceptance remain required. After the stock slice is released, add supplier purchase receiving without duplicating Grocery/Market ledgers. QueueGo/POS remain NOT DONE.

## 2026-10-21 QueueGo POS release continuation after owner `ต่อ`
- Startup recovery: read `WORKFLOW.md`, this checklist and `docs/PROJECT-MEMORY.md`; fetched latest main/open PRs and checked the #572 successor. Main before this work was `643a115129b0bf1d6b05de261baa0aa959310a1b`; #568 head `a1592df31c41cbb6bbfdf13e166d977c9bcf901c`, #572 head `330d900e5b78191ab012905e4f42bbca09889a2c`.
- #568 exact-head QueueGo RC `39622237860`: SUCCESS, all 12 test/browser steps completed. Merged using expected-head lock; merge commit `c79640e0c896cc81961e8ba2dc69d336a9f519b0` is now main. No Production migration or data mutation was made.
- Retargeted #572 to `main`; its current HEAD remains docs-only `330d900e5b78191ab012905e4f42bbca09889a2c`, inventory code SHA `3ac73c2c1aa9221c392e5cfcaca5afd82653dd15`. Pre-retarget exact-source RC `39633575108` PASS. Fresh exact-head RC after retarget is still required.
- Supabase Production read-only check at `2026-10-21`: latest migration `20261021133330`; `pos_register_cash_events` has RLS enabled, anon SELECT false, authenticated SELECT true/INSERT false. No `event_type` or `idempotency_key` columns; `pos_record_cash_movement(text,numeric,text,uuid)` absent. Therefore merged cash movement is not live. Four #572 migrations are not applied.
- Supabase security/performance advisors were inspected. They return global findings (private RLS/no-policy tables, public SECURITY DEFINER endpoints requiring intent review, and unindexed foreign keys); no blanket policy change was made. These findings need endpoint-level ownership review before future database activation.
- Updated `NEXT.md`, `FEATURE_GAP_MATRIX.md`, and `RELEASE_GATE.md` to the current merge/Production evidence and preserved the NOT DONE status.
- STATUS: #568 source DONE / Production activation BLOCKED. #572 source/test DONE on prior exact-source CI / fresh post-retarget CI and Production acceptance OPEN.
- NEXT: trigger/run a fresh full exact-head RC on the retargeted #572 successor. If green, complete staged migration/RLS/trigger order review and authorized real cashier/scanner/cross-channel acceptance before controlled activation. Then resume supplier/PO receiving on the same inventory authority; do not add a second ledger. Overall QueueGo/POS remain NOT DONE.

## 2026-10-21 POS inventory toggle authorization follow-up
- After PR #572 merged to main at `6050a51ab7cb06001fb4163b5ff88c890fb4950d`, reviewed Production `products_shop_update` RLS and found authenticated shop users can PATCH their own product rows. Existing migration guarded tracked stock quantity changes but did not require manager permission to enable/disable the inventory tracking owner flag (nor to insert a product with that flag set).
- Production `public.pos_allowed(text)` is a SECURITY DEFINER permission check: owner or active shop staff with the named capability. Used only read-only function definition; no user/product data read or write.
- Added successor migration `20261021233200_pos_inventory_tracking_manager_guard.sql`: the tracking guard now covers INSERT and UPDATE, requires authenticated `pos_allowed('manage_staff')` when tracking starts/is switched, preserves Grocery Master exclusion and open-order lock, and revokes direct trigger-function execution.
- Regression: `tests/pos-generic-inventory.cjs` denies unauthorised direct tracking toggle and tracked-product insertion; focused test passes 40 checks. Full `npm test` EXIT 0; inventory adjustment UI test 9 PASS; `git diff --check` PASS. Exact-source RC `39638917216` passed all 12 steps; PR #573 merged as `ab36bfb9da66d5d4a43a83011e4a50fb08c27213`. Migration has not been applied to Production.
- NEXT: continue actual schema/trigger compatibility review for the five inventory migrations. Real manager/cashier and physical device acceptance remains blocked; do not apply Production migration or claim POS release readiness.

## 2026-10-21 POS Market stock authority isolation — merged
- Read-only Production catalog inspection found `market_item_stock_guard()` decrements `market_products.stock_quantity` and appends `market_stock_movements`; it is the authoritative Market ledger. PR #572 generic reservation skipped only Grocery Master, so a Market-linked `products` row with generic tracking enabled could be double-decremented.
- Added `20261021233300_pos_market_inventory_owner_guard.sql`: migrate preflight rejects any existing tracked Market overlap; product toggle and Market mapping triggers lock/check `products` so ownership cannot be assigned concurrently; remaps lock both products in stable order and deletes serialize on the old product; generic order-item reservation rechecks Market mapping after row lock and leaves it to the existing Market trigger.
- `tests/pos-generic-inventory.cjs` simulates the Market item trigger and an inconsistent legacy overlap; asserts Market count changes while `products.stock` remains unchanged and no generic reservation exists. It also covers both rejection directions for future ownership changes. Focused test 47 PASS after adding remap/delete guards; full `npm test` EXIT 0 on the final local tree; `git diff --check` PASS. Exact hosted RC remains pending on the latest branch HEAD.
- PR #574 merged as `2d0c6b58a45689585dc7653bd18bcf89dfd1e4c1` after exact QueueGo RC `39643279380` passed all 12 steps. Pages build/deploy `39643901620` succeeded on this merge SHA. Production migrations/data were not changed.

### Current continuation checkpoint — PR #574 merged
- `qg_inventory_tracking_guard()` now runs SECURITY DEFINER with fixed `search_path` because Production `market_products` is RLS-enabled without authenticated read policies; manager check still uses the caller JWT. Regression now applies migrations in timestamp order and tests an authenticated product update with RLS active.
- Local focused inventory test: 47 PASS; adjustment UI: 9 PASS; full `npm test` EXIT 0; `git diff --check`: PASS. Exact-source hosted RC `39643279380` passed all 12 steps; Pages deploy `39643901620` succeeded on merge SHA.
- Map remap/delete serialization now locks both affected products and serializes unmapping; Main merge SHA is `2d0c6b58a45689585dc7653bd18bcf89dfd1e4c1`; six POS migrations remain unapplied and production acceptance is open.
- No Production DDL/data write. Next: continue read-only schema/ACL/RLS/trigger/recovery review before any controlled Production migration. Production activation still requires authorized cashier/device acceptance and controlled migration/readback.


## 2026-10-21 POS Market inventory post-merge
- Merge `2d0c6b58a45689585dc7653bd18bcf89dfd1e4c1`; exact RC `39643279380` passed all 12 steps; Pages deployment workflow `39643901620` succeeded. Browser runtime readback unavailable due environment disconnection.
- Production remains unmodified; latest migration `20261021133330`. The inventory tracking column is absent until prerequisite migration 330, so Market overlap preflight must run only after ordered feature schema installation.
- QueueGo/POS remain NOT DONE until authorized user flows and Release Gate evidence are complete.


## 2026-10-21 POS numeric quantity regression follow-up
- Production read-only schema shows `order_items.quantity numeric` and `market_products.stock_quantity numeric`; Market mapping uses composite product/shop FK. Existing Market and Grocery item stock triggers are SECURITY DEFINER.
- PR #576 changed inventory PGlite quantity to numeric and adds fractional generic rejection plus fractional Market ledger decrement assertions. Exact RC `39647073978` passed; merged at `a4f12dd39b751e7e05a2d37b54e47863d477f391`. No Production DDL/data write.


## 2026-10-21 POS numeric quantity compatibility — merged
- Production schema readback confirmed `order_items.quantity numeric`, `market_products.stock_quantity numeric`, composite product/shop FK and SECURITY DEFINER Market/Grocery stock triggers.
- PR #576 changed the generic inventory PGlite fixture to numeric; fractional generic quantity is rejected while fractional Market quantity affects only the Market ledger. Exact RC `39647073978` passed all 12 steps. Merged to main at `a4f12dd39b751e7e05a2d37b54e47863d477f391`; post-merge main RC `39647725266` passed and Pages deploy `39647725023` succeeded.
- Production migration/data remain untouched. Latest migration `20261021133330`; inventory tracking column absent until ordered feature migrations. Cashier/device and controlled migration gates remain open; QueueGo/POS NOT DONE.


## 2026-10-21 main RC and POS Production preflight
- PR #577 merged main SHA `e70961d5b70fa7f84519fbebf473659603e35132`; exact PR RC `39648478732` passed. Main RC `39649126036` first failed Admin WebKit assertion at `tests/admin-boot-browser.cjs:69`; retry of exact failed job passed every step. Pages deploy `39649125931` succeeded. First-attempt failure is retained; no source runtime edit was made.
- Supabase read-only: migration history ends `20261021133330`; POS inventory chain `20261021211800`–`20261021233300` and cash movement migration `20261021211709` are unapplied. Existing Grocery/Market stock triggers remain authoritative. Generic POS tables and triggers are absent. No Production mutation.
- Activation gate: authorized cashier/device flow, ordered migrations, overlap preflight after 330, post-apply ACL/RLS/trigger readback, then real sale/cancel/refund/adjustment regression. QueueGo/POS NOT DONE.


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


## 2026-10-22 POS Product Code Production receipt
- Main: `bc8bdc173a8b798e84dee09833b76caf8b546614`. PR #617 exact RC `39769241578` PASS, merge `30865060c4238ecbaf2b2ece83a108caeab3b454`; Production migration `20261022113556`.
- PR #618 exact RC `39772099278` PASS, merge `bc8bdc173a8b798e84dee09833b76caf8b546614`; Production FK index migration `20261022114328`.
- Readback: registry empty, RLS on, direct anon/authenticated table access denied, four guarded authenticated-only RPCs, POS storefront price lookup, audit writes in save/remove. Product Code FK lints resolved; no sample transactions created.
- Still open: generic inventory chain, authorized cashier/device end-to-end and remaining POS Release Gate. POS is NOT DONE.


## 2026-10-23 Laundry photo cache follow-up
- Owner tested on iPhone and still saw only the empty photo frame after #688. Read-only Production edge logs at 2026-10-23 13:20 UTC show the Laundry catalog RPC returning HTTP 200 (478-byte response), but no subsequent `shop_profiles?select=id,public_logo,public_cover...` request. Current Production has an active registered Laundry shop with both photo fields populated; both public Storage image URLs return HTTP 200 image/jpeg. This confirms the browser was serving the prior Laundry HTML from cache rather than executing the photo-fetch code.
- Cause: the standalone page URL kept the same `?v=20261023-dir1` cache key across the photo-code deployment. Bump all Customer Home Laundry links to `?v=20261023-dir2` so navigation fetches the current HTML; regression asserts the cache key.
- No Production data/schema changes. NEXT: exact-head RC for the bounded cache-bust change, merge, verify Pages serves `dir2` and the current photo-fetch code, then owner re-tests via Customer Home.


## 2026-10-23 Customer Laundry shared storefront
- Owner screenshot confirms Laundry listing still uses a standalone layout with a blank image frame; owner asked it to follow the same shop pattern as food/cafe and vary only the inside menu.
- Implementation: route Laundry listing and shop detail through the Customer SPA; render the directory with shared `shopCard` and use the same profile cover/header on detail, while Laundry services and order submission remain Laundry-specific and use `queuego_laundry_customer_catalog` / `queuego_place_laundry_order_v2`.
- No Production schema/data changes. NEXT: exact-head RC; merge and deploy Pages; verify the live Customer route/layout and owner-device view.

## 2026-10-23 Nationwide Rider registration map pin
- Started from local QueueGo Places successor `43e8eb125f8b0ecdaeadb7f8545ab0b92c3c5ed5`; fetched `origin/main` again and confirmed latest SHA `0c22c1dc98ed6aa793483aa90ad8dfc8007486a8` remains an ancestor. Remote branches were checked; no newer QueueGo Places successor exists. GitHub CLI is unavailable in this environment; earlier GitHub connector search found no QueueGo Places successor PR.
- Implemented Rider registration step-2 QueueGo Maps with Thailand-wide coordinate validation, shared QueueGo Search outside the map, selectable result recentering, drag-center pinning, optional GPS and lifecycle cleanup. Selected pin persists into existing `rider_profiles.latitude/longitude`; no migration or direct Production write.
- Added `tests/rider-registration-map-runtime.cjs` (13 checks) to `npm test`; updated map build-marker regression while preserving the prior Ride-fix marker identity.
- Exact source commit: `299defd88848c920efa7bb91b51e628527361639`. Full `npm test` EXIT 0 on the identical tree before commit; focused Rider pin checks 13/13, nationwide registration checks 15/15, and `git diff --check` PASS. No hosted exact-head RC, live QueueGo Search backend, Production write, authenticated signup, or physical-device test is claimed.
- Push/PR remains blocked by the previously recorded GitHub HTTPS authentication failure. Supabase migration creation remains blocked by auto-review on CLI telemetry; no workaround or Production migration was attempted. QueueGo nationwide mission remains NOT DONE.
- NEXT: verify one search authority contract across Customer/Merchant/Rider; continue architecture/importer work that does not require DDL, and resume schema-dependent work only through an approved migration path.

## 2026-10-23 Map View QueueGo Search authority
- Replaced Map View's Buriram-only place search fallback chain (regional Nominatim viewbox, regional Overpass POI query, and pinned Buriram catalog) with the shared `QueueGoMapWeb.search` → `qg_search_places` API. Nearby category queries now use the same QueueGo Places API. Coordinate search remains local and exact.
- Removed the DOPA Buriram-only village catalog and Buriram province defaults from address entry. Initial viewport remains Buriram for launch-market convenience; map/search coordinates and results are not region-gated.
- Tests: `map-view-platform-core.cjs` 52 PASS; `map-view-routing.cjs` 33 PASS; `queuego-search-runtime.cjs` 16 PASS; full `npm test` EXIT 0 on exact source commit `721da7bd2597478e3f0d7df590a8e9d98cd517d0`; `node --check tests/map-view-search-browser.cjs` and `git diff --check` PASS.
- Updated browser integration now stubs the central RPC and proves a Phuket result recenters the map and adds a marker. Local browser execution was unavailable because Playwright WebKit executable `webkit-2248` is not installed; exact-head hosted RC remains pending. Existing QueueGo Search migration/function has not been deployed, so this is source integration evidence only; Production search availability is not claimed.
- No database mutation. Local commit remains unpushed because `git push` cannot read GitHub HTTPS username in this environment; GitHub search finds no open QueueGo Places successor PR. Mission remains NOT DONE.
- NEXT: add a Node runtime test for Map View result normalization/selection; then continue unblocked data source and service-area design work.

## 2026-10-23 Map View QueueGo Search runtime coverage
- Added `tests/map-view-search-runtime.cjs` to the full `npm test` chain. It executes the actual Map View `engineGeocode` and `searchPlaces` functions with a mocked QueueGo Search response and verifies the shared RPC options, returned QueueGo provider, nationwide Phuket coordinates, map recenter, and marker selection.
- Focused runtime test: 10 PASS. Full `npm test` EXIT 0 on the identical source tree; `node --check` and `git diff --check` PASS.
- Exact source commit: `0674446c1a9515f37172d272c26809377e17f304`. Browser WebKit integration remains unavailable locally because `webkit-2248` is not installed; exact-head hosted RC is pending. No Search backend migration is applied and no Production behavior is claimed.
- NEXT: review the current Buriram route-service gate and design a scalable availability authority that separates nationwide map/registration from regional order enablement.

## 2026-10-23 Nationwide service-area design checkpoint
- Reviewed `QueueGo-Pilot-Service-Area.sql` and the existing `tests/service-area.cjs`. The active source model seeds a single Buriram 20 km center/radius with 15 km direct-route cap; `queuego_service_area_route_status` requires pickup and dropoff inside that circle, and Food/Market/Laundry checkout calls the server guard. This remains the current Production blocker.
- Added architecture proposal `docs/NATIONWIDE-SERVICE-AREA-ARCHITECTURE.md`: independent `MAP_AVAILABLE`, `REGISTRATION_AVAILABLE` and `ORDERING_AVAILABLE`; order polygons/admin-area references; explicit allow/deny specificity; separate direct-distance cap; server-authoritative route decisions; staged shadow/readback/switch/rollback sequence. It does not create or apply DDL.
- Schema work remains blocked by Supabase migration-creation auto-review concerning CLI telemetry. No alternate path was attempted and no Production write occurred.
- Current local branch remains a descendant of fetched main `0c22c1dc98ed6aa793483aa90ad8dfc8007486a8`. Latest tested implementation source `0674446c1a9515f37172d272c26809377e17f304` passes full `npm test`; local Playwright WebKit remains unavailable. Push/PR remains blocked by missing GitHub HTTPS authentication.
- NEXT: continue source-only QueueGo Places provenance/data coverage and Rider phone/no-show authority audits; retain the blocked Production order-area gate until approved migrations, advisor review and exact-head RC are available.

## 2026-10-23 Thai admin importer provenance-key alignment
- Reviewed the generated importer SQL against both Places migrations. The importer `ON CONFLICT(place_id,field_name,source,source_record_id) WHERE source_record_id IS NOT NULL` matches the partial unique index created by `20261023195500_queuego_th_admin_source_registry.sql`; no importer SQL change or Production write was needed.
- Strengthened `tests/th-admin-import.cjs` to assert the conflict target and the migration index definition stay aligned.
- Exact source commit `06eb3e02a3539a834a6c290e56854841bf1ff12a`: full `npm test` EXIT 0; importer test 26 PASS; QueueGo Places schema 60 PASS; shared Search 16 PASS; Rider pin 13 PASS; Map View Search runtime 10 PASS; nationwide registration 15 PASS. `git diff --check` PASS.
- No imported data, migration apply, Production write, hosted RC or browser device evidence. Push/PR remains blocked by missing GitHub HTTPS credentials. The QueueGo nationwide mission is NOT DONE.

## 2026-10-23 Rider snapshot customer-phone minimization
- Source audit found `rider/reliability.js` serialized `S.activeOrder._customer.phone` into Rider localStorage snapshots and restored legacy snapshots without scrubbing it. This could preserve a phone from an older/enriched payload even though current ordinary Customer enrichment selects only `id,name`.
- Changed snapshot writes to omit Customer phone and made restore scrub/rewrite any legacy snapshot before placing its order into Rider state. Added runtime coverage for both fresh and legacy snapshots.
- Exact source commit: `938051b1adbdd3ada91828f27f538996ffdab661`. Focused Rider reliability 38 checks PASS; voice-call contract PASS; full `npm test` EXIT 0; `git diff --check` PASS. No schema/Production mutation or fallback-call capability added.
- NEXT: continue source-only search for Rider phone paths and no-show flow; implement server-authoritative phone fallback/no-show only through an approved migration path. Keep current Buriram checkout gate recorded as a release blocker.

## 2026-10-23 Production Rider authority and advisor audit
- Read-only Supabase inspection of active QueueTech project confirmed `queuego_service_areas` still has only the active Buriram Pilot row (center 14.99292,103.10804; 20km radius; 15km delivery cap). Nationwide ordering remains blocked by the live route guard.
- Production `qg_call_sessions` has `status`, `answered_at`, `ended_at`, 45-second `ring_expires_at`, and server RPCs. `qg_call_active`/`qg_call_answer` change expired ringing calls to `missed`; accepted calls have `answered_at`; caller-ended ringing calls are just `ended`. Table RLS denies all direct client access. No call rows existed at audit time. This gives a sound counting basis (`missed` with no `answered_at`) but no 10-failure counter or phone entitlement RPC exists.
- No-show RPC/audit table is absent. Existing `qg_rider_mark_arrival` requires assigned rider and delivery-stage order, but GPS is optional/advisory and its arrival timestamp alone is not verified proximity evidence. It is insufficient for customer suspension.
- Read-only Supabase advisors checked at 2026-10-23 16:06 UTC: security findings include 10 anon-executable and 186 authenticated-executable SECURITY DEFINER functions, 25 RLS-enabled/no-policy info findings, and leaked-password protection disabled; performance reports 18 unindexed FKs, 81 unused indexes, 57 multiple-permissive-policy warnings, and 3 no-PK backup tables. These are project-wide existing findings; no advisor remediation or DDL was performed in this scope.
- Read-only Production check also confirmed `public.qg_places`, aliases, field-source table, and `qg_search_places(...)` are all absent. The local Places migrations/importer remain source-only; there is no live QueueGo Places Search backend or imported national hierarchy.
- Rider privacy readback found no `phone` column on Production `orders` or `deliveries`; assigned-order RLS is scoped by Rider profile, while `qg_call_sessions` has explicit no-client-access policy. This supports keeping customer phone behind a future entitlement RPC.
- No Production data or schema was modified. NEXT: keep phone fallback/no-show and national order-area DDL blocked until the approved migration path is available; retain the exact source authority evidence for the implementation plan.

## 2026-10-23 Rider direct-phone dead path removal
- Removed the unused `qgTelHref(cust.phone)` derivation from the Rider delivery confirmation flow. There was no rendered phone link, and the global `tel:` lock still blocks every direct phone URI. QueueGo Voice remains the active contact path.
- Added web voice contract guards that ordinary Rider UI cannot derive/render a Customer direct phone link and that the `tel:` lock remains installed pending server entitlement.
- Exact source commit `f20d40314e7bbaedd18d8a0745c104eb84723228`: full `npm test` EXIT 0; focused `web-voice-call-contract` PASS; Rider action UI 112 checks PASS; `git diff --check` PASS.
- No direct phone fallback was enabled. NEXT: server-authoritative phone fallback and verified cash no-show remain blocked on approved migration path; keep all contact private until entitlement RPC exists.

## 2026-10-24 DOPA village GIS source registration
- Identified DOPA dataset `gis-01`, “ข้อมูลที่ตั้งและสภาพทั่วไปของหมู่บ้านใน 76 จังหวัด”. Catalog metadata lists JSON resources and GIS/LAT/LONG plus village, subdistrict, district and province fields; its license is Open Data Common. Bangkok is not part of the 76-province village dataset.
- Registered `th_dopa_village_gis` in the existing source-registry migration with storage/redistribution permission, DOPA attribution, refresh/checksum/source-record requirements, and an explicit rule to omit population attributes. Source reference: https://catalog.dopa.go.th/dataset/gis-01 ; Open Data Common terms: https://gdhelppage.gdcatalog.go.th/p00_03_new7.html .
- Exact source commit `e0781def6109c1c89de70664b16a7d903ba24b5b`: importer/source registry 29 checks PASS; Places schema 60 PASS; full `npm test` EXIT 0; `git diff --check` PASS. No source data imported and no Production schema/data changed.
- NEXT: obtain and checksum an actual DOPA province JSON resource and data dictionary through the permitted data access path, then implement a format-verified village importer with record-level provenance and dedupe. Do not infer coordinates/IDs from catalog tags alone.

## 2026-10-24 DOPA village importer source checkpoint
- Confirmed the DOPA GIS-01 catalog lists province-separated JSON resources and Open Data Common; published resource dictionary names `pcode/pname`, `acode/aname`, `tcode/tname`, `mcode/mname`, `lat/long`. The DOPA catalog download endpoint returned HTTP 503/timeout from this environment; the actual resource bytes and checksum therefore remain unverified.
- Added `scripts/import-dopa-villages.cjs`: requires a local resource, official DOPA catalog resource URL, retrieval timestamp and exact SHA-256; checks province identity, required hierarchy/village codes and names, duplicate village IDs, and Thailand coordinate ranges. It accepts only the published public fields and drops all unrelated population/questionnaire attributes. Generated SQL uses stable DOPA village keys, links subdistrict by code, upserts place/source/provenance/Thai alias rows, and records each resource URL/checksum in registry history.
- Added `tests/dopa-village-import.cjs` to the full `npm test` chain. Exact code commit `78a37cd33345a58c0228d1b00ce18fa3c86e65d0`: full `npm test` EXIT 0; synthetic DOPA-shaped importer 17 PASS; `th-admin-import.cjs` 29 PASS; Places schema 60 PASS; `git diff --check` PASS. This is fixture-level evidence only; no DOPA row was imported and no database schema/data changed.
- Source was continued from newest fetched `origin/main` `0c22c1dc98ed6aa793483aa90ad8dfc8007486a8`; its exact tree `af7e281180c935fac870950da2d9ec17622d54cf` is published as draft PR #697 (head `a788413c14d412173dc5d9091db9943632ee76c0`). Local full suite passed against that tree before publication; hosted exact-head RC/deploy is pending. Production places/search are still absent, Buriram checkout gate remains, Rider phone/no-show RPCs remain unimplemented and Supabase advisors still require remediation. Mission is NOT DONE.
- NEXT: acquire and checksum a real DOPA province JSON using an approved access path, validate counts and importer output, then continue through approved migration/release and security gates. Keep order-area, phone fallback and no-show schema work blocked while migration creation approval remains unavailable.

## 2026-10-24 DOPA source timestamp correction and PR checkpoint
- Source commit `21e24b0e` leaves `source_updated_at` unset because the published resource update date is not a per-row fact; retrieval timestamp and SHA-256 remain recorded in resource history and field observation time. DOPA importer fixture now covers 19 assertions, including this distinction.
- Full `npm test` EXIT 0 and `git diff --check` PASS on the tree containing source commit `21e24b0e`.
- GitHub draft PR #697 is open from the newest-main descendant branch. The published branch was fetched back and its tree hash matched local exactly. Hosted RC was running for the published head; source timestamp correction may require the next workflow run. No merge/deploy or Production database changes.
- NEXT: wait for hosted exact-head QueueGo RC on the latest branch head; fix only the first failure. Continue only after approved migration route, actual DOPA resource checksum/data validation, advisors, phone fallback/no-show authority and Buriram gate redesign are complete.


## 2026-10-24 QueueGo nationwide successor exact-head RC
- Hosted QueueGo RC run `40052035925` / run `3228` passed on exact head `8d0654223dc77ae2e13a7dc5160e304c12e0eb3f` (tree `4b7bc7d83bcdce6b998203b4bcd7988d4fdc1f4d`). `npm ci` and the full `npm test` passed.
- Hosted mobile WebKit gates passed: Admin fast boot/section isolation, complete Merchant document boot, Map Lab hit targets/pan/zoom/sheet gestures, QueueGo Map place search and Grocery responsive rendering. This does not certify a physical device.
- Local full `npm test` also passed on the same source tree before publication; local/remote tree hashes matched after fetch. No Production DDL/data writes or deployment. PR #697 remains draft.
- Mission remains NOT DONE: actual DOPA resource checksum/import, approved Places/service-area/phone/no-show migrations, Production security/performance advisor remediation, end-to-end live backend acceptance, and Web deploy to Owner remain outstanding.
- NEXT: continue from PR #697 head only; resolve approved migration path, real DOPA data validation and server authority gates. Re-run exact-head RC after each source change; do not merge/deploy or claim full release readiness while the listed blockers remain.


## 2026-10-24 QueueGo RC run 3229
- Hosted exact-head run `40052486098` / `3229` passed on PR head `42fbfd3632f47ef07b4158c71b6f11c6af179b03` (tree `22eff897e90f8826c8753092a3fd2f7cdf9fd7c7`). The complete `npm test` and all configured Admin, Merchant, Map Lab, QueueGo Map search and Grocery WebKit gates passed.
- This head includes the DOPA importer timestamp correction and QueueGo checklist/next-action updates. It was fetched from GitHub and its tree hash matched local before the run. No physical-device pass, Production mutation, merge or deployment is claimed.
- NEXT: complete approved migration/security gates, actual DOPA province data/checksum/import, phone fallback and verified no-show server authorities, and replace the Buriram ordering gate. PR #697 stays draft; QueueGo mission stays NOT DONE.


## 2026-10-24 Nationwide successor reconciled with latest main
- Startup sequence followed: reread `WORKFLOW.md` and `CHECKLIST.md`, fetched `origin/main`, inspected the active PR #704 and successor branch. Latest main was `fddf53d165750b341d85cdc22ea5df157d5b6681` (#705); active #704 remained open/draft at `ae9828f58f6f65311cde2ee0729798082bba77ef`.
- Forward-merged both the current #704 successor and latest main. Main's `merchant/index.html` had reintroduced an explicit `province=eq.บุรีรัมย์` filter in the Merchant market picker. Resolved by retaining the nationwide all-active-market query from the successor and combining it with the latest Add Menu action layout. No newer Merchant/Laundry/Rider code was discarded.
- Reconciled exact source HEAD: `5cb58e3cb91160f1798f4d50a25c187328865374` (tree `8411ebcc30ff3e90413de416fd905784cd204796`). `git merge-base --is-ancestor origin/main HEAD` and `origin/feat/queuego-places-search-successor-20261024 HEAD` both pass.
- Evidence: nationwide registration 15 PASS; Merchant product reliability 90 PASS; full `npm test` EXIT 0; `git diff --check` PASS. Test output includes source/fixture tests only; no Production migration/import or live backend certification.
- No Production writes/deploy. Actual DOPA payload remains unavailable (official endpoint previously returned HTTP 503); Places/Search migrations remain absent from Production; Buriram order-area gate, phone fallback, verified no-show, and advisor remediation remain outstanding. GitHub HTTPS push credentials are unavailable; publish through GitHub connector compare-and-swap and run hosted exact-head RC.
- Status: IN PROGRESS. Mission NOT DONE.
- NEXT: publish `5cb58e3cb91160f1798f4d50a25c187328865374` onto PR #704 successor only if its current head is unchanged; otherwise fetch and reconcile forward first. Verify hosted exact-head RC. Then continue real DOPA payload validation, approved migration route, Production advisor and end-to-end gates.


## 2026-10-24 Nationwide successor RC and DOPA payload access — IN PROGRESS

- Verified current main `28a121178b6e15ceb0a7575452b5cb2a374f40a3`; active successor PR #704 remains open, draft and mergeable. Its forward-merged exact source head is `e34c368716be12069fe1848e17c44a0c46800791`.
- Exact-head QueueGo RC run `40088891010` completed SUCCESS on `e34c368`: full `npm test`, Admin and Merchant mobile WebKit, Map Lab gestures, QueueGo Map search and Grocery mobile/tablet gates all passed. This is hosted source/CI evidence; no Production import, deployment or physical-device visual acceptance is claimed.
- Official Thailand government portal metadata for the DOPA GIS-01 Nakhon Ratchasima JSON resource reports Open Data Common, 9 MB, data last updated 9 June 2025, but datastore is inactive and does not contain all source records. The official DOPA download still did not return payload bytes in this environment. No exact SHA-256, validated row count or import-eligible receipt exists; do not import the mirror metadata or infer coverage from it.
- The shared nationwide Places/Search implementation and DOPA importer remain source-only. Production Places/Search schema and RPC are absent; Buriram is still the sole active ordering area; Rider phone fallback/no-show authority, Production advisor remediation and live backend acceptance remain open. No Production writes or deployment.
- **NEXT:** obtain the original province JSON and data dictionary through an approved official access path; verify exact bytes, SHA-256, province identity and importer row counts before recording any resource as import-eligible. Continue read-only schema/security and service-area planning while access is blocked. Keep mission NOT DONE and do not claim nationwide service readiness.


## 2026-10-24 Production Places, service-area and RPC recheck — IN PROGRESS

- Read-only QueueTech project inventory: no QueueGo Places relation and no `qg_search_places` RPC. `queuego_service_areas` has exactly one active province: Buriram. This confirms nationwide search source is not live and ordering coverage is still pilot-only.
- Existing `qg_call_*` RPCs are SECURITY DEFINER with fixed `search_path=public,pg_temp`, anon EXECUTE false and authenticated EXECUTE true. No phone-fallback or no-show authority RPC was found. This inventory does not validate rider/customer runtime journeys.
- Supabase advisor snapshot (2026-10-24): 25 RLS-enabled tables without policies, 10 anon-callable SECURITY DEFINER warnings, 18 unindexed foreign keys, 3 tables without primary keys and 79 unused-index notices. Findings need table/function intent review; no blanket policy/grant/index changes were made.
- DOPA registry on PR #704 lists 76 province JSON resources; all 76 lack exact payload SHA-256 and validated row count and remain `import_eligible=false`. Government portal mirror metadata for one JSON resource states datastore inactive/incomplete. No data import or Production write.
- PR #704 documentation head `779fa7b7fc11f310c8897042afff846f5df94afe`; exact QueueGo RC run `40089120095` SUCCESS across npm and all browser gates. Source head `e34c368716be12069fe1848e17c44a0c46800791` also passed exact-head RC `40088891010`.
- **NEXT:** obtain and hash an original DOPA province payload via an approved official access path; validate it against the published dictionary before staging. Until then continue only read-only schema/security planning and owner-side map review. Keep PR #704 draft and QueueGo mission NOT DONE.


## 2026-10-24 Nationwide successor checkpoint — exact-head CI verified

- Current main remains `28a121178b6e15ceb0a7575452b5cb2a374f40a3`. Active PR #704 remains open, draft and mergeable. Latest source-reconciliation commit `e34c368716be12069fe1848e17c44a0c46800791`; latest verified documentation head `f876a6761c7d01631fa4bea699024f3152285962`.
- Exact-head RCs passed: `40088891010` on source reconciliation and `40089120095`, `40089392407`, `40089654836` on documentation successors. Each completed npm and all configured Admin/Merchant mobile WebKit, Map Lab, QueueGo Map search and Grocery mobile/tablet gates.
- Current DOPA registry covers 76 provinces but has zero raw payload SHA-256/validated row count receipts. Production read-only inventory confirms no Places relation or search RPC; only Buriram has an active order area. No phone-fallback/no-show authority exists. Advisor review remains open. No Production mutation, data import or deployment.
- Map responsive changes are source-covered by CI; physical iPhone Safari acceptance remains owner-side. The website route itself loads publicly, but no physical mobile map appearance is certified.
- **NEXT:** retrieve the original DOPA JSON and dictionary through an approved official access path, hash and validate province identity/schema/count, then stage only after provenance checks pass. While access is blocked, continue read-only release/security planning and owner-side map review. QueueGo remains NOT DONE; keep #704 draft until all deployment gates pass.


## 2026-10-25 Production QueueGo Places hierarchy + exact admin ranking — IN PROGRESS
- Continued from nationwide successor PR #704 only and preserved the latest merged Rider Web compass on the successor (`06a0a6770122f041a7775be9acfd08ae9950dc07`).
- Applied Production Places/Search foundation and Thailand source registry to QueueTech through Supabase migrations. Verified `queuego_places` and `qg_search_places` are live; direct anon table SELECT remains false while the bounded search RPC is executable by anon/authenticated.
- Retrieved the pinned Open Admin Data Thailand snapshot at repository commit `eef739a534e26181c86073d69e9aeb527110cf1a`. Exact source blobs matched the pinned SHAs: `baa3eb18fb79412e85dcef08059d16f8cf47b317` (levels 1-2) and `bd5c49a7e1af578c9e34e32020ddd90eabdc167f` (level 3).
- Validated and imported the complete hierarchy into Production with provenance/source records: 77 provinces, 928 districts, 7,364 subdistricts; subdistrict orphan count = 0. No mock/demo data was used.
- Live acceptance exposed an exact-name ranking defect: searching `เชียงใหม่` could return the identically named subdistrict in Roi Et above Chiang Mai province. Added migration `20261025063500_queuego_places_admin_exact_rank.sql` and regression coverage; source/test HEAD `84fc04a599fb70c0ad6c5cc94232818d984697db`. Production migration applied successfully and live searches now return the province first for Chiang Mai, Bangkok, Nakhon Ratchasima and Ubon Ratchathani.
- Status remains IN PROGRESS: villages/POIs and nationwide ordering service-area authority are not complete; DOPA village payloads still require exact official bytes/checksums before import.
- NEXT: replace the single Buriram ordering-area gate with the documented nationwide service-area architecture, preserve operational safety semantics, add tests, then run exact-head RC before continuing POI/village ingestion.


## 2026-10-25 Nationwide order coverage cutover + operational POI sync — IN PROGRESS
- Added Natural Earth 1:10m Thailand Admin-0 boundary using pinned repository commit `ca96624a56bd078437bca8184e78163e5039ad19` and exact GeoJSON blob `5ebc66e25fc1af01edaebe9375c546655e04cf1e`. Natural Earth terms were reviewed as public-domain compatible; source/provenance is stored in `queuego_place_sources`.
- Added `queuego_country_capabilities` and versioned `queuego_order_service_area_rules`; Thailand map + registration are independent capabilities and nationwide ordering is represented by a real multipolygon with a separate 15 km route cap. Source migration commit chain includes `95660092899c31fdecc4e1f5584fed43c27196bc` and service-area regression coverage `ada2a1b8c2be2f5101ac30f44adf62cbf73e7791`.
- Shadow acceptance compared the legacy Buriram circle with v2: Buriram remained OK; Bangkok, Chiang Mai and Phuket changed from `OUTSIDE_SERVICE_AREA` to `OK`; a Singapore case remained `OUTSIDE_SERVICE_AREA`; a ~21.4 km Bangkok route remained blocked as `DELIVERY_DISTANCE_EXCEEDED`.
- Cut over the existing `queuego_service_area_route_status` contract to the v2 polygon authority while preserving `area_id`/`area_name` compatibility. Production migration succeeded and the same acceptance matrix passed after cutover. Source cutover regression HEAD: `3e0bbc717052419451d9d104e77797278afab92e`.
- Materialized the existing single-authority Shop/Market Places generator into migration `20261025080000_queuego_operational_places_runtime_sync.sql`, applied it to Production, installed runtime triggers, and backfilled only existing eligible QueueGo operational records. No fake/demo record was created. Regression source HEAD: `9267e77bb1449a4c07018e6f65e968770a82777a`.
- DOPA GIS-01 official resource endpoints remain unreachable from this execution environment; DNS/download access failed again on the exact official resource URL. Village import remains BLOCKED on exact official payload bytes + SHA-256 + validated counts. Mirrors/metadata are not accepted as substitutes.
- Hosted exact-head RC has not yet been created for the latest successor commit; do not claim source CI PASS until GitHub reports an exact-head run.
- NEXT: run/obtain exact-head RC for the latest successor, review Production advisors specific to the new Places/service-area surfaces, verify public search/route RPC permissions and representative runtime calls, then continue OSM/verified POI ingestion while DOPA official payload access remains blocked.
