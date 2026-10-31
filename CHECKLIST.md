## 2026-10-31 Tablet harness root-cause follow-up — preserve real POS tab state

- Run 41157766525 still failed Native tablet navigation and Web tablet-pos-counter. Heartbeat/read allowlisting is correct but was not the cause of the remaining Web timeout: qgPosDisconnect preserves state.view, so reentering POS after the phone cashier screenshot legitimately restores the bills/cashier view. The harness incorrectly waited for a counter-only .qg-pos-panel before selecting a tab.
- Wait for the actual POS navigation shell, explicitly select the existing counter button (as already done for tables/cashier), then capture. No fake route/session or business action. This respects normal restored Web state instead of changing Production behavior to satisfy the test.
- For Native tablet capture, after wm size/density changes force-stop and reopen the existing Activity without clearing app data. Require authenticated Home with the same persisted session before capture. This is a real cold-session/window recovery check; it is not certification of tablet Back behavior. Existing product Back callbacks remain unchanged.
- Retain the existing remove-before-dump/null-root guard and include its known-label/node-count diagnostic JSON in the artifact (the previous artifact glob omitted that JSON). Do not persist raw UI hierarchy or credentials.
- Python compilation, Merchant request policy, Native source 471/0 and Actions pinning PASS. Actual after-capture remains pending. Current app layout improvements are preserved; no Production schema/financial writes or deployment.

## 2026-10-31 Merchant Dashboard — image-backed stat/menu styling correction

- The same actual Native/Web tablet Home pair also confirms Native stat cards are horizontal and every icon is red. Production final CSS (.qgm-stat, .qgm-menu and nth-child palette) uses vertical icon/value/label cards and distinct status/menu colors. This is a verified layout mismatch, not a business-data difference.
- Update only the existing MerchantMetricCard and MerchantMenuTile presentation: 87dp minimum vertical summary cards, 30dp/17dp status icons, 20sp totals and 10sp labels; 85dp menu tiles with 36dp colored icon containers and 10sp labels. Preserve every summary value, item, permission and callback. Keep added Native service entries; no business feature is removed based on an incomplete screenshot.
- Source/title/request-policy checks and diff check PASS. Exact Kotlin build and after-images are required before accepting these changes. No VAT/payment/backend behavior change. This follows the centered 620dp Merchant/1440dp POS correction; complete UI parity remains OPEN.

## 2026-10-31 Real tablet comparison — centered Merchant shell correction

- Inspected actual Production tablet-home.png (1067 CSS px) from 13025851356 against Native tablet-back-blocker.png from 13026266754 (1600px/density240). Web content is centered at 620px; Native primary content incorrectly fills the entire tablet while only bottom navigation is bounded.
- Verified final served-source CSS: merchant/index.html .qgm-shell max-width:620px;margin:0 auto and existing .qgm-content max-width620. Existing separate POS CSS has max-width1440px. Constrain the existing Scaffold to centered 620dp for ordinary Merchant routes and 1440dp for POS. Keep phone fill, route/session state, every callback and full-screen voice overlay unchanged.
- This is a confirmed Compose layout fix from actual images, not a new design or duplicate shell. Source contracts/title/policy and exact-head Kotlin build/phone-tablet after-images remain required. Dashboard stat-card composition/colors and remaining form/content mismatches are still OPEN.
- Parent 30a9596a preserves the stale-XML/heartbeat QA corrections; current parent tests/build are in progress. Do not claim APKs from older source match this layout revision. No backend/schema/business writes or deployment.

## 2026-10-31 After-image confirms POS header; stale hierarchy/session heartbeat recovery

- Current app-code source `0e75d6f92b0a464dbccde356894b9049afa75bce`: clean local full npm test EXIT 0, including pre/posttest. Actual authenticated phone POS after-image from run 41156854297 / artifact 13026266754 confirms centered Q/QueueGo/context header and compact back/reload controls. Remaining counter form/table/default/product layout differences are NOT certified.
- The same run still failed Native tablet navigation detection while the retained screenshot again showed Home. Text/content-desc support alone was insufficient. Existing launch runner already documents UiAutomator's zero-exit/null-root stale-XML behavior; reuse its remove-before-dump approach in Merchant capture, return no nodes on null-root/invalid/missing XML, and retain only safe known-label/node-count diagnostics. No raw hierarchy/credentials are persisted. Actual tablet recapture remains required.
- Web artifact 13025851356 failed tablet POS with blocked qg_call_incoming and touch_active_session. Actual Production heartbeat definition reviewed read-only: only UPDATE last_seen_at for auth.uid + exact session + not revoked. Permit this existing normal-session heartbeat alongside claim_active_session; retain denial of business writes and revocation. Do not call it a read-only RPC. qg_call_incoming remains blocked.
- Exact 0e75d6f9 offline run 41156859004 SUCCESS, 5 tests with no failures/errors/skips. Artifact 13025662139 SHA256 2e067c772522acc909ceb420e531c3aa5194fa04b0b283a9e8330151b05f5919; metadata records merge 433f3fc7. Actual inspected Bangkok image still has no clearly visible labels after the settling delay; label visual quality stays OPEN despite rendered-feature checks. Offline roads are confirmed; nationwide offline routing is not.
- Exact-source export 41156859038 has passed regression and generated the country archive, reaching JVM tests; no accepted role APK/size/signature is claimed yet. This follow-up changes QA only and preserves all app/POS/backend work. Keep PR #736 Draft/unmerged; no Production business/schema mutations or deployment.

## 2026-10-31 Merchant real-image correction and tablet capture recovery — IN PROGRESS

- Inspected actual run 41155683786 artifacts: Native 13025201549 (SHA256 397902d6b2cf00a8bbd5c80cc15034b7c752ed7cc96b1b71ee722fc165aac639) and Production Web 13025231470 (8739d89434bcc1350f6cda5129c1b6cd704931f24512c34a627c6873c53c06c7). Phone primary/POS frames were retained. Native tablet-back-blocker.png shows a fully rendered Home with bottom navigation, not a signed-out or broken Back screen.
- Native capture previously required text-only labels after tablet recreation. Recognize exact line labels from both text and content-desc for navigation/readiness and taps, preserving existing bounds and clickable checks. Recapture is required; this is harness recovery, not an invented app Back bug.
- Web failed at tablet-pos-counter after repeatedly blocking check_active_session. Actual Production function definition reviewed read-only: auth.uid-scoped SELECT from user_active_sessions and boolean comparison, no writes. Add only this read RPC to the existing allowlist and regression; continue denying revoke, order, payment, shift and settings writes. qg_call_incoming remains blocked.
- Confirmed phone POS image mismatch: Native uses a left-aligned two-line title/shop header and text reload; actual Web has centered red Q/QueueGo/context brand with compact back/reload controls. Update the existing Compose header to that 62dp/40dp/31dp structure, retaining back/reload/loading callbacks and shared title size. No cart/pricing/payment/backend behavior change. Counter form, table defaults, product/cart layout and remaining POS screens still differ and remain OPEN.
- Local Python compile, read-request policy, shared title 18 checks, Native source 471/0 and diff checks PASS. New Kotlin/build and phone/tablet after-images are REQUIRED before accepting this UI chunk.
- Current earlier implementation 4f5b7e74 fixes actual checkout SHA and adds apksigner evidence; exact-head rebuild/export is still queued/running. Do not redistribute stale accepted-parent APKs. Full-order/cashier E2E, VAT, offline routing, hardware and Production signing gates remain OPEN.

## 2026-10-31 Exact checkout versus event SHA — confirmed build identity fix

- Actual Export run 41155450610 built all three Debug APKs and passed the 16KB and WebView gates, then correctly FAILED the DEX source check. Root cause: checkout pinned the PR branch HEAD, but root Gradle preferred event GITHUB_SHA (synthetic PR merge). Embed git rev-parse HEAD from the actual checkout for every app; retain the strict APK validator. No acceptance by weakening the guard.
- Final role export additionally runs Android build-tools 36.0.0 apksigner verify --verbose --print-certs for each APK and retains APK-SIGNATURE.txt alongside exact-size/SHA256 metadata. Signing remains Debug/Test, not Production release signing.
- Current-source offline run 41155688244 PASS: 5 instrumentation tests, zero failures/errors/skips; exact artifact 13024888108 digest d523374c063fa2e5b13059d2d5699671b2a7842206db87b4d0e7e7e145d191e5. Metadata identifies synthetic merge 25f572a5 for ef03e3a7. Bangkok label-feature assertion passed, but its PNG still shows no clearly visible text. Wait one additional second after positive symbol indexing for fade-in before retaining screenshot; visual label acceptance stays OPEN until the new image is inspected.
- Native POS CI 41155688277 on ef03e3a7 SUCCESS. Authenticated Merchant run 41155683786 reached real phone/tablet screenshot capture; review and Web comparison still pending. No new APK delivery or E2E/full-parity claim.
- Read-only backend recheck: all public ordinary tables have RLS enabled; orders/items/payments/users/notifications retain scoped role/ownership policies. queuego_places has no direct anon/authenticated SELECT grant. No tax columns/settings/save RPC present; pos_submit_offline_draft exists. No Production schema/data/financial mutations made.
- NEXT: exact new-head rebuild/export with correct DEX identity and signature evidence; inspect settled offline labels and Merchant images, then fix confirmed differences. Physical GPS/printer/push, real non-production order/cashier E2E, nationwide offline route graph and release signing remain OPEN.

## 2026-10-31 Real offline screenshots and current Merchant visual review — IN PROGRESS

- `f7b64d6dc9c7e2ed611601d2383ed5b5fec16266` fixes reusable-workflow permission validation. Exact-head Export 41155450610 started successfully, reaching full regression; APK output still pending at this checkpoint.
- Downloaded and inspected actual predecessor offline evidence 13023528249 from run 41151804368 (archive SHA f92e6a3c73e509a20c3daa8af086da56fd7b5be277620a8555843f7515d45a41). Its four-test suite passed, retained metadata identifies merge c74fdebb, not the new HEAD. Bangkok/Buriram/Phuket PNGs show offline roads and the airplane-mode icon, but no visible labels. Do NOT infer label readiness or physical GPS from these images.
- Strengthen the existing offline test to wait up to 30s for actual rendered Bangkok symbols before acceptance, keeping the failed-case screenshot through MediaStore. This distinguishes geometry readiness from later glyph/symbol placement; no product map or duplicate renderer change.
- Enable the EXISTING serialized, secret-backed Merchant Web/Native phone/tablet review on PR #736's branch with the existing [merchant-visual-review] opt-in. Keep all existing session/allowlisted-read/request guards. No financial/order business writes or injected sessions. Actual new screenshots and visual acceptance remain pending.
- Exporter exercised with isolated test fixtures: exact-byte export/Debug metadata PASS; stale source rejection PASS; post-validation file tamper rejection PASS before any output directory is created. These fixture checks are export-safety tests, NOT actual APK acceptance evidence.
- Clean worktree full npm test on f7b64d6d completed EXIT 0, including pretest and posttest. The earlier dirty-checkout evidence-gate failure was environmental. New Merchant request-guard, Actions pinning 106/0, Native source 471/0 and diff checks PASS. Android build, signing, physical GPS/push/printer and authenticated order/cashier E2E gates remain open.

## 2026-10-31 Export workflow startup failure — corrected

- Published correction `595f26b54f5a0106da1100e367cbfd4a35553d84` to PR #736 through the connected GitHub API (ordinary shell push has no credentials).
- First exact-head export runs 41155370943/41155374478 failed workflow validation before execution: the reused Pilot declares a separately branch-guarded legacy publish job requesting actions:read/contents:write, beyond caller permissions. Actual GitHub annotation inspected. Caller permissions now permit that declared maximum; native build job itself stays contents:read and the legacy publisher remains disabled on this successor branch. No release or deployment performed.
- Source 471/0, pinning and integration checks passed. Full direct regression is still running; npm pretest is blocked in external pinned-map asset download. New build/instrumentation/runtime/signing/role-size evidence remains pending; do not claim the failed exports produced APKs.

## 2026-10-31 Exact-HEAD role APK export correction — IN PROGRESS

- Fresh remote check: main `7e036a0735cc49905e9b0a2434492470551a0c9d`; PR #736 remains draft/unmerged at parent `b580a65b760160ef67596e689a1310fa55f6d693`. Preserve PR #735/POS and all existing successor work.
- Current source already attaches QG_OFFLINE_MAP_ASSETS only to Rider. Customer/Merchant stream the Web OSM basemap. No application rewrite was necessary for role separation in this chunk.
- Removed historical run 41029740374 / 7c5b6195 APK redistribution and fixed eight-piece assumptions from the export workflow. Export now calls the existing Native Pilot workflow at this revision, with nationwide data mandatory. Checkout explicitly uses the event's exact PR head or push SHA; validation reads the actual checkout SHA. Workflow concurrency includes its caller name to avoid reusable-workflow self-deadlock. Raised the 35-minute job budget to 75 for build plus real emulator gates.
- After all runtime steps succeed, rerun actual APK map/DEX validation, verify file bytes and SHA again, and export one Debug APK per role with TESTED-SOURCE.json and SHA256SUMS. Metadata explicitly retains authenticated_e2e=false / physical_device=false / production_certified=false. Customer/Merchant must contain no maps assets; Rider must contain the matching stored archive and manifest.
- Local source contracts 471/0, Actions pinning 106/0 and integration predicates 76/0 PASS; Python compilation and diff checks PASS. Full npm regression started, but pinned web-asset network fetch remains pending. No new hosted CI success or APK size is claimed before the new commit's run completes.
- Read-only Production Security Advisor fetched: 32 RLS/no-policy INFO, 10 anon definer WARN, 186 authenticated definer WARN, leaked-password protection disabled WARN. These are review findings, not proven vulnerabilities; no blanket permission changes or Production writes were made. Existing allowlist/authorization review must be checked before any change.
- Current machine lacks Android SDK, adb, Gradle and physical hardware; use the existing hosted CI for builds/instrumentation. All screenshot parity, authenticated full-order/cashier E2E, nationwide offline routing graph, physical GPS/printer/push and release signing gates remain OPEN. Existing PMTiles proves map data presence only, not offline route calculation.

## 2026-10-30 Native POS tabs — correct final CSS cascade (IN PROGRESS)

- Successor parent `e82ae91604de1c9a30437cbeb85ca683f0e49184` retains all UI and financial safety work, and serialises the existing shared-account visual review with real Thai fonts.
- Corrected a source-reading mistake: the earlier #181b20 rule is overridden by the later "light professional correction — no black primary surfaces" and <=650px compact rail rules. Actual Production phone PNG `phone-pos-counter.png` in artifact `12955664939` confirms a white selected tab with red text/bottom inset line. Missing Thai glyphs in that old runner do not affect this color/shape evidence.
- Native tabs now follow the FINAL cascade: white/red bordered/shadowed selection on wider screens; <=650dp 36dp/12sp compact selection with 2dp red underline, 9dp corners and 2dp gaps; 650–700dp retains the existing 38dp narrow sizing. No black active surface. Keep every tab, permission and callback; do not undo the single-header or bottom-nav corrections.
- NEXT: exact-head Kotlin/source/CI and serialised real-account phone/tablet screenshots with Thai glyphs. Inspect actual after images before reporting visual parity; continue counter/other Merchant differences from evidence, not intermediate CSS declarations.

## 2026-10-30 Merchant visual review — real session collision and missing Thai glyphs (IN PROGRESS)

- Latest app/UI successor: `92b5996e8805103e25f04a129d5b6f731a6fcecc`; Native POS CI push `41007905597` and PR `41007914631` SUCCESS. Login, POS chrome and bottom-navigation corrections are preserved.
- Actual diagnostic PNG `tablet-back-blocker.png` from run `41006986955` / artifact `12956795518` shows signed-out Login, not an observed broken Back control. Multiple authenticated review jobs overlapped using the same Merchant credentials. Production claim_active_session definition inspected read-only: it replaces the one user_active_sessions row per auth.uid(); Native validate checks that same active session and clears a displaced login. Serialise the existing review workflow with one shared concurrency group; do not weaken Auth/RLS/session validation. Drain older pre-lock runs before starting the next shared-account capture.
- Web baseline artifact `12955664939` contains actual phone primary/POS and tablet primary screenshots, but Thai glyphs are missing on the runner. Install official runner Noto core fonts and verify Noto Sans Thai before capture; do not treat tofu text as an app font bug.
- Safe failure diagnostics also prove the Web request guard blocked the site's normal claim_active_session handshake and read-only queuego_credit_summary RPC. Both definitions inspected read-only. Permit only that existing authenticated session handshake plus the summary read; keep financial/settings/order/shift writes and session revocation blocked. Native capture finishes before Web signs in to the same account.
- Local syntax, request policy, action pinning and Native source checks pending at checkpoint. NEXT: after old shared-account jobs finish, run the serialised exact-head phone/tablet review, inspect real Thai Web/Native images, then continue confirmed visual differences. No full visual-parity claim and no Production schema/business mutation.

## 2026-10-30 Merchant bottom navigation — current Web dimensions and selected icon (IN PROGRESS)

- Latest successor `3f9a69692cb33b45cb7f54adc9e2e560638912cf` preserves login and POS visual fixes. Git grep confirms QgBottomNav has exactly one application caller: QueueGoMerchantApp. Customer and Rider use separate existing renderers; no duplicate navigation system is introduced.
- Evidence: actual Native profile PNG `05-profile.png` from artifact `12955502227` shows the compact 58dp bar with no selected icon background. Current main/served Web `.qgm-bottom` CSS specifies 72px height, max-width 620px centered on tablet, 42x34px icon container, #ffedf0 selected background, 23px icon, 10px label, #e7092b selected and #8f97a2 inactive colors.
- Update the existing Merchant-only-used component to those dimensions/colors while preserving all item keys, labels and onSelect callbacks. Replace the obsolete 58dp source contract with the current Web navigation contract. No auth, backend, data, payment or route behavior change.
- NEXT: exact-head build/source regression and actual phone/tablet captures. Inspect complete primary-tab and POS images; do not infer full visual parity from source values alone. Tablet/Web capture recovery remains under verification in the existing review pipeline.

## 2026-10-30 Native Merchant POS — remove duplicate chrome; match Web tabs (IN PROGRESS)

- Successor base `166761d2c5ba1e57d01a3b79526a6aacdd52c114` preserves login visual changes and the tablet-capture recovery. The owner visual instruction remains active; no financial/backend work is substituted for appearance.
- Evidence: real Native phone POS image in baseline artifact `12955502227` shows a dashboard QueueGo/bell bar above a second POS back/title/reload header. Production `merchant/pos-table-v2.js` shell owns one full-page POS header. Current Production CSS uses dark #181b20 selected tabs, transparent #626a75 inactive tabs without outlines, min-width 88px/min-height 40px, 10px corners, 13px bold labels and 4px gaps. Native showed red active/outlined inactive 46dp/14dp tabs.
- Hide only the outer MerchantTopBar on the existing POS route; keep POS's own header/back/reload and the brand/bell bar on other routes. Match the POS tab color, border treatment and dimensions to the Web CSS without changing tab list, permissions or callbacks. Existing category/cart/payment/offline recovery remains untouched.
- Local source/diff checks pending at checkpoint; exact-head Kotlin/build and after-image capture required. Before-change source already compiled successfully in review `41006159308`; its old tablet harness failure is addressed in the parent, not an application-build failure.
- NEXT: inspect current phone/tablet Web/Native captures, fix the first capture or visual failure, then continue counter layout/product cards and other Merchant screens. Do not report complete visual parity from these bounded changes alone.

## 2026-10-30 Merchant visual capture — first confirmed tablet harness failure (IN PROGRESS)

- Latest UI successor: `9ace1b2cc5c5971071ac74cab4a7c159a9ae4c5e`, preserves the Web-derived login correction. Before-change review run `41005707232`, job `123900036743`, built successfully and saved 9 real Native PNGs (login, five primary phone screens, POS counter/tables/cashier) in artifact `12955502227`.
- First failure: `capture-merchant-authenticated-review.py` line 125 waited for Tablet Home after an unconditional Back issued 5 seconds after wm size/density changes. The harness never established whether Activity/Auth restoration had finished or whether Home had already restored; the final tablet state was not captured. Do not declare this an app Back bug from that evidence.
- Fix the harness to wait for visible POS or primary navigation, send Back only from confirmed POS, select Home explicitly, scroll to its summary and save a diagnostic PNG on failure. Replace ambiguous XML-element truthiness with explicit None checks.
- Let Production Web capture/dependency setup run independently after a Native capture failure so one failed surface cannot erase the other baseline. Failed Web review saves only stage and blocked method/path diagnostics, never credential fields/tokens/body contents.
- NEXT: exact-head captures, inspect actual phone/tablet Web and Native images, then correct the first confirmed Compose layout mismatch. Login shadow/focus correction still requires its own after-image/build evidence; no full visual-parity claim.

