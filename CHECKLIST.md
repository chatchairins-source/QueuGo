## 2026-10-24 Merchant Add Menu bottom action — reconciled latest successor

- Source base: PR #697 advanced to `a751de50ab41246d2f2151f865229705c5399f7a` while this fix was being validated; Merchant/test blobs were re-read from that exact latest successor before reconciliation.
- Owner request: restaurant/cafe product management uses `เพิ่มเมนู` and the action sits at the bottom above Merchant navigation.
- ROOT CAUSE: iPhone product CSS overrides `.qgm-fab` to `position:sticky`, leaving the action directly after the cards.
- RECONCILED: the dedicated `.qgm-menu-add-action` is applied onto the exact latest Merchant file; Grocery keeps `เพิ่มสินค้าเอง` / catalog product actions unchanged.
- TEST EVIDENCE: predecessor exact-head RC `40054555578` on `a2349a53d0728f60f61cbe4d326e95ecbad8bddb` passed npm, Merchant mobile WebKit, Admin mobile, Map Lab, QueueGo Map search and Grocery browser gates.
- IN PROGRESS: this merge successor requires exact-head RC because PR #697 advanced during validation.
- No Production data mutation.
- NEXT: exact-head RC on the reconciled merge successor, then merge PR #698 into PR #697 and continue the latest successor line.

Warning: truncated output (original token count: 59206)
Total output lines: 1386

## 2026-10-24 Nationwide map POI authority checkpoint

- Source change: `queuego-map-web.js` no longer fetches or searches a bundled Buriram POI catalog. Map viewport POIs, shared text search, category lookup and nearby lookup now use only `qg_search_places`; map search overlay mounting returns null, leaving Search outside the embedded map. Removed `data/maps/buriram-places.json` and updated its README. Search fails closed when shared QueueGo Search is unconfigured; no local regional or unconfigured geocoder fallback.
- Successor PR #697 branch `feat/queuego-places-search-foundation-20261023`; latest source commit `ec713aed763deb0bcb3bee9c9002280fddf89e5b` locally. Equivalent source tree is published at commit `629b158fc7989f32c907e1b119ff820bfad2eb14` (tree `6552f7d96180ab2dfc02e9b3197af1034ad51b7f`) directly after exact prior PR head `06c7f4d1c95671c233a2765dab78711a887fb53b`; GitHub exact-head RC run `3231` / `40054522196` is in progress. The prior RC #3230 is not evidence for this source change.
- Verification on local exact source tree: focused map catalog/search/map UI/runtime checks PASS (4/8/5/16), `node --check queuego-map-web.js` PASS, full `npm test` exit 0, `git diff --check` PASS. These test mocked QueueGo Places; Production RPC/table availability remains absent, so live nationwide POIs/search are not yet available.
- Tile deployment review: client still uses OSM standard raster tiles with visible attribution. The official OSM tile policy documents best-effort service/no SLA, requires correct attribution, normal interactive-only requests and cache compliance, and recommends switching/self-hosting for unsuitable production use. No owned tile infrastructure or licensed service credentials are provisioned in this repo; do not bulk-download public tiles or silently introduce a paid provider.
- Mission remains NOT DONE. Nationwide Places/admin schema is source-only and not imported/deployed; actual DOPA village data/checksum remains unavailable; Buriram order-area gate remains in Production; server-authoritative phone fallback and no-show suspension are not implemented; exact-head CI/security advisors/live backend acceptance/Web deploy remain outstanding.
- NEXT: verify exact-head run #3231; publish this checkpoint after source RC; then continue from newest PR #697 successor to the approved Supabase migration path and live nationwide Places/service-area authorities. Keep source ingestion blocked until a real licensed DOPA resource is retrievable and checksum-verified. Do not merge/deploy or claim full service readiness while those gates remain.

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

- Exact-head run `39052949572` on `e8cf3f4df2fc72f7b0e76043afed08a16b512eef` passed Native Rider route-bundle parity, then failed first in `tests/rider-return-to-sender.cjs…29206 tokens truncated…nary product stock remains products.stock. Missing values are shown as unknown.
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
