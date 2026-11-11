Warning: truncated output (original token count: 123553)
Total output lines: 2520

## 2026-11-11 Rider Fused GPS capture transport — IN PROGRESS

- Exact-head Rider offline-routing run `42954010332` passed the real installed Valhalla route, cold-process restart, country rendering and GPS-camera instrumentation, and software-renderer comparison. Its later Fused GPS acceptance reached screenshot capture after the instrumentation returned successfully, then failed because `adb exec-out screencap -p` timed out after 30 seconds; `adb emu kill` also timed out and masked the original failure during cleanup.
- The failure is in screenshot/ADB transport handling, not evidence that GPS passed or failed. Added a bounded retry helper for genuine PNG captures, preserves each timeout in the artifact, adds cleanup-timeout handling, and tests recovered timeout, exhausted timeout and invalid screenshot output. The GPS assertion remains unchanged and a screenshot remains required.
- **NEXT:** exact-head offline-routing CI must pass the Fused GPS step; only then retain the exact-source transfer candidate. Physical road/GPS acceptance, Firebase background push and signed release remain separate gates.

## 2026-11-10 Relative PBF mount path correction — PENDING EXACT-HEAD CI

- Run `42829494568` passed full npm regression, Express PostgreSQL race tests, and Customer/Merchant/Rider Kotlin/JVM + Debug APK package-integrity jobs. Planetiler parsed the staged ocean file successfully, then failed because the PBF symlink target was an absolute host path outside the container's `/data` mount.
- Changed the workflow symlink to a basename-relative target inside the mounted directory; regression now requires the relative form. Need exact-head proof of map generation and downstream offline/APK gates. No Production changes.

## 2026-11-10 Planetiler 0.10.2 ocean input CLI corrected — PENDING EXACT-HEAD CI

- Mirror download succeeded in run `42827962253` and Planetiler started. The build then used its default `ocean_path` and failed because the staged zip was not passed through; this exposed that the 0.10.2 Shortbread profile accepts `--ocean-path`, not the generic water-polygons config option.
- Changed builder/test back to `--ocean-path=/data/<staged-file>`; a local exact-source dry-run will guard it. The next exact-head run must confirm archive generation, installed offline runtime and APK export. No Production changes.

## 2026-11-10 Planetiler water polygon CLI contract correction — PENDING EXACT-HEAD CI

- Source review of Planetiler config names found the supported input key is `water_polygons_path`; changed the staged-input CLI argument to `--water-polygons-path` and updated the builder dry-run regression. Exact-head map generation must still validate the argument against pinned Planetiler 0.10.2.
- Water source outage and mirror fallback remain as recorded below; no Production changes.

## 2026-11-10 Water polygons origin outage; mirror fallback — PENDING EXACT-HEAD CI

- Exact-head run `42824979640` passed the full QueueGo regression, Express PostgreSQL races, and all three Android Kotlin/JVM + Debug APK build/integrity jobs. Native Pilot failed only at downloading water polygons: `osmdata.openstreetmap.de` timed out repeatedly; the pinned Geofabrik PBF checksum passed.
- Changed the planned download to the daily `www.osmdata.org` mirror and a dated FAU archive fallback; CI records final URL, byte count, and SHA-256. These downloads and retries still require exact-head confirmation. No map archive, installed offline runtime, offline routing or release APK is certified by this failing run.

## 2026-11-10 Planetiler remote-input timeout fix — PENDING EXACT-HEAD CI

- Run `42813653497` generated correct source inputs but Planetiler still ran with `--download` and attempted remote OSM/ocean fetches; the ocean endpoint timed out. The builder now supports explicit staged inputs, and the workflow downloads ocean data with bounded retries, disables Planetiler downloads, and records its size/SHA-256. Regression dry-run asserts `--ocean-path` is passed and `--download` is absent. Awaiting exact-head build, offline runtime, and artifact gates; no Production changes.

## 2026-11-10 Geofabrik snapshot URL retired; offline bundle source refreshed — IN PROGRESS

- Exact-head run 42812449576 on c98d2e6 passed the full QueueGo regression and independent Express PostgreSQL races. The nationwide-map step failed before generation because Geofabrik returned HTTP 404 for pinned snapshot thailand-261029.osm.pbf; no country runtime or tested APK export ran.
- Current official Geofabrik index lists thailand-261109.osm.pbf (330,076,369 bytes; data through 2026-11-09). Follow-up pins that dated URL and expected size, downloads and validates Geofabrik's MD5 sidecar, records the resulting SHA-256 in CI evidence, then generates the archive with source date 2026-11-09.
- Added regression assertions for the source snapshot, sidecar check, byte count, and rejection of the retired URL. Exact-head map build, offline runtime and artifact export remain pending. No Production data or database changed.
- Checked Planetiler's documented `--osm-path` contract. The builder reads `/data/thailand-latest.osm.pbf`; linked the checksum-verified dated PBF to that exact input path and added a regression assertion so the builder cannot silently use another input.
## 2026-11-10 Native offline-map CI trigger follow-up — IN PROGRESS

- PR #914 exact-head Native Pilot run `42808637904` completed successfully, including the full npm regression, Kotlin/JVM and Debug APK jobs, and Android launch/lifecycle capture.
- Its Thailand archive build and no-network country runtime were skipped because the bundle condition only selected the dedicated Rider offline branch, while the Customer Express fix uses a different stacked branch.
- Added a targeted follow-up branch selector and a regression wired into `npm test`; the exact-head CI must prove that the pinned Thailand archive builds, offline runtime executes, and test APK export stays behind those gates.
- Source review found the `express-races` job selected push refs but omitted `github.head_ref`, so PR-triggered Express PostgreSQL race coverage was skipped. Added the PR head selector and expanded the same regression to fail if this returns.
- No Production database or data changed. Offline-map acceptance remains pending the new exact-head CI run; Debug APKs are not Production releases.

## 2026-11-11 Customer Express quote freshness regression — IN PROGRESS

- Rechecked main at `2c44d6f9313d2efdeb6dceef4c03d3f1ea9aa27e`, PR #913 at `9b61e4dd1990dfd14a31127cb1a0d1f9cf7ead05`, and current stacked heads before refreshing this branch. The #913 source already invalidates the visible fare and disables the Rider request control immediately on input, and rejects quote responses whose live payload changed; this branch now carries that exact source instead of the stale #913 predecessor file.
- Strengthened `tests/customer-express-quote-freshness.cjs` to execute production `formPayload()`, `bindForm()`, and `action('quote')` with a deferred API response. Regression covers sender/receiver phone, weight, vehicle, pickup, destination, hides the old fare synchronously, and rejects each stale response. The test is wired into `npm test`.
- Local `node --check customer-express.js` and focused quote freshness/in-flight race regression PASS. No Production SQL, Auth, RLS, RPC, order, payment, or user data changed.
- Exact-head Native Pilot run `42941106634` passed on `5dc08fbed0cafd29b9906ee4ec32edbc13ef3909`; the `navigation-map-runtime` job was skipped, so this is not evidence for nationwide offline routing. The three APKs are Debug artifacts, and Rider Firebase configuration was absent.
- **NEXT:** retain the quote-freshness regression, complete exact-head offline-map/runtime CI, and continue Production migration/security, isolated E2E, backup, and signed Release gates. Express remains disabled in Production.


## 2026-11-11 Source-bound APK manifest — PENDING EXACT-HEAD CI

- PR #915 adds `native-android/qa/create-apk-manifest.py` to record each Customer/Merchant/Rider APK's exact size and SHA-256 with the checked-out source SHA and an explicit Debug/non-Production certification boundary. The primary Native Pilot artifact includes the manifest.
- `tests/native-apk-manifest.py` (16 checks) and workflow assertions in `tests/native-offline-map-trigger.cjs` pass locally. The manifest does not certify signed release, Firebase background push, backup restore, or offline routing.

## 2026-11-11 Offline-map navigation runtime selector — PENDING EXACT-HEAD CI

- Native Pilot run `42949957095` on `ad8514ffa6a6cd708e67be46f3f1a11cb07da76c` passed Express race, RPC catalog and all three role APK jobs, then remained in the Android lifecycle capture step. The separate `navigation-map-runtime` job was skipped because its PR branch selector omitted `work-native-express-offline-map-*`.
- Added that stacked PR branch to the job condition and a static regression so it cannot silently skip the dedicated map runtime acceptance again. Exact-head rerun is pending; no nationwide offline navigation acceptance is claimed until that job passes.
- No Production data changed. Preserve #914 ancestry and Express disabled state.

## 2026-11-11 Rider offline data in signed Play bundles — PENDING EXACT-HEAD CI