## 2026-10-30 Native Merchant login — Web shadow and focus treatment (IN PROGRESS)

- Successor parent `002a83a1a44cb4e8751d1174cb768caeca4a8874` continues the green POS safety head. Existing authenticated review `41005707232` has passed Merchant credential readiness and is building the real apps; its screenshots are the before-change baseline, not final certification.
- Evidence: actual Production Web login inspected in browser and current `qg-login-v08` CSS specify a primary-button shadow `0 6px 18px rgba(231,9,43,.13)`, panel shadow `0 -8px 24px rgba(0,0,0,.05)` and focused-input 3px red halo. Actual Native phone/tablet login PNGs from green baseline artifact `12955096018` plus Compose source show these treatments missing.
- Add those same shape/color/radius/offset/spread values through native Compose dropShadow (available in existing Compose dependencies). Focused inputs retain a white fill and existing red border; button disabled shadow follows existing 65% opacity. Preserve layout, assets, text, Auth callbacks and POS/backend code.
- Local Native source/diff checks pending at checkpoint; exact-head Kotlin/build and before/after screenshots required. This is one bounded visual correction, not complete Merchant visual parity.
- NEXT: inspect exact-head authenticated Web/Native phone/tablet captures, fix capture failures first, then compare primary tabs and POS and correct the first confirmed layout difference. Do not claim visual parity until actual after-change images are inspected.

## 2026-10-30 Merchant Native visual parity — real Web/Android comparison (IN PROGRESS)

- Owner explicitly requires the Native Merchant appearance to match actual Production Web `https://chatchairins-source.github.io/QueuGo/merchant/`, including POS, mobile and tablet. This is now the active UI workstream; preserve Kotlin/Jetpack Compose and all current POS financial recovery changes.
- Clean successor base: `b64d5d9c4d28d2f50e7bc085f3668a0254b1474d`. Exact push Native Pilot `41002046757` and POS CI `41002046760` SUCCESS; all three actual Debug APKs verified to contain that source SHA, artifact ZIP digest verified. No reset, legacy branch or overwritten uncommitted work.
- Live Web login inspected in browser: actual merchant photo, white rounded login panel, red controls and max-width 520px layout. Browser session is signed out, so authenticated visual parity cannot be certified from this page alone.
- Extend the EXISTING secret-backed authenticated-review workflow to this newest POS successor with `[merchant-visual-review]`. Merchant-only requests use the existing Merchant credential readiness, skip unrelated Rider capture, and capture Native POS counter/tables/cashier plus the primary Merchant tabs on phone/tablet. Add real Production Web capture using the same existing account and logical phone/tablet viewports. Never inject a fake login/session or business data.
- Web capture permits ordinary Auth sign-in/refresh plus explicitly allowlisted reads; blocks order, payment, shift, cash, settings and other business writes. Credentials are neither logged nor saved into screenshots; artifacts retain the existing 7-day policy. Metadata remains emulator-only and visual_parity_certified=false until actual images are inspected.
- Local Python/JS syntax, request-policy checks, Native source 467/0, action pinning 98/0 and diff check PASS. Hosted screenshot capture/build evidence pending; no UI similarity completion claim yet.
- NEXT: inspect exact-head authenticated Web/Native screenshots; fix the first evidence-backed visual mismatch in existing Compose screens, then recapture phone/tablet. Keep backend/business behavior and new POS safety fixes intact. Continue across Merchant screens rather than returning to unrelated feature work.

## 2026-10-30 Native POS cashier preflight — reject server-invalid amount before durable save (IN PROGRESS)

- Parent `fac788661fa8e602dbf22c543f1c4ffa941293b8`: full local npm test EXIT 0; exact-head Native POS CI push `41001504935` and PR `41001511432` SUCCESS. Android Pilot still queued behind predecessor builds.
- Follow-up review: the cashier input accepted amounts above the existing `recordCashMovement` maximum 999999999.0, whereas shift-history and API reject them. With durable preparation, such an invalid request could otherwise be persisted before local API validation and remain permanently unresolved despite never being sent.
- Align cashier preflight to the existing maximum before durable persistence; retain all prior decimal/positive checks. Add source assertion requiring validation before save. No backend, business limit, ledger or Production change.
- NEXT: latest exact-head JVM/build/Native Pilot and first confirmed failure; then scope matrix acceptance gates. No full-parity/release claim.

## 2026-10-30 Native/Web POS P0 — preserve offline draft identity after 4xx retry (IN PROGRESS)

- Parent `e54714eccdd8ee10a468ba0fa48afd7f9f6d7fa2` is the latest fetched Native successor, existing PR #735; main Web oracle unchanged. Full local npm test on the cash-store chunk exited 0.
- Production `pos_submit_offline_draft` definition inspected read-only: it locks the scoped request and hashes the full item payload, including per-unit request IDs. Both clients cleared those IDs after any 4xx. Reproduced real Web runtime sequence: server commit/lost response → retry 401 → stored IDs become empty. That changes the immutable payload and unlocks editing/deletion despite a possible existing bill.
- Both clients now preserve durable draft and exact IDs on every uncertain failure. No automatic resubmit, replacement request, payment, backend schema or Production write. New actual Web runtime regression covers exact-payload replay, 401, blocked edit/delete, and one server bill.
- Local evidence: POS runtime 99/0; Native source 466/0; diff check PASS. Exact-head full regression/JVM/build/hosted CI pending. Source parity matrix and remaining gates recorded in `docs/native-merchant-pos-parity-20261030.md`.
- NEXT: exact-head Native POS/Android Pilot first failure, then authenticated cashier/process-kill acceptance and remaining matrix blockers. VAT Production schema/RPC absent; offline currently supports drafts rather than paid checkout. No full-parity or release claim.

## 2026-10-30 Native Merchant POS P0 — cashier cash movements share durable pending store (IN PROGRESS)

- Parent `cf62cfae0fd9072fa1b4af5bbf71f20307b68079` is the latest fetched Native successor on PR #735. Source regression recovery parent `c962ea7c` passed Native POS CI push `41000094234` and PR `41000098909`; full local `npm test` exited 0. Latest tax-compatibility head has its own CI queued/running, not certified from the parent.
- Root cause: the new cashier cash-in/out entry point used six `rememberSaveable` fields only, while existing shift-history cash controls already use `MerchantPosCashMovementStore`. A cold launch can lose the new screen's idempotency UUID, and switching entry points can create a new request while an earlier result is unknown.
- Both entry points now use the existing shop/actor-scoped durable store. Cashier restores its exact UUID/type/amount/reason, persists before any RPC, blocks replacement (including unreadable stored data), and clears only after a matching server receipt. A failed follow-up shift read preserves successful financial acknowledgement. No new ledger/RPC/store or Production write.
- Added SharedPreferences-injected JVM store tests for reopening/cross-entry-point restore, actor/shop isolation, mismatched acknowledgement, failed durable write/clear and corrupt pending bytes. Source tests require durable cashier integration. Local source checks 465/0 and diff check pass; JVM/build/hosted exact-head CI pending.
- NEXT: inspect exact-head Native POS and Android Pilot, repair first confirmed failure, then finish the POS scope matrix and remaining unblocked parity work. No APK/release/full-parity claim; device cashier timeout/process-kill acceptance remains required.

## 2026-10-30 Native Merchant POS P0 — undeployed optional tax schema breaks bill loading (IN PROGRESS)

- Parent `c962ea7c67c47c3cb4ce5ecc2290b40177dfd815` is the published descendant of `dde90e26` on existing PR #735. No uncommitted work was overwritten; local unpublished checkpoint `50d7829d` has the same tree as published `c962ea7c`.
- Read-only Production information_schema audit on `pkypiqhlrmzocysgeqew` confirms `orders` has existing subtotal/total_amount/discount_amount/sales_channel but none of the new tax columns, and no `public.pos_tax_settings` columns. The latest Native POS snapshot unconditionally selects these undeployed fields in both bill reads, so the request fails before rendering the POS snapshot. No schema/payment/Production mutation performed.
- Fix: attempt full tax snapshot reads first; retry only HTTP 400 identifying one of the exact optional tax columns as absent, using identical shop/POS/status/order scope with established bill columns. Auth, timeout, server, unrelated column errors and second-read failure propagate. Server totals are never recomputed; migrated servers keep all tax snapshots. Tax settings load only on entering the owner tax tab, and failure disables saving instead of allowing default settings to overwrite unseen server state.
- Added JVM execution tests for migrated and unmigrated responses, exact read scope, preserved totals, no fake tax fields, auth/network failures and bounded retry. Source contracts verify both snapshot reads use this helper and tax settings fail closed. Local native source checks and diff check pass; JVM/build/exact-head CI pending.
- NEXT: inspect exact-head Native POS and Android Pilot; fix first confirmed failure. Continue full POS parity audit (offline drafts/sync, settlement, receipt, tax checkout) against Web main and real backend. Tax migrations are NOT applied; tax production readiness, authenticated cashier/device acceptance and signing remain unverified.

## 2026-10-30 Native Merchant POS exact-head regression recovery — IN PROGRESS

- Stream: CLOSE NATIVE MERCHANT POS FULL PARITY. Clean checkout; newest fetched Native successor `dde90e26e77b76200a05240beeeb719341c316f6` on existing PR #735 / `work-native-pos-tax-foundation-20261029`. Git ancestry confirms cash-movements HEAD `9ca88a2f4da4796d015933a43ed310c653b2043b` is its ancestor. Web oracle remains main `7e036a0735cc49905e9b0a2434492470551a0c9d`; no reset/overlay or Production writes.
- Confirmed first failure: exact-head Native POS CI run `40886152464`, job `123490456770`, cannot parse `tests/native-android-source.cjs` because receipt-tax guards redeclare `const merchantPrinterSource`. Reproduced locally before editing.
- Removed only the second declaration, retaining every receipt/tax assertion. Moved the success/count output after all tax guards so a late assertion cannot follow a misleading PASS summary.
- Local evidence: `node tests/native-android-source.cjs` PASS 462/0; `git diff --check` PASS. Full repository regression, hosted exact-head CI and Android build remain pending. No APK supplied and no full-parity/release claim.
- NEXT: run full clean-checkout regression and exact-head Native POS / Android Pilot; repair first confirmed failure. Then audit tax integration and remaining payment/offline parity against Web and actual backend authority before live rollout. Physical cashier acceptance and release signing remain separate gates.

## 2026-10-29 Native Merchant POS P0 — make table management discoverable from counter (IN PROGRESS)

- Successor base verified: PR #730 / `work-native-web-parity-20261026` at `ae315af6eb57da72c0946c8647ac73f5b3c8ec90`; new isolated branch `work-native-pos-counter-table-access-20261029` preserves that exact HEAD and does not reset or overwrite it.
- Evidence: the Native POS already had a table-management screen and add-table dialog, but the main `＋ รับออเดอร์` counter screen only showed table selection; users had to discover the separate `โต๊ะ` tab. Added a clearly labeled `จัดการโต๊ะ · เพิ่มโต๊ะ / แก้ไข / QR Code` action on the dine-in counter, gated by the same existing owner/staff permission used by the table screen. It navigates to the existing server-backed table screen; it does not add a duplicate table implementation or bypass permissions.
- Added a native source regression assertion requiring this entry point.
- **NEXT:** open a PR targeting the current successor branch; run exact-head Native Android Pilot and Map Engine CI; fix first confirmed failure. Then audit why the user's installed build still appears unchanged and verify artifact commit/build metadata before delivering another APK. Cashier end-to-end against non-production backend / real device remains unverified; no claim that payment/close-bill is now fixed.

## 2026-10-28 Native Merchant POS P0 — reconcile cashier-shift RPC timeouts (IN PROGRESS)

- Successor HEAD before this chunk: `98defb7a657dd872ad6aea617766b8b5fa14b1a9`; PR #730 / `work-native-web-parity-20261026`. Exact-head Map Engine run `40650534289` and Native Android Pilot run `40650534308` both succeeded. Pilot artifact `12799851173` contains Customer, Merchant and Rider **Debug** APKs from that same HEAD.
- Root cause: open/close shift handlers treated any RPC exception as an error even if the server committed the operation and the response was lost. Opening only read the current shift without checking the requested opening float; closing only read the current shift and did not verify the exact closed shift/count from server history. This could leave the UI showing an error after a successful shift operation and make the cashier uncertain whether to repeat it.
- Bounded fix: after uncertain responses, use read-only Production RPCs to reconcile the exact opening float, or the original shift ID + closed history + exact counted cash. Never automatically resend the open/close mutation. A mismatch or failed read remains explicitly unresolved.
- No Production writes, schema changes, new RPCs, or payment-path changes. New Kotlin regression tests cover matching and mismatched reconciliation cases.
- **NEXT:** run exact-head Native Pilot + Map Engine CI; fix the first confirmed failure. Then verify artifact Build Info / APK metadata and create a SHA-256 bundle. Real cashier acceptance (open shift → create table/bill → tender/change → server PAID/CLOSED → receipt; forced timeout/recovery) remains BLOCKED until a non-production backend/device run is available.

## 2026-10-28 Native Merchant POS P1 — Web report parity (IN PROGRESS)

- Previous QR chunk commit: `867959a838695b66d295414ce4d97ddb8ab4d0d0`. Source integrity and Kotlin unit tests passed; Build APKs, 16 KB page-size check and WebView gate passed in the Native Pilot PR run `40649529851`. Map Engine run `40649529798` passed. Emulator launch was still running at this checkpoint, so full Native Pilot CI is not yet marked green.
- Web Production `merchant/pos-table-v2.js` CSV export contains POS dine-in/takeaway totals, order count, average ticket, cash sales, discounts, refunds, voids, comparison percent, hourly rows and product rows. Native previously discarded those server fields, omitted hourly rows/CSV, and mixed Delivery/GP into the POS-only report.
- This change parses the existing RPC fields, renders the Web POS metrics and hourly rows, exports the same live report/item-sales data through Android's document picker as UTF-8 CSV, protects spreadsheet formula cells, and removes Delivery/GP from POS-only totals. No new RPC or Production data mutation.
- **NEXT:** inspect the first failure on the new exact-head CI and fix it before more features; then package APKs from the newest green Push run, verify Build Info SHA + file hashes, and continue with non-production cashier-flow/device acceptance. Do not claim the real cashier workflow was tested without a test backend or physical device.

## 2026-10-28 Native Merchant POS P1 — QR PNG save/print parity (IN PROGRESS)

- Previous table-create chunk: commit `c4501523c6208202ee7d59ce6ce13615439a6359`; parent `e15b9b5c0a10e755e80cb302ab13f1c9a6a1da4b`. Map Engine run `40647812433` is `success`. Native Pilot push run `40647806064` and PR run `40647812316` are `success` / `success` at this checkpoint; source regression, Kotlin tests, APK build, 16 KB and WebView gates passed before the emulator launch step. Artifact `12798194645` contains three Debug APKs built with Build Info SHA `c4501523c6208202ee7d59ce6ce13615439a6359`; it is an intermediate reference, not the final bundle.
- Web Production `merchant/pos-table-v2.js` supports copy, PNG download and print for table QR. Native previously supported only copy. The new change adds PNG saving through Android's document picker and QR printing through the Android system print UI; receipt printing remains on the existing Print Bridge.
- Table save form now closes only after a confirmed save or after an uncertain result has been reconciled; known server rejections keep the form open. Unknown results close the form and require a read-only reload before the user can try again.
- **NEXT:** inspect the first CI failure on this commit, if any, and fix it before another feature. Then bring the POS sales report into Web parity (cash sales, discounts, refunds, voids, average ticket, change vs previous period, hourly rows and CSV export); do not mix Delivery/GP into POS-only totals. Actual cashier acceptance against a non-production test backend or a physical device is still outstanding.

## 2026-10-28 Native Merchant POS P0 — table-create and QR recovery parity (IN PROGRESS)

- Baseline verified: Web Production on `main` at `7e036a0735cc49905e9b0a2434492470551a0c9d`; authoritative table UI `merchant/pos-table-v2.js` blob `5bff3b5981b5eb1ae2d25f3d1f5095f66f52bc2a`. Native work continues on PR #730 / `work-native-web-parity-20261026` from exact predecessor `e15b9b5c0a10e755e80cb302ab13f1c9a6a1da4b`; PR #641 is an older separate release-source branch, not a descendant successor.
- Evidence-backed gap: Web creates a table then opens its server-backed QR; Native saved the table but did not open/create its QR automatically. Native QR action also offered a rotate button even when a QR already existed, unlike Web, and Native table delete only blocked currently open bills while Web rejects any table with order history.
- Bounded change: Native table create now calls existing `pos_save_table`, then existing `qg_table_rotate_qr`, refreshes the shop snapshot and opens the confirmed QR. If the RPC response is uncertain, it reconciles by reading the table list and never blindly repeats the same create/QR write. Opening a table with no QR triggers the existing QR RPC; an existing QR is not rotated by a default dialog action. Table delete now explains the history restriction and does not send a delete RPC for a table referenced by a bill.
- No Supabase Production mutation, new RPC, new printer, or duplicate table/payment system. Source regression added; exact-head CI after this chunk is pending.
- **NEXT:** inspect the first failure in exact-head Native Pilot / Map Engine CI; fix it before new features. Then close remaining QR save/print parity and produce three APKs + ZIP from the same green HEAD. Physical cashier acceptance remains BLOCKED until an actual Android device tests open shift → create table/bill → cash tender/change → PAID/CLOSED → receipt and timeout reconciliation.

## 2026-10-28 exact-head CI first confirmed failure — obsolete refresh error assertion (test-only)

- Native Pilot run `40638508067`, job `122650947357`, failed first at `tests/native-android-source.cjs:1470`. Its old contract requires literal `refreshError: Exception` in the payment closure branch.
- In the prior bounded payment fix, `refreshSnapshot` catches errors **internally**. The payment-result view now verifies a server-paid snapshot after calling refresh and replaces any swallowed read error with a confirmed paid result or a paid-with-receipt-still-loading message. A literal `refreshError` catch was removed intentionally; the regression must assert the server paid receipt and exact tender instead.
- Changed only this stale test source assertion; it now requires `val verified = snapshot?.bills?.firstOrNull` plus `receiptDialogBillId = verified.id`, retaining `paymentConfirmed` and no duplicate-payment checks. Production, Kotlin behavior, and financial data unchanged.
- **NEXT:** exact-head Map Engine/Native Pilot first failure; then three same-head debug-test APKs and authenticated physical Merchant checkout validation. Release signing remains gated on certified evidence.

## 2026-10-28 Native Merchant POS P0 — preserve paid confirmation after swallowed refresh errors; preview verified receipt

- Exact predecessor `ee8bf7449b3f956e2cc337fea0df6291339d4141` passed both Map Engine CI `40636782133` and Native Pilot CI `40636782098` with three debug-test APKs. CI artifact `12794051734` (debug only) verified and locally repackaged as `QueueGo-Android-POS-Test-3-Apps-ee8bf744.zip`.
- Further code audit found `refreshSnapshot` catches read errors internally and **overwrites the message**. Earlier fix wrapped it in catch, which cannot intercept those swallowed refresh errors. Payment already confirmed by `pos_take_payment` or `paymentConfirmed` could still appear as load failure.
- Native checkout now writes the final confirmed-payment status AFTER refreshing; when a fresh order in the authorized snapshot matches PAID + CLOSED + method (+ exact cash tender), it shows a read-only receipt preview using existing `merchantReceiptTicket` and exposes manual printing via the existing HTTPS Print Bridge (with explicit printer setup if absent). If the latest receipt cannot load, the payment remains acknowledged without fabricating a receipt or retrying cash collection.
- No server SQL/data mutations, no duplicate printer implementation. Existing automatic printer behavior remains unchanged. Source regression added.
- **NEXT:** exact-head Map Engine and Native Pilot CI first confirmed failure. Then build same-head 3 APK set, inspect emulator and validate physical Android POS cashier flow (open shift -> create bill -> payment -> receipt) and barcode SKU. This source fix does not constitute physical acceptance. Non-debuggable release/signing still pending.

## 2026-10-28 Native Merchant POS P0 — verified payment acknowledgement and timeout reconciliation (source checkpoint)

