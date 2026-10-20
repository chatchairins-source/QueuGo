# Admin Web operational audit — 2026-10-20

Status: IN PROGRESS, not a completed release. Baseline main b8b9f9883b50d7da3efbd0d37d1e0080bc4b158e. PR445 is already merged. Checkpoints continue on PR457. Backup: backup/admin-before-overhaul-20261020. No reset/revert or old implementation overlay.

## Evidence and source boundaries

- Inspected current Admin HTML, all external Admin modules, shared role-realtime.js, source migrations and tests. Inspected staged newest Rider/settings source38baf74c78a5881a225df5264a9fd28be485bf9b and Express Web source81faa3e1. The native successor contains newer backend features but is not a Web main descendant; merging its whole tree would overwrite unrelated newer Web work.
- Credit and Passenger Ride modules are reused from38baf74c, with actor/status fencing added. Credit bytes before these guards match Express successor exactly (SHA256 f29a8fd1346e5ccf6ecaed8d6ae762e980b390876582d356be02ec89a4be06eb). No new ledger/payment implementation.
- Production catalog/ACL read only on project pkypiqhlrmzocysgeqew: all discovered public Admin RPCs deny anon EXECUTE. qg_admin_change_audit is not directly executable by authenticated/anon. Audit triggers already cover users, orders, shop_open_states, shop_gp_rates and system_settings, with RLS enabled. Do not duplicate those triggers.
- Production has Rider identity, vehicle inspection, vehicle review and document-expiry RPC wrappers. Their source uses private live-session/current-admin guards and audit. qg_admin_live_counts exists with an active-admin guard; it reports shopping-order status counts, saved online state and saved shop-open state, not worker/notification health.
- Production currently lacks queuego_admin_credit_accounts, queuego_admin_public_licenses and queuego_admin_configure_ride in the inspected public Admin function inventory. Source UI alone does not activate these features. Credit/Ride screens show errors rather than fabricate balances, configurations or successful saves.
- A rollback-only authenticated-role transaction with a synthetic non-Admin subject verified qg_admin_live_counts rejects with active-admin-required and queuego_admin_approve_shop rejects with42501. This is database guard evidence, not an authenticated real-user/browser acceptance test. No real account/order/credit data was changed.

## Performance findings and changes

| Entry point | Before | Current change | Remaining |
| --- | --- | --- | --- |
| Login / restored session | blank app while own-profile restore; then full hydrate | visible verification shell; existing role validation retained; scoped background read | physical Safari/network validation |
| Overview | users/profiles, all Orders/Items/Chats, quotations, Notifications, Products, Promotions, Settlements, order audit; sequential chains | count-only HEAD requests in parallel; no detail hydrate; shared short read cache coalesces repeated reads | real Production timings; count-query index/scale evidence |
| Approval / Users / Shops / Security | five PR445 directory reads but unbounded users/profiles; Users omitted from fast path | 200-user keyset pages with 201 sentinel; profile reads filtered to returned user IDs; scoped shop overview gets only current page IDs | legacy no-arg backend overload still exists but browser fallback is removed; deployed-call compatibility review before backend retirement |
| Rider status | three reads every5s even for hidden panel | no hidden polling; entry-scoped presence; scoped realtime/fallback; 100+1 Rider pages + page-ID profile read; current metadata online state | no 2,000-row presence replay; physical/Production latency evidence remains |
| Audit | full hydrate reset audit cache to empty | audit_logs latest100, no unrelated tables | server pagination/search beyond100 |
| Notifications | all notifications as part of full hydrate | current Admin user's latest100 | paging beyond100 |
| Orders | own bounded500 list plus unrelated hydrate; names looped over user cache | no unrelated hydrate | proper server filter/page and exact-page names remain |
| GP / Cash / Support / Catalog | own loader plus background full hydrate | own existing loader only | per-loader pagination and payload review remains |
| Realtime | one channel subscribed to24 tables on every Admin page | same shared owner/queue; table list scoped by visible section; scope changes retire old channel; coalesced refresh | private operational queues do not yet have changefeeds; explicit refresh required |
| Polling |8s whole-system fallback; Rider5s | Admin fallback at most60s, disabled while realtime is subscribed; no hidden Rider5s | Support20s behavior remains to scope/fallback |
| Rendering | all governance panels built every render; repeated scans | directory payload/DOM bounded200; summary participates in render signature | split governance renderers and field-preserving refresh remain |

Before/after request counts and timings are deliberately not invented. tests/admin-boot-browser.cjs executes the complete baseline and current HTML in mobile WebKit with50ms localhost transport fixtures, records shell/ready timing and Supabase request counts, and asserts no detail hydration on Overview. The first hosted attempt passed npm but failed because shallow checkout omitted the pinned baseline; full history checkout fixes the harness. Physical iPhone and Production latency are separate evidence.

## Permission and feature coverage matrix

Implemented distinguishes existing main features from staged source. View/control describe the current candidate UI, not proof of Production release. Audit means source/live evidence where stated, not a blanket assertion.