- The Rider field-test APK contains a 404 MB ODbL Thailand PMTiles map and a 1.1 GB ODbL Valhalla motorcycle graph. Putting both in the base Play module would exceed its 500 MB compressed-download limit. The signed-release workflow previously produced a Rider AAB without these offline assets.
- Added separate install-time Play Asset Packs for map and routing data. A source-bound pack manifest records source SHA, file inventory, byte counts and SHA-256. The verifier rejects missing/changed assets, wrong source, oversized packs, and map/routing payloads under base/assets.
- Release workflow also selects the already audited source-built MapLibre 13.7.1-qg4625.1 component used by offline acceptance and verifies both native-library hashes in the signed Rider APK and AAB. This prevents a release from silently reverting to the unmodified upstream renderer.
- Release certification now requires successful exact-HEAD Rider offline-routing evidence and unexpired contiguous candidate-APK parts. The signed-release job reassembles the certified APK, verifies its whole-file hash and source SHA, extracts both payloads, builds the signed Play AAB and standalone Rider APK separately, then verifies both outputs. The run ID is read from the persisted job environment at execution time.
- Full npm test passed in a clean isolated source snapshot; the added source-built MapLibre APK/AAB hash fixtures also pass independently. Targeted checks cover pack preparation, offline APK/AAB integrity, renderer identity, fail-closed manifests, release selectors and Customer Express quote freshness. Exact-source Gradle builds, install-time split runtime and post-change hosted CI are still required; this does not certify a signed release or Production readiness. No Production data/configuration changed.

## 2026-11-09 Bounded Rider offline routing metadata

- Rider bundled routing now reads manifest bytes under a strict 64 KiB cap instead of trusting `InputStream.available()`, and rejects a config manifest declaring more than 64 KiB before copying and parsing it. The actual graph remains checksum verified and offline-only.
- Added a JVM regression with an input stream reporting zero `available()` despite containing data. Source checks passed; hosted exact-head Rider JVM and Native Pilot verification remain required. No Production data or map assets changed.

## 2026-11-09 Bounded Rider offline routing metadata

- Rider bundled routing now reads manifest bytes under a strict 64 KiB cap instead of trusting `InputStream.available()`, and rejects a config manifest declaring more than 64 KiB before copying and parsing it. The actual graph remains checksum verified and offline-only.
- Added a JVM regression with an input stream reporting zero `available()` despite containing data. Source checks passed; hosted exact-head Rider JVM and Native Pilot verification remain required. No Production data or map assets changed.

## 2026-11-09 POS committed write and readback separation

- Merchant POS now preserves a successful server write acknowledgement when the subsequent snapshot read fails. The cashier sees that the action was saved and must refresh before the next action, instead of a misleading generic write failure that could prompt a duplicate cash or bill operation.
- Native source checks passed 492/0 and diff whitespace passed locally. Exact-head Native Pilot compilation/runtime remains pending. No Production order, payment or schema data changed.

## 2026-11-09 Retail barcode lookup response fencing

- Native Merchant POS now invalidates an in-flight barcode/SKU lookup when the code, shop, view or authenticated actor changes. Late success, error and loading completion can no longer overwrite a newer counter lookup. The existing shop-scoped RPC and cashier confirmation remain authoritative.
- POS add-to-bill now rechecks the current snapshot product for identity, availability and tracked stock before reusing a product selected from an older screen. PR #903 exact prior head Native Pilot CI passed; follow-up exact-head CI remains pending.
- Source regression: `node tests/native-android-source.cjs` passed 492 checks. `tests/pos-runtime.cjs` could not run in this checkout because `jsdom` is not installed; Android Gradle/SDK are unavailable locally. Require exact-head hosted Merchant build and runtime acceptance before calling this release-ready. No Production data or schema changed.

## 2026-11-09 Retail barcode scanner focus and clean regression

- Native Merchant retail POS now requests focus on its barcode/SKU field when a non-food counter opens or returns from another POS tab, then hides the software keyboard so a HID USB/Bluetooth keyboard-wedge scanner can enter a code immediately. Restaurant POS does not request scanner focus. Added source regression assertions.
- Commit `86b7301a60691f14ed445ecbd2382a8faf789e71` is published to both PR #872 branches. On the exact clean commit, Native source passed (492/0), POS runtime passed (115/0), Native release-evidence bundler passed (28/0), and release packaging guard passed. Repository test-script entries: 163 passed, 0 failed, 1 skipped; skipped `source-integrity` requires pinned `map-assets` absent from this checkout.
- Local Android Gradle/APK build remains unavailable: no `gradle`, Gradle wrapper, `sdkmanager`, or Android SDK is installed. No CI result was polled and no Production data changed.

## 2026-11-09 Local production regression pass

- On exact source HEAD `09252fb6731a842f7401cdf7ba3b0d994ab27abb`, ran the repository test-script entries directly with the existing sibling `node_modules` and skipped npm lifecycle hooks to avoid implicit downloads. Result: 163 entries passed, 0 failed, 1 skipped.
- The skipped entry is `tests/source-integrity.cjs`, which requires pinned `map-assets` absent from this checkout; the normal pretest downloads those assets from external URLs. No asset was substituted and no assertion was weakened.
- The passing entries include Rider, Customer, Merchant/POS, Admin, security, credit, map contracts and native release checks. This does not include an Android Gradle/APK build; Gradle, wrapper and Android SDK are unavailable here. No CI status was polled and no Production data was changed.

## 2026-11-08 Native retail POS copy parity

- Source review found the non-food Native POS already hid restaurant tables and Kitchen, but still labeled its catalog search “ค้นหาเมนู” and product section “เมนูสินค้า”. Web correctly uses retail product language.
- Changed Native labels by authoritative `kitchenEnabled`: restaurants see “ค้นหาเมนู” / “เมนูอาหาร”; retail shops see “ค้นหาสินค้า” / “สินค้า”. Added source regression assertions.
- Further source review found the Native category-chip row was hidden when a shop had no product categories, even if its best-seller RPC returned real sales. Match the Web behavior by rendering the row when either categories or best-sellers exist; added a regression assertion.
- Local validation: `tests/native-android-source.cjs` PASS (492/0), `tests/pos-runtime.cjs` PASS (115/0), workflow SHA pinning (118/0), Web JS syntax, workflow YAML parsing and `git diff --check` PASS. Release packaging guard must be run on a clean checkout; this edit is not APK-build verification. Local Gradle build unavailable (`gradle` is not installed; no Gradle wrapper or Android SDK in this checkout). No Production changes.

## 2026-11-08 Remove retired SDK checksum from Android test workflows

- Exact-head Customer P0 run `42483395923` completed 3/4 API jobs successfully; API 33 failed before Gradle execution because `curl` downloaded a different SHA-256 for Longdo Android SDK AAR v0.1.8 than the hard-coded `0918c236...` pin. This is an external artifact-integrity failure, not a Customer UI test failure.
- Confirmed by source search that current Customer, Merchant and Rider Gradle source no longer references the Longdo AAR; Native Rider uses the in-house MapLibre module, and release-gate tests explicitly forbid the retired SDK. The Customer P0 and Merchant grocery workflows still fetched the obsolete AAR for no build dependency.
- Removed only those stale Longdo download/hash steps; retained Android SDK provisioning. Added regression guards preventing either workflow from reintroducing the dependency; did not update the expected hash. Local source (492/0), workflow pinning (118/0), POS runtime (115/0), release packaging guard, YAML parsing, JS syntax and diff checks pass. Published as `eee556b8dc49782705f09848231b1f0928827967` to both stacked Rider branches. The branch push triggers a new exact-head workflow run; its result has not been polled or claimed.
- No application source, Production data/schema, or financial records were changed by this workflow correction.

## 2026-11-08 Retail POS counter scanner and real best sellers

- Owner clarified that barcode/SKU scanning is required during checkout for non-restaurant shops; it should not be confused with barcode/SKU management. Restore a real counter scan flow only for non-food shops in Web and Native Merchant POS. It uses the authenticated, shop-scoped `pos_product_code_lookup` RPC, checks current shop/user/bill context, POS availability and inventory, then requires cashier confirmation before adding the item. Supports keyboard-wedge USB/Bluetooth scanners and manual code entry; camera scanning is not implemented.
- Non-food POS now uses “ขายหน้าร้าน”, defaults to TAKEAWAY, and hides restaurant-only dine-in/table/Kitchen tabs and controls. Food/cafe retains the restaurant flow. Add “สินค้าขายดี · 7 วัน” based on authorized `pos_owner_item_sales` results and only filter currently available catalog products; no synthetic products or sales are created.
- Local exact-PR-base checks: `node tests/native-android-source.cjs` PASS (490/0); isolated `node tests/pos-runtime.cjs` PASS (114/0); `node --check merchant/pos-table-v2.js` and `git diff --check` PASS. These are source/runtime-fixture checks, not a Native Gradle build or live-sale acceptance. Android Gradle/SDK is unavailable in this local environment; require exact-head CI APK build and Merchant review before claiming build/UI acceptance.
- First GitHub blob publication was truncated by a tool output limit and exact-head CI correctly caught the malformed Web JS. Recreated the three affected blobs from full-length source and fast-forwarded both refs in `f5173fe84b4d226a5a9bcd468ef51b021e053bd1`; rerun all source/runtime checks and verify the new exact-head CI before treating the earlier result as delivered.
- No Production Supabase data/schema, payment, or sale was mutated. Physical scanner/printer and live cashier checkout remain unverified. Continue with an isolated test sale/reconciliation and inspect CI artifacts/screenshots; do not claim release readiness.
- Read-only Production function inspection (project `pkypiqhlrmzocysgeqew`) confirms `pos_product_code_lookup` is `SECURITY DEFINER`, executable only by authenticated callers, checks `auth.uid()`, `pos_allowed('receive_order')`, and resolves codes only inside `pos_my_shop()`. `pos_owner_item_sales` is likewise authenticated-only but explicitly owner-only. Web and Native now gate the best-seller request to owners so staff with `view_sales` do not hit a denied RPC. No orders, payments, schema, or settings were written.