- Latest exact-head green Native Pilot + Map Engine at parent `8fe40b929a7d2cd02a8348b8a45bd916c0536271`. Audited Production `pos_take_payment`: server commits payment and closes bill atomically, returns paid order JSON; identical payment attempts can be idempotently accepted but *different* payment/tender cannot. Found Android incorrectly returned `billId` as a success fallback if RPC response was not a confirmed paid receipt, and mixed a follow-up snapshot refresh failure into the financial operation's catch path, displaying failed checkout after a successfully paid bill.
- Changed Native `takePayment` to require exact server receipt id + `PAID` + `CLOSED` + payment method + original cash tender, with no fallback success. Added read-only `paymentConfirmed` that checks the same scoped POS bill after uncertain RPC failure; does not resubmit payment. A confirmed payment remains successful even if refreshing the POS screen subsequently times out. Unknown outcomes explicitly instruct checking the old bill before attempting again.
- This change touches neither Supabase Production function nor payments, amount computation, permission, printer or ledger schema. Regression source contract added; exact HEAD CI and actual cashier tablet acceptance remain mandatory.
- **NEXT:** exact-head Map Engine + Native Pilot first confirmed failure. Then test cashier open shift → real POS bill → cash received → paid/closed → receipt and forced timeout/reconcile on physical device. Verify no duplicate payment writes. Review mobile printing and remaining Android POS parity only after these gates.

## 2026-10-28 exact-head POS FULL CI first failure — stale shop category reset source test

- HEAD `981aa4277e0f214110d26173671b55ac8d1a8073` Native Pilot run `40634661409` and Map Engine run `40634661414` both stopped at `tests/native-android-source.cjs:1403` in initial regression: the test demanded the prior single-line `LaunchedEffect(snap?.shopId) { selectedPosCategory = "all" }`. The actual Android code correctly expands this shop-switch effect to clear barcode preview, scan status and code entry while retaining category reset.
- Fixed **only** the stale source string assertion to require shop reset, category reset and cleared scanned result. Did not change Kotlin, barcode API, checkout, payment, database or Web.
- **NEXT:** exact HEAD Map Engine + Native Pilot CI first failure, build three APK from same HEAD if green. Test real tablet scan / barcode management and cashier cash-close flows; report only verified device features.

## 2026-10-28 Native POS FULL — counter barcode/SKU lookup with guarded add-to-bill (IN PROGRESS)

- Native Counter now has compact barcode/SKU input (compatible with keyboard-style scanners and manual entry), reads current store's `pos_product_code_lookup` Production RPC and confirms a matching live POS catalog product before allowing an explicit `เพิ่มเข้าบิล` action.
- Guards offline lookup, shop/actor/selected bill/mode/table context changes during asynchronous lookup, unavailable or POS-disabled products, sold-out stock and pending idempotent order creation. Uses existing `addPosProduct` action for option selection, stock checks and server-authoritative order creation; no second order implementation and no automatic duplicate scan insertion.
- Management tab and code save/remove from predecessor retained. Source tests added; camera scanning, auto-add-on-scanner-enter and real device barcode acceptance NOT claimed.
- **NEXT:** exact-head CI first confirmed failure and Kotlin compile. Resolve any source test first, then real tablet POS cashier cash-close and code lookup + create-bill acceptance on same-head APK.

## 2026-10-28 Native POS FULL — barcode/SKU management from existing Production RPC (IN PROGRESS)

- Exact Native successor parent `78ca5284939dc309a0306c98141a086230082be5`. Current Web FULL POS has shop-scoped Barcode/SKU administration, but Android POS had no code management tab or API adapter.
- Added authorized `บาร์โค้ด/SKU` tab for actors with `manage_staff`, reusing existing `pos_product_codes_for_shop`, `pos_product_code_save`, and `pos_product_code_remove` RPCs with existing server checks. Product chosen explicitly from current store catalog; client validates barcode 8–14 digits or SKU syntax before server validation. Lists saved codes and confirms delete; no duplicate backend or synthetic catalog.
- Regression guards presence of exact RPCs and managed tab. The existing Server remains sole authority for shop ownership, collisions, auditing and permissions. This does not yet implement the counter barcode *scan/add* workflow; that remains NEXT.
- **NEXT:** exact-head CI first confirmed failure, then integrate barcode lookup into existing counter add-product flow with duplicate guards; test cash-register real-device close and barcode SKU on Samsung tablet before marking FULL parity.

## 2026-10-28 Native POS FULL — restore Production item sales reporting (read-only)

- Current Web FULL POS displays per-item sales from `pos_owner_item_sales(p_days)`. Native Dashboard displayed only overall totals and paid bills. Production function inspected read-only: returns `{days,item_count,items:[{item_name,quantity,bills,sales}]}` scoped to current owner shop and paid POS orders.
- Added owner-only Native RPC read for 1/7/30 days, typed item counts and per-product paid revenue, and dedicated `สินค้าขายดี` section in POS reports. No local counterfeit calculations, server writes, new permissions or transaction paths. Existing overall report / refund logic preserved.
- Source test added. **NEXT:** check latest exact-head CI and fix first confirmed failure before further features. Physical Android screenshot parity and actual owner report data verification required. Barcode and audited cash-register movement E2E remain open.

## 2026-10-28 Native POS cash ledger fail-closed pending-store hardening

- During exact-head review, found a safety edge case: `save` only blocked an existing pending request when JSON decoding succeeded. Corrupt persisted bytes could otherwise be overwritten with a fresh request UUID, risking duplicate cash records after a network timeout.
- Now fails closed for **any existing persisted key**, including unreadable contents, before creating another transaction. No Production RPC or financial data touched. Added source guard.
- **NEXT:** exact-head Kotlin + Map Engine verification, then test normal payment, cash-in/out and same-key retry on real tablet; if pending JSON is corrupt, handle it via audited recovery rather than automatic replacement.

## 2026-10-28 Native FULL POS register cash-in/out — durable idempotent request (pending exact-head CI)

- Added Android register cash-in/out under `กะรับเงิน` only when cashier has a live server-confirmed shift. Uses existing Production `pos_record_cash_movement` RPC, not a new ledger.
- Before each cash mutation, stores actor/shop-scoped exact UUID + type + amount + reason synchronously on the device; a timed-out result retains the same payload and the UI offers **ตรวจรายการเดิมด้วยรหัสเดิม** instead of generating a second cash entry. Confirms returned server receipt (id, event type, signed amount, reason) before clearing pending. No automatic retry, no offline transaction, no fake success. Cash-in/out amounts and reasons validated according to Production function.
- Displays server current-shift cash-in/cash-out totals. Source regression added; Production DB and existing POS bill/payment RPCs unchanged.
- **NEXT:** exact HEAD Native Pilot + Map Engine first confirmed failure; exercise retry after timeout and reopen app on real device; verify cashier shift and ledger. Continue FULL barcode and item sales gap. Do not mark financially complete on source tests alone.

## 2026-10-28 Merchant POS Full Option — restore Native cashier shift history (read-only)

- Exact Native successor parent `77e052f5a45d1993b0a9a9cf22db9c8da25e5fe0` already passed Map Engine + three-APK Native Pilot. Current Production Web FULL POS shows a dedicated cash-register shift history from `pos_register_shift_history(p_limit)`; Native only showed the live shift on the `คิดเงิน` panel, with no history.
- Added a Native **กะรับเงิน** tab for actors authorized `view_sales` or `close_bill`, wired to the existing server-authoritative `pos_register_shift_history` RPC. Cashier role can also see current shift from `pos_current_shift`; other report-only staff never invoke an unauthorized current-shift RPC. Shows actual cashier, opened/closed timestamps, opening cash, expected cash, counted cash and variance. Read-only: no financial mutation, fake data or new database capability.
- Added source test. **NEXT:** exact-head Map Engine/Native Pilot Kotlin compile and runtime first failure; then implement cash movements with durable idempotency or fix payment acceptance, leaving Production until owner device test. POS FULL barcode and item reports still pending.

## 2026-10-28 exact-head CI: Merchant POS login stays on POS after sign-in

- Map Engine run `40631653765` / `122629176218` reached `tests/merchant-routing.cjs:7` and expected navigation `dashboard` after owner sign-in while the current Web main `qtShopDoLogin` intentionally returns to POS if login was triggered from `#pos`. The mock already verified a successful shop session `saved[0].role==='shop'`; failure was the retired route expectation.
- Updated **only** the obsolete expected destination from dashboard to POS. Preserved real owner/admin/staff role checks; no app login or backend changes.
- **NEXT:** exact-head CI first error; afterwards test Native Merchant cashier shift/payment and FULL POS category parity on Android tablet. Do not mark DONE until verified.

## 2026-10-28 Native Merchant POS FULL category parity from current Web Production

- Current `main` Web FULL POS supports product category filtering combined with text search; Native successor lacked `products.category` in `PosProduct`, snapshot query, counter UI and offline catalog. Restored category as optional field using the existing Production products column (no new schema).
- POS now shows an "ทั้งหมด" and actual distinct category chip row, combines category+text filters, resets category on shop switch or vanished category, preserves null/uncategorized products under All, and serializes optional category into the same actor/device-scoped offline catalog. Older cached catalogs with missing category remain readable. No fake products or stock changes.
- Added source regression. Exact-head Kotlin/unit/CI and real Android tablet acceptance are pending. Web controls authoritative in `main`; no cherry-pick of outdated UI code.
- **NEXT:** inspect exact-head CI first failure. Then continue POS FULL Option gaps for Native cashier register cash movements, barcode/code management, item sales and shift history; run same-head Native 3 APK pilot and merchant cashier/order/table acceptance.

## 2026-10-28 exact-head Web FULL POS first CI: obsolete Merchant login mock (test only)

- Exact HEAD `b463606cb15e53a8d2b57160eaa6872050712e72` Map Engine `40631473005` / `122628600353` failed `tests/merchant-routing.cjs` at `saved[0].role`. The test still mocked `qtLoadOwnSupabaseUser` / `qtUserFromRow` but controlling Production POS main `qtShopDoLogin` now calls `qtLoadOwnMerchantIdentity` and requires mapped shopProfileId. This mismatch is a **test stub** deficiency, not evidence of Production login failure.
- Added current `qtLoadOwnMerchantIdentity` stub composed from the existing mutable `profile` and active staff fixtures, including authoritative profile and shop identity. Preserved admin reject, POS staff, merchant login, deleted account and inactive staff contract tests.
- No Production JS, Kotlin, RLS, auth, store or backend behavior changed in this follow-up.
- **NEXT:** check exact-head Map Engine + Native Pilot; fix first confirmed regression; then restore Native POS category filters and remaining Web Production FULL features by gap-based parity; test cashier payments on device before release.

## 2026-10-28 P0 FULL POS provenance root cause — preserve current Web main feature set

- User reported FULL Option vanished. Compared latest Web `main` POS assets with Native successor. They are **different lineages**, not equivalent UI: `main` `merchant/pos-table-v2.js` retains product category filters, cashier shifts, cash movements, shift history, barcode code scan/management, per-item sales reports, offline draft support and option controls; Native branch's older JS had none of most of those and did not load `pos-offline-drafts.js`. Main JS length ~410 lines vs old Native-branch Web JS ~286; main POS CSS ~38k vs old ~11k. This was the main reason FULL Option was absent from the Native successor's copied Web source.
- Following Web Production authority **forward**, this descendant now carries **exact current main blob SHAs** for `merchant/pos-table-v2.js`, `merchant/pos-table-v2.css`, `merchant/pos-offline-drafts.js`, and `tests/pos-runtime.cjs`; updated only POS asset tags in Native-branch `merchant/index.html` to load the offline dependency first and bust old cache. No reverting to older POS, no duplicate implementation, no Production mutation, and `main` is left unchanged.
- This only restores the full Web POS asset set **inside this successor branch**. Native Kotlin still requires parity work for category filters, barcode/code management, register movements/history and per-item sales. Product options are partly present in Native. Any browser/Android release must be verified from exact HEAD; no claim of Production deploy or full Android parity yet.
- Cashier/shift Kotlin integration from ancestor commit remains preserved. Tested Web `main` POS (not the interim branch-only reconstruction) is the controlling FULL POS blueprint. Do NOT later overwrite the newer `main` with old Native-branch Web assets.
- **NEXT:** exact-head Map Engine+Native Pilot regression, fix first confirmed failure, then Native parity gap implementation by highest business impact. Verify Android cash take/close receipt with live cashier shift on device; APK distribution only from same exact HEAD.

## 2026-10-28 Merchant Web POS cash-register parity — open/close shifts and non-kitchen settlement (IN PROGRESS)

- Verified Production requires `pos_current_shift` / `pos_open_shift` before `pos_take_payment`, and Web `merchant/pos-table-v2.js` lacked this UI while filtering all unpaid bills by kitchen READY/SERVED, breaking grocery/non-kitchen cashier. The native fix uses same RPCs; this Web change restores a single server authority.
- Web POS now reads authoritative shop category, hides Kitchen tab where irrelevant, renders all unpaid POS bills with ready/waiting status, and exposes current cash shift, opening float 0 or chosen amount, expected cash, counted cash and confirmation to close shift. Counter and cashier preflight ensure an authenticated open shift before invoking the unchanged `pos_take_payment`. Server permissions and settlement invariants remain final authority, and no shift opens silently.
- Updated `tests/pos-runtime.cjs` source contract. Web behavior needs browser E2E with authenticated cashier and production deployment; Native APK pending latest-head CI. Do not call either Production done based on source changes only.
- **NEXT:** exact-head Map Engine and Native Pilot; repair first failure, then test full cash flow on Android tablet and Web (open shift → create bill → collect cash → close bill → receipt). Continue broader FULL Option gap audit (cash in/out, barcode/product codes, shift history, inventory ledger, item sales).

## 2026-10-28 exact-head CI first failure: legacy bill-list Kitchen filter assertion

- Map Engine `40630499304` / job `122625490655` failed first at `tests/native-android-source.cjs:1248`: assertion required old `bills = openBills.filter { !kitchenEnabled || READY/SERVED }`. This hid unpaid POS orders instead of showing them with status, while Production permits direct settlement for non-kitchen shops.
- Changed **only** old source assertion to require complete open-bill visibility and readiness gating in checkout while preserving counter action policy. No financial mutation, Kotlin behavior, or RLS changes in this follow-up.
- **NEXT:** inspect exact new HEAD Map Engine and Native Pilot; fix first confirmed failure, then test actual cashier shift and payment closure on a real owner/cashier account. FULL Option feature parity remains incomplete pending inventory/register/report/printing audit.

## 2026-10-28 P0 Merchant POS Full Option cashier/shift gap — source fix pending exact-head CI

- Owner reports POS cannot calculate/close bills and asks where FULL Option went. Audited live Production Supabase (read-only) and real Native/Web source: Production `pos_take_payment` requires an open cashier `pos_register_shifts` row for the authenticated cashier. Production exposes `pos_current_shift`, `pos_open_shift`, `pos_close_shift`; Android has no UI/API wiring for these. Observed zero currently open shifts at audit time (not sufficient to identify specific user account). Native `PosBillsView` also filtered ALL bills by `kitchen_status in READY/SERVED` for food/cafe only but Native non-food category detection can be missing/incorrect. Explicit non-kitchen eligibility is enforced by the server.
- Bounded fix: Android now fetches Production cashier shift on entering `คิดเงิน`, exposes opening float / explicit start shift, displays expected cash and manual counted amount for close shift, and shows every open POS bill with ready/waiting status. Food/cafe settlement remains gated by kitchen status; non-kitchen can pay at NEW. UI only enables server-authorized settlement with confirmed open shift and preserves cash received / change. Existing `pos_take_payment` remains server authoritative and idempotent for matching retries.
- No implicit financial mutation, no synthetic bills and no bypass of `pos_allowed('close_bill')`. Web also lacks the shift UI, so FULL Option restoration is not yet complete; follow-up must unify Web and Android instead of adding parallel POS engines.
- Regression added to `tests/native-android-source.cjs`; no release claim until exact-head Map Engine and Native Pilot pass and real test cashier opens shift, adds product, selects bill, takes cash, closes bill and verifies receipt and ledger.
- **NEXT:** exact-head CI first confirmed failure. Audit remaining FULL Option gaps (shift cash in/out, inventory/stock, returns, receipts/printing, table/QR, staff roles) against Production schema and Web; implement only missing integration with same POS RPCs, then same-HEAD APK builds and tablet acceptance.

## 2026-10-28 Merchant POS counter product grid — responsive phone/tablet visuals (IN PROGRESS)

- Owner's real Android tablet counter screenshot displayed only two oversized product cards despite a wide screen. Source in `PosCounterView` hard-coded `products.chunked(2)` regardless of viewport.
- Counter now renders 2 columns on compact phones, 3 on >=620dp and 4 on >=980dp, with bounded photo heights, stronger price typography, readable edit-price action and consistent card min heights. This modifies presentation only: product availability, stock guards, real selection, bill creation, server checkout and permissions stay unchanged.
- Added source regression for responsive thresholds and preserved click/stock guard. No mock/demo products.
- Parent table-create visibility fix at `d205e7b167453d3591f38f1840e2e47899cff4fc` is on exact-head CI; this successor must receive its own Native Pilot/Map gate. Actual Samsung tablet visual comparison, real POS table creation and order/payment acceptance are still required.
- **NEXT:** check exact-head CI first confirmed failure; then verify the POS table-add control, tablet grid, QR and server-backed create/open/close flows with authenticated device evidence. Do not distribute an old APK as fixed.

## 2026-10-28 Merchant POS tablet — hidden Add Table button root cause and responsive visual repair (IN PROGRESS)

- Owner's seven real tablet photos show POS typography/actions far too small, large empty regions, and no visible `เพิ่มโต๊ะ` button even though existing tables 1/2 expose QR/edit/delete. Read newest successor source and confirmed root cause: `PosTablesView` placed shared `QgSectionTitle` (internally `Modifier.fillMaxWidth()`) inside a horizontal `Row`, consuming the full width and clipping the sibling add button on a tablet. `canManage = can("manage_staff")` remains the existing server-authorized owner/staff gate.
- Replaced the header with a weighted title column plus prominent red 48dp `＋ เพิ่มโต๊ะ` action, visible on tablet and phone. Added occupied/available/total table summaries, legible table cards, 2-column responsive layout for >=650dp, larger controls and empty-state create action. Increased POS tab tap targets and labels. Existing Open Table, QR, edit, delete, and `api.saveTable` creation remain wired as before. No fake tables or new authority.
- Added source regression asserting visible add, no full-width title-row collision, responsive layout and existing server create API. Source-only checkpoint pending exact-head Native Pilot (Kotlin/APK) and Map Engine; owner real-device visual and functional creation/reopen tests required before marking DONE.
- **NEXT:** inspect exact-head CI first failure; then test add table on Samsung tablet, create/refresh/reopen table, QR, dine-in bill and delete permissions. Continue POS consistency for other tabs after evidence.

## 2026-10-28 exact-head Map Engine first regression: legacy 115dp Checkout height assertion — test-only fix

- Source HEAD `9b24d6ef73edb844d233902ed7b0146f7e2e7fbb` Map Engine `40626760052` / job `122613435467` first failed `Dependencies and regression` in `tests/native-android-source.cjs:185`: the old test requires `height(115.dp)` while intentional Web/Android Checkout map layout uses bounded `checkoutMapHeight`.
- Updated **only** this stale Native source test to require `height(checkoutMapHeight)` and `.coerceIn(200.dp, 300.dp)`; retained the existing cart vs checkout, real QueueGo Map, production RPC and action-dock assertions.
- No Kotlin, Web map, backend or financial logic changed in this follow-up.
- **NEXT:** inspect exact new HEAD Map Engine and Native Pilot and fix first confirmed failure. If green, obtain and validate three same-HEAD APK artifacts; physical map tap, Rider photo-preview and POS parity remain OPEN.

## 2026-10-28 Customer Checkout map touch-target height parity — IN PROGRESS (exact-head CI NEXT)

- Confirmed Customer Web Checkout and Native Checkout both hard-coded a 115 px/dp tall selectable map. The same app's dedicated Location screen already allows 240–400 dp. A 115 px checkout viewport constrains precision when the user must tap a coordinate, especially with zoom controls.
- Both checkout surfaces now use an equivalent screen-relative height: 28% of viewport, bounded to 200–300 CSS px / Android dp. This keeps a usable tap area on phones while limiting tablet overflow. Native composes the height from `LocalConfiguration`; Web uses CSS `clamp`.
- No data, routing, location selection authority, fare/order mutation or address rules changed. Added a Web/Native source contract to prevent the 115 px/dp regression.
- Previous exact HEAD `4c5785590cdefa5f2a8e54b2b55f412645a516ec` Map Engine success and Native Pilot still pending on shared runner at time of change; do not claim latest-head APK/build green without its own evidence.
- **NEXT:** verify this exact HEAD Map Engine + Native Pilot, fix first confirmed failure, then physically test tap-select on Customer Checkout and dedicated Location screen in phone/tablet layouts. Resume Rider receipt photo and Merchant POS owner acceptance thereafter.

## 2026-10-28 Web Customer address picker tap-to-pin alignment — IN PROGRESS (exact-head CI NEXT)