| FEATURE | IMPLEMENTED | ADMIN CAN VIEW | ADMIN CAN CONTROL | ADMIN RPC / POLICY | AUDIT LOG | MISSING |
| --- | --- | --- | --- | --- | --- | --- |
| Customer users/profile/status | Main | Users, bounded page | suspend/restore existing flow | users RLS and account guards | live users audit trigger | global server search/address inspection |
| Customer saved delivery location | Main: users.metadata lat/lng/address; order address snapshots | existing Users detail now shows saved address/map | read-only; no duplicate address book or order rewrite | existing Admin users SELECT/RLS | existing user_view audit | global user lookup; live signed-in acceptance |
| Customer Orders | Main | Orders/trace | existing reviewed deletion | qg_admin_order_trace / admin_delete_order_cash_safe | existing trace/archive | page/filter beyond current bound |
| Customer complaints | Main | Support tickets/UGC | ticket/report decision | qg_admin_ticket_action / qg_admin_ugc_report_action | existing guarded RPCs | live signed-in E2E |
| Merchant approval/documents | Main | Approval/detail | approve/reject/request changes | queuego_admin_shop_application/approve_shop/reject_shop/request_shop_changes | source audit | server directory readiness optimization |
| Merchant suspension/activation | Main | Users/Shops | existing account status | current users RLS/admin validation | live users audit trigger | real browser acceptance |
| Shop type/category | Main | Shop detail/list | existing profile editor incomplete | shop profile policies | partial | dedicated operational category editor review |
| Store coordinates | Main | location detail/map | existing location form | qg_admin_update_location | source atomic audit | physical map acceptance |
| Opening status | Main | Shop controls | existing open/close | shop_open_states RLS | live audit trigger | avoid stale scoped state |
| Grocery/Market stores | Main | existing Shops/Market/Catalog | existing approval/catalog | existing guarded catalog/market RPCs | existing catalog/market audit | catalog full lifecycle/security evidence |
| Store products/stock | Main | catalog-linked data only | partial | existing product/stock owner + Admin policies | not fully certified | operational stock/product UI inventory |
| Merchant GP/rates | Main legacy flow | GP/GP Rates | existing rate/review controls | admin_review_gp / shop_gp_rates policies | live GP-rate trigger | staged credit retirement gate; do not duplicate finance |
| Merchant credit | Staged source | Credit menu; fail closed if absent | existing credit module only after backend rollout | queuego_admin_credit_accounts / grant_merchant_credit | staged immutable ledger/audit | Production ledger release gate |
| Admin free Merchant credit | Staged source | existing grant form | existing durable UUID grant | queuego_admin_merchant_credit_targets/grant_merchant_credit | staged atomic audit/idempotency | live backend currently absent |
| Merchant top-up/settlement | Main legacy + staged credit | legacy GP; credit after release | existing legacy review only | admin_review_gp; staged credit RPCs | existing source ledger | paid-provider and credit cutover gates |
| Rider approval/status | Main | Approval/Riders/Users | existing approve/suspend/restore | role-checked account path | live users trigger | live real Rider acceptance |
| Rider identity recheck | Live backend + staged UI | new Rider review screen | request/review existing workflow | qg_admin_*rider_reverification* | existing private source audit | paged queue RPC; physical evidence |
| Rider vehicle inspection | Live backend + staged UI | new Rider review screen | request/review | qg_admin_*rider_vehicle_inspection* | existing private source audit | paged queue RPC |
| Rider vehicles | Live backend + staged UI | new Rider review screen | approve/reject existing vehicles | qg_admin_rider_vehicle_queue/review_rider_vehicle | source audit | complete service capability UI |
| Rider document expiry | Live backend + staged UI | new Rider review screen | set/clear verified expiry | qg_admin_rider_document_queue/set_rider_document_expiry | source audit | paged queues and real-document acceptance |
| Public driving licence | Staged Ride | Ride module, backend readiness shown | existing review after deployment | queuego_admin_public_licenses/review_public_license | staged audited review | Production RPCs/release gate |
| Online eligibility/availability | Main + staged | saved presence/status; Rider review rows now explain the four server-enforced base blockers | account/review controls, no forced bypass | server online gates + qg_admin_rider_review_page | source audit | device-side readiness evidence and additional service-specific gates |
| Food/Market/Grocery capabilities | Main + staged | incomplete | incomplete | existing dispatcher eligibility | source-specific | service capability editor with authoritative guards |
| Ride/Express capabilities | Staged | job screens/review where available | no unsafe force-enable | existing staged driver/vehicle/licence guards | staged | Production controlled activation |
| Laundry platform Rider | Main optional flow | bounded Laundry jobs | no forced assignment | existing Laundry invitation/acceptance | existing events | operational platform option UI |
| Rider credit/top-up | Staged | Credit menu after deployment | existing reverse-fee flow only | queuego_admin_credit_accounts/reverse_credit | staged ledger | Production ledger/provider release gate |
| Orders state/rider assignment | Main | Orders/trace, new bounded Dispatch view | existing safe trace/actions only | existing dispatcher ownership | existing trace | manual reassignment policy/RPC not implemented |
| Targeted offers/timeout/retry | Main dispatcher | bounded Dispatch offers/history100 each | dispatcher remains authoritative | live qg_admin_dispatch_overview, active Admin/live session | existing history | safe manual retry/assignment controls |
| Stacked jobs/Auto Accept | Staged successor | partial | no capacity/cap bypass | existing route bundles/preferences | staged source | operational reader/control + rollout gate |
| Cancellation/no Rider | Main | Orders/trace | existing cancellation paths | existing server state machine | existing order audit | operator actions coverage review |
| Ride service/pricing | Staged | new separate Ride page | existing configure RPC after deployment | queuego_admin_configure_ride | staged atomic audit | Production backend release |
| Ride active/failed/cancelled trips | Staged | bounded service Orders | no invented trip transition | Orders RLS/existing trace | staged state audit | status filtering/pagination/Production acceptance |
| Express service/pricing | Staged | separate Express page, jobs only | not added without server API | existing Express backend | staged | secure pricing/config Admin API/UI |
| Express pickup/dropoff/state | Staged | trace and bounded job IDs/states | no force transition | existing Express state/driver guards | staged | complete operational detail/eligibility |
| Laundry pickup/return | Main | separate bounded Laundry page | existing store workflow retained | Laundry RLS/events | existing events | operational pickup/return controls |
| Laundry platform option/fee | Main + staged | existing Settings partly | existing configured settings only | platform rules / existing Laundry ownership | partial | store-vs-platform operational controls |
| Food delivery pricing | Main | Settings | existing validated settings | system_settings RLS/admin | live trigger | versioned complete rate surface |
| Market/multi-shop fees | Main | Settings | existing platform controls | queuego_platform_rules Admin policies | existing source | source/live rule audit and scheduling checks |
| GP/service fees | Main + staged | GP/Settings | existing guarded controls | existing rates/rules | live GP trigger; source rules | credit cutover consistency |
| Wallet transactions/reserved fees | Staged | Credit menu after backend ready | existing fee reverse/grant only | existing immutable credit APIs | staged atomic ledger | Production release, operator history paging |
| Grocery Master | Main | existing paged catalog | existing import/status/image/merge | existing guarded Grocery RPCs | source catalog audit | all control/security regressions/live acceptance |
| Market Master | Main | dedicated filtered50-row Admin Master browser | read-only until guarded mutations certified | existing Market master RLS | source receipts/audit | audited master editor/status/category controls |
| Category enable/images/master visibility | Main | existing catalog/banner tools | existing reviewed controls | existing catalog/storage RLS | source-specific | full category coverage/security audit |
| Home/category/service banners | Main | separate Content page reusing managers | existing banner CRUD | existing settings/storage policies | not fully certified | full mutation/audit certification |
| Notifications | Main | own latest100 + broadcast | existing role-targeted broadcast | existing notification policies | source-specific | delivery health and paging |
| Maintenance/feature flags | Main + staged | Settings | existing allowlisted toggles | existing platform rules | source-specific | full inventory/maintenance Admin controls |
| Audit logs | Main | actual audit_logs latest100 | read only | existing audit RLS | live | pagination/server search |
| Notification/job health | Partial | System saved counts + unified/rider outbox status aggregates | no fabricated worker controls | live qg_admin_notification_health active Admin/live session | read-only | worker execution/device delivery/GPS evidence |
| Map/location operations | Main + staged | existing store map + source Rider feedback | coordinate control existing | qg_admin_update_location and staged review RPCs | source audit | map feedback/alerts Admin UI integration |