## 2026-11-08 Resolve Merchant-only review mode on PR events

- Merchant visual review was still falling back to Rider credentials because `github.event.head_commit.message` is absent from pull-request events; the tagged push copy was coalesced by the shared authenticated-review concurrency group. Resolve the actual PR head commit message by its exact SHA, export `merchant_only` as a job output, and use it consistently for credential selection, Rider-artifact skip, Merchant capture, and Web comparison. The tagged `fix-rider-invalid-step-metrics-20261106` push and PR paths are enabled.
- Local Native source 490/0, POS lifecycle 99/0, workflow YAML parse and `git diff --check` pass.
- NEXT: inspect the exact-head authenticated Merchant POS + Production Web screenshots, background FCM result and Native Pilot CI. No live purchase/payment or Production mutation.

## 2026-11-08 Unblock Merchant POS authenticated visual review

- Exact POS successor `96dc1bbf19dc94f4e166d19f852442a2acd76881` has Native Pilot SUCCESS, including all three APK builds, packaging and emulator/runtime checks; Merchant Kotlin/JVM + Debug APK and authenticated Merchant hierarchy/tab visual regressions passed.
- Found the Merchant visual-review path still unconditionally fetched the nationwide offline Rider candidate before booting its emulator. That made a Merchant-only screenshot job wait on an unrelated Rider artifact/workflow. Add the PR branch to the push trigger, add the `[merchant-visual-review]` branch gate, and skip only the Rider fetch in Merchant-only mode; retain the Rider path for Rider review. Add a native-source regression guard.
- Next: publish with `[merchant-visual-review]`, inspect exact-head authenticated Merchant POS screenshots and Web comparison, then fix any concrete POS regression. No real sale/payment or Production mutation is part of visual review.

## 2026-11-08 Remove duplicate barcode/SKU lookup from POS counter

- Owner-provided POS screenshot showed the counter's inline barcode/SKU scanner and search duplicated the dedicated `บาร์โค้ด/SKU` tab. Removed the inline counter field/result/lookup callback and the now-unused Native `lookupProductCode` client model/method. Kept the dedicated permission-gated barcode/SKU management tab, table QR generation, counter menu search, product selection, and all server RPCs used by those flows unchanged.
- `node tests/native-android-source.cjs` PASS (489 checks, 0 failures); isolated `tests/pos-runtime.cjs` PASS (99/0); `git diff --check` PASS. Android SDK is unavailable locally; exact-head Merchant Kotlin/JVM + APK CI is required before claiming build verification.
- NEXT: publish the focused change on the existing PR #872 successor, inspect exact-head Android Pilot and Merchant screenshots, and verify the counter no longer shows the duplicate scanner while the dedicated code-management tab remains.

## 2026-11-08 Rider online switch color parity — local source gate PASS

- Refreshed GitHub `main` at `2c44d6f9313d2efdeb6dceef4c03d3f1ea9aa27e`; PR #872 was open/draft at source head `b4c5665187165178e77d9471b9910be90e1ce004`. Local commit `79201cca157fe25a5c6db472ea69be3fd6493c37` changes only the Rider availability checked-track color. PR #872 is intentionally stacked on `test-rider-terminal-turn-restriction-20261106`; current `main` is not an ancestor of this branch, so preserve the existing stack and do not rebase/reset onto main.
- Verified Rider Web's `.qg-online-pill.on .qg-online-switch` background is `#1fbe70`; Android switch now uses the same `Color(0xFF1FBE70)`. Added an exact-source guard so the checked state cannot silently revert. `node tests/native-android-source.cjs` PASS (490 checks, 0 failures); `git diff --check` PASS.
- `git push` was unavailable because this checkout had no GitHub credentials. Published the same tree through the connected GitHub writer as `a9f833f5` (Rider color change) and `6e584dc2` (source regression guard + this memory entry); created `work-rider-web-parity-20261108` and fast-forwarded PR #872 source branch to `6e584dc2`. This preserves the exact `79201cca` file change, but remote commit SHAs differ because GitHub created new commit objects.
- Exact `24ace278` workflows are active/pending: Native Android Pilot run42463098760, Rider offline routing42463098744, Map Engine42463098756, Customer P0 matrix42463098772, and authenticated Rider review42463098745. Predecessor `b4c56651` had all five corresponding workflows complete successfully; that does not certify this head. Local checks: native Android source490/0, Rider refresh runtime114/0, Rider GPS-transition isolated PGlite35/0, completion isolated PGlite58/0, Rider map124/0, MapLibre route rendering29/0, GPS stream9/0, route progress22/0, Web camera69/0, plus navigation/map/delivery/P0–P4 contracts all PASS. PGlite tests ran with an existing sibling-workspace dependency because this checkout has no node_modules. Android SDK variables are unset, so no local APK build/runtime was possible. No APK, physical-road/GNSS, or release acceptance claim.
- Next: inspect exact-head workflows and fix any confirmed failure, then continue the remaining Rider delivery/navigation acceptance. No Production data changed.

## 2026-11-08 Correct picker readiness and apply approved in-app role branding

- Refreshed main2c44d6f9 and Native69eb2404 before edits. Customer/Merchant location picker no longer equates style-loaded with road-ready: require a fully rendered MapLibre frame and idle, invalidate readiness on GPS/search recenter, ignore disposed callbacks. Customer shows loading feedback before the existing retry timeout. Installed Customer QA now inspects actual MapLibre snapshot pixels for geographic detail on first/last lifecycle cycle and saves map-only evidence; semantics alone cannot certify a blank map. Online Customer/Merchant and offline Rider sources remain unchanged.
- Approved thick white Q/short-tail connected role symbols now generated from the same launcher geometry as shared in-app vectors. Apply QueueMarket to Merchant login/register/POS and Queue Rider to Rider login. Customer auth/guest/authenticated headers, Rider registration/pending approval and profile also use the approved role family. Shared auth brand follows actual package role. Cap Merchant login hero at360dp on tall tablets; raise Rider earnings secondary text to12sp and improve contrast. Customer empty shops offers location selection and distinguishes missing location from no nearby results.
- Full local npm test including isolated SQL/DOM/source suites and map field QA passed on the first patch tree. Hosted1664573b Customer compile exposed a missing Material3 TextButton import in the new empty-state action; fixed the import in source. Full local npm test also passed on the follow-up branding tree. Hosted5179d58c app compiles, but the new AndroidTest snapshot code cannot see MapLibre behind shared implementation; add an explicit AndroidTest dependency on the same SDK13.7.1. Export map-only PNGs and detail metrics alongside existing screen captures. Exact-head installed runtime still required. No Production schema/RLS/orders/finance mutation, no new paid resources, no main merge. Missing Native backend RPCs, authenticated full delivery E2E, logged-in Admin and physical-device/road acceptance remain open. Do not treat this UI patch as Production Ready.

## 2026-11-08 Require Merchant process recovery, POS and real background FCM

- Continue from Nativebcff18cd (same tested local tree as d7b69cc5), retaining approved icon family. Existing secret-backed Merchant review now verifies force-stop actually removes the process, cold starts without clearing Auth, requires authenticated navigation AND server-backed home summary, and captures a recovery screenshot. Always exercise existing POS counter/tables/cashier views read-only; tablet parity remains its separate optional scope. Metadata records actual git HEAD rather than PR merge-event SHA.
- Build matching Merchant instrumentation alongside the actual debug app. MerchantPushRuntimeTest uses the existing active shop review session, refreshes through real NativeAuthApi, registers real FCM token through existing NativePushApi, backgrounds app and invokes existing account-scoped test action; require a fresh matching active Android notification. No fake RemoteMessage or direct local notification. This sends a test notification only to the authorized review account and may reach its other registered devices. Keep shared review-account workflow serialization. New installed runtime pending, not certified by test source.
- Local Merchant hierarchy5 and tab10 regression cases, Python compile, Push/session contracts, workflow YAML and diff checks PASS. Android SDK/Gradle unavailable locally; exact-head hosted compile/runtime required. Customer credential-check actually reports QG_CUSTOMER_REVIEW_PHONE/PASSWORD missing; no Customer authenticated/E2E/push claim. Isolated Supabase still absent, full delivery UI and physical-road acceptance remain open. No Production schema/order/payment changes or main merge.

## 2026-11-08 Release-name and Customer field-targeting failures corrected