- Confirmed Web `index.html/initLocationPicker` still bound `moveend` to automatic coordinate selection and offered `เลือกตรงกลาง`; Native Customer already selects only an actual map tap. This caused a behavioral parity gap for address/checkout selection.
- Web Customer picker now selects the exact tapped `lngLat` on a generic map click; pan/zoom no longer changes the address. Tapping a rendered QueueGo Places feature defers to its precise Places callback rather than overriding it with screen-click coordinates. GPS and initial point selection remain supported.
- Removed the extra center-choice button, changed the visible hint to `แตะบนแผนที่เพื่อปักหมุดสีแดง`, and updated the Customer lifecycle regression to exercise tap selection, no pan selection, teardown safety and GPS. Merchant picker remains unchanged in this chunk.
- Code invariants checked before publication. No claim of real-phone tap/GPS verification or Web Production deploy.
- **NEXT:** inspect exact-head Native Pilot + Map Engine, fix first confirmed failure only, then test both Customer location and checkout on phone/tablet; continue the same-head APK build and Rider photo-preview investigation.

## 2026-10-28 exact-head Map Engine Rider road-display stale marker assertion — IN PROGRESS

- On exact HEAD `5a456495f6c76943a7b0c1b4e3470d798b062047`, Map Engine `40625287818` / job `122608629362` failed the first regression in `tests/native-rider-road-display.cjs:21`: the test still expected `demandMarkers.clear()` after intentional Native-only demand-pin removal.
- Replaced only the retired marker-container assertion with absence-of-container and lifecycle signature-reset guards. Route geometry, Rider runtime and Production are untouched by this test-only patch.
- **NEXT:** rerun Native Pilot + Map Engine at new exact HEAD. Fix first confirmed red check, then review three-app APK artifact and device parity. Real photo/gesture/merchant POS acceptance remains open.

## 2026-10-28 exact-head Map Engine next first failure — obsolete MarkerOptions contract repaired (IN PROGRESS)
- Head `c54a0a991640ef74ad64cd3fe63d75a444642de2` Map Engine run `40625105793`, job `122608043753`, first confirmed failure in `Dependencies and regression`: `tests/native-map-marker-design.cjs:16` still required legacy `MarkerOptions()` after removing native-only demand pins. Current actual shop/customer pins are already GeoJSON+CircleLayer and routes use PolylineOptions.
- Changed only that stale source assertion to require GeoJSON marker renderer and absence of MapLibre MarkerOptions/demandMarkers. No production app logic changed.
- **NEXT:** obtain exact-head Native Pilot and Map Engine, repair first newly confirmed failure only; inspect signed/debug three-APK artifact from current HEAD after green; physical gestures and receipt preview remain BLOCKED on real-device evidence.

## 2026-10-28 exact-head Map Engine first failure — stale Rider demand marker assertions fixed (IN PROGRESS)
- Exact head `4f9b91b74a46155a142115e94789c3afeb6acecf` failed Map Engine run `40624833834`, job `122607140703`, first failing `Dependencies and regression` step: `tests/native-three-app-stability.cjs:656` still demanded `demandMarkers.clear()` after the Web-parity source removed native-only demand map annotations.
- Corrected the **two** stale lifecycle assertions to require absence of `demandMarkers` and retention of `lastDemandSignature = null`. Kotlin/product code unchanged in this follow-up. No source-test runtime/Gradle/physical-device PASS claimed yet.
- **NEXT:** require fresh exact-HEAD Native Pilot + Map Engine green; fix only first newly confirmed failure; continue black receipt preview diagnosis and three-app same-HEAD APK gate. Do not distribute the older e35b0bff ZIP as current.

## 2026-10-28 Rider native-only work-area map pin removal — IN PROGRESS (exact-head CI NEXT)

- Audited the controlling Native successor `35332cf49ea5c45b30e8ed44e798a4df3a7e9540` in PR #730 against `rider/index.html`. Android alone created clickable MapLibre demand-level markers with work-area popup text, the source of the extra owner-observed map label; Web Rider has no equivalent map marker. This does not prove or fix the separate oversized floating card.
- Removed only these Native-only demand-marker annotations; underlying demand-zone data, route, GPS, work assignment and non-map recommendations remain intact. Map diagnostics retain demand-data change accounting. Updated the Rider navigation source contract to prevent the Web-parity regression.
- Source invariants verified before publication; full npm/Gradle/APK and Android device tests **not yet certified** for this change.
- **NEXT:** exact-head Native Pilot and Map Engine on the new SHA; repair the first confirmed failure, then inspect the black receipt-photo preview and remaining floating-card discrepancy with actual Android image evidence. Previous e35b0bff APKs do not contain this change.

## 2026-10-28 OWNER SINGLE-QUEUE: all reported and unfinished Android Web parity — P0/P1 OPEN until physical acceptance

This is the **one controlling unresolved-device list** for Customer / Merchant / Rider. User explicitly requires inclusion of unfinished fixes, regressions, and items merely committed/CI-green but not proven functional on a real Android device. Exact source inspected at `2144a5e340d3130d3108b56cfc0608cfa2ee7ba5`; Native Pilot `40622365522` and Map Engine `40622367200` are green. Earlier published APKs/ZIP are from `e35b0bff` and **do not contain** subsequent native fixes. DO NOT mark all-parity DONE or re-send an older APK as evidence.

| Priority | Owner-visible problem / required acceptance | Evidence / state |
| --- | --- | --- |
| P0 | Rider map navigation FOLLOW: heading-up 3D, smoothly track Rider, rider anchor lower third, speed zoom, faster turns, route-ahead-only, snap-to-road, immediate off-route reroute, recenter, bottom-right compass/speed, map-first responsive job sheet | Multiple owner photos; source fixes exist; physical movement/rotation/GPS validation OPEN |
| P0 | Rider navigation manual multi-touch: pinch zoom without camera stealing gestures, two-finger rotate and pitch 3D, uninterrupted tracking after gestures finish; same behavior on tablet and phone | `98dd82ab` code change allows multi-touch and 8-second manual idle; physical test OPEN |
| P0 | Customer Ride map and journey: pinch/rotate/tilt without camera reset; map-first pickup/destination, nearby Rider supply, quote/request/active trip, isolate history/irrelevant QT entries | `2465b5da` source fix (initial viewport once); real interaction parity OPEN |
| P0 | Rider food pickup evidence: remove redundant confirmation popup and green primary CTA; preview receipt **must display real photo (not black rectangle)**, preserve one photo proof and secure server-backed pickup transition | `98dd82ab` removed redundant popup; photo preview black in owner image, root cause/retest OPEN |
| P0 | Rider home/area map: investigate extraneous `พื้นที่งานระดับ 1` map overlay and oversized floating card; ensure same map overlays, visual hierarchy and controls as Web, not obscure Rider arrow/route | Owner physical photo; compare to exact Web production and validate correct source OPEN |
| P0 | Customer location selection and checkout: tap precise point moves a visible red pin immediately, GPS/place search, address/reverse-geocode and save match Web; do not select camera center merely by panning; preserve tablet layout | Native on-tap code committed, BUT Web picker still has `moveend`/select-center path; behavioral parity and real-device acceptance OPEN |
| P0 | Merchant order: 20-minute countdown must really tick from server/persisted target and +/- adjustment; progress connector lines/stage colors/back controls same as Web across all order roles | Source change committed; timed real order + refresh tests OPEN |
| P0 | Merchant POS: real functional dine-in/takeaway/delivery, tables, bill, cash checkout, POS 0% GP except delivery configured GP, KDS, receipt/printer, QR table, staff, reporting/history and inventory; no decorative shells | Substantial Native source exists; owner image reveals parity gaps; authenticated end-to-end parity OPEN |
| P0 | Merchant Grocery catalog: render permissible real product reference thumbnails in Android, including Makro source preview using allowlisted host, not blank boxes; source-only images must not silently become customer-approved images | Native/Web fix committed; owner physical CDN/image test OPEN |
| P1 | Customer shopping: remove persistent global red `สั่งซื้อสำเร็จ · QT-XXXX` on unrelated routes; order detail/status has correct compact back button and connecting progress stages | Source fixes claimed in prior commits; owner physical screenshots; device check OPEN |
| P1 | Customer Notifications: Web-like large title, back icon, red `อ่านทั้งหมด`, readable cards; no auto-read on entering; unread count and explicit user action consistent; no stale success banner | `ac294d6e` code change committed; real-device acceptance OPEN |
| P1 | Customer live order tracking: distinguish merchant/pickup, Rider, customer/dropoff markers and visible legend; enough map height on phone/tablet; no fabricated Rider marker when unassigned | `2144a5e3` code change committed, exact-head CI green; owner device proof OPEN |
| P1 | All three apps: no duplicate buttons/dialogs, no broken back paths or invasive full-screen overlays; retain useful previously loaded data locally and avoid recurring load indicators; test orientation/tablet and physical login/order flow | Owner complaints; comparative regression sweep OPEN |
| Release blocker | After all P0/P1 acceptance: run exact-head Native Pilot + Map Engine + 16 KB/emulator/auth checks; build Customer/Merchant/Rider three fresh APKs from **the same tested HEAD**, verify signatures and deliver new three-APK ZIP/public HTTPS links for LINE/Facebook | Current green CI != real-device parity. Source `2144a5e3` artifact exists, but physical acceptance is still OPEN |

**Sequencing / NEXT**: (1) inspect the two latest Rider screenshots against exact Web and current Kotlin (remove non-Web work-area overlay only if inappropriate; determine why receipt preview is black), (2) retest Rider/navigation + Customer Ride gestures from a newly built exact-head APK, (3) exercise Merchant timer/order and Catalog/POS real account, (4) perform Customer location/notifications/tracking checks, (5) fix only reproduced failures, commit small changes from newest descendant HEAD, and only then issue a fresh signed/debug device-test ZIP. **Never close a row merely because CI is green.** If real hardware evidence is needed, record the precise failure and treat that row BLOCKED, while continuing unblocked rows.

## 2026-10-28 Customer live order map actor / stop identity — P0 fix pending exact-HEAD CI

- Owner device photo of QT-4626 shows three colored dots with no indication which is the merchant/pickup, Rider, or customer/dropoff; map height was only 145–178 dp.
- Customer tracking point authority now carries explicit role labels (ร้านค้า · รับ / ไรเดอร์ / ลูกค้า · ส่ง) into the shared MapLibre role-specific marker artwork. The unassigned/searching state does not invent a Rider marker.
- Enlarged the live tracking map and added an always-visible 3-part legend; gray Rider legend correctly indicates no valid Rider position. No changes to coordinate source, RLS, or dispatch.
- Added Kotlin assertions and Native source regression for labels and visibility.
- **NEXT:** check exact-HEAD Native Pilot and Map Engine. Fix first confirmed failure. On successful build, generate new Customer debug APK and test QT live tracking in Android portrait/tablet. Do not treat CI-only marker visuals as proof of physical-device parity.

## 2026-10-28 Rider camera gestures + remove redundant pickup confirmation — code fix pending exact-HEAD CI

- Owner physical evidence: Rider map could not zoom, rotate or tilt like the Web while navigating. Root causes confirmed at source: multi-touch was explicitly *not* reported as a user gesture; 110 ms FREE_PAN auto-follow preempted finger input; MapLibre interaction flags were not explicitly enabled; route-aware camera bearing was overwritten with raw puck bearing; manual input during initial GPS acquisition was forced back to FOLLOW.
- Native Rider map now explicitly enables pinch/scroll/rotate/tilt plus simultaneous zoom/rotate. A two-finger gesture enters FREE_PAN, the idle timer starts once fingers are lifted, and active navigation returns to FOLLOW only after 8 s idle or immediate user recenter. Initial GPS acquisition does not block manual map gestures. The FOLLOW camera uses the already smoothed route/GNSS bearing decision; zoom/tilt remain governed by NavigationCameraPolicy.
- Removed the duplicate `ได้รับอาหารแล้วใช่ไหม?` popup and its green CTA from the photographed Food pickup path. The proof/photo quality requirement and the existing single server-backed pickup action remain; after acceptable evidence the Rider button calls `onConfirm` directly.
- Updated source and Kotlin tests to protect these behaviors.
- **NEXT:** exact-head Native Pilot and Map Engine; first confirmed failure only; verify active navigation pan/zoom/rotate/tilt and confirmed pickup on physical Android. After CI passes, publish new APKs from this exact source, never distribute old e35b0bff as fixed.

## 2026-10-28 Ride gesture CI first failure repaired

- First confirmed regression failure of `2465b5da` was in `tests/native-android-source.cjs`, which still required the old Buriram fallback viewport and did not recognize the new one-time camera initialization. Both Native Pilot and Map Engine stopped at that same stale guard.
- Set the shared Ride preview initial camera to a neutral Thailand viewport (15.5, 100.5; zoom 6) until the first valid points are available, then fit once. Replaced stale Buriram/repeated-camera test with a guard ensuring no user gesture hijack on rider marker updates.
- **NEXT:** rerun exact-HEAD CI and fix the first confirmed failure, then build/verify new APK and validate physical two-finger zoom/rotate/tilt and Customer Notifications parity.

## 2026-10-28 Customer Notifications visual/state parity — IN PROGRESS

- Physical Web/Android side-by-side images showed Android compact top outline buttons, tiny cards and a global red `อ่านการแจ้งเตือนทั้งหมดแล้ว` line not present on Web. Source also automatically invoked mark-all on screen entry, clearing the unread state without an explicit user action.
- Native Notifications now has a compact back icon/large Web-like heading, red flat `อ่านทั้งหมด` action, legible white cards with expanded title/body/date, no automatic mark-all on page open, and no sticky global success banner after a deliberate mark-all. Existing user-scoped read API remains unchanged.
- Replaced old source regression that required auto-mark-on-open with an explicit user-action contract.
- **NEXT:** exact-head Native Pilot, Kotlin/JUnit and physical Android compare vs Web for notification navigation, unread badge and cards. Continue Rider map gestures and three-APK rebuild.

## 2026-10-28 Customer Ride pinch/rotate/3D gesture regression — IN PROGRESS

- Owner physical screenshots reported difficult pinch-zoom, inability to rotate/tilt map. Exact HEAD `c0bcbf1c` used `QgTrackingMap` with `animateCamera` invoked on every Compose/data update, resetting user zoom/rotation; Web Ride explicitly disabled rotation/tilt.
- Native shared tracking map now fits the first valid points **once** and renders subsequent rider/supply point changes without camera animation; enables MapLibre pinch/scroll/rotate/tilt and simultaneous scaling/rotation. Web Customer Ride tracking map now enables rotation, pitch and compass as well, preserving a common gesture contract. Added source test that blocks regression.
- **NEXT:** run exact-HEAD Native Pilot and Web Ride source tests, fix first confirmed failure, then physical-device verify manual pinch zoom, two-finger rotate and 3D tilt during Rider updates and publish new APKs only after passing build gates.

## 2026-10-28 Android three-app public test download — DONE (build/distribution), physical parity BLOCKED

- Exact APK source: `e35b0bff819fd015e5624fb91c8e18d53919c402`. Native Pilot push `40532373938` and PR `40532384468` both SUCCESS; source/regression, Android Kotlin/JUnit, all three debug APK builds, 16 KB packaging and emulator lifecycle passed. Source code was not rebuilt for distribution.
- Pinned GitHub Actions APK artifact: `12747471350`, sha256 `05c9c35422b5f03d0a3d21937a039869059e330850b049bd76b7d4db0b6557e8`. The downloaded ZIP passed CRC; all three APK bytes were verified locally and publisher workflow `40597357467` SUCCESS verified artifact provenance, digest and all three `apksigner verify` checks.
- Public prerelease: https://github.com/chatchairins-source/QueuGo/releases/tag/android-device-test-20261028-e35b0bff
- Direct Customer: https://github.com/chatchairins-source/QueuGo/releases/download/android-device-test-20261028-e35b0bff/QueueGo-customer-Device-Test-e35b0bff.apk
- Direct Merchant: https://github.com/chatchairins-source/QueuGo/releases/download/android-device-test-20261028-e35b0bff/QueueGo-merchant-Device-Test-e35b0bff.apk
- Direct Rider: https://github.com/chatchairins-source/QueuGo/releases/download/android-device-test-20261028-e35b0bff/QueueGo-rider-Device-Test-e35b0bff.apk
- **BLOCKED (device parity):** authentic Customer/Merchant/Rider on-device comparison against Web Production across tap-to-select map, Rider heading-up/follow/route, Merchant countdown/progress/back, Customer Ride/success banner, POS workflows and Catalog image loading. Debug-signed builds are **not** certified Play release or Production-ready. Do not mark 100% parity without real device evidence.
- **NEXT:** collect owner physical-device evidence for the nine reported gaps; fix only concrete reproduced failures from latest source HEAD; then rebuild new exact-HEAD three APKs and repeat distribution gate.

## 2026-10-28 Three-APK exact-source public device-test delivery — IN PROGRESS

- Exact source `e35b0bff819fd015e5624fb91c8e18d53919c402` passed Native Pilot push run `40532373938` and PR run `40532384468` including regression, Kotlin/JUnit, Customer/Merchant/Rider APK build, 16 KB package checks, and emulator lifecycle. Artifact `12747471350`, ZIP SHA-256 `05c9c35422b5f03d0a3d21937a039869059e330850b049bd76b7d4db0b6557e8`, has been downloaded, ZIP CRC-tested, and all three APKs extracted with distinct SHA-256 hashes.
- Added a fail-closed, one-off public **device-test** publisher for exact existing APK bytes, requiring successful source run and artifact digest plus per-APK `apksigner verify` before publishing individual downloadable assets under prerelease tag `android-device-test-20261028-e35b0bff`. Does not modify Production/backend or relax certified release gates.
- **NEXT:** inspect the publisher Actions job, verify real public HTTPS release asset URLs; do not claim Production parity without owner physical testing and authenticated screenshots.

## 2026-10-27 Merchant countdown Kotlin compile fix

- Exact-head Android unit gate exposed a missing Compose runtime import after the stable preparation fallback timer was added. `mutableLongStateOf` was used by Merchant order detail but not imported; the remaining timer/signature compiler messages were cascades from that unresolved state type.
- Added only the missing `androidx.compose.runtime.mutableLongStateOf` import. Countdown logic and Web-parity behavior are unchanged.
- **NEXT:** exact-head Native Pilot; verify Rider navigation tests and Merchant compile, then continue to three-APK build and packaging.

## 2026-10-27 Rider Web-parity reanchor unit contract

- Exact-head Android tracking/unit gate reached 137 Rider navigation tests and failed only because `NavigationViewModeTest` still expected the retired 8-second free-pan timeout. Production Native/Web behavior now reanchors after 110 ms after a navigation gesture, and the controller already returns FOLLOW correctly.
- Updated the unit contract to the current 110 ms Web-parity policy; product code unchanged.
- **NEXT:** exact-head Native Pilot; then fix the first remaining Merchant Kotlin compile failure reported after this unit test, rerun, and continue to three-APK build.

## 2026-10-27 Makro source-image guard updated for pinned Merchant preview

- Exact-head Native Pilot on `b15665a3` passed the updated Grocery image test, then `tests/grocery-makro.cjs` still asserted the retired `image:null` Merchant behavior.
- Updated that guard to require the pinned Merchant-only preview path and the single allowed Makro image host. Customer-facing image rights projection remains unchanged.
- **NEXT:** inspect exact-head Native Pilot/Map Engine; fix first confirmed failure only, then continue Kotlin/JUnit, three APKs, 16 KB packaging, emulator lifecycle, artifact verification and public device-test links.

## 2026-10-27 Grocery image regression updated for Merchant-only pinned previews

- Exact-head Native Pilot on `c22c7859` passed Ride and 411 Native source checks, then failed `tests/grocery-images.cjs` because that legacy assertion still required Merchant source rows to force `image:null`.
- The current owner-approved behavior intentionally renders Merchant-only reference images from the single pinned HTTPS host `images.mango-prod.siammakro.cloud`; customer-facing approved-image projection remains rights-gated and unchanged.
- Updated the regression to require the pinned-host sanitizer and source-row preview while continuing to reject the old global-preview mechanism and customer promotion.
- **NEXT:** inspect exact-head Native Pilot/Map Engine; fix first confirmed failure only, then continue Kotlin/JUnit, three APKs, 16 KB packaging, emulator lifecycle, artifact verification and public device-test links.

## 2026-10-27 Catalog image contract path correction

- The Merchant reference-image implementation is in `merchant/grocery-catalog.js`, but the new regression assertion accidentally searched `merchant/index.html`. Corrected the test fixture path; product code is unchanged.
- **NEXT:** exact-head Native Pilot/Map Engine; fix first confirmed failure only, then three-APK artifact verification.

## 2026-10-27 Merchant Grocery source images — real merchant-only preview enabled