## Current validation and remaining gates

- Full local npm on first checkpoint passed. Hosted47ead7ca npm passed; WebKit failure was pinned-baseline checkout, not evidence of a successful browser run. Subsequent exact-head results must be recorded before merge.
- Focused current checks: executed section19; actual operational DOM24; realtime50; Admin interface28; directory category22; network timeout26; source integrity94 scripts/81 references. These are isolated transport/DOM tests.
- Missing Admin feature controls above remain real source tasks, not automatically BLOCKED on a person. Staged backend/provider/physical-device release gates remain separate. Do not label the complete mission DONE from the first performance slice.

### Follow-up correctness fixes

- Governance inserts only the selected panel into DOM. Hidden account/approval/settings card trees are not mounted. Existing section selection lazily rebuilds the selected panel.
- Orders now uses100 rows/101 sentinel, deterministic ordering and server status filters. Shop/customer labels are fetched only for IDs present in the page and joined with Maps. Search explicitly applies to the current page.
- Directory paging exposed an old broadcast dependency on the full user cache. Broadcast now pages recipients200 at a time, bulk-inserts durable per-notification UUIDs with ignore-duplicates, and persists cursor only after acknowledgement. Lost replies retry the same IDs. No live broadcast has been sent for testing.
- Shop opening had a pre-existing block-scoped desired variable referenced after the successful write; fixed to preserve truthful post-commit UI.
- Hosted b0abefb9 full npm passed. WebKit then failed a request-count assertion because the test did not wait for the directory navigation/page completion before taking its snapshot; the successor explicitly awaits the real page state and retains the strict no-refetch assertion with diagnostic request output.