- Refreshed main2c44d6f9/Native9ff8c4d7. Actual NativePilot42444232757: all three role JVM/Debug jobs and independent backend/map jobs passed, native-pilot failed because native-release-gate still required old Merchant/Rider launcher labels. Update label expectations and Play draft naming to owner-selected QueueMarket/Queue Rider; package IDs and reviewed specialUse subtype unchanged.
- Actual API30 artifact13602868461 from Customer42444236016: five installed tests, two failures because hasSetTextAction matched both place-search and delivery-address inputs. Add a stable semantic test tag on actual delivery address field and target it throughout 30 save/background cycles and saved-state restoration. Retain original map/pan/tap, coordinates, manual text, lifecycle and screenshot assertions. No mocked E2E claim.
- Local launcher72 and native-source489 checks PASS. Release gate needs clean committed checkout and is rerun after commit; hosted matrix still required. Supabase list_branches again contains only Production/default main; no isolated authenticated backend or physical-device certification exists. No Production mutation or main merge.

## 2026-11-08 Owner-approved connected-symbol red launcher family

- Owner explicitly selected the latest image: all-red backgrounds, white thick circular Q, short low tail, and bag/storefront/road joined to the lower Q rim. Names: Customer QueueGo, Merchant QueueMarket, Rider Queue Rider. This supersedes the earlier Rider Q-only/red-on-white rule.
- Native launcher generator now produces shared white Q geometry and role-specific connected symbols, outlined English wordmarks, red adaptive backgrounds, density/store PNGs and Android13 monochrome icons for all roles. Preserve native family preview; Android vectors reproduce selected design and selected image remains in the conversation. Package IDs, POS, navigation and backend unchanged.
- Local native-launcher-assets72/0 and native-android-source489/0 PASS; geometry actual alpha radii Customer30.98dp/Merchant32.05dp/Rider31.07dp within33dp safe circle. SVG/PNG generation and diff checks pass. Existing launcher tests updated to selected requirements, not discarded. No Android build/device launcher certification or updated APK claimed from asset checks. Existing installed APKs keep previous artwork until a new build is installed. No main merge or Production mutation.

## 2026-11-08 Customer installed matrix current-runtime corrections

- Concurrent source6891f529 directly descends from9f04c802 and already fixes stale picker compilation; preserved without duplicate push. Actual API30 artifact13599834280 from42437149316 shows five tests:30 open/GPS/manual-save/close/background cycles and build-info PASS, three failures from obsolete expectations. Pan does not select by current Web-parity policy; location implementation now uses Android LocationManager, not removed Longdo Google classes; populated address hides placeholder.
- Update existing instrumentation to require pan produces no selection and actual tap produces exactly one; retain programmatic-camera no-selection assertions. Resolve editable address through its unique set-text semantics before/after saved-state restore. Verify current MapLibre/platform location runtime classes, without adding obsolete SDK dependencies. No product behavior or Production changes. New exact-head Android execution required.
- Supabase inventory only Production/default branch; development branch request returned confirmation expired, no isolated UI backend exists yet. Physical-device/road/full three-role delivery E2E remain open.
- Downloaded published56ceb44d Rider APK1004253063bytes and recomputed SHA2563ee3352465ba0ad753a3b996b8cd676d775a9181d2c67bd4c9c8d2f9d21003c5 equal GitHub asset digest; full ZIP CRC check PASS. Stored404204685byte country map and1114767360byte uncompressed routing-tar entries present. Signature and current successor APK acceptance remain separate; no Production-ready claim.

## 2026-11-08 Restore Customer installed picker API matrix on Native PR872

- Refreshed main2c44d6f9 and Native56ceb44d; current four workflows succeeded, with actual authenticated Rider/Merchant capture and background FCM step executed. Native Pilot deliberately skips country packaging; separate Offline42410606530 is the country gate. Release rider-country-field-56ceb44d targets this source and GitHub reports APK1004253063 bytes digest3ee3352465ba0ad753a3b996b8cd676d775a9181d2c67bd4c9c8d2f9d21003c5. Binary has not been downloaded/rehashed in this session.
- Found Customer P0 installed matrix only triggered obsolete branches, leaving API26/30/33/35 picker/recreation coverage absent on current successor. Enable existing matrix on872 pushes and relevant PR changes; checkout explicit PR HEAD. Harness records actual checked-out Git commit rather than merge-event GITHUB_SHA. Existing real Android tests, 30 picker cycles, screenshots, crash checks and no-account/no-backend-write isolation retained.
- Local Python compile, YAML trigger/exact-source/API-matrix checks, native-integration-ci86 checks and git diff check PASS. Android SDK/Gradle unavailable locally; actual new matrix execution pending hosted CI. This is runtime QA wiring, not full delivery UI E2E, physical GPS or Production readiness. No main merge or Production mutation.

## 2026-11-07 Isolated-backend prerequisite wiring for Native UI QA

- Separate lane work-native-local-backend-20261107 based on refreshed de147818. Shared API/Auth and Rider API (including Storage) now use one paired backend configuration; both Realtime clients derive ws/wss from it. Explicit QG_DEBUG_BACKEND_URL and QG_DEBUG_BACKEND_PUBLISHABLE_KEY apply only to shared DEBUG BuildConfig. Unset values preserve existing Production defaults. Release BuildConfig contains empty override fields and always resolves Production.
- Gradle rejects partial pairs, credential/path-bearing origins, remote cleartext, Production mixing and privileged/non-anon keys BEFORE BuildConfig generation so a rejected secret is never embedded. Runtime repeats boundary validation. Six meaningful JVM tests cover Production defaults/Release ignore, paired local/staging protocol behavior, partial/mixed settings, unsafe origins and elevated keys. Debug-only network config permits HTTP only localhost/127.0.0.1/10.0.2.2/::1; Release manifest unchanged.
- Actual local Native source489/0 and diff checks PASS. Kotlin/Gradle/Android toolchain unavailable in this fresh executor; new six JVM tests and all-role builds require hosted exact-head CI (not claimed locally). No mock backend, WebView, Production schema/order/payment mutation or main merge.
- Real UI food-flow bootstrap remains blocked: repository migrations assume existing users/orders/products base tables and active-session Auth contracts, while supabase/config.toml only holds Edge function JWT settings. Root read-only project inventory found only Production. Genuine schema/session definitions must be recovered read-only into an ephemeral backend; do not invent claim_active_session/assert_live_session stubs or call synthetic SQL fixture a Production-equivalent backend. This endpoint plumbing is independently useful, not UI E2E certification.

## 2026-11-07 Single-database food checkout to photo completion runtime

- Isolated lane work-food-e2e-20261107 derives from refreshed Native43d96010, preserving newer backend CI and photo-only completion assertions. New tests/food-delivery-flow-runtime.cjs executes the whole food chain in ONE disposable PGlite engine: public checkout wrapper + canonical20261013 checkout core, real source service-area/haversine policy, canonical continuous dispatcher and Rider claim, Merchant once-only preparing/ready, actual pickup photo/cash/auto-deliver and delivery photo completion.
- No manually assigned order status or Rider, no mocked business transition RPC or synthesized completion response. Fixture schema/actors/products/storage-object rows only; shared dispatch scaffolding reused and its distance approximation replaced with actual source distance function. Existing Rider-first business order preserved. Same order ends completed/delivered/paid; exactly one pickup proof/delivery proof/cash advance/Merchant receipt; amounts100/100/130 conserved. Negative out-of-order Merchant, customer-as-Merchant, wrong Rider, missing pickup/delivery proofs and no partial proof insertion plus checkout/Merchant/completion retries covered.
- Actual node tests/food-delivery-flow-runtime.cjs PASS34/0. Added to existing npm test chain after checkout-server. This certifies actual SQL body composition on a synthetic fixture, NOT Android/Web UI end-to-end, Production schema/JWT/RLS/concurrency or offline road routing. No Production mutation/schema deploy/main merge; hosted exact-head full regression pending.

## 2026-11-07 Exercise current photo-only completion rather than only legacy PIN

- Existing completion runtime suite loaded old PIN/action migrations; Android now calls qg_complete_with_photo. Execute exact canonical function body from20261006150500 migration in existing isolated SQL fixture. Added missing/foreign photo, wrong Rider, invalid GPS/no partial proof, paid completion and repeated completion/exactly one proof assertions. Actual local node tests/rider-completion.cjs PASS58/0 (was48), synthetic PGlite fixture only; not fullfoodE2E/JWT/RLS/concurrency certification. No Production or product-code mutation.
- Previous7e8fd094 exact backend3gates PASS25+100+45. Fullfood single-database Customer→Merchant→Rider flow and Android/Web acceptance remain open; do not claim legacy tests prove that path. New exact-head CI pending.

## 2026-11-07 Activate isolated backend acceptance on current Native successor

- Exactf891e1be all4 CI passed, including actual QUEUEGO_REAL_BACKGROUND_FCM_PASS in42325887125. No false push claim from permission alone.
- Existing continuous-dispatch-races,…103553 tokens truncated…oduction gates stay open.