- The owner/device evidence requires actual Catalog product photos, not an empty box or placeholder. Production `qg_grocery_source_search` has 10,000 active Makro reference-image URLs and the add-to-shop RPC already stores them only as `catalog_preview_image` metadata while keeping `products.image = null`.
- Web and Native Merchant Catalog now render only the pinned HTTPS host `images.mango-prod.siammakro.cloud` as a merchant-only preview. Arbitrary hosts are rejected. Saving a source item still does not promote the preview into the customer-facing image field.
- Kotlin and source contracts now verify the pinned-host preview and untrusted-host rejection.
- **NEXT:** exact-head Native Pilot; fix first confirmed failure, then verify all three APK artifacts.

## 2026-10-27 Native POS + Grocery catalog parity continuation

- Added the Web POS Delivery/readiness destination to Native POS while retaining counter, tables, kitchen, bills, reports, history, printer and staff capabilities already implemented in Native. POS header now uses the shared compact back control rather than a large standalone button.
- Grocery source catalog no longer renders an empty white image slab when no rights-approved image exists. Approved product photos still render normally; source-only rows show an explicit QueueGo placeholder and remain prohibited from hotlinking provenance-only `image_url` values.
- **NEXT:** exact-head Native Pilot. Fix the first confirmed failure only; once green, build and publish fresh Customer/Merchant/Rider device-test APKs from that exact head.

## 2026-10-27 Tonight parity batch — stale Rider control guard corrected

- Exact-head Native Pilot on `c4cd3c9a` passed all earlier regression checks and then failed only because `tests/native-android-source.cjs` still required the retired top-right Rider control rail. The current product code intentionally moves recenter / route overview / compass to the lower-right stack above the measured job dock, matching the owner/Web navigation layout direction.
- Corrected the source contract to require the lower-right measured stack instead of reverting product UI.
- **NEXT:** inspect exact-head Native Pilot; then continue the remaining owner-reported batch: Rider camera policy/CTA color, Catalog imagery, POS parity, and APK build/artifact verification.

## 2026-10-27 Customer/Shared map tap-to-select — source fix published; exact-head CI NEXT

- Physical-device Customer checkout evidence showed the location picker still used the old camera-center model: moving the map changed the selected coordinate while a fixed red dot stayed at the viewport center. Root cause is the shared `QgLocationPickerMap` using `addOnCameraIdleListener` as the selection authority and a Compose center-dot overlay.
- Updated the shared picker to make an explicit MapLibre map tap the only manual selection action. A tap now selects that exact latitude/longitude, renders the red/white selected-point layer at the tapped coordinate, and notifies Customer checkout/location picker, Merchant shop setup, and Rider personal destination through `onPointSelected`. Pan/zoom alone no longer mutates the selected coordinate. GPS/search recenter still moves both camera and selected marker.
- Updated Merchant copy from “เลื่อนแผนที่ให้หมุด…” to “แตะบนแผนที่…” and added a source contract preventing the camera-idle/fixed-center behavior from returning.
- **NEXT:** run exact-head Native Pilot/Customer Kotlin source gates. If successful, rebuild Customer APK and re-test on the physical Android device by tapping several visible map points and confirming the marker, Lat/Lng, and reverse-geocoded address all follow the tap.

Warning: truncated output (original token count: 92227)
Total output lines: 2112

## 2026-10-27 Tonight's device-test build — IN PROGRESS

- Latest published exact-head commit `3371e4cdea3faaeffc1cc58c462052c53b906239` passed Full QueueGo regression and source/assets contracts, then its Native Pilot failed Rider Kotlin compilation at `RiderNavigationIntegration.kt:219`: call site supplied `numbered = true`, but the local `add` helper did not accept or forward that field. Fixed the helper to default `numbered` to false and pass it to `NavigationMapStop`; source guard is now 409 PASS, Grocery catalog 28 PASS, and `git diff --check` PASS. This correction is local and still needs exact-head CI.
- On exact-head commit `f2fd60ac14463f5743f4e08b99e541b120d48020`, Full QueueGo regression, all source/assets guards, Rider/Kotlin compilation and Map Engine passed. Rider map unit tests had one failure in `NavigationRouteCoordinatorTest.threeSecondsContinuouslyOffRouteReroutesOnlyActiveLeg`: after the reroute, its fake provider correctly starts the new leg at the rider's current GPS fix, but the test reused that same fix and expected a second off-route reroute. Updated the test to move the fake rider away from the replacement leg before checking the 20-second cooldown. The test correction is local and needs exact-head CI.
- **NEXT:** commit/publish the corrected route test to `work-native-web-parity-20261026`, inspect exact-head Kotlin/JUnit, then continue through all three APK builds, 16 KB packaging and emulator gates. Download and verify the artifact if successful. Visual/authenticated parity and physical-device acceptance remain separately open.
- Verified the newest `work-native-web-parity-20261026` successor is `938ff733300bd9c1560829dfbebb5013c3db2898`; `origin/main` is `7e036a0735cc49905e9b0a2434492470551a0c9d`. PR #730 remains open/draft. The current source HEAD is `e2f44f20183e9abdec06e32042437a2314299182`, a fast-forward descendant of that successor.
- Exact-parent Native Pilot run `40482744827` passed all regression, Kotlin/JUnit, three-APK build and 16 KB packaging steps and published artifact `queuego-native-pilot-apks` (artifact `12723717869`). Newer local commits still need their own exact-HEAD CI build before those APKs can be treated as the tonight test build.
- First exact-head Pilot on published tree `67b7e8e01137ff41620dc9e66e8067ff3ed88aba` stopped in Full QueueGo regression at `tests/merchant-routing.cjs:9`: the assertion still expected the retired CSV suffix `เงินสดค่าสินค้าที่ร้านได้รับ,สถานะ GP` after the report now emits credit GP and legacy GP columns. Updated that assertion to the current CSV header; product behavior was not the failing assertion.
- Focused current checks pass: Native source 409, Grocery catalog 28, Merchant product editor 8, Merchant revenue parity 8, product reliability 41; Customer/Merchant/Rider inline scripts 35/35 parse. Full `npm test` is BLOCKED at its first test by missing `jsdom`. Local Android build is BLOCKED because this checkout has no Gradle wrapper or Gradle.
- **NEXT:** publish the corrected regression guard to `work-native-web-parity-20261026`, inspect the next exact-head Native Pilot result, then download/verify the three APK artifact and continue Merchant POS/settings plus Customer/Rider parity. Target owner testing before midnight; physical-device acceptance remains pending.

## 2026-10-27 Merchant Delivery price parity — stale/default GP save fixed

- Compared Web and Native product pricing. Web fetches and validates the effective GP rate again on save; Native could silently use 0% when its initial GP read failed. Native now strictly parses the RPC result, fetches the current rate at save time, and rejects missing/invalid values. Failed initial reads no longer show a misleading 0% preview.
- Added JUnit cases for accepted response shapes and rejected missing values, plus a source guard. `native-android-source.cjs` 408 PASS, product description parity 8 PASS, Merchant Grocery catalog source 28 PASS; Kotlin/JUnit remains unavailable without Gradle.
- **NEXT:** finish Merchant POS/settings, then Customer/Rider source parity and exact-head Android/device gates. Authenticated visual evidence remains open.

## 2026-10-27 Merchant revenue GP parity — credit deductions shown on Web

- Compared both clients against `merchant_revenue_days`. Native shows `gp_credit_deducted` as current GP and separates historical `gp_due`; Web report and CSV had treated legacy `gp_due` as current GP. Updated Web period view, selected-day view, and CSV to show the same credit-deducted GP and retain legacy amounts separately.
- Added `merchant-revenue-web-parity.cjs`: 8 PASS. Product, inventory, and Native source checks are rerun after the change. Runtime authenticated data remains unverified.
- **NEXT:** continue Merchant POS/revenue/settings behavior and Customer/Rider map/workflow parity. Full suite is blocked at first test because `jsdom` is not installed; Android JUnit/device evidence remains open.

## 2026-10-27 Merchant custom category creation — Native parity added

- Web lets general grocery/other Merchant product editors enter their own category; Native now offers “เพิ่มหมวดหมู่ใหม่” for the same shop types, selects the new value immediately, and keeps fixed category menus for Food, Market, and Shopping.
- Added a Native source guard. `native-android-source.cjs` is rerun after the change; Android JUnit/device review remains unavailable locally.
- **NEXT:** continue Merchant POS/revenue/settings and then Customer/Rider parity. Full audit and release evidence remain open.

## 2026-10-27 Merchant legacy custom product category — Native edit no longer drops it

- Web permits free-text categories for general grocery/other Merchant products. Native used its standard category list and could replace a valid existing custom category when opening the editor. Native now adds the current/initial category to its choices, preserving it through edits while keeping the normal category catalog.
- Added a Native JUnit case and a source guard. `native-android-source.cjs` is rerun for the updated source contract. Kotlin/JUnit execution remains blocked here because Gradle is unavailable.
- **NEXT:** continue Merchant product/category creation and channel-price review, then POS/revenue/settings and Customer/Rider parity audit. Full three-app completion and release evidence remain open.

## 2026-10-27 Merchant product description parity — editable details restored

- Compared product editor/save/restriction paths. Android edits product description and uses it when disabling Delivery for restricted products; Web previously preserved the old description, offered no editor field, and its live Delivery restriction preview ignored descriptions. Added the editable field, saved its value, and included it in live restriction recomputation.
- Added `merchant-product-editor-parity.cjs` (8 PASS), covering Web and Native fields and representative alcohol/tobacco description classification. `merchant-product-reliability.cjs` 41 PASS, inventory parity 10 PASS, Native source 406 PASS; Merchant inline JS 22/22 parses. Android JUnit still needs Gradle/CI.
- **NEXT:** finish Merchant catalog/stock editor and POS/revenue/settings surfaces, then continue Customer and Rider parity audit. Release/build/device evidence remains open.

## 2026-10-27 Merchant tracked inventory parity — audited stock controls added to Web

- Earlier Native inventory work exposed tracked-stock mode, locked direct quantity edits, a reason/note adjustment flow and paged audit history; current Web Merchant editor had lost those controls. Re-added the behavior to Web using only the existing `pos_inventory_stock_adjust` and `pos_inventory_adjustment_history` RPCs. Web now reads/writes `inventory_stock_tracking`, locks tracked quantities, submits bounded signed changes with a request key stable across identical retries, and renders the server audit trail with cursor pagination. Grocery Master remains the stock authority and is excluded from generic controls.
- Added `merchant-inventory-web-parity.cjs`: 10 PASS, including stable request-key behavior. Native source 406 PASS; Merchant product reliability 41 PASS; Grocery catalog source 28 PASS. On exact committed HEAD `d9c5488b`, the package test inventory ran 157 commands: 97 PASS, 60 BLOCKED by missing `jsdom` (24), `acorn` (4), or `@electric-sql/pglite` (32), zero assertion failures. All 44 external JavaScript files and 35 inline scripts across Customer/Merchant/Rider entry pages parse. Gradle/Kotlin, authenticated server writes and paired device captures are unavailable in this workspace; no APK/device/JUnit claim is made.
- **NEXT:** finish Merchant catalog and POS/revenue/settings parity, then continue Customer and Rider workflow/map audit. Keep Rider passive arrival HUD display and Android build/device certification open.

## 2026-10-27 Merchant special-hours date parity — Thailand date mismatch fixed

- Compared Web `qgmRenderHours` with Native `MerchantHoursScreen` and found Web queried special hours from the UTC calendar date while Android used `Asia/Bangkok`. At 00:00–06:59 local time, Web could show an already-passed special date that Android excludes. Web now derives the query date in `Asia/Bangkok`.
- Added `merchant-hours-parity.cjs`, which checks both sides and tests the UTC 17:00 boundary where the Thai date changes. `merchant-hours-parity.cjs` 6 PASS; `native-merchant-grocery-catalog.cjs` 28 PASS; `native-android-source.cjs` 406 PASS; `git diff --check` PASS. Kotlin/JUnit remains unavailable locally.
- **NEXT:** finish Merchant product/catalog/financial/report screens and loading/error states; then continue Customer and Rider parity gaps.

## 2026-10-27 Merchant product-list parity — search and Grocery category filter added

- Comparing Web `renderShopProductsModern` with Native `MerchantProductsScreen` found Android lacked Web's product-name/category search and Grocery-only category selector. Added both to Android with a shared matching helper and JUnit cases, retaining the existing availability filters. Web had no explicit empty-result message for a filter/search with no matches; added an accessible status message there too.
- `native-merchant-grocery-catalog.cjs`: 28 PASS; `native-android-source.cjs`: 406 PASS; `merchant-product-reliability.cjs`: 41 PASS; `merchant-ready-estimate-web-parity.cjs`: 9 PASS. Merchant inline JS: 22 scripts parse; `git diff --check`: PASS. Kotlin/JUnit remains unavailable locally without Gradle.
- Broad `package.json` test sweep on the pre-product-filter revision: 153 commands, 92 PASS; 60 BLOCKED by missing `jsdom`, `acorn`, or `@electric-sql/pglite`; the remaining release-evidence bundler requires a clean checkout and correctly declined while the worktree was dirty. Rerun from a clean committed checkout.
- **NEXT:** complete Merchant product editor/catalog, POS/report, profile/settings and load/error-state comparison; then continue Customer and Rider workflow parity. Keep map arrival display and APK/device certification open.

## 2026-10-27 Merchant ready-estimate Web parity — server-backed controls added

- Continued the three-app source audit at the Merchant order-detail controls. Android already had authenticated ±5 and +10 minute ready-time controls against `qg_merchant_set_ready_estimate`; Web order detail lacked them. Added the same controls to Web, reading the estimate/revision from the existing order row and using the same revision-guarded RPC with the existing 0–120 minute clamp. No direct table write or new server authority was introduced.
- The first review caught `orderState` being declared below the new control expression; moved its declaration above first use before finalizing. A follow-up visual-source review added QueueGo-styled, tappable controls instead of browser-default buttons. Added `tests/merchant-ready-estimate-web-parity.cjs`; it passes 9 checks. Native source contract passes 406, Merchant readiness 13, action reliability 33. Merchant inline JS parses (22 scripts, 0 syntax errors); `git diff --check` pending.
- **NEXT:** finish Merchant catalog/editor, profile/hours/settings and loading/error-state comparison, then verify Customer and Rider workflows against Web and Android. Rerun the available broad test sweep from a clean committed checkout; exact-head Android compile/JUnit and device evidence remain unavailable locally and must stay unclaimed.

## 2026-10-27 Merchant POS report parity — server-backed Delivery/GP summary added

- Compared Web `merchant/pos-table-v2.js` with Native `MerchantPosApi` / `MerchantPosScreen` and the existing `pos_owner_dashboard` SQL contract. Found Native report omitted Web's Delivery amount and Delivery GP and excluded Delivery from its displayed total.
- Native now parses the existing RPC's `DELIVERY` and `GP` fields and renders the same channel rows, aggregate, order count, and GP summary as Web. Operational POS remains scoped to POS orders; no new financial authority, endpoint, or payment action was added.
- Added `MerchantPosDashboardTest` for field mapping/defaults and changed the source contract from prohibiting Delivery summary values to requiring Web parity. `native-android-source.cjs` 406, `native-merchant-grocery-catalog.cjs` 24, `merchant-delivery-readiness.cjs` 13, `native-merchant-order-substitution.cjs` 32: PASS. `git diff --check`: PASS. Gradle/JUnit unavailable locally; exact-head CI not yet available for these unpushed commits.
- **NEXT:** inspect Merchant order actions and call/report entry points against current Web, then finish Products/catalog/settings and loading/error-state inventory. Continue three-app source audit; do not claim APK, Kotlin/JUnit, authenticated screenshot, or device acceptance without those gates.

## 2026-10-27 Rider map animation budget guard — stale assertion corrected

- The Rider map runtime-budget test asserted a 1,200 ms marker animation cap, while the checked-in Rider MapLibre policy now caps at 1,250 ms and its regression cadence uses the same limit. Updated the test to assert the current source value; product code was unchanged.
- `node tests/native-rider-map-runtime-budget.cjs`: PASS; `git diff --check`: PASS. Full `tests/native-*.cjs` sweep is rerun after this commit because native release-evidence tests require a clean checkout.
- After commit `82ad0058`, the complete `tests/native-*.cjs` sweep is clean: 48 PASS, 0 FAIL, and 2 BLOCKED only because `@electric-sql/pglite` is not installed. Release-evidence tests passed on the clean checkout.
- **NEXT:** continue the Customer page-by-page source audit, then Merchant POS/reports/call actions. Two PGlite-backed cases and Android exact-head build/device evidence remain open.

## 2026-10-27 Three-app source audit — Rider route speed-limit cache failure fixed; Customer/Merchant audit NEXT

- Audited current workspace HEAD `ab80e843` across Native Customer (98 Kotlin/Java/XML files, 14,952 lines), Merchant (69 files, 18,237 lines), and Rider (200 files, 29,211 lines). This is a static/source regression pass, not a claim that each line has been compiler- or device-certified.
- Rider map regression initially failed because its DOM fixture omitted the speed-limit sign. After modeling the actual page DOM, it exposed a production defect: `qgRequestRiderRoute()` returned `speedLimits`, but `drawActiveRoute()` omitted it from destructuring and then referenced an undefined variable while caching the route. The exception made otherwise-valid routes show “โหลดเส้นทางไม่สำเร็จ”. Passed `speedLimits` into the route cache and added a valid OSM interval to the route fixture; the regression now verifies the 50 km/h interval is cached.
- `node tests/rider-map.cjs`: PASS, 124 checks. Focused Customer/Merchant/Rider source, screen coverage, order-state, workflow, route and map set: PASS (30 scripts). JavaScript syntax: 27 local external app JS files and 35 inline scripts in Customer/Merchant/Rider entry pages PASS. `git diff --check`: PASS.
- The repository-wide `npm run test --ignore-scripts` is BLOCKED at its first test because `jsdom` is not installed; source-integrity also needs missing `acorn`. No dependencies were installed. Android compilation is BLOCKED because this workspace has Java but no Gradle wrapper, Gradle, or Kotlin compiler. No APK/device/visual PASS is claimed.
- Web and Native Rider maneuver selection aligns on its 15 m instruction window and 400/300 m following-instruction display. The manual arrival action also matches: both check GPS distance against 200 m, show a non-blocking warning when farther or GPS is unavailable, then call `qg_rider_mark_arrival`; customer arrival continues into proof capture. The passive map HUD differs: Native can switch to its “ถึงจุดหมาย” state when GPS is within its accuracy-adjusted arrival radius and route remaining is low, while Web shows the final arrival instruction when its instruction list is exhausted. Keep this display difference open until paired screenshots clarify intended parity; do not change the physical arrival action.
- Rechecked Customer tracking states against Web `QGCustomer.renderOrder` and `chatAvailable`: stage/status labels, terminal map behavior, cancellation authority and 30-minute post-completion chat cutoff align in the inspected paths; tracking/chat Kotlin unit contracts are present but cannot run locally without Gradle.
- **NEXT:** inspect Merchant POS/reports/call actions against current Web, then continue Merchant order/catalog/settings coverage. Restore declared Node dependencies and obtain exact-head Android CI/build/device evidence before any APK or launch-readiness claim.

## 2026-10-27 Checkout Places call-site compile fix — source correction; exact-head CI NEXT

- Native Pilot run `40472996941` passed Full QueueGo regression and source/asset checks, then failed Kotlin compilation at `QueueGoCustomerApp.kt:1006,1212`. Exact compiler errors: Location Picker was missing required `searchPlaces`; Checkout still passed the removed `searchPlaces` parameter.
- Cause was the prior edit removing the first matching callback in the app file, which belonged to Location Picker. Restored Places callback to Location Picker, removed it from Checkout, and strengthened source guard to assert each call site separately. Checkout map/GPS/location/note remain unchanged.
- **NEXT:** inspect exact-head Kotlin compile and tracking JUnit result, then APK, 16 KB and emulator lifecycle; no APK or visual-completion claim yet.

## 2026-10-27 Native source regression follow-up — cache-aware guard; exact-head CI NEXT

- The rerun on the checkout parity checkpoint fixed the early-reference error, then Full QueueGo regression exposed a stale source assertion at `tests/native-android-source.cjs:740`. It still required unconditional shop/banner downloads even though the current Native Customer refresh deliberately reuses a fresh Home cache and skips those static requests.
- Updated the guard to require cache freshness gating for shops/banners/service banners, preserve parallel order refresh, and retain Guest Home's parallel initial read path. This keeps the newer download-reduction behavior while aligning source contracts to it. Checkout UI change remains unchanged.
- **NEXT:** inspect the new exact-head Native Pilot regression result; then continue compile, APK, packaging, and emulator checks. No APK or visual acceptance claim until those gates finish.

## 2026-10-27 Checkout parity guard correction — regression source fix; exact-head CI NEXT