### Verified deployment and next source slice

PR457/source42fcd824 passed RC2076/run39348758285 completely. Main827b34cd has the same tree04584c3b. Pages1459/run39349127341 succeeded. Actual HTTPS200 bytes for HTML and all five changed JS assets match that main exactly. Browser login renders and loads versioned Admin scripts; no authenticated Admin session is available, so no live mutation acceptance is claimed.

The successful complete-document mobile WebKit run measured43→10 Supabase boot requests,3392→828ms ready and478→329ms visible shell using the same50ms/request localhost fixtures. This is not Production latency.

The successor separates Content and Pricing from Settings using existing banner/platform controls, introduces a Catalog hub and paged/server-filtered read-only Market Master screen, orders navigation into the requested18 groups, and expands mobile WebKit across every actual route and governance section. Market Master mutations still require a guarded/audited backend; this source does not grant new direct-table writes.

## 2026-10-20 Admin scoped operational readers checkpoint
- CURRENT BASE:9017567baff0b9fb325245578e3dbef5e7a0efea. PR458 sourceae0b438355b5684a466e742f4df575bfd2def25c passed RC39349869823; main CI39350311495 and Pages39350310925 SUCCESS. Full Admin mobile WebKit74 checks PASS. Latest controlled fixture25→10 requests /1933→762ms ready; prior baseline43 reflects legacy duplicate-boot timing variability, not Production latency.
- IMPLEMENTED: additive page-scoped shop overview overload (max200 IDs, invoker/RLS); bounded Dispatch projection (max100 each); notification outbox status aggregates. Private table grants unchanged. New private readers require active Admin and live session, wrappers authenticated only; no payloads/tokens or business mutations.
- TEST: full npm EXIT0; operational SQL25 and DOM24 PASS; whitespace PASS. SQL verifies anon/non-admin/suspended/session denial and limits. Production migration NOT APPLIED; exact-source hosted CI pending.
- REMAINING/BLOCKED: whole mission IN PROGRESS per audit matrix; Credit/Ride backend rollout, audited missing controls, further paging/abort work, authenticated and physical-device acceptance remain. NEXT: publish checkpoint, exact-source CI, rollback rehearsal before additive migration deployment, merge/Pages and byte verification.

## 2026-10-20 Admin readers deployed / Support successor
- DONE (PR459 slice): sourceccd9f421 passed RC2084/run39351062344 including full npm and mobile WebKit74. Maina1fb415b94a7ee4b55753c5f98199603ba8d4456 treefbdf1d5694894f044dabc3e96d2fc615fcc77bb2 equals tested source. Pages39351465709 SUCCESS. Controlled fixture43→10 requests; ready3427→754ms; shell435→254ms, not Production latency.
- Migration applied via connector version20261020010500; source CLI file20261020005115. Three public reader wrappers invoker; anon EXECUTE false; authenticated true with server role checks. Rollback rehearsal passed3 non-Admin denials; no business writes. Advisor warning counts unchanged; five new indexes appear as unused INFO immediately after creation (not evidence to drop them). Existing unrelated advisories remain.
- CURRENT BASE:a1fb415b. Support successor: inbox200/201 and per-shop conversation100/101 keyset pages; profile+thread reads parallel; Map grouping instead of repeated full filters; selecting shop only fetches that thread; actor/route fencing and text escaping. Dedicated visible/online60s fallback because live support table is not in Realtime publication; removed unrelated order-chat scope and duplicate shared Support loader.
- TEST: Support DOM18, section19, performance15, source95/82 PASS. Exact successor full suite/CI still required. REMAINING: refund backend atomic workflow (legacy client-only action unsafe), financial/staged backend gates and all unresolved matrix controls. NEXT: exact Support CI/merge/Pages, then remaining permission and performance source work. Whole mission IN PROGRESS; authenticated/physical acceptance unclaimed.

## 2026-10-20 Support deployment and stale-read checkpoint
- DONE (Support slice): PR460 source4982883c passed RC2087/run39351721764, full npm and Admin mobile WebKit74 checks plus Merchant/Map/Catalog. Support DOM22 PASS. Mainb4b3f9f6f15c4b4b178d4fcaa57510cecc5882ae; Pages39352035615 SUCCESS. No Production messages sent. Server keyset paging is bounded; existing per-shop index remains, a global recent-inbox index is a remaining scale optimization.
- CURRENT BASE:b4b3f9f6. Implemented navigation/session cancellation for outstanding Admin GET/HEAD reads, pending request coalescing beyond cache TTL, reuse within shared directory scope, fresh requests across scope changes even before hash event delivery. Mutation requests continue normally; read-only POST RPC cancellation is not claimed. Abort/timeout are distinguished. These changes retain role/RLS authority.
- TEST: actual section/read orchestration34, Support22, network interruption26 and source95/82 PASS; exact full npm/CI pending. Remaining source work: server-paged Rider review, global directory search, financial/refund atomic backend, additional safe operational controls in matrix, read-only POST cancellation and further aggregate/index optimization. Authenticated/physical acceptance remains unclaimed.