## 2026-10-18 route-progress display while retaining stable geometry
- The camera/proof repair keeps the road route instead of recalculating every10 seconds; preserve live distance/ETA presentation by reading accepted route-match progress against existing authoritative distance/duration. Time is labelled approximate; invalid/opposite/off-route evidence does not invent progress. Helper14/GPS9/proof34/camera65 PASS; full gate required. Build rider-20261018-camera-proof2; no backend/cap/pricing change or duplicate route engine.

## 2026-10-18 bold route from real20:19 recording
- Continue newest sourcec41453e9/livebad7dcba. Owner asks large visible navigation route. Web6px/no casing becomes12px red with16px white; Native active8 becomes12, future6 becomes8, casing4 wider. Same route geometry/camera/state/proof retained; current one-photo fix stays. Build rider-20261018-route-bold1; source/physical CI gates apply.

## 2026-10-18 real route disappearance and aligned arrows
- Owner20:43 recording plus actual MapLibre reproduction proves casing loading can suppress red fill via past load event. Existing helper now applies immediately and owns style.load retry/removal. Direction chevrons, displayed remaining-only route and same-point camera/puck road snap (<=12m, known accuracy<=35m, no opposite heading) preserve raw GPS/full route authority. Native reuses its hybrid signal pipeline; renderer alignment/chevrons only. Build route-arrow1; parent58acbd5be2536ab7495390a0bc9692224c184f6c; full exact CI and physical acceptance required.

## 2026-10-18 camera follows shared Rider course
- Owner asks camera behind/rotating with Rider arrow. Cache Web smoothed course once per fix for camera and puck; interpolate puck yaw with same easing. Native FOLLOW uses existing filtered course via existing camera policy option; keep unknown-heading, gesture, lifecycle and all route safety gates. Build route-arrow2; exact full CI and physical acceptance required.

## 2026-10-18 exhausted route lifecycle
- Endpoint replay exposes leftover red/casing when remaining geometry becomes empty. Reconcile renderer-owned IDs, remove exhausted/past multipart overlays and preserve later authoritative parts. Full road geometry/heading/financial authority unchanged. Build route-arrow3; require exact CI and physical acceptance.

## 2026-10-19 Buriram business places
- Owner authorizes real business places on QueueGo Map. Start from latest531c1ac9/mainf836f05d; branch work-native-route-bundle-places-20261019 and backup retained. Existing normalized places/geocode/nearby reused, now including named area ways. Pinned parent CI PBF checksum verified; snapshot Oct15 includes3079 named places/1284 business-service records in padded envelope. Same ODbL JSON for Web/Android; no imported record becomes verified or orderable or auto-linked to a merchant. Next integrate search/display and require full/exact CI; physical entrance/contact verification separate.

## 2026-10-19 Web place catalog integration
- Existing map helper/search/map-view geocode/nearby reuse one pinned catalog; add generic business dots/named labels/details and explicit pin selection, retain source/ODbL and unverified status. Native will bundle this same asset. No merchant/order mutation or inferred commerce eligibility. Catalog/search/DOM tests PASS; full/exact gates required; Web-first review.

## 2026-10-19 owner: no startup tutorial; missing credit/Admin UI
- Owner wants service/how-to guidance in settings only. Remove automatic Rider learning popup entirely; retain existing manual settings guide and operational readiness gates. Refresh obsolete Google Maps lesson to existing QueueGo navigation.
- Read-only live audit finds no credit functions/tables despite deployed queuego-credit Edge handler. Existing Native credit cards are staged under earnings; main lacks current credit/Admin grant UI. Do not claim source implementation is live payment activation. Continue bounded UI and release-gate work; no fake balances, duplicate ledger, live provider calls or Production migration outside release gate. Places Android integration remains pending after this owner priority.

## 2026-10-19 owner-authorized Rider Home / Settings redesign
- Owner supersedes earlier no-redesign/idle camera experiment: Web and Android calm map-first Home, QueueGo red puck, exactly one Ready switch; remove statistics/recommendations/configuration from Home. Move existing Auto Accept/destination controls to Settings, reuse same state/store/dialog; modern readable rows, account/document details behind explicit entry. No new availability/dispatch/schema authority.
- Continue from PR437 d367af6c via work-native-route-bundle-clean-home-20261019 with backup retained. Web single switch serializes and displays acknowledged existing rider_profiles.metadata online/available writes; failure/session guards preserve current work. Poll/reconnect refresh reconciles profile, not a realtime certification. Android retains existing changeOnline/setOnline operational and active-job guards. Temporary idle FOLLOW/3D disabled by current owner instruction.
- This is source checkpoint only, NOT release/DONE. Full regression/exact-head Android build/runtime, visual/authenticated availability/dispatch/restoration checks, Web settings parity and Web-first preview remain pending. No Production deployment, APK/signing/Play certification or fake implementation.

## 2026-10-19 Ready switch response-order fence
- PR438 source3789cc00/tree2607b0c81 exact CI Native3422 and Map167 running; five SQL race jobs PASS. Add transport request sequence so late profile poll cannot overwrite newer acknowledged Ready switch. Actual DOM overlap/rollback/session/current-job27 PASS, build rider-20261019-ready1.
- Local machine is New York Oct18 versus Bangkok business Oct19; current_date test mismatch caused missing cash row. Existing dispatch test passes108/0 with TZ=Asia/Bangkok, no runtime SQL change. Require clean full run under business timezone. Separate current-main UI-only projection prepared, not deployed; Settings Web parity and authenticated/visual/native gates remain pending.


## 2026-10-19 Web-first Ready card publication
- Public Web main e37dc61cd37ec6f24460d69f12dd17b8d2ba72e3/tree daef900932cff884e3310d5b4a8cef9876d09288 publishes the bounded UI/profile/poll-fence projection, preserving current legacy route transport and real places. Full clean Web TZ=Asia/Bangkok npm pre/test/post EXIT0; projected DOM27, runtime83 and map96 PASS. Pages/RC deployment verification still required before claiming the live page updated.
- Full clean source TZ=Asia/Bangkok npm pre/test/post EXIT0 on local eff80250 tree (same follow-up code, before this documentation receipt). Published parent3789 Native3422 has full regression/JVM unit, navigation/MapLibre and five SQL race gates PASS; APK and display/launch runtime still running. Exact final successor CI still required.
- Web review URL: https://chatchairins-source.github.io/QueuGo/rider/ (build rider-20261019-ready1 after Pages). Current Settings source is improved but Web feature preference parity/authenticated acceptance/screenshot gates remain pending; this is not DONE.

## 2026-10-19 Rider Web My Destination settings parity
- Strict successor of PR4389e16277f on work-native-route-bundle-rider-settings-20261019. Reuse installed qg_rider_destination_preference/qg_rider_set_destination_preference and existing QueueGoMapWeb create/search/GPS; no new backend/schema/availability. Native already has this existing setting and is unchanged.
- Production read-only function signatures verified. Web-only editor acknowledges/reconciles backend values, fences account/session, prevents duplicate writes and disposes map/late GPS; actual DOM36 PASS plus Home27/runtime83/UI103/source integrity. Full/exact gates still required; no real preference/account/order writes during tests.
- Parent Web e37 RC/Pages SUCCESS and live ready1 confirmed. Current browser has no authenticated Rider session; sign-in page is not Home visual acceptance. Native parent9e exactCI has JVM/three APK/16KB and five SQL races PASS, final emulator steps pending. No qg_rider_dispatch_preference installed in Production, so do not label continuous-dispatch readiness live or enable staged flags as part of UI parity.

## 2026-10-19 actual Ready Home Android map gate failure
- PR439187ac19d is the current Settings source successor, not a completed Android release. Web destination editor full suite PASS and main65638570 published; Pages/RC/live check pending. Existing destination RPC ACL/live-session/active guards verified read-only.
- Exact parent9e Native39195851635 map-display117878415155 failed actual SDK camera continuity: initial16 vs resumed17; evidence12161594769. Existing idle recenter pulsed follow for180ms and could vanish before style/GPS readiness. Replace pulse with recenterRequest identity on the existing render state, retained in holder lastState and consumed after scheduling the real SDK camera with a valid fix. Idle remains manual/flat; no backend state/route policy changes or weakened runtime assertions. Backup actual187ac19d retained.
- Focused source/navigation/runtime-budget checks PASS; full clean regression and exact successor Kotlin/emulator instrumentation remain required. Do not certify parent9e or release APKs from a failed containing run.