- Parent Native source included `e0fcd4009d7b1e02ae642a33d97c82ade42d5e34`, which removed the extra Checkout Places field while retaining Places in the dedicated Customer Location Picker.
- Exact Native Pilot push `40472155179` reached Full QueueGo regression and failed at `tests/native-android-source.cjs:193`: the new assertion referenced `customerLocationPicker` before its later declaration. Earlier regression contracts passed; no APK or Kotlin source failure was reached.
- Corrected the guard to read `CustomerLocationPicker.kt` inline at the assertion site. This preserves the check that Checkout has no Places panel and the dedicated picker keeps it. No product logic change in this correction.
- **NEXT:** inspect exact-head Native Pilot Full QueueGo regression first; if green, continue Kotlin/JVM, APK, packaging and emulator gates. Customer authenticated screenshot and physical-device status remain open.

## 2026-10-27 Customer Checkout Web parity — source checkpoint; exact-head CI NEXT

- Parent Native successor HEAD: `e0fcd4009d7b1e02ae642a33d97c82ade42d5e34`; PR #730 remains open/draft; Production Web remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Source audit confirmed Web Checkout has the QueueGo map, GPS action, delivery address and note; the picker attaches Places with `searchControl:false`. Removed the extra Places search panel from Native Checkout so the visible control order matches Web. The dedicated Native location picker retains Places search; map pin/GPS, reverse-geocoded address, note, fee, and server submit/recovery remain.
- Updated `tests/native-android-source.cjs` to guard this UI contract and its retained Location Picker search. Updated Customer parity matrix and PROJECT-MEMORY. No Web/backend/schema/business-authority changes.
- Source commit `e0fcd4009d7b1e02ae642a33d97c82ade42d5e34` has PR Native Pilot `40472086491`, push Native Pilot `40472081080`, and PR Map Engine `40472086542` queued; Rider authenticated review readiness is PASS, not screenshot acceptance.
- **NEXT:** inspect exact-head Pilot and Map Engine first confirmed failure only. Continue Customer Shop/menu/cart and checkout/tracking source and visual comparisons; Customer authenticated credentials and physical-device checks remain open.

## 2026-10-27 Preserve Native exact-head gate runs — source checkpoint; exact-head CI NEXT

- Parent Native successor HEAD: `c1fa9f5a63f880446bda5c2c0a146a05fb615aaa`; PR #730 remains open/draft; Production `main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Confirmed why exact-head Native Pilot and Map Engine runs repeatedly ended cancelled while the successor advanced: both workflow concurrency groups used `cancel-in-progress: true`. Changed both to preserve the active run and leave the newest pending run queued. Added a source guard for both workflow policies.
- CI scheduling only; no product behavior change. Cancelled runs did not establish a source failure. No Production data/schema mutation.
- Customer authenticated visual review remains blocked by missing Actions credentials; emulator CI does not certify physical-device or visual parity.
- **NEXT:** inspect exact-head Native Pilot and Map Engine on this source commit. Fix only the first confirmed failure, then continue the Customer P1 parity matrix.

## 2026-10-27 Customer Market GPS refresh deduplication — source checkpoint; exact-head CI NEXT

- Parent Native successor HEAD: `c32b37d333556f2dce7a50f60d4233cbf0d71c7d`; PR #730 remains open/draft; Production `main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Confirmed root cause in `QueueGoMarketScreen.kt`: the main market-data `LaunchedEffect` depended on latitude/longitude while loading the complete markets, shops, and catalog sets. GPS updates therefore cancelled/restarted all three remote reads despite the local cache being hydrated.
- Removed location coordinates from that refresh key. Cache-first display remains; account changes and explicit/lifecycle refresh still reload. Active trip, pricing, availability and checkout remain server-backed. Added a source guard for the request trigger.
- Exact-parent Native Pilot and Map Engine runs were cancelled during emulator lifecycle capture as the successor advanced; Rider authenticated review succeeded. Run both release gates on this source commit. No Production schema/data mutation.
- Customer authenticated screenshot review remains blocked by missing Actions credentials; emulator CI does not certify physical-device or visual parity.
- **NEXT:** inspect exact-head Native Pilot first. Fix only its first confirmed failure; if it passes, continue auditing remaining repeated, non-authoritative reference downloads.

## 2026-10-27 Merchant local-first reference categories — exact-head CI PASS

- Current source HEAD `9e9068429c60ce08b37bc9daa126cc38cbe875e5` on `work-native-web-parity-20261026`. Exact PR Native Pilot `40464819987`, push Native Pilot `40464814881`, and Map Engine `40464819951` all completed SUCCESS. PR #730 remains open/draft; Production main is `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- The Customer and Merchant Grocery category references and Merchant Market catalog categories now load from a bounded local cache before server refresh. Merchant Market categories refresh once per visible screen lifecycle instead of on every search/page request. Current product pages, prices, availability and add-to-shop/order actions remain server-backed.
- Exact Native evidence includes full regression, Kotlin unit tests, all three APKs, 16 KB packaging, WebView rejection and emulator launch/map/voice lifecycle. Exact Map Engine runtime PASS. These are CI emulator/source gates, not physical-device or visual-parity certification.
- Local `node tests/native-android-source.cjs`: 379 checks PASS; `git diff --check` PASS. Gradle is unavailable in this workspace. No Production schema/data mutation.
- Customer authenticated screenshot review remains blocked by the recorded missing GitHub Actions credentials; Rider/Merchant emulator screenshots do not certify Customer visual parity.
- **NEXT:** audit the remaining screens for repeated, non-authoritative reference downloads. Cache only display/reference data; keep live order, inventory decision, price, financial and dispatch authority server-backed. Continue the broader authenticated Customer parity work where credentials permit.

## 2026-10-27 Merchant Grocery category local-first cache — source checkpoint; exact-head CI NEXT

- Parent exact Native successor HEAD: `dcfcc43273548874ba48ee266b067c981486ba8a`. Before this change, Native Pilot run `40461138799` and Map Engine run `40461134860` both completed SUCCESS on exact parent HEAD. PR #730 remains open/draft; Production `main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Merchant Grocery master-catalog category names are now read from and written to the existing owner-scoped durable content file, with the shop ID checked both ways and a 200-row/100-character bound. Cached categories appear before server refresh. Fresh server categories replace them; catalog item pages remain online/server-backed and no inventory, price, finance, order, or dispatch authority is cached.
- Customer's per-shop Grocery category cache from the preceding checkpoint remains in the same successor. Local `node tests/native-android-source.cjs`: 378 checks PASS; `git diff --check` PASS. Gradle is unavailable locally; Kotlin, APK and emulator gates must come from hosted exact-head Actions.
- No Production schema/data mutation.
- **NEXT:** inspect exact-head Native Pilot and Map Engine for the new branch tip. Fix only the first confirmed failing step. If green, continue auditing the remaining bounded reference-data downloads; keep operational state server-authoritative.

## 2026-10-27 Customer Grocery category local-first cache — exact-head CI in progress

- Current Native successor: `work-native-web-parity-20261026`; source checkpoint `19b1392998e82e41ec3c3c201b9a334ff936b08f`, parent `3585fa937cc2c06112046bf577041a299f88fe01`. PR #730 remains open/draft; `main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Confirmed the exact parent Native Pilot run `40458248182` and Map Engine run `40458248211` completed SUCCESS. The branch tip had advanced beyond the older checklist checkpoint before this continuation.
- Customer grocery category names are now stored in the existing bounded durable content cache, scoped per shop. A cached category list is rendered before fresh categories/products return; fresh nonempty server categories replace and refresh it. This is display/reference data only; current products, price, availability and checkout remain server-authoritative. Guest and signed-in flows use the same behavior.
- Added a coroutine regression for immediate cached-category display and fresh replacement, plus a source guard. Local `node tests/native-android-source.cjs`: 377 checks PASS; `git diff --check` PASS. Gradle is not installed in this workspace, so Kotlin/APK/emulator evidence is delegated to exact-head Actions.
- No Production schema/data mutation.
- **NEXT:** inspect only the first confirmed failure, if any, on exact-head Native Pilot `40461039903` and Map Engine `40461039883`; both are running on the source checkpoint. If green, continue the next safe local-first reference-content gap and keep live order/financial/dispatch state uncached.

## 2026-10-27 Android local-first cache + silent data refresh — source checkpoint; exact Pilot NEXT

- Current Native successor: `work-native-web-parity-20261026`; checkpoint HEAD `26a9c5ab6042dab897aeaaeeccd388a649d2455b`.
- Owner rule restored as an Android-wide UX contract: data refresh must be visually silent. Existing/cached content stays on screen; data-loading spinners/progress chrome are removed. Action progress such as login/save/upload/checkout remains explicit.
- Shared remote images now use durable app storage under `noBackupFilesDir` instead of expiring from Android cache every 24 hours; the bounded disk LRU remains. Existing legacy cached images migrate forward when read.
- Customer already had durable Home/shop/Market content cache; Merchant now has durable local-first shop/menu cache. Rider retains its durable home snapshot. Live orders, finance and dispatch remain server-authoritative and are not converted into stale durable decision state.
- Customer Laundry had a confirmed duplicate-download path: choosing a hub called `api.services()`, which fetched the full laundry catalog again although the first catalog already includes `servicesByHub`. Added durable laundry catalog cache, load-cache-first/background-refresh, and direct service reuse from the current catalog. Hub selection no longer redownloads the catalog.
- Silent-refresh regression coverage now includes Customer primary/Market/Laundry/Shopping/Promotions/tracking, Merchant primary/Catalog/POS/Revenue/Support/Stock/Media/Modules/Tickets/Laundry/Hours/Setup Map, Rider Earnings/Messages/Support/Safety, and shared role-shell loading chrome.
- Exact-head Rider authenticated review is green at the latest observed checkpoint; exact Native Pilot/Map CI for the final successor is still pending. No Production data/schema mutation was made.
- **NEXT:** inspect exact-head Native Pilot first confirmed failure only. If green, continue durable caching for remaining safe static/reference content without persisting live order/financial/dispatch authority. Keep Customer authenticated visual review and physical-device/GPS/release gates separate.

## 2026-10-27 Customer Notifications page-entry parity — source/test/CI PASS; authenticated Rider/Merchant recapture NEXT

- Source commit `869da7a219d74dea05d6b598f02021fdd8b142e1` is the latest fetched descendant on `work-native-web-parity-20261026`; `origin/main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`. Exact-head PR Native Pilot `40450263531`, push Native Pilot `40450259446`, and Map Engine `40450263616` all completed SUCCESS. Native gates include regression, Kotlin/JVM, three APK builds, 16 KB packaging, WebView rejection and Android emulator launch/map/voice lifecycle.
- Rider artifact `12708029912` captured 11/11 expected screens and Merchant artifact `12708139641` captured 5/5 on exact review HEAD `92bf10a2`; actual contact sheets and representative Rider/Merchant screenshots were inspected. Metadata states `physical_device:false`, no Production order/dispatch mutations and `visual_parity_certified:false`. Customer authenticated review remains blocked by missing `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`.
- Web Production `renderNotifications()` fetches the authenticated user's notifications, PATCHes owned unread rows to read on page entry and always renders “อ่านทั้งหมด”. Native reuses the existing authenticated own-user `notifications?user_id=eq.<user>&is_read=eq.false` PATCH; it now triggers that action when unread IDs enter the Notifications screen and keeps the manual control visible even with no unread rows. It does not add a table, endpoint, RPC or authority.
- Regression evidence: clean-checkout `npm ci && npm test` completed through `posttest`; `node tests/native-android-source.cjs` passes 361 checks; `git diff --check` passes. No Production data was changed.
- Readiness-only authenticated review `40450259433` on `869da7a` succeeded with capture not requested. **NEXT:** add the repository-required `[authenticated-review]` marker on the current successor, wait for authenticated Rider/Merchant capture, inspect actual screenshots before making any UI changes, then continue the next unblocked Customer parity row. Customer visual review is still blocked by missing `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`; readiness-only is not screenshot acceptance.

## 2026-10-27 Rider/Merchant authenticated capture PASS; Customer Notifications source audit COMPLETE

- Published descendant `92bf10a2cfdc2a9c585c76989d32b7dce528523b` is parented on the green harness-fix commit `a7ceb1adf6de262e825cfab29657dfaeb6e4e96d`. Exact-head PR Native Pilot `40446858417`, push Native Pilot `40446853549`, and Map Engine `40446858313` completed SUCCESS. Authenticated read-only run `40446853492` completed SUCCESS after the transient SDK archive retry was needed on the preceding attempt.
- Actual exact-head artifacts: Rider `12708029912` captured all 11/11 expected screens, Merchant `12708139641` captured all 5/5; metadata source SHA matches `92bf10a2`, `missing_screens` is empty, `physical_device:false`, Production order/dispatch mutations false, and `visual_parity_certified:false`. I inspected actual contact sheets plus Rider `03-profile-laundry.png`, `08-learning-center.png`, and Merchant `01-home.png`: Laundry remains server-backed OFF, extra-tools accordion screens now capture, and no new Rider/Merchant mismatch was found. This is emulator review evidence, not physical-device or parity certification.
- **NEXT:** the source-confirmed Customer Notifications parity gap is implemented in the newer checkpoint above. Continue with its exact-head CI result, then audit the next unblocked Customer P1 row. Customer authenticated Profile/Notifications visual review remains blocked by missing review credentials.

## 2026-10-27 Customer Profile contact parity — source/test/Pilot PASS; authenticated recapture NEXT

- Recovered from repository source of truth: `origin/main` is `7e036a0735cc49905e9b0a2434492470551a0c9d`; PR #730 remains open/draft with newest `work-native-web-parity-20261026` HEAD `6de3a93eb92a304ea9eb574aea17247339ac9ccd`. No newer successor branch tip was found after fetch. Exact-head PR Pilot `40439224823`, push Pilot `40439220389`, and Map Engine `40439224755` all completed SUCCESS on `6de3a93e`.
- Rechecked Web Production Profile: it renders `phone || email` below the customer's name. Supabase `public.users` has `phone` and no `email`; Native now reads that existing own-user phone field and uses authenticated Supabase Auth email only as fallback. No schema, RPC, backend authority or Production mutation was added.
- `NativeUser` retains Customer phone/email in the existing encrypted role session; validate refreshes phone from the user row and retains Auth email. Synthetic `@auth.queuetech.local` values are suppressed. Profile renders the optional contact line using Web priority. Added focused contact precedence/blank/synthetic-address JVM cases and source guards. Source commit `de32099c8c540b6fb22dfd1632ef2871048529fc` is parented directly on the latest successor.
- Local `node tests/native-android-source.cjs` passes 358 checks; `git diff --check` passes. Exact `de32099c` PR Native Pilot `40441166424`, push Native Pilot `40441160644`, and Map Engine `40441166419` completed SUCCESS. This includes regression/source, Kotlin/JVM, all three APKs, 16 KB, WebView rejection and emulator launch/map/voice lifecycle. Emulator evidence is not physical-device acceptance.
- Customer authenticated screenshot acceptance remains BLOCKED by missing GitHub Actions secrets `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`. The readiness-only reviews are not screenshot evidence. Do not claim physical-device PASS.
- Readiness-only review run `40441160801` on `de32099c` succeeded but capture was skipped because no marker was present. Marker `08a4a407` then requested authenticated Rider/Merchant capture; see the newer harness-failure checkpoint above.
- **NEXT:** the Rider capture harness fix above supersedes this checkpoint.

## 2026-10-27 Customer Profile cleanup; latest Pilot and Rider/Merchant capture PASS