## 2026-10-20 Legacy refund action guard
- CURRENT BASE:3929a38cf61ddda9f26a12d2f0e7508d560386a5 (PR461 exact successor of mainb4b3f9f6). Live function inventory exposes only POS owner refund, not a generic Admin order refund. No provider-backed Admin workflow is deployed.
- IMPLEMENTED: financial requests remain visible; removed legacy client-only approve action and false success notification. Direct old function calls fail closed with a clear unavailable reason, including repeats/non-Admin. No money, payment, order, audit or notification mutation. Does not claim actual refund processing exists.
- TEST: financial failclosed8 PASS; full/exact successor CI pending. REMAINING/BLOCKED: authoritative provider-backed refund review/execution, role validation, atomic audit/idempotency and provider confirmation must precede enabling this control. Other unresolved source tasks remain in audit matrix; whole mission IN PROGRESS.

## 2026-10-20 Customer saved-location coverage checkpoint
- CURRENT BASE:4ae1c89bb5f90f980033384b612988d9cd27791f (PR462 successor). Verified actual Customer source stores one location in users.metadata plus local cache, and immutable per-order delivery snapshots; live schema has no separate address table. Corrected initial inventory's assumed owner address policies.
- IMPLEMENTED: existing guarded Users detail shows saved delivery address, safely escaped; reuses existing read-only location map with validated coordinates. No additional queries, address table, customer edit, order-location rewrite or new privilege. Existing user_view audit retained; clearly distinguishes current account location from the order snapshot.
- TEST: Customer detail DOM11 and source95/82 PASS; full/exact CI pending. PRIOR PR461 passed RC39352282187 (full npm/mobile WebKit74) and merged main791a25340f87f7d47d55ab04569b36e4b2f7f4de; Pages verification pending. Whole mission remains IN PROGRESS per matrix.

## 2026-10-20 Server directory search checkpoint
- CURRENT BASE:283aec9bc977f93e4822e7e3033a96b66192a90c (PR463 successor). Newest main at start02d27a67c87a565fbf75f663032f018ed4f6e581; no reset/revert/cherry-pick. PR461 source3929a38c RC39352282187 PASS and PR462 source4ae1c89b RC39352428793 PASS (full npm/mobile WebKit74 each), merged791a2534/02d27a67 respectively. Pages/live verification follows deployed head.
- IMPLEMENTED: server name/phone search across accounts, max200 rows/201 sentinel, existing page-ID profile/readiness queries retained. Unicode Thai marks preserved, reserved filter/wildcard punctuation removed before building filter. Search resets cursor/history and cancels stale context reads; context-keyed directory coalescing never substitutes an older query. Old replies cannot overwrite current query. Own Admin cache retained for session authority but excluded from result cards when outside returned page.
- TEST: actual directory query16, section34, category22 and source95/82 PASS; exact full suite/CI pending. REMAINING: shop-name-specific global search, Rider queue pagination, search scale indexes/aggregates, unresolved backend/feature matrix and physical/authenticated acceptance. Whole mission IN PROGRESS. NEXT: exact successor CI, merge/Pages and actual deployed asset verification.

- Directory successor follow-up: scoped Realtime invalidates GET cache as well as section TTL; a change during an existing fetch queues exactly one fresh load after completion, avoiding indefinitely cached pre-event data. Unrelated events retain cache. Section orchestration37 and directory16 PASS; exact successor CI pending.


## 2026-10-20 Rider review paging / shop-search successor
- BASE: PR465 merged to main `7ba59d04d556f2c1360eff2729f63a86ea7872ab` after RC run `39357745885` SUCCESS. Admin global directory now includes bounded current shop-name/phone search without full hydration.
- PRODUCTION BACKEND: migration `20261020021801 admin_rider_review_pagination` applied after BEGIN/ROLLBACK rehearsal. One centralized private reader pages identity/inspection/vehicle/document queues; legacy four queue RPCs delegate to it for compatibility. Public wrapper is SECURITY INVOKER; anon/PUBLIC EXECUTE denied, authenticated allowed; post-apply advisor has no finding for the new reader.
- WEB: Rider review requests 50 rows/page from `qg_admin_rider_review_page`; next/previous navigation and post-action refresh remain on the current page. Removed browser-side full-queue read followed by `.slice(0,200)`.
- TEST/GATE: source migration contract and Admin operations DOM regression added. Exact successor RC/Pages still required before this slice is DONE.
- REMAINING: search-scale indexes/aggregates, audit/notification paging and remaining safe matrix controls. Refund/provider financial execution, staged Credit/Ride cutover, and authenticated/physical-device acceptance remain separate release gates; do not bypass them.