## 2026-10-20 Native Merchant Market Master Catalog parity
- Status: IN PROGRESS; branch work-native-market-catalog-parity-20261020; parent PR439 HEAD38baf74c78a5881a225df5264a9fd28be485bf9b. Parent Native39197043125 and Map39197043133 are SUCCESS. Web-only main contains no native-android tree, so it is not a Native merge source.
- Evidence: Native had market stock management but its catalog action routed to the generic category editor, with no qg_market_master_categories/qg_market_catalog_page/qg_market_catalog_add callers. The live Web catalog already exposes128 populated catalog previews.
- Change: existing Products action now opens a Native Market catalog for market/meat/fish/vegetable/fruit; Grocery flow retained. Reads the SAME deployed public guarded RPCs,50/51 rows, search/categories, added-state and clean catalog_preview_image. Add form supports1/2/3kg plus custom decimal weight, price/cost/stock/minimum and existing GP display.
- Recovery: app-private no-backup atomic intent file is persisted/fsynced off Main before the RPC. Lost response/recreation replays identical UUID/body; matching server product UUID is required before clearing. Store isolates actor/shop, rejects changed pending bodies and malformed input. Backend remains authoritative for inventory, idempotency and eligibility; no new mutation/table/dispatcher.
- Tests: existing Grocery source24 and three-app source273 PASS. New JVM behavior tests cover50/51, malformed rows, fractional2.25kg, invalid prices/weights, durable UUID reuse, mismatched retries, actor/shop isolation and corrupt files. Actual Kotlin compilation/JVM/full Native CI PENDING; local Android SDK/Gradle absent.
- NEXT: full local regression from committed tree; exact-head Native Pilot compilation/unit/APK/16KB/emulator and Map gates; repair first failure. Physical approved/pending-owner save/reopen/stock/font-scale acceptance and signed Play release remain separate gates.


## 2026-10-20 Native Market first exact-head runtime failure
- Exact PR491 HEADa5c0bf8a7986d5510c33e5b93b5a5d0794d83188 / Native39395017610: full regression, Kotlin/JVM tests, all three APK builds,16KB and fresh-session phone/small/large launch matrix passed. Containing run FAILED during Customer system-kill recovery; no full Native PASS claim.
- First failure: capture-rider-launch.py line792 expected no Customer PID after am-kill + run-as SIGKILL; artifact12252380039 reports mode=run-as-sigkill and PID4838. Prior PID values and kill return codes were not saved, so evidence cannot yet distinguish a failed signal from a new process starting.
- Diagnostic successor records original PIDs, each kill return code/stdout/stderr, poll observations and failure logcat/process/services snapshots. The original death assertion is unchanged; no gate is bypassed. NEXT: run exact successor and diagnose from recorded evidence before changing runtime or acceptance criteria.
- Additional catalog context fence: before catalog-page/add/recovery calls, compare existing qg_market_owner_shop with the selected Native shop. This rejects stale archived/replaced shop context. Live unique shop_profiles_one_current_per_owner index is confirmed; no new backend or RLS changes. A JVM test covers matching, replaced and absent shop IDs.
- Full local regression on first source tree EXIT0; focused Grocery24, three-app source273 remain PASS after context guard. Exact successor JVM/runtime evidence is required next.


## 2026-10-20 Credit / Ride rule-guard reconciliation
- Status: IN PROGRESS; branch work-native-credit-rule-guard-reconcile-20261020; newest Native integrationbb9f41e6ccc99e9acd0e56744db050f4bf53182c contains merged PR491. Its exact code64a8a2b6 passed Native39396673821 including JVM,3 APKs,16KB and real Android lifecycle/system-kill recovery. Integration recheck39398702208/39398702254 running.
- Root cause: isolated13-migration credit rollout followed by the actual later Ride17 guard definition rejects credit feature keys; that definition also dropped the earlier continuous-dispatch numeric keys/bounds. Empty13-chain replay alone did not cover later migration replacement.
- Change: CLI-generated forward migration20261020091222 restores the union of existing credit/dispatch and current Ride tariff validation. No activation, balances, business rows, grants or old migration edits. Production NOT APPLIED.
- Test: actual SQL13-chain plus later guard and forward reconciliation PASS; checks valid/invalid credit flags and dispatch values, preserved tariff bounds/ACL/security mode, repeated apply and unchanged real platform rules. Full local suite requires a clean committed checkout because release-evidence tests deliberately reject dirty sources; initial uncommitted run stopped at that guard.
- NEXT: commit, full clean-checkout regression and exact hosted CI. Reconcile the complete deployed migration history before any financial/service rollout; this isolated fix is not Production cutover approval.


## 2026-10-20 Map HTTP runtime resource bounds
- Status: DONE (bounded source/local socket test); hosted exact-head Map CI and public release remain separate.
- Base: newest fetched Native map successor `3ffd43f1` on `work-native-route-bundle-carriageway-lock-20261020`; no Web/native tree overlay.
- Root cause: `ThreadingHTTPServer` created an unbounded daemon thread per accepted connection, and accepted sockets had no read timeout. A client stalled before finishing headers/body could retain runtime threads indefinitely even though parsed JSON bodies were capped.
- Change: existing shared service now caps concurrent accepted workers at16, expires socket inactivity after15s, and returns503/Retry-After when admission is full. Every worker exit releases its slot. No route algorithm, dataset, GPS display, booking, finance or Production change.
- Validation: `python tests/map-http-resource-bounds.py` PASS9/0 with actual loopback stalled-header/stalled-body/overload/recovery/size-limit requests; `map-engine-trace-safety.py` PASS12; Python compile PASS. Socket tests do not certify graph accuracy or physical road behavior. Existing Map workflow executes the new test.
- BLOCKED (hosting): approved hostname/VM access/TLS owner/package store are still required by `docs/map-staging-deployment.md`. Public Web main currently still calls Longdo RouteService; switching to QueueGo `/map-api/route` requires a reachable approved runtime, not just merging a client button.
- NEXT: exact successor hosted Map CI; run existing `stage_container.py` against approved host/package, terminate TLS with request/rate limits, verify `/map-api/health` and real Buriram route, then forward only the reviewed shared transport to newest Web main and configure Native endpoint. Physical GPS/U-turn/parallel-road certification remains BLOCKED.


## 2026-10-21 PromptPay Web publication / Native client successor
- Web source222538752a1cf273aee3e47e03bea45306c5ea8a PR528 merged ascee5a58d7505b1e810d717687c060a6d02beb74f. Full local Web npm test EXIT0, exact RC39523018771 SUCCESS including mobile WebKit; Pages39523292975 SUCCESS. Actual served queuego-credit.js and admin/credit.js equal tested files.
- Backend: queuego-credit v2 + signed queuego-credit-webhook v1 deployed. Existing database ledger retained. Rider grant migration20261021003910 applied; authenticated active-Admin/live-session guards, durable request IDs and audit. Isolated Rider ledger16 plus Web Rider form4, Merchant38, Edge82, Web37 checks passed; no real bank settlement/grant-to-owner was claimed.
- Readiness: environment_ready=true, live_key_configured=false, webhook_signing_configured=false, live_intake_enabled=false. Both existing credit feature flags remain false. BLOCKED: OMISE_SECRET_KEY (LIVE), OMISE_WEBHOOK_SECRET, QUEUEGO_CREDIT_LIVE_ENABLED, provider LIVE PromptPay and webhook signing configuration. No secrets fabricated, no charges or money seeded.
- Native scope: strict descendant of current credit021caf67 (PR526), preserving its passed Native run39520235790. Sync shared Merchant/Rider client only: server pending UUID restoration, provider recheck through original queuego-credit endpoint, authoritative summary, explicit terminal statuses, same one-balance display. Public Web remains on main; no old Web snapshot published from Native branch.
- Evidence: native source282 and Edge82 PASS. NEXT: exact Native full regression/three-app build; signed bank webhook and actual Owner payment/persistence acceptance remain blocked by provider settings above. Do not claim LIVE DONE.


## 2026-10-22 Native POS / Delivery context separation
- Continue PR596 exact bdf99bfa; Native Pilot39731712694 SUCCESS after nullable shop-category compile fix. Web hotfix PR598 is the matching storefront/Delivery backend policy source.
- Remove Delivery reads/DTOs/Kitchen cards/actions/setup tab/history filter and GP/Delivery report rows from native POS. Food/Cafe keep POS Kitchen; Grocery/Market have no Kitchen tab/actions/staff role option. POS history reads sales_channel=POS; totals use storefront dine-in/takeaway only. Existing Delivery screens/APIs remain.
- Separate Delivery cancellation predicate allows pending/accepted/searching/rider-assigned/legacy-assigned only when preparation and ready timestamps are null; confirmation closes on state/timing changes. New JVM tests cover Rider acceptance, started/terminal states and stale timestamps. No APK release or Production data mutation in this source checkpoint; exact-head Native full CI required.


## 2026-10-30 Native nationwide offline completion — IN PROGRESS
- Verified remote main `7e036a0735cc49905e9b0a2434492470551a0c9d`; PR #735 remains open/draft at `2d57fb6b9d32fa44f9d2f54078aad09651a4d249`. Common ancestor `65638570b670b3949457d2fd8317bf4f65d8c0a5`. Fresh clean clone; continuation branch derives from #735 to preserve unmerged Native/POS work. No wholesale divergent-main merge.
- Existing shared map only painted online OSM raster despite registering a PMTiles vector source. Local file/asset sources now reuse the existing ShortbreadVillageStyle rather than requiring the online raster. Shared module reuses the existing map module; no duplicate renderer.
- Added packaged Thailand asset preference and uncompressed build-time asset input, plus nationwide wrapper around the existing pinned Planetiler builder and a structural/coverage/checksum staging validator. A valid header alone is NOT completeness, rendering or routing certification.
- Downloaded real Geofabrik Thailand `thailand-261029.osm.pbf` (329,513,947 bytes) for local generation with verified Planetiler 0.10.2. Generation/build/tests are in progress; no APK or nationwide rendering PASS claimed here.
- Original npm run stopped at the existing clean-checkout release-evidence assertion while edits were uncommitted; rerun from committed source is required. Android tools installed locally; initial attempts exposed missing compiler and proxy configuration, being repaired without changing dependency versions. No /dev/kvm or physical device available. No test-account secrets obtained.
- NEXT: finish country data generation, validate exact output and license/attribution, package into all three APKs, rerun full regression/unit/build/packaging, and collect offline renderer/camera evidence on emulator/physical devices. Existing local style lacks place/road labels; offline routing is not certified. No Production data, merge or deploy performed.