- Recovered on newest published descendant `9f6d54f81416cee63fc4b44987e42cc8fe39e446` (PR #730 remains open/draft; no later successor was present after fetch). `origin/main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`; no newer Native descendant or main update was found.
- Exact-head PR Native Pilot `40433753021` and push Native Pilot `40433747432` completed SUCCESS on `9f6d54f8`; full regression/source, Kotlin/JVM, three APKs, 16 KB, WebView rejection and emulator launch/map/voice lifecycle passed. Map Engine `40433753070` completed SUCCESS. These are emulator/runtime gates, not physical-device evidence.
- Authenticated read-only review `40433747486` completed SUCCESS on exact `9f6d54f8`: Rider artifact `12703387135` captured 11/11, Merchant artifact `12703103639` captured 5/5, with no missing screens. Actual contact sheets and `03-profile-laundry.png` were inspected: Laundry mode row is cleanly placed under work preferences and retains its server-backed off state. Review metadata says `physical_device:false`, no Production order/dispatch mutation, `visual_parity_certified:false`.
- Customer authenticated visual review remains BLOCKED by missing GitHub Actions secrets `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`; readiness-only is not visual acceptance. Continue unblocked source work without claiming Customer visual acceptance.
- Current Native source evidence showed Customer Profile's debug-only “ข้อมูลเวอร์ชันทดสอบ” control exposes build metadata but has no Web Production counterpart. Removed only that Profile control; the shared diagnostic component remains available to Merchant/Rider and the isolated Customer Android test. Added a source guard. `node tests/native-android-source.cjs` passes 357 checks; `git diff --check` passes.
- On exact `72eb8c45`, PR Pilot `40435861715` and Map Engine `40435861671` passed. Push Pilot `40435855531` failed only in Android Emulator SDK installation: `sdkmanager` reported `ZipFile unknown archive` while unpacking the emulator download. Regression/source/JVM/APK/16 KB/WebView gates passed before that transient package-download failure. No app-source failure was reported.
- Newest descendant `f8145b87502d9b1c108b1dad4e26440421377911` passed both PR Pilot `40437689163` and push Pilot `40437684464`; Map Engine `40437689276` also passed. Both Native runs completed the emulator launch/map/voice lifecycle, confirming the SDK archive incident did not recur on the successor.
- Authenticated capture `40437684481` completed on exact `f8145b87`: Rider artifact `12704589704` has 11/11 screens and Merchant artifact `12705111401` has 5/5. Their metadata source SHA matches `f8145b87`, missing screens are empty, and actual contact sheets plus Rider `03-profile-laundry.png` were inspected. No new Rider/Merchant visual mismatch was found. Both artifacts state `physical_device:false`, no Production order/dispatch mutation, and `visual_parity_certified:false`.
- Readiness artifact `12705165067` says Rider/Merchant secrets are present but Customer is missing `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`; capture was requested but only the credential-gated Rider/Merchant screenshots ran. Customer visual acceptance remains BLOCKED.
- Web Profile (`index.html` + `customer-features.js`) displays the authenticated customer's phone or email below the name. Native `ProfileScreen` passes only `auth.user.name` to `CustomerProfileHero`, and `NativeUser` carries no phone/email. This is a source-backed information gap; do not add another endpoint or expose synthetic `@auth.queuetech.local` addresses.
- **NEXT:** trace the existing Web `users.phone` / Auth email source through Native session parsing and encrypted persistence, then add the Customer Profile contact line using those same existing fields only. Add focused model/source coverage, run exact-head Native Pilot, and request Customer authenticated screenshots only after the two recorded secrets exist. Do not claim Customer visual acceptance or physical-device PASS.

## 2026-10-27 Rider Laundry authority re-check — source checkpoint; exact Pilot NEXT

- Reconciled forward to authenticated-review checkpoint `1be05ed44343ed04e46b169392a8cb00a0f5bced` before continuing. Rider/Merchant authenticated capture `40428480371` on `907cc32c0e0e909db14cd06bc6801aab4a3cbe87` completed with Rider 10/10 and Merchant 5/5; metadata is exact-head, read-only, and `physical_device:false`. Actual screenshots were inspected: map chrome, inbox, all Earnings ranges, Profile hierarchy, Support/Learning/Safety/Diagnostics and Merchant primary surfaces have no new visual mismatch. This does not certify Customer visuals or a physical device.
- Rechecked current `origin/main` (`7e036a0735cc49905e9b0a2434492470551a0c9d`) and read-only Production backend state. Web still reads Rider Laundry preferences/invites and calls `queuego_set_laundry_rider_mode`, `queuego_laundry_rider_invite_action`, active-job/pool/claim/action RPCs. Production currently reports Laundry enabled and these dedicated authenticated RPCs/ACLs present. This is separate from `qg_rider_work_preferences`, whose services remain Food/Grocery/Market/Express/Ride only.
- The earlier checkpoint describing Rider Laundry as retired predates the current Web/main and Production authority readback. Merchant/shop Laundry remains shop-managed through its own RPC. No new RPC/schema, Production mutation, or Laundry key in general Rider work preferences was added. The repository's shop-transport SQL still describes retirement of the Rider RPCs; current Production has not applied that retirement, so do not claim it has.
- Commit `77eebc1b` reconnects Native Rider snapshot to the existing Laundry state RPC, restores the Web Profile mode/invite controls using existing server mutations with refresh-after-write acknowledgement, and reuses existing Home job/pool UI. Parser tests cover feature/mode/invites/active/pool and malformed fail-closed data; the source contract checks dedicated authority separation. Focused Node contract and `git diff --check` pass. This clone has no Gradle wrapper; Kotlin/JVM, APK and emulator gates are pending hosted Pilot.
- Forward-merged Customer successors `7ebdf25e` (density/geometry), `32789851` (source proportions) and `636c0f53` (CSS contract) without replacing their work; the combined Customer Home contract passes 55 checks. Exact Pilot `40430149422` on `32789851` completed regression/source/assets successfully and was cancelled at the tracking/JVM step as the branch advanced; no failure was reported. Exact Pilot `40430483949` on `636c0f53` was in progress when integrated. The old Rider-retirement assertion in `tests/native-android-source.cjs` was the first confirmed local contract failure and has been updated to enforce the current Web/Production authority; full native source contract now passes 357 checks. Current local integration head before this docs refresh is `9d40bfdb` plus commit `07dec5ae`.
- Exact current app-source head `8f17026a0c909691b1f194484ac38aaba6634646` passed PR Native Pilot `40430819774` (full regression/source, Kotlin/JVM, three APKs, 16 KB, WebView rejection and emulator launch/map/voice lifecycle) and Map Engine `40430819796`. Push-triggered review `40430814885` only produced readiness: Rider/Merchant secrets are present, Customer is missing `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`, and `capture_requested:false`; that is not visual acceptance. No failed CI step.
- Authenticated read-only review `40432384271` on documentation-only successor `61be1798c79a0bc84d99e9d9f5184cf79488b038` completed SUCCESS: Rider artifact `12702138652` reports 10/10, Merchant artifact `12702023702` reports 5/5, with exact source metadata and no missing screens. Actual screenshots were inspected; map/inbox/earnings/primary Rider+Merchant surfaces are clean. The top-of-Profile screenshot does not include the new below-fold Laundry mode control, so that control has not yet received visual review. Both artifacts state `physical_device:false`, no Production order/dispatch mutation, and `visual_parity_certified:false`.
- Added a read-only `profile_laundry` capture after scrolling to the Web-backed control; updated the exact-source contract. Rider Laundry/Customer Home/source contracts pass (357 native source, 55 Customer Home); capture Python syntax and `git diff --check` pass. Exact docs-head Pilot `40432391503` and Map Engine `40432391569` on `61be1798` both completed SUCCESS; the authenticated capture `40432384271` also completed successfully.
- **NEXT:** reconcile any new successor and wait for…69227 tokens truncated…chema changes.
- Validation checkpoint: Home DOM/mutation25, Rider runtime83/map121, Native screen384, delivery and P0-P4 source contracts PASS. Full regression running; exact-head Native compilation/emulator CI PENDING (local SDK/Gradle absent). Browser screenshot attempt BLOCKED: Chromium download returned truncated archive; no visual result claimed.
- NOT DONE: rendered Web review/preview, Android compile/emulator/visual and authenticated ON/OFF/reload/reconnect/process/dispatch evidence still required. Current source has no verified reusable Web service/Auto Accept/destination settings parity yet; do not invent a new preference backend. Realtime channel delivery remains unverified (existing Web reconciliation is polling/reconnect).
- NEXT: inspect first full-suite failure, commit corrections; push exact candidate for Native CI, review screenshots on small/large/font-scaled devices, publish bounded Web-only preview preserving current main and routing transport. Production cutover, physical GPS/authenticated accounts, signed AAB/Closed Testing stay BLOCKED until actual gates pass.

## 2026-10-19 Ready switch follow-up
- Published PR438 source3789cc00ac6d25ae2ea59a413096216c440de189/tree2607b0c81ba2a93ef834e88b65c56ffebe80d567. Native3422/run39195324274 and Map167/run39195324104 running; five independent SQL race gates PASS, Android build/runtime pending.
- Delayed profile read cannot overwrite a later acknowledged switch write: request-order fence and actual overlapping read/write regression added; Home DOM27 PASS. Build marker rider-20261019-ready1. No new business state.
- Local full regression exposed test timezone mismatch (machine Oct18/New York vs business Oct19/Bangkok); existing dispatch suite passes108/0 under TZ=Asia/Bangkok. No business SQL changed. Require clean full run exit0 in business timezone.
- Main Web-only projection prepared preserving current transport/places; not deployed. Visual/authenticated availability/dispatch, Web configuration parity, Android screenshots/build/restoration still PENDING. No DONE/release claim.


## 2026-10-19 Web-first Ready card publication
- Public Web main e37dc61cd37ec6f24460d69f12dd17b8d2ba72e3/tree daef900932cff884e3310d5b4a8cef9876d09288 publishes the bounded UI/profile/poll-fence projection, preserving current legacy route transport and real places. Full clean Web TZ=Asia/Bangkok npm pre/test/post EXIT0; projected DOM27, runtime83 and map96 PASS. Pages/RC deployment verification still required before claiming the live page updated.
- Full clean source TZ=Asia/Bangkok npm pre/test/post EXIT0 on local eff80250 tree (same follow-up code, before this documentation receipt). Published parent3789 Native3422 has full regression/JVM unit, navigation/MapLibre and five SQL race gates PASS; APK and display/launch runtime still running. Exact final successor CI still required.
- Web review URL: https://chatchairins-source.github.io/QueuGo/rider/ (build rider-20261019-ready1 after Pages). Current Settings source is improved but Web feature preference parity/authenticated acceptance/screenshot gates remain pending; this is not DONE.

## 2026-10-19 Rider Settings Web destination parity
- Stream: existing My Destination settings parity; branch work-native-route-bundle-rider-settings-20261019, parent9e16277f594fdab737d1ae2e6e7040b62844e012, backup backup/rider-before-settings-parity-20261019. No newer open successor at start.
- Existing Native My Destination state/store/dialog remains unchanged. Web Profile Settings now opens a clean map/label/preference editor using the SAME public qg_rider_destination_preference and qg_rider_set_destination_preference RPCs and existing QueueGoMapWeb create/place search/GPS helpers. No new preference table/API/availability state or route engine.
- Production read-only pg_proc audit confirms both destination RPCs and expected signatures are installed. qg_rider_dispatch_preference is absent; no continuous-dispatch/Auto Accept readiness activation is claimed or deployed. No real account preference/order write during QA.
- Existing saved preference loads from backend on every open; only matching acknowledged data yields success. Lost-write response reconciles with the same read RPC. Single dialog/write, account/session fencing, blank/out-of-range backend rejection, map/GPS cleanup and keyboard focus handling retain valid current state.
- Focused TEST: actual Settings DOM/RPC36 PASS, Home DOM27, Rider runtime83/UI103 and source integrity99/87 PASS. Full exact-tree npm pre/test/post required next. Native parent9e16277f exact run39195851635 has full regression/JVM/three APK/16KB/no-WebView and five SQL race gates PASS; emulator launch/display final steps still running at this checkpoint.
- Web review: previous maine37dc61c RC39195747917 + Pages39195747447 SUCCESS, actual HTTP200/build rider-20261019-ready1 confirmed. New Settings editor not yet deployed. Browser inspection reached the real sign-in page without an authenticated session; no credentials bypass, synthetic logged-in page or authenticated screenshot claimed.
- NEXT: full source/Web projection tests; checkpoint/publish source descendant, deploy bounded Web editor after passing tests, inspect exact-head CI. Authenticated availability/destination save/reopen, physical GPS/font-scale/Android visual and signing/Play/Production backend gates remain BLOCKED; owner must use an approved real Rider session and actual phone for those acceptance steps.

## 2026-10-19 actual Android idle-recenter runtime failure
- Latest source PR439187ac19d/tree536479b9 preserves Home PR4389e16277f. Settings source/Web full npm pre/test/post EXIT0; Web editor published as main65638570b670b3949457d2fd8317bf4f65d8c0a5/treee282a6827e2d07e0eb529b91c07ed1a5e8d69c12, awaiting Pages/RC/live verification.
- Actual parent9e Native run39195851635 map-display job117878415155 FAILED. First real failure: RiderMapRuntimeTest assertSameCamera line102, initial SDK zoom16 versus resumed17 at line47. Artifact12161594769 retains actual frames/cameras. JVM/three APK/16KB/five SQL races and isolated navigation/MapLibre gates passed; this is NOT a green containing Native run.
- Root path: idle recenter used a transient 180ms follow-state pulse. It could be overwritten/expire before the map style/GPS was ready, leaving the fallback camera on first load. Existing renderer now receives the durable UI recenter command identity in its existing render state; lastState retains it before SDK/style readiness and consumes it only after scheduling a real camera update with a valid fix. No idle continuous follow, new map engine or business state; policy and strict continuity assertions unchanged.
- Backup backup/rider-before-idle-recenter-20261019 at actual published187ac19d. Focused navigation/source273/runtime-budget contracts PASS. Actual Android compilation and the unchanged first-load/resume/recreate instrumentation MUST pass on this successor before claiming repaired. Full local regression next; do not merely rerun the known-failing parent or weaken zoom assertions.


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


## 2026-10-20 six-lane integrated successor
- Canonical branch: `work-native-release-six-lane-integrated-20261020`. Base includes carriageway PR #510 plus merged Map runtime PR #512 (`fc146950d44afa67a34020cdfaa2a7e4a1b2f1b4`). This successor replaces the temporary parallel #513/#514 heads for continued integration; no reset/revert/force/cherry-pick was used.
- Lane 1 Rider Native Navigation: forward-ported only the tested bounded display-hold delta after heading/carriageway rejection plus current manifest/source guards.
- Lane 2 QueueGo Map: PR #512 exact Map Engine run 39428225752 / job 118646115172 SUCCESS and merged; runtime files are unchanged in this successor.
- Lane 3 Admin: latest main audit found no new full-table hydration or DB polling regression; Admin sync is 60s-bounded and suppressed while Realtime is subscribed, Support refresh is visible-page/newest-cursor only. No speculative Admin rewrite added.
- Lane 4 Credit/Top-up: restored the existing shared QueueGoCredit surface on Merchant Revenue and Rider Earnings Web; Native Merchant/Rider shared credit cards already existed and remain unchanged. Added source regression so later UI rewrites cannot orphan those mounts. No provider/schema/balance/Production activation.
- Lane 5 Grocery/Market: PR #491 exact green implementation is contained; compare to this successor shows no later Market/Grocery implementation rewrite. Preserve existing RLS, approved-image and idempotent-add contracts.
- Lane 6 Passenger Ride/Express/Dispatch: inherited release-unified implementation remains contained; after PR #497 only navigation/UI files changed, so existing exact green race evidence remains the baseline while this integrated Native full regression runs.
- STATUS: SOURCE INTEGRATION ACTIVE. Exact-head Native Pilot required. Physical GPS/road, authenticated real-account flows, provider settlement, signing/AAB, Play Closed Testing, public map HTTPS and Production cutover remain BLOCKED until real evidence/approval.
- NEXT: inspect exact-head Native Pilot. If red, fix only the first actual failure on this branch; if green, use this branch as the sole successor for subsequent six-lane work and close superseded temporary PRs after preserving their evidence.


## 2026-10-21 PromptPay Web publication / Native client successor
- Web source222538752a1cf273aee3e47e03bea45306c5ea8a PR528 merged ascee5a58d7505b1e810d717687c060a6d02beb74f. Full local Web npm test EXIT0, exact RC39523018771 SUCCESS including mobile WebKit; Pages39523292975 SUCCESS. Actual served queuego-credit.js and admin/credit.js equal tested files.
- Backend: queuego-credit v2 + signed queuego-credit-webhook v1 deployed. Existing database ledger retained. Rider grant migration20261021003910 applied; authenticated active-Admin/live-session guards, durable request IDs and audit. Isolated Rider ledger16 plus Web Rider form4, Merchant38, Edge82, Web37 checks passed; no real bank settlement/grant-to-owner was claimed.
- Readiness: environment_ready=true, live_key_configured=false, webhook_signing_configured=false, live_intake_enabled=false. Both existing credit feature flags remain false. BLOCKED: OMISE_SECRET_KEY (LIVE), OMISE_WEBHOOK_SECRET, QUEUEGO_CREDIT_LIVE_ENABLED, provider LIVE PromptPay and webhook signing configuration. No secrets fabricated, no charges or money seeded.
- Native scope: strict descendant of current credit021caf67 (PR526), preserving its passed Native run39520235790. Sync shared Merchant/Rider client only: server pending UUID restoration, provider recheck through original queuego-credit endpoint, authoritative summary, explicit terminal statuses, same one-balance display. Public Web remains on main; no old Web snapshot published from Native branch.
- Evidence: native source282 and Edge82 PASS. NEXT: exact Native full regression/three-app build; signed bank webhook and actual Owner payment/persistence acceptance remain blocked by provider settings above. Do not claim LIVE DONE.


### Rider Grab-benchmark navigation / pickup / payment completion (2026-10-22)
- Status: IN PROGRESS
- Predecessor PR: #623
- Active branch: `work-native-rider-grab-benchmark-20261022`
- Current checkpoint HEAD: `3f9a4d55baf43bd1123866f7d1b8556da37805ae`
- Successor base: PR #620 / `f64ad6b2c85e04a2c6016c452753d264c4b6cee3`
- Completed source checkpoints:
  - off-route requires >30 m sustained for 3 seconds on Native and Web;
  - display road snap capped at 25 m; beyond that use real Rider position/bearing;
  - Android current + following maneuver HUD, U-turn guidance and 150 ms crossfade;
  - Android/Web merchant receipt proof is durable and explicit “ได้รับอาหารแล้วใช่ไหม?” confirmation gates pickup;
  - Web proof compression target <=500 KB with IndexedDB recovery;
  - Android/Web full-swipe completion requires 100%, haptic feedback and 200 ms bounce-back when incomplete;
  - completion summary uses authoritative Rider payout and real rating summary only;
  - Android and Web pan now resume navigation follow after 8 seconds idle; return animation is <=500 ms.
- Map Engine off-route policy now also uses >30 m sustained for 3 seconds and rejects navigation decisions above 35 m GPS accuracy.
- Source tests added/updated:
  - `NavigationGuidanceTest.kt`
  - `NavigationViewModeTest.kt`
  - `RiderNavigationHudTest.kt`
  - `tests/native-rider-delivery-success-parity.cjs`
  - `tests/queuego-map-web-camera.cjs`
- Evidence status: source assertions committed; this `work-native-*` successor exists specifically to run exact-head Native Pilot CI.
- Release blockers: physical Android road/GPS test, ANR/OOM/battery evidence, Production/public runtime gate remain mandatory before launch-ready claim.
- NEXT: finish Web current/next maneuver HUD using QueueGo route `legs[].steps`, then run exact-head source/CI gates; fix only the first failure before any further release claim.


## Rider Android authenticated visual review — 2026-10-24

- Branch: `work-native-rider-auth-review-20261024`, based on the latest Native Rider/route-bundle checkpoint.
- Added a dedicated secret-gated Android Emulator workflow and capture script for authenticated Rider Home map, Messages/Notifications, Earnings for today/7/30 days, Profile, Learning Center, Safety Center, Support, and Diagnostics.
- The capture script uses a verified pinned real Buriram PMTiles archive in debug-only app storage; it does not tap online/accept/start/complete controls or save credentials/screens UI hierarchy.
- Added the same debug-only map archive to Native Pilot capture so its map lifecycle is exercised instead of recorded as blocked.
- Screenshot evidence status: **not captured yet**. The repository Actions secrets `QG_RIDER_REVIEW_PHONE` and `QG_RIDER_REVIEW_PASSWORD` must exist before the authenticated job can run. The current GitHub connector cannot create Actions secrets.
- NEXT: configure those two repository secrets, then rerun the Rider authenticated review workflow and inspect the four screenshot artifacts. Until then, login and all post-login screens remain unverified.
## 2026-10-27 Native local-first cache checkpoint — exact-head Pilot PASS

- Current successor branch `work-native-web-parity-20261026`, source HEAD `cd94ffcf23c232d5ffaa1053180ae6ff9aa6a9fc`; PR #730 remains open/draft. The branch tip is unchanged from the inspected exact HEAD and descends from the current PR base and the earlier Native rebuild successor.
- Exact-head Native Pilot run `40473824690` (#4486) completed SUCCESS: Full QueueGo regression, tracking coordinate/terminal-state Kotlin tests, Customer/Merchant/Rider APK builds, Android 16 KB packaging, WebView rejection and emulator launch/map/voice lifecycle all passed. Exact-head Map Engine run `40473824733` (#535) also completed SUCCESS.
- Local `node tests/native-android-source.cjs`: 398 checks PASS. Existing local-first coverage persists safe Customer/Merchant content, shared remote images and bounded reference caches; routine data refresh stays visually silent. Orders, prices, stock decisions, balances and dispatch remain server-authoritative. No Production writes.
- This is CI/emulator and source evidence; it does not certify authenticated Customer screenshots, physical Android devices, road/GPS behavior or release readiness. Customer authenticated screenshots remain blocked by the recorded missing review credentials.
- **NEXT:** audit remaining Customer/Merchant/Rider remote reads for repeated downloads of non-authoritative display/reference data. Record each exact callsite and trigger; cache only a confirmed repeatable gap, and keep live order/financial/inventory/dispatch decisions server-authoritative. Run exact-head Native Pilot after each source change.


## 2026-10-27 Customer Favorites Web parity — source checkpoint; exact-head Pilot NEXT

- Current successor branch parent: `5680a9d4d8d490061efc18df89fce43a042fff8c` (PR #730). Source audit of Production Web `origin/main` confirmed Favorites renders the item name, product price when applicable, and a filled heart removal control; it does not render product/shop thumbnails or shop-category metadata.
- Native Favorites had added thumbnails, shop-category text, and a text `นำออก` button. Updated the screen to match the Web row content and red heart action, keeping existing authenticated favorite toggle, stale local cache, unavailable-item removal, and shop navigation behavior.
- Added a scoped source contract for the Web-aligned row. Local `node tests/native-android-source.cjs`: 399 checks PASS; `git diff --check` PASS. No business authority or Production data changed.
- Exact parent Native Pilot run `40475975171` and Map Engine run `40475975073` are being inspected; parent Pilot has reached emulator lifecycle without a confirmed failure. Do not treat parent CI as validation of this new source change.
- **NEXT:** commit this bounded source chunk, inspect terminal results for the parent exact-head Native Pilot/Map Engine, then publish this successor and run exact-head Native Pilot for the new commit. Continue the Customer Shop/menu/cart parity matrix after the source gate.

## 2026-10-27 Customer order-tracking contact parity — source checkpoint; exact-head CI NEXT
- Compared Production Web `customer-features.js` tracking action with Native `CustomerOrderTrackingScreen.kt`: Web's `linkPhone` omits the shop contact action when the phone value contains no digit or `+`; Native previously rendered the action whenever an order context contained a shop, including missing/blank phone values.
- Native now applies the same usable-phone guard while preserving its existing in-app voice call action. Added a source regression contract for this boundary; `node tests/native-android-source.cjs` passes 400 checks and `git diff --check` passes.
- Parent Favorites exact-head Native Pilot `40478110875` and Map Engine `40478110860` were still running at checkpoint time; Native was in Full QueueGo regression and Map Engine in Dependencies and regression. No failure observed yet.
- No Production data, pricing, dispatch, or backend authority changed.
- **NEXT:** inspect parent exact-head runs, publish this bounded source successor and run exact-head Native Pilot + Map Engine. Then continue the remaining Customer Shop/menu/cart parity matrix; do not claim visual/device acceptance without its evidence.

## 2026-10-27 Customer tracking Thai timestamp parity — source checkpoint; exact-head CI NEXT
- Continued the Production Web `customer-features.js` vs Native Customer Shop/menu/cart/tracking audit. Shop review summary and product rows are present in both; Web's menu request does not select category fields, so the apparent Web category filter renders no category options. Cart image/name/price/quantity/remove/subtotal/checkout hierarchy is present in both.
- Confirmed a real tracking display mismatch: Web formats `statusTime` using Thai locale `toLocaleString('th-TH')`, while Native omitted the Buddhist year and seconds. Native now formats day/month/year, 24-hour time and seconds with `ThaiBuddhistChronology` in Asia/Bangkok.
- Source guard now checks both usable shop-phone action availability and Thai timestamp precision. Local `node tests/native-android-source.cjs`: 401 checks PASS; `git diff --check` PASS.
- Exact current source predecessor Native Pilot `40478683739` is queued; predecessor Map Engine `40478683750` is running dependencies. Parent Native Pilot `40478110875` has passed regression, source/SQL contracts and tracking JVM tests and is building the three APKs. Parent Map Engine `40478110860` passed.
- No Production records, financial authority, inventory decisions, dispatch logic or backend data changed.
- **NEXT:** publish this bounded source successor and inspect its exact-head Native Pilot + Map Engine. Continue only from the first confirmed failure; preserve Customer screenshot and physical-device gates as open until their evidence exists.

## 2026-10-27 Customer Profile photo parity — source checkpoint; exact-head CI NEXT
- Continued Customer Web Production parity review and found Web Profile renders `users.metadata.profileImage`, `profile_image`, or `avatarUrl`; Native Profile showed only an initial even when metadata contained a safe image.
- Native sign-in now reads the existing `metadata` column for Customer role, accepts HTTPS or small JPEG/PNG/WebP base64 image data, carries it in the existing encrypted role-session store, and renders it in the 60 dp profile hero with the same initial fallback. Session restore remains backward-compatible when no photo is stored, and every subsequent session validation refreshes the latest Customer metadata so pre-existing sessions hydrate the image without requiring a new login.
- Extended source contracts for metadata keys, encrypted session persistence, and Web-aligned Profile rendering. Local `node tests/native-android-source.cjs`: 402 checks PASS; `git diff --check` PASS.
- Timestamp/contact source HEAD `5ca0bc5419ffb18ff4ab266a7d57d8bfe2d0b4ac` has Native Pilot `40479003024` queued and Map Engine `40479003003` in progress. Parent Native Pilot `40478110875` passed three APK builds, 16 KB packaging and WebView rejection and is still on Android emulator lifecycle; parent Map Engine `40478683750` passed all jobs.
- No Production data or operational authority changed.
- **NEXT:** publish this source successor and inspect exact-head Native Pilot + Map Engine; fix only the first confirmed failure. Continue remaining Customer parity after the gate while keeping screenshot and physical-device acceptance distinct.

## 2026-10-27 Customer notification order-number parity — source checkpoint; exact-head CI NEXT
- Continued the same Web Production parity audit. Web `renderNotifications` normalizes legacy/current order identifiers in every notification title and message with `QueueGoOrderNumber.replaceInText`; Native previously displayed raw strings.
- Added shared `nativeOrderNumberInText` using the existing Production-compatible number parser, and apply it to the Customer notification title and message only at display time. Added regression fixtures for current, legacy POS and QO references plus a scoped source contract.
- Local Customer/Merchant/Rider source contract: 404 checks PASS; `git diff --check` PASS. No database values are rewritten.
- Exact predecessor Native Pilot `40479861625` is currently in Full QueueGo regression; Map Engine `40479861694` is queued. Previous Native Pilot `40478110875` completed successfully through emulator lifecycle and evidence upload. No failure observed.
- **NEXT:** publish this bounded successor, confirm exact-head Native Pilot + Map Engine, then continue Customer screen parity. Keep any visual/authenticated or physical-device claims tied to actual evidence.

## 2026-10-27 Customer notification timestamp parity — source checkpoint; exact-head CI NEXT
- Continued the Customer notification comparison. Web formats `created_at` with Thai-locale `toLocaleString('th-TH')`; Native displayed the raw ISO string. Native now formats day/month/Buddhist year plus 24-hour time and seconds in Asia/Bangkok, with the original value as parse-failure fallback.
- Added a source guard for the Web-aligned timestamp and retained the previous title/message order-number normalization. Local `node tests/native-android-source.cjs`: 405 checks PASS; `git diff --check` PASS.
- Previous exact-head Native Pilot `40479861625` completed regression/source checks and has reached Tracking coordinate and terminal-state JVM tests; exact-head Map Engine `40479861694` was queued. Latest published HEAD `6a9012914873d5dce1aa998ad69e3611decdbf16` has Native Pilot `40480117757` and Map Engine `40480117472` pending. No failures reported.
- **NEXT:** publish the timestamp parity delta and inspect its exact-head Native Pilot + Map Engine. Continue from the first confirmed failure and the remaining Customer parity checklist.

## 2026-10-27 Customer Support ticket timestamp parity — source checkpoint; exact-head CI NEXT
- Extended the Thai timestamp formatter introduced for Customer Notifications to the Native Support ticket history. Production Web displays each ticket's `created_at` under its status; Native now does the same with Thai Buddhist year, seconds and Bangkok time while retaining a raw-value fallback.
- Updated the Customer source contract for both notification and support timestamp usage. Local `node tests/native-android-source.cjs`: 405 checks PASS; `git diff --check` PASS.
- Predecessor Native Pilot `40480117757` and Map Engine `40480117472` are still pending/in progress, without a reported failure. Latest published HEAD before this delta was `ead21d9cb49d3784dc6c320f285f22704919a574`, exact Native run `40480268978` and Map run `40480269102` pending.
- **NEXT:** publish this bounded parity delta and inspect exact-head Native Pilot + Map Engine. Continue the Customer parity matrix and preserve actual authenticated screenshot/device gates.

## 2026-10-27 Customer Support evidence parity — implementation checkpoint
- Web Customer Support history exposes `ดูรูปหลักฐาน` for tickets with `evidence_path`. Native now selects and caches that field, exposes the same action, and requests a 300-second signed URL through the existing shared Support API.
- Before requesting the URL, Native requires the object path to match the signed-in customer's Supabase Auth UUID folder and a UUID-named JPG/PNG/WebP object. Before handing off to Android, it accepts only HTTPS URLs from the configured Supabase host with the private `qg-evidence` signed-object route.
- Updated source contracts to cover the query, cache persistence, ownership check, signed URL flow and visible action. Verification and exact-head CI are in progress.

## 2026-10-27 Customer Support submit parity — local checkpoint, not yet published
- Follow-on audit confirmed Web Customer Support accepts optional JPG/PNG/WebP evidence up to 5 MB, preserves a ticket ID across retry and offers up to 30 related orders. Native had the shared uploader available for Rider but Customer ticket submission always sent `p_evidence_path = null` and showed only 10 orders.
- Local working changes now add the Customer attachment picker/upload, retain the same ticket ID and uploaded object path for an in-screen retry, check for an already-created ticket before retrying, raise the order list to 30, and match the Web Rider/shop category wording. This remains local while the preceding `baab348` exact-head Native/Map CI runs complete.
- Local `node tests/native-android-source.cjs`: 406 checks PASS; `git diff --check` PASS. Kotlin/APK verification will come from exact-head Native Pilot after the combined source checkpoint is published.

## 2026-10-27 exact-head Native failure and forward fix — next source checkpoint
- Exact-head Native Pilot `40480925767` (#4510) failed after Full QueueGo regression and source contracts: `NativeOrderNumberTest` expected a legacy `POS-` prefix to survive replacement, but the Production Web formatter and Native implementation both normalize it to `QT-`; the unit assertion was stale. Kotlin compilation also reported the new Customer Support viewer's missing `QueueGoNativeApi` import.
- Corrected the expected normalized output to `QT-0007 · QT-1863` and added the missing import. The same local checkpoint also completes the Customer Support submission evidence picker/upload, same-ticket in-screen retry check, 30-order selector and two Web-aligned category labels. Local source checks pass 406/406; Web/native order-number contracts pass 39/39; `git diff --check` passes.
- Map Engine `40480925423` (#547) remains on regression; its workflow declares `cancel-in-progress: false`. Publish this combined fix as a single descendant and require a fresh exact-head Native Pilot before considering the code validated.

## 2026-10-27 Rider Web parity: earnings and idle camera — source committed; exact-head CI NEXT
- Parent HEAD: `938ff733300bd9c1560829dfbebb5013c3db2898` on `work-native-web-parity-20261026`; Production Web blueprint remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Compared Rider Earnings source against `rider/index.html`: Native had non-Web-only performance/planning/mission sections and extra API reads, expanded route/cash rows, and a different cash net calculation. Native now uses the Web four-stat summary, four cash totals (`collected - paid`), status/fee recent rows, and only the summary/ledger requests needed by this page. Source coverage locks this parity shape.
- Compared idle Home camera policy against Web `qgNavigationCameraFollowEnabled()`: Web only follows navigation for active work/open offer; Native incorrectly enabled heading follow and perspective from `online`. Native now leaves idle Home north-up and follows only for active work (or the explicit test flag). Updated the old regression that encoded the mismatch.
- Commit `b01d30b49edcf5293973fe839f0d7c6f51931c6e`. Local checks: source 406/406, surface coverage 384/384, map camera 69/69, delivery-success parity PASS, route-bundle parity PASS, `git diff --check` PASS. This environment has no Gradle/Android SDK/adb, so no Kotlin/APK result yet.
- Screenshots are Web-only references; authenticated Android paired capture and physical device review remain unverified. No APK is certified for release.
- **NEXT:** push this exact descendant to PR #730 and inspect exact-head Native Pilot + Map Engine. Fix the first confirmed failure; then continue Rider Messages/Profile source parity and obtain authenticated paired Android screenshots before any visual-parity claim.

## 2026-10-27 Rider work-type auto-accept parity — local source committed; publish blocked
- Compared `rider/work-preferences.js` with `RiderAcceptanceMode.kt`: both expose auto-accept, bundle acceptance, and the same five service types (`food`, `grocery`, `market`, `express`, `ride`), use the same server-authoritative `qg_rider_work_preferences` RPC, capability gating, and require at least one selected service.
- Found the behavioral gap: Web `maybeAccept()` checks the offered `fulfillment_vertical` against selected services; Native auto-accept did not. Added the same selected-service guard before and after the offer delay, failing closed while preferences are unavailable. Added JVM cases and a source guard. Commit `cf08df6c866a7b09d1f4123f535c62e47517fbc5` follows earnings/map source commit `9102914bbbda748d5305000a7711c4aa021d5f90`.
- Local checks after the latest source: source 406/406, screen coverage 384/384, Web map contract 69/69, `git diff --check` PASS. Kotlin/JVM and APK gates remain pending because local Gradle/Android SDK/adb are unavailable.
- Push of `9102914` to `work-native-web-parity-20261026` was rejected by automatic approval review: external export of private source to a GitHub destination whose organization ownership was not verified. No alternate publication path was attempted. Latest local HEAD is `cf08df6c866a7b09d1f4123f535c62e47517fbc5`; remote PR branch remains `938ff733300bd9c1560829dfbebb5013c3db2898`.
- **NEXT:** continue local Rider Messages/Profile source audit. Publishing the commits and obtaining exact-head CI requires resolving the auto-review block; authenticated paired screenshots and physical-device verification remain open. No APK is certified for release.

## 2026-10-27 Web/Android Ride and map parity — Native zoom source committed; customer Ride Web port in progress
- Successor HEAD: `bc526a4cd902cd6cba35b5b31587cf8f74f2b21f`, following local auto-accept parity commit `dee740f824d8c226de05de7cf6fc8973a290820a`; remote publication is still blocked by the recorded private-source export review rejection.
- Compared Web `navCameraDecision` and Android `navigationZoom`: Android interpolated zoom across speeds while Web uses thresholds. Native now uses Web's route distance and speed thresholds (<=60m 19, <=180m 18, >=18m/s 15, >=11m/s 16, >=5m/s 17, <=1.5m/s at least 18) plus the same >60m GPS accuracy decrement. The Kotlin test now covers representative thresholds. No route/dispatch authority changed.
- Evidence: `tests/native-rider-navigation-map-module.cjs`, `tests/native-rider-maplibre-runtime.cjs`, and `tests/queuego-map-web-camera.cjs` pass; `git diff --check` passes. Android Gradle/JVM execution and paired device screenshots remain unavailable here.
- Commit `851e6dced1d00393e892d9395bd769652480c0ba` ports the Native Passenger Ride active-trip map and privacy-preserving nearby supply clusters to Customer Web using the existing `queuego_customer_ride_supply` RPC, and aligns arrival milestone text. Test `customer-ride-runtime.cjs` passes 18 checks, including pickup request payload, valid anonymous clusters, active pickup/destination/Rider markers, and arrival status. Passenger quote and Rider Ride source contracts pass. No new backend query or Production write was added.
- Commit `0747b6ef0ed8d28cb9efcdd414a299e96a72e94b` aligns Android Passenger Ride completion with Web's separate cash confirmation step: the Rider opens a cash amount dialog, then swipes to confirm before the existing authoritative Ride completion action runs. Missing, negative, NaN, and infinite cash totals fail closed; zero remains valid. Local `native-rider-delivery-flow.cjs`, `native-rider-slide-complete-parity.cjs`, `native-rider-delivery-success-parity.cjs`, and `native-android-source.cjs` pass. Gradle wrapper and Android SDK are absent, so the added Kotlin/JVM boundary test has not executed.
- Rider receive/delivery source cross-check: Web `acceptJob` and Native `acceptOffer` both use the existing action-once `claim`/`market_claim` RPC payload, attach only a <=10-second valid GPS fix, and enter QueueGo navigation immediately after a successful claim. Web pickup uses `qg_pickup_with_photo`; Native uses the same RPC and reconciles the committed proof. Both gate final ordinary delivery on a delivery photo, finite cash total, and a swipe before the existing completion RPC; Native retains a recoverable proof queue as an additional offline behavior. The Web/Native >200m arrival warning contracts pass. Passenger Ride Web/Native now both require explicit cash amount review and a swipe before server completion.
- Targeted local evidence passes: `customer-ride-runtime.cjs` 18 checks; Passenger Ride quote 27; Rider Ride Web 8; Native Rider delivery flow, P0–P4, swipe completion, delivery success, arrival guard, route bundle, offer push, and navigation/map source/runtime contracts; Native Customer/Merchant/Rider source 406 checks; `git diff --check`. No Gradle wrapper, Android SDK, or `jsdom` dependency is installed here, so Kotlin/JVM/APK and the jsdom-backed broader Web regression suite have not run.
- **NEXT:** obtain a fresh exact-head Native Pilot + Map Engine run and authenticated paired Android/Web screenshots after publication can proceed. Publishing remains blocked by the existing export review rejection. Real-device Rider GPS/road map parity and APK release readiness remain unverified; do not call the Android build release-ready yet.
- Camera source follow-up commit `d941242421758e1b005f04c2e6615dc18e2e4e66` removes Native's extra 0.6–1.8m Rider marker dead zone, uses Web's 260–1250ms cadence clamp, and applies the same linear-interpolator + QueueGo easing curve for marker animation. The route-snapped display fix and authoritative GPS values remain unchanged. Navigation module, MapLibre runtime source, Web camera, Rider delivery-flow, and 406 native source contracts pass. Kotlin/JVM/APK remains pending because Gradle/SDK are absent.
- Follow-on map audit aligned route-arrow cadence with Web's 80 CSS-pixel symbol spacing by projecting route points through the Native map camera and placing matching white chevrons along the rendered line. Rider stop pins now use Web's 20px green shop/red customer circles with white borders; market pickups alone receive in-circle sequence numbers, completed market pickups remain visible gray at 16px, cancelled market stops are omitted, and completed/active work stops stay rendered while route guidance still filters to pending stops.
- Verification: `native-rider-navigation-map-module.cjs`, `native-rider-maplibre-runtime.cjs`, `queuego-map-web-camera.cjs` (69 checks), `native-map-marker-design.cjs`, `native-map-marker-family.cjs`, `native-rider-route-bundle-parity.cjs`, `native-android-source.cjs` (406 checks), and `git diff --check` pass. These are source/runtime contract tests; no local Gradle wrapper or Android SDK is available, and there is no authenticated Android screenshot capture. Exact-head CI remains blocked by the previously recorded export review rejection.
- **NEXT:** continue comparing Rider navigation behavior against Web on reroute, completed multi-pickup stops, and camera gestures. Keep APK readiness and full visual parity unclaimed until exact-head Android build plus paired authenticated device screenshots are available.

## 2026-10-27 Rider reroute parity — local source checkpoint
- The route audit found Native could reroute immediately after a missed turn and shortened that case's cooldown to 3 seconds. Web uses a consistent rule: more than 30m off route for 3 seconds, then a 20-second cooldown. Native now uses the same thresholds for every reroute; successfully replacing a route resets geometry progress while retaining the cooldown for the active stop.
- Updated Kotlin guidance/coordinator unit cases for missed turns, the under-30m boundary, and cooldown persistence after a successful reroute. Source contract now checks the Web and Native constants and reset behavior. Local navigation map module, MapLibre runtime, Web camera (69 checks), marker design/family, route bundle parity, native source (406 checks), and `git diff --check` pass. Local Kotlin/JVM/APK execution is still unavailable because this checkout lacks Gradle wrapper/Android SDK; exact-head CI is still blocked by the previously recorded external export review rejection.
- The same follow-up found Native exited route overview whenever the active stop changed. It now keeps overview while work remains and returns to FOLLOW only when navigation becomes inactive, matching Web's overview state across pickup/delivery progression. The view-mode unit case and source contract cover this behavior.
- Overview bounds now include completed pickup pins as Web does, so gray finished Market stops remain within the framed route. A source and Kotlin unit case covers the bounds.
- Ported Native's OSM speed-limit sign to Web using only valid `osm:maxspeed` intervals from the same QueueGo route response. It follows route progress, stays hidden for offer previews/weak GPS/off-route fixes, and is placed beside the live speed pill. Rider inline-script parsing and the map/navigation source suites pass; authenticated visual placement and Kotlin/APK execution remain unverified.
- **NEXT:** check the Native guidance cue/arrival thresholds and compare them with Web route progress, then examine authenticated paired device output when exact-head build access is restored.

## 2026-10-27 Market navigation cancelled-stop sequence correction
- IN PROGRESS: recovered newest remote successor `3b0a3fd981b450ec517074e40af3ba4d8bfada87` on PR #730; no newer containing remote branch found.
- Exact Native Pilot `40513651567` passed full regression and compilation, then failed its sole Rider JVM assertion `RiderNavigationIntegrationTest.marketStopsPreservePickupOrderBeforeCustomer` at line 87 (325 Rider tests, one failure). Pickup numbering excluded CANCELLED shops but dropoff numbering used the unfiltered count, producing [1,2,4] instead of [1,2,3].
- Fixed the existing navigation-stop builder to reuse the filtered pickup list for dropoff rank. Existing behavioral assertions are unchanged; completed pickup visibility, cancelled-stop exclusion and server dispatch authority are preserved.
- Local navigation module and route-bundle contracts PASS; three-app source 409/409 PASS; diff check PASS. Local Gradle/Android SDK unavailable; exact-head JVM/APK evidence remains pending.
- NEXT: publish this strict descendant and inspect exact-head Native Pilot + Map Engine; fix first confirmed failure. Then inspect three-app APK artifacts and remaining map/parity gates. Physical-device and authenticated visual acceptance remain unverified.

- BLOCKED (publication): local fix commit `bd1efb65` is complete, but automatic approval review rejected push to `chatchairins-source/QueuGo` / `work-native-web-parity-20261026`, citing no explicit authorization to export potentially sensitive source to an unverified destination. No alternate write path attempted. Required unblock: explicit owner approval for publishing this bounded fix/checkpoint to that existing repository branch; then recheck remote HEAD before pushing and run exact-head CI.

- Publication unblock: owner explicitly confirmed publishing this fix to the existing repository/branch in this session. Git CLI lacks a credential; use the connected GitHub API with a strict parent/ref check. Exact-head CI remains NEXT.


## 2026-10-29 Native Merchant POS cash-in/out controls — exact-head CI pending

- Compared current Web Production POS shift UI with the Native Merchant POS implementation. The existing Native API already exposes the authoritative `pos_record_cash_movement` RPC, and the server returns cash-in/out totals, but the Native cashier screen did not expose those controls.
- Added Native controls for cash-in and cash-out during an open shift, with positive two-decimal amount validation, a required reason, and display of server-provided cash-in/out totals alongside expected till balance.
- Uncertain responses retain the same request UUID and payload for idempotent retry. Pending retries are scoped to the same authenticated actor and shop; retries remain available after a shift closes so a previously committed movement can be reconciled without creating a second movement.
- Added source regression coverage and enabled automatic Native POS CI on `work-native-pos-cash-movements-20261029`. Draft stacked PR #733 targets the cash-tender guard branch and must not merge before PR #732 is merged.
- Verification status at documentation time: Native POS CI and Native Android Pilot are running/queued for exact HEAD. No APK generated; no Production data changed. Still required: exact-head green CI/Pilot, then authenticated cashier acceptance on a real device for cash-in/out, uncertain retry, shift close/history, and expected balance.
- Next: inspect the first failing exact-head check if any, fix only confirmed regressions, and wait for the latest SHA's CI/Pilot before producing artifacts.


## 2026-10-29 Native POS cash-movement follow-up CI receipt

- Repository regression suite passed on code HEAD `5c19eed527bf5ffdf116a2975252991e962fbb28` in Native POS CI run `40850750130`: https://github.com/chatchairins-source/QueuGo/actions/runs/40850750130. The cash-tender assertion step was skipped by its branch condition, although the full `npm test` suite passed; corrected that condition on the cash-movement branch so the assertion runs there too.
- Current workflow fix commit: `2cf9a7b9a4ee3bb9c0e6355a2ff527c63890c529`. **NEXT:** wait for latest exact-HEAD Native POS CI and Native Android Pilot; inspect any first failure and fix before promoting PR #733 from Draft. No APK created.