- SCALE SEARCH follow-up: Production migration `20261020022140 admin_directory_search_trgm_indexes` applied after rollback rehearsal; existing `pg_trgm 1.6` reused. Four partial/full GIN trigram indexes now cover account name/phone and current shop name/phone contains-search. Production currently has 35 users / 10 current shops; indexes are capacity work, not a claim of current latency improvement. Repo regression locks all four index definitions. Search-scale text indexes are no longer a remaining item.


## 2026-10-20 Admin audit / notification paging checkpoint
- BASE: exact descendant of PR466 source `004743628cba7410b9d7b5769356749d90b25aa6`; PR466 RC #2123 SUCCESS and merged main `cd857a2eea894b791f4397ce253aea08f4bc61fb`.
- IMPLEMENTED: Audit Log and Admin's notification history now read 101-row sentinel pages and render at most 100 rows. Older pages use a deterministic `(created_at,id)` keyset cursor instead of growing OFFSET scans; previous-page history is kept only in-session. Cursor identity is part of the read scope so an older in-flight page cannot overwrite the active page. Page changes refresh only the active section; no full Admin hydrate.
- PRODUCTION DB: migration `20261020022520 admin_audit_notification_paging_indexes` applied after rollback rehearsal. Added `audit_logs(created_at desc,id desc)` and `notifications(user_id,created_at desc,id desc)` indexes matching page filters/order. No RLS/grant changes.
- TEST: section-loader regression now asserts 100/101 keyset queries, no OFFSET paging, older/newer cursor navigation, stable order, page metadata/functions, and both migration indexes. Exact successor full RC/mobile WebKit/Pages still required.
- REMAINING SOURCE: read-only POST/RPC cancellation and audited safe controls that already have authoritative backend. Provider-backed refund/credit/top-up/Ride cutover, worker/device delivery evidence and physical authenticated acceptance remain Release Gates, not bypass targets.


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


## 2026-10-20 Admin Rider Missions / Benefits / Insurance checkpoint
- BASE: PR #473 exact HEAD `4d7dc1c9983d9d16647439e511a4befe8b22b055` passed RC #2240 / run `39361877941` and merged to main `93837a8c99b2bb4318ddd1fb122265111b3946d2`.
- REUSED AUTHORITIES: Production already exposes authenticated, SECURITY INVOKER wrappers for Rider Missions, Benefits and Insurance. Their private implementations require a live session + active Admin. Historical migration mirrors from 2026-10-14 are restored exactly; no duplicate program subsystem is created.
- CONTROL: new `admin-rider-programs` workspace lists/creates/toggles Missions, lists/creates/toggles/deletes Benefits, and lists/adds/deletes verified Insurance records. Mission UI explicitly states that it does not pay bonuses or credit automatically.
- PERFORMANCE/LIFECYCLE: read-only RPCs use the merged cancellable Admin read layer. Mutation RPCs remain non-cancellable. Insurance Rider selection uses a bounded 200/201 account page plus page-ID profile lookup rather than full account hydration.
- AUDIT HARDENING: existing program tables had no audit trigger. Rollback rehearsal passed with synthetic insert/update/delete and redaction sentinels, then Production migration `20261020030951 admin_rider_program_audit` was applied. Three triggers are present and direct anon/authenticated/PUBLIC EXECUTE on the private trigger function is denied. Insurance policy number, coverage summary, support phone/URL and Benefit action URL are never copied into Audit Log metadata.
- TEST: dedicated DOM/source regression covers existing RPC names, read-vs-mutation separation, bounded Rider selector, non-Admin denial and redaction contract. Exact full RC/mobile WebKit remains required.
- RELEASE GATES UNCHANGED: provider-backed refund, Credit/Top-up activation, Ride cutover, worker/device-delivery evidence and authenticated physical-device acceptance remain blocked from bypass. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin Rider online-readiness checkpoint
- BASE: PR #474 exact source `ccb5d80a862f524aa8d0bc428abcfdeccf6fe985` passed RC #2260/run `39362964572`, merged to main `c1742d8aaa5d4bd7cccc300ae62a44497e080f3f`; Pages #1473 deployed that exact merge SHA successfully.
- VERIFIED SERVER RULES: Production online triggers block required/pending/rejected identity re-verification, required/pending/rejected vehicle inspection, a selected non-approved non-legacy vehicle, and expired national ID / driver licence / selected-vehicle PRB.
- IMPLEMENTED: the existing Admin Rider review screen now labels each visible review row as blocking or not blocking the base online gate and explains why. It reuses the existing paged `qg_admin_rider_review_page`; no extra request or new backend authority is introduced.
- DEVICE BOUNDARY: the Native readiness card also checks network, Firebase configuration, notifications, location permission/service, Google Maps and overlay return. Those are device-side and Admin cannot truthfully certify them from server state, so the Web screen says so instead of inventing a green status.
- SAFETY: no `set_rider_availability`, no metadata force-enable, no `qg_private` browser read, no Ride/Express/Laundry gate bypass.
- TEST: dedicated runtime regression covers all four server blocker categories, clear states, page-size reuse, no force-online symbol and non-Admin denial. Exact-head RC/mobile WebKit remains required before merge.