## 2026-10-30 Native Thailand archive generated; APK acceptance pending
- Real country PMTiles generated with verified Planetiler 0.10.2 from Geofabrik `thailand-261029.osm.pbf` (source SHA256 `0f26968be9675bbdadaab232d19cafb4464e6535a73afe5eee4afd94b43825c6`). Output 404,204,683 bytes, SHA256 `abfedfc200c1ff81045f9f946a6f281b0a8664d4b440e269b68f0e628408ba3b`, zoom0–14, bounds97.1,5.4,105.8,20.6. Decoded actual MVT samples in Bangkok/Buriram/ChiangMai/Ubon/Phuket/Narathiwat/KoTao/Lipe/Ranong all contain streets; this is data evidence, not an on-device rendering PASS.
- Thai/Latin glyph PBFs now bundled from pinned Protomaps basemaps-assets commit `028c18f713baecad011301ff7a69acc39bcc2ae7`, including SIL OFL and checksums. Existing Shortbread style gains ocean and street/place/POI names. Verified glyph blocks contain Latin A and Thai ก.
- Fixed library debug Theme leakage into Customer and AGP9.4 source-set cast by using variant asset sources. A clean build avoided a stale incremental Kotlin duplicate-overload failure without renaming or deleting source functions.
- Full npm regression on committed first-stage tree passed. Local six-module unit run passed795 tests, zero failures/errors/skips; three APK builds passed before final packaging/source-identity rerun. Those intermediate APKs used compressed PMTiles: corrected noCompress at APPLICATION level, and final rebuild is required. Do not deliver intermediate APKs as final.
- Added an installed-country instrumentation gate using real packaged source, painted-road queries and screenshots in five regions; offline mode asserts no active network and requires archive presence. Country-only CI preparation pins actual source PBF checksum and stages the archive before three APK builds. No installed-country runtime PASS yet.
- Git CLI has no push credentials. First stage published via GitHub connector at `2331ecc2c943f3368aab6499bdfd7d4281b1993a` (same tree as local `b407be23`), without merge/deploy. Continue successor branch `work-native-rider-nav-unified-offline-20261030`.
- NEXT: publish this successor, rebuild exact committed source with stored archive, inspect APK hash/compression and16KB alignment, run installed-country/no-network and existing camera/lifecycle emulator gates. Software emulator attempted because no KVM; actual road GPS/phone acceptance and authenticated E2E remain unverified. No Production writes, merge or deployment.


## 2026-10-30 Installed PMTiles SDK blocker corrected before delivery
- Verified upstream tagged C++ source: MapLibre Android13.5.3 and13.6.1 AssetManagerFileSource ignore resource.dataRange. Direct PMTiles APK assets can crash; previous intermediate APKs must not be delivered as working offline builds. Upstream issue4360 was fixed by PR4602/c758494379f6a084959131e3a13af4bf6d698238.
- Verified stable13.7.1 tag has AAsset_seek64/AAsset_read and passes resource.dataRange through. Pinned ALL four Native SDK declarations and their source/release contract tests to13.7.1. Requires fresh unit/build/16KB/runtime checks; no runtime PASS claimed from source inspection.
- Added application/library hidden-asset exclusion and fail-closed APK validator for exact country asset set, uncompressed PMTiles, manifest SHA/bytes, equal maps across apps and embedded Git SHA. Actual intermediate Customer APK contained a transient copy; another packaging attempt found an incomplete compressed-assets jar. Clean sequential packaging is required, without shipping those intermediates.
- Read-only Production check: Customer0111111111/customer, Merchant0222222222/shop, Rider0333333333/rider all have non-null auth_user_id matching auth.users.id and exactly one public profile per Auth user. No credentials read or changed; authenticated login/order E2E still needs approved secrets. No Production mutation.
- Full local npm on f463c8ab source passed before this SDK update;795 local unit tests previously passed on13.5.3. These are NOT13.7.1 acceptance. Fresh gates remain required. Software emulator cannot yet complete boot without KVM; no physical/device screenshot PASS claimed. Continue PR736, keep draft/unmerged.


## 2026-10-30 Exact-source offline Thailand test APKs delivered; release gates OPEN
- Tested APK source: `e0ff2620393df82db35b8fe799f29abf6dfc2156`, PR736 (draft, unmerged). Fresh isolated build at `/tmp/queuego-build-src` avoids stale large-file intermediates in the conversation workspace. Full npm regression exited0; fresh six-module JVM unit suite passed795 with zero failures/errors/skips; all three Debug APKs plus navigation instrumentation compiled.
- All APKs passed signature verification, actual ELF/ZIP16KB alignment and the fail-closed country-assets validator: exactly one stored/uncompressed404,204,683-byte Thailand archive, matching manifest SHA `abfedfc200c1ff81045f9f946a6f281b0a8664d4b440e269b68f0e628408ba3b`, and sourceSHA e0ff2620 embedded in DEX. SDK13.7.1 supports byte-range reads from APK assets.
- Saved distinct test deliverables: `QueueGo-Customer-THOffline-e0ff2620.apk` (511534070 bytes; SHA256 `4f07834ded9a03eccddebe70d4b29668b17022f291a6fd3e61b4690a0c99511e`), `QueueGo-Merchant-THOffline-e0ff2620.apk` (511919685; `ca203015762042f8b11a9d6fc5e7d9833cc5a909e98be07830562b5a040ae5ff`), `QueueGo-Rider-THOffline-e0ff2620.apk` (511730085; `4e398ec4b5c05aa1665155f2398dc4e55fa71f3ce6dbac8e5e5571aa62401632`). `QueueGo-Evidence-e0ff2620.zip` contains exact-source logs,795 unit XMLs/summary, map generation/decoded-nine-region audit, APK/signature/alignment checks and honest emulator failure status. Existing pilot APKs were not overwritten.
- Actual local software emulator boot FAILED after600s without /dev/kvm. Clean install, rendered country map/airplane mode/camera UI/lifecycle are NOT locally executed/certified. No fake screenshots. Physical road GPS/performance and authenticated E2E remain open; this session has no locally accessible approved password Secrets. Existing hosted review-account workflow is separate. Read-only Auth linkage checks matched all three specified accounts without duplicate profiles; no Production writes.
- Hosted Native POS CI41029740363 passed. Native Pilot41029740374 on the tested source was still running when this checkpoint was written; inspect its offline-required gate and artifacts rather than claiming success from queued/running status. No nationwide offline routing graph certification, push/signing/Production release acceptance, merge or deploy.
- NEXT: finish hosted country/offline-render/camera gate, fix actual failures and retain screenshots/logs; then authorized authenticated E2E and physical-device GPS/process-death/screen-size acceptance. The appended checkpoint is documentation only; APK source stays e0ff2620. QueueGo remains NOT Production-complete.


## 2026-10-30 Hosted country map PASS; stale gesture assertion corrected
- Native Pilot41029740374 on PR merge7c5b619596174da83d1d6e36a959de61649b50f5 (head e0ff2620) passed full regression, units, all3 APK builds,16KB, WebView rejection and exact installed-country asset checks. Hosted launch evidence12967260467 includes9 successful role/viewport launch cases (720x1280,1080x2280,1600x2560), native map recreation/resume, and three-role process-kill recovery; no authenticated login/orders or physical certification. Lifecycle setup still injects a legacy regional debug fixture, so its Buriram metadata/images must NOT be used as nationwide coverage proof. Actual packaged-country tests are separate.
- Offline artifact12966264961 XML: installedCountryArchivePaintsRoadsInMultipleRegions PASSED in67.902s, zero skips, with no active network and painted-road checks in five regions. Recreation and GPS/overlay/map reuse passed. Overall suite FAILED solely because the inherited manual-drag test expected1 notification while production intentionally sends2 (drag start suspends Follow, finger-up restarts the idle period). Source contract already requires that finger-up callback; do not remove it to satisfy an outdated test.
- Corrected the exact assertion to2 and added tap-after-drag/no-extra-notification plus repeated-drag/exact4 assertions. Runner now pulls country screenshots before propagating a test failure so successful evidence is not lost. Added an isolated hosted country-runtime workflow which reuses actual validated country assets from prior APK artifact12965729022, then runs the unchanged production map code and strengthened instrumented tests under no-network conditions. This is test/QA/workflow-only; delivered production source remains e0ff2620, not a new untested product implementation.
- NEXT: execute strengthened4-test offline suite, inspect five real screenshots/labels, and update the existing evidence deliverable. Preserve all open Auth/order/physical-road GPS and offline-routing gates. No merge, deploy or Production writes.