## 2026-10-20 Rider presence performance checkpoint
- BASE: merged PR #475 main `76ba873fdcccb324e45d98e50240658e062affff`; Pages #1474 deployed the exact merge SHA successfully.
- PRODUCTION AUTHORITY CHECK: `qg_record_rider_presence` is an AFTER INSERT/UPDATE OF metadata trigger on `public.rider_profiles`. It appends a presence row exactly when `metadata.online` changes and stores that same boolean. The current profile field is therefore sufficient for current online/offline state; event history remains history.
- BEFORE: Rider panel fetched all Rider users, all Rider profiles and up to 2,000 presence events, then replayed the event list client-side to find the latest state.
- AFTER CANDIDATE: 100+1 Rider users per page, one profile query restricted to the visible page IDs, no presence-history request, Prev/Next paging and actor/page stale-response fencing.
- NO BEHAVIOR BYPASS: user status + Rider profile status + current online boolean still determine the displayed state; no availability/dispatch mutation was added.
- TEST: `tests/admin-rider-presence-page.cjs` rejects any presence-history query or 2,000-row contract and exercises page navigation plus account race.
- Whole Admin mission remains IN PROGRESS; exact-head RC/mobile WebKit is required before merge.


## 2026-10-20 Scoped shop overview checkpoint
- BASE: merged PR #476 main `0e03fcd1a57a5e2b31177fbd5cefeffab0ce9285`; Pages #1475 deploys this main line.
- PRODUCTION EVIDENCE: two overloads exist. `queuego_admin_shop_overview(p_user_ids uuid[])` is SECURITY INVOKER, checks active Admin and caps cardinality at 200. Legacy `queuego_admin_shop_overview()` is SECURITY DEFINER, loops every shop, calls readiness per shop and counts orders per shop; Supabase Security Advisor flags authenticated execution of that legacy definer.
- SOURCE CHANGE: current Admin fast path no longer catches a missing scoped overload by calling the no-arg overload. It sends only the current directory page IDs and fails closed if the scoped contract is unavailable.
- CLEANUP: the unreachable no-arg Admin call inside the non-Admin full-hydrate branch is removed so current source has no direct no-arg browser call.
- TEST: directory regression requires `p_user_ids`, <=200 IDs, simulates PGRST202, and verifies exactly one scoped overview attempt with no unscoped retry.
- No Production function/grant change in this checkpoint; backend retirement of the legacy overload needs separate compatibility evidence.
- Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin Orders pagination checkpoint
- BASE: merged PR #477 main `d6caeebb5ccf0608707ea391be0035e148b04dd6`.
- BEFORE: `orders?...order=created_at.desc,id.desc&limit=101&offset=page*100`; deeper pages force OFFSET scanning.
- AFTER CANDIDATE: keyset cursor `created_at.lt(cursor.at) OR (created_at=cursor.at AND id.lt(cursor.id))`, still ordered `created_at desc,id desc`, 101-row sentinel / 100 visible rows.
- PREVIOUS PAGE: browser stores only cursor history, not order payloads; going back re-queries the authoritative page.
- FILTER: all / active / completed / cancelled remains server-filtered and resets cursor history. Search remains explicitly “ในหน้านี้” and exact visible-page shop/customer names remain bounded to page IDs.
- STALE LOADS: generation + Admin owner fencing prevents page/filter/account races.
- Production already has `idx_orders_created_at` and the order primary key; no DB mutation in this source-only checkpoint.
- Whole Admin mission remains IN PROGRESS; exact-head RC/mobile WebKit required before merge.


## 2026-10-20 Admin Support fallback-refresh checkpoint
- BASE: merged PR #478 main `0f84fe969b75f3029fad13577c2c4e9ec4b27be2`.
- BEFORE: the existing 60s Support fallback was route/visibility/online/Admin scoped, but could still run while an operator was reading an older keyset page or composing a reply.
- AFTER CANDIDATE: the same 60s fallback is additionally limited to newest inbox + newest selected thread + empty reply draft.
- This preserves the no-Realtime fallback without silently jumping history or refreshing during composition.
- Inbox/thread request limits and keyset paging are unchanged; no background full hydrate, new timer, DB/RLS/grant/schema change, or realtime publication change.
- Existing Support runtime regression now verifies old-page and draft suppression plus one bounded newest-idle refresh.
- Whole Admin mission remains IN PROGRESS; exact-head RC/mobile WebKit/Pages required before merge.