## 2026-10-30 Offline harness must enforce radios AFTER UTP installation
- Isolated run41034698501/12968250642: all3 existing gesture/recreation/GPS-map-reuse tests passed after the exact gesture assertion fix. Country test correctly FAILED before rendering because ConnectivityManager.activeNetwork was100. The pre-Gradle dumpsys showed no active network, so setting radios before APK installation is insufficient; keep the strict offline assertion rather than accepting connected rendering.
- Test now invokes Android connectivity airplane-mode and Wi-Fi/data disable through UiAutomation AFTER installation, waits at most15s for activeNetwork=null, asserts real airplane mode, and checks no network before/after each region. These are test controls, not app production code.
- Gradle's test APK cleanup removes app-private external screenshots before adb pull. Test-owned PNGs are now copied by shell into Download/QueueGoOfflineEvidence before cleanup, then pulled with precise five-name/hash metadata; failures still fail the gate. This avoids losing successful screenshots and does not fabricate them.
- APK/production code remains exactly e0ff2620. NEXT: rerun isolated4-test no-network gate and inspect actual five PNGs, then replace evidence ZIP preserving its identity. No new product behavior or Production write.


## 2026-10-30 Screenshot persistence uses MediaStore; tested APK transfer stays byte-identical
- Offline rerun41037517383 again passed gesture/recreation/GPS-map reuse; country rendering reached its first offline screenshot, but scoped app-data shell copying failed. Replaced that test-only transfer with Android's MediaStore.Images API (API29+; actual CI API30/target36), pending-publication handling, exact copied-byte and retained-file-size checks. Official Android storage contract preserves shared media after app uninstall; runner pulls Pictures/QueueGoOfflineEvidence and records post-test connectivity. No product APK code changed.
- Actual emulator-tested role APKs are PR merge source7c5b619596174da83d1d6e36a959de61649b50f5 (production tree equals e0ff2620), not the separately signed local variant. Reference run41029740374 has9 successful role/viewport launch cases and no Auth/order or physical claim. Per-role verified export artifacts12968955082(Customer),12967249537(Merchant),12968197725(Rider) preserve their exact tested bytes.
- Connector cannot materialize the combined1.37GB artifact and timed out on512MB role ZIPs. Export now additionally publishes temporary64MiB transfer pieces with per-part and whole-APK SHA256 manifests, retaining original role downloads. Pieces are an internal transport only: reassemble and verify the original full APK before Library replacement; never ask riders to download map pieces or alter tested bytes. APK deliverable remains one file per role.
- NEXT: run strict four-test offline UI gate with five retained PNGs; materialize exact tested role APKs, verify signatures/16KB/map/source identities, update existing deliverables and evidence. Keep pending hardware/Auth/routing/release gates visible.

## 2026-11-08 Remove duplicate barcode scanner from POS home across apps

- Exact-head authenticated review `42474427032` captured Merchant Android APK SHA-256 `7d2b778d394128fe4a26ab062c82324463b1c3dafe9bc71377bc7a217c92d768` on API 30 / 1080×2280, plus deployed Merchant Web phone/tablet screenshots. Review was read-only; no sale/payment mutation. Merchant background FCM passed `OK (1 test)`.
- Visual comparison showed Android no longer had the duplicate inline scanner while Production Web still did. Remove the Web counter scanner and its now-unused lookup queue/guards; keep dedicated barcode/SKU administration and table QR. POS runtime regression now asserts scanner absence and code-management tab presence.
- Verified locally against exact-head source: JavaScript syntax passes; `tests/pos-runtime.cjs` passes 102 checks / 0 failures. Exact-head CI still running for APKs and offline navigation; no purchase/payment flow or physical POS hardware has been certified.

## 2026-11-08 POS review routing and stale scanner styles

- Removed unused counter-scanner CSS and added a runtime source guard that the POS stylesheet has no scanner selectors while the dedicated SKU/barcode tab remains. The authenticated review head must carry `[merchant-visual-review]` so PR events select Merchant-only capture and skip the unrelated Rider archive.

## 2026-11-09 Native release gate accepts attested emulator evidence (draft PR #905)

- Stack on draft PR #903 (`83531c34`); do not merge, reset, or use older October release source as if it were current. Existing physical evidence still works. Explicit emulator envelopes require an identified emulator, source-bound CI run, operator certification, complete gate checks, hashed artifacts, and all required roles. Reject unknown environment labels and emulator claims on non-device gates.
- Certification attests exact-HEAD Native Pilot; offline Rider emulator evidence additionally binds the independently successful exact-HEAD Rider routing run and its retained artifact. No evidence bundle was created or marked PASS by this code change. Release certification and signed APKs remain blocked by absent Production evidence, signing inputs, backup drill, and the old pinned release branch. The exact-head Native Pilot run for PR #905 must finish before its CI can be claimed.

## 2026-11-09 Prepare reviewed main for future Native release certification

- The previous certification and release workflows required a 2026-10-21 work branch even though the newer Native successor is stacked in draft PRs. Change the release source requirement to `main` after review/merge, and require independently attested Native Pilot, backup drill, and certification runs on the same exact `main` SHA. Add `main` to Pilot and secret-readiness push triggers. Existing manual dispatch, signing, Play version, private evidence and offline routing checks remain mandatory. No workflow was dispatched and no Production release is claimed; the successor code must first be reviewed and merged through the repository workflow.
- Carry the independently attested Rider offline run ID in certification metadata and verify its exact main SHA, workflow identity, result and retained route artifact again in the signed release job. Export the verified ID to the canonical verifier. This closes the gap where emulator routing could pass certification but fail the final packaging gate for lack of the run ID.
- Include the stacked release-source review branch in the Native Pilot push selector so the exact proposed workflow tree receives CI before merge. Production certification remains main-only.
- Run the existing release-secret presence check on this review branch as well. It reports missing configuration without printing secret values; a secret-presence PASS alone does not certify an APK, payment flow, or operator evidence.
- Exact review-branch release-secret run `42630226782` failed because `QG_SUPABASE_SERVICE_ROLE_KEY`, `QG_SUPABASE_DB_URL`, and `QG_BACKUP_PASSPHRASE` are absent; Firebase and Android signing secrets were present but their validators were skipped by the earlier failure. Run those independent validators even when Supabase inputs are absent, while keeping the overall job failed and skipping Storage access without its credential. Never expose secret contents or treat presence as certification.
- Follow-up exact-head run `42630768316` confirmed Firebase config and Android release signing identity both PASS, while the same three backend/backup secrets remain absent. Native Merchant product entry audit found three silent monetary/inventory defaults: an incomplete restaurant special surcharge became 10 baht, an incomplete laundry fee became zero, and an empty grocery stock became zero. Reject those incomplete visible inputs, preserve hidden laundry fees when changing mode, and keep save disabled until the visible values parse. No Production data was edited.

## 2026-11-11 Release certification blocks stale Production security review

- Re-read PR ancestry before editing: #908 `000e9c73`, #913 `9b61e4dd`, #914 `607ac130`, #915 `f2e47161`; #915 remains stacked on #914 with no behind commits. Exact-head Customer P0 `42971060688` succeeded across API 26/30/33/35. Rider routing `42971060672` succeeded. Native Pilot `42971060683` is still running; Customer/Merchant/Rider JVM + Debug APK, RPC catalog, dispatch, Express race and navigation-map-runtime jobs passed; native-pilot job was still in progress. Auth review `42971060775` credential-check passed; capture remained in progress.
- Read-only Production Supabase Advisor observation at `2026-11-11T06:30:24.358Z`: 32 RLS-no-policy INFO, 10 anon-executable SECURITY DEFINER WARN, 189 authenticated-executable SECURITY DEFINER WARN, and 1 leaked-password-protection WARN. A separate read-only `pg_proc` query identified the additional anon-executable signature `queuego_ride_config()`. Its current authorization/ACL must be reviewed before deciding whether the source grant or allowlist needs a change; no Production policy, grant, schema, order or financial data was changed.
- The previous source document reviewed only 142 authenticated and 9 anonymous functions on 2026-10-10. Update it to preserve the historical reviewed counts while recording the current 189/10 snapshot and explicitly marking `BLOCKED_UNREVIEWED_LIVE_DELTA`; do not infer that the delta is safe or requires a Production change before examining it.
- Added `native-android/qa/security_review_gate.py` and wired it into `verify-native-release-gate.py`. Native release certification now requires a current (≤24h), project-bound snapshot whose reviewed counts/classifications and anonymous allowlist exactly match the live Advisor surface; current stale evidence fails closed. Regression covers current mismatch, a valid synthetic fresh snapshot, stale snapshot rejection, anonymous count mismatch, and the full release verifier integration. No signed APK/certification PASS claimed.
- Local regression `tests/security-auth-definer-review.cjs`: 42 checks, 0 failures; Python compilation and `git diff --check` passed. Hosted CI for the new commit remains required. QueueGo is not ready for Production release.