## 2026-10-20 Admin Support inbox-index candidate
- Stacked on PR #479 exact HEAD b232595212a3dcf817ee503f879fd781ff517e43.
- The global inbox query is shop_support_messages ORDER BY created_at DESC,id DESC LIMIT 201; Production currently has no index beginning with created_at.
- Current Production has only a tiny Support dataset, so EXPLAIN currently chooses Seq Scan + Sort at low cost. This checkpoint is future-scale alignment, not fabricated current-speed evidence.
- Live/source-aligned migration 20261020041534_admin_support_inbox_paging_index.sql adds only shop_support_messages_recent_idx(created_at DESC,id DESC). Thread index behavior is unchanged; no grants/RLS/data writes or Realtime publication changes.
- Rollback rehearsal succeeded without leaving the rehearsal index behind.
- Parent PR #479 is merged/Pages/post-merge green; Production migration 20261020041534 is applied and verified. Exact successor RC on the version-aligned source remains the merge gate.

- PRODUCTION APPLY VERIFIED: Supabase migration version 20261020041534 admin_support_inbox_paging_index is live; pg_indexes returns CREATE INDEX shop_support_messages_recent_idx ON public.shop_support_messages USING btree (created_at DESC, id DESC).
- This confirms the live index matches the source migration contract exactly; planner choice on the current tiny dataset is not used as a latency claim.


## 2026-10-20 Admin complaint ticket pagination candidate
- BASE: merged PR #480 main `a9a6a2271ea0c48bff79d890467e4c002025cf16`.
- Complaint modal previously read only the latest 200 `qg_support_tickets` rows and had no history navigation.
- Candidate UI uses `created_at DESC,id DESC`, 101-row sentinel / 100 visible rows, browser cursor history, and same-cursor refresh after `qg_admin_ticket_action`.
- Existing RLS remains authoritative: authenticated users can read own tickets; active Admin can read all. No policy/grant/state-machine change.
- Live/source-aligned migration `20261020042804_admin_ticket_paging_index.sql` installs `qg_support_tickets_recent_idx(created_at DESC,id DESC)`; existing status/user indexes remain unchanged.
- Production currently contains 0 ticket rows, so this work is future-scale and operator-history coverage, not fabricated present-day performance evidence.
- Dedicated runtime test rejects OFFSET/fixed-200 behavior and covers paging, mutation refresh and stale-account fencing.
- Production apply verified: Supabase migration `20261020042804 admin_ticket_paging_index` is live and `pg_indexes` matches the source contract.\n- UGC reports remain a separate bounded-300 RPC follow-up; this candidate does not modify them.


## 2026-10-20 Admin UGC moderation pagination candidate
- BASE: merged PR #481 main `c7d9373504b56eda3f3bdf842c43f997c4fa50d2`.
- Legacy reader `qg_admin_ugc_reports(p_status)` is active-Admin guarded but hard-limits 300 rows and has no keyset cursor.
- Candidate keeps `qg_ugc_reports` direct client access denied and adds a public SECURITY INVOKER wrapper over a private SECURITY DEFINER reader guarded by live session + active Admin.
- Private reader validates optional status, requires a complete cursor pair, caps `p_limit` at 100 and orders by `created_at DESC,id DESC`.
- Admin UI uses the existing cancellable read-only RPC layer. Existing moderation mutation RPC remains unchanged/non-cancellable, and successful action refreshes the same active page.
- Existing content snapshot escaping and strict jpeg/png/webp data-image allowlist remain unchanged.
- Live/source-aligned migration `20261020044510_admin_ugc_report_paging.sql` installs `qg_ugc_reports_recent_idx(created_at DESC,id DESC)`; existing status/reported-user indexes remain.
- Production currently has 0 UGC reports, so this is bounded-history/security-preserving capacity work rather than a fabricated speed claim.
- Rollback rehearsal on Production passed with a real active Admin/live session: page contract valid, incomplete cursor rejected with 22023, anon wrapper/private EXECUTE denied, authenticated public wrapper EXECUTE present, and rollback left no candidate functions/index.\n- Production apply verified: migration `20261020044510 admin_ugc_report_paging` is live; public wrapper is SECURITY INVOKER, private reader is SECURITY DEFINER, anon/PUBLIC EXECUTE are denied, authenticated EXECUTE is present, and the global recent-report index matches source.

## 2026-10-20 Admin GP payload checkpoint
- Base is merged PR #482 main b4284e3526923426e620500596d04cfae942978b.
- GP daily calculation still needs all qualifying orders for the selected day because the browser currently computes per-shop totals; this checkpoint does not invent a new financial aggregate RPC.
- The avoidable unbounded reads were shop_profiles and daily settlements. Candidate scopes both to shop IDs observed in the already-loaded daily orders, batching at <=200 IDs.
- Existing GP calculation and mutation authority are untouched. Cash remains on existing admin_cash_report and is not duplicated.
- Generation/owner/date fencing prevents stale async reads from painting after an Admin/date change.
- Dedicated runtime regression forces 201 shops, checks two bounded batches and verifies the same totals plus stale-response suppression.
- Whole Admin mission remains IN PROGRESS; exact-head RC/mobile WebKit/Pages required before merge.
