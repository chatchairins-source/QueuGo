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
| Approval / Users / Shops / Security | five PR445 directory reads but unbounded users/profiles; Users omitted from fast path |200-user keyset pages with201 sentinel; profile reads filtered to returned user IDs; shared directory cache | existing shop overview RPC still computes readiness internally for all shops; server-paged replacement/optimization needed |
| Rider status | three reads every5s even for hidden panel | no hidden polling; entry-scoped presence; scoped realtime/fallback | presence query2000 still needs a latest-per-rider server reader at scale |
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
| Customer addresses | Main Customer flow | incomplete | no address editor | owner address policies | not fully audited | operational read surface/justification |
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
| Online eligibility/availability | Main + staged | saved presence/status; review prerequisites | account/review controls, no forced bypass | server online gates | source audit | one operational eligibility explanation per service |
| Food/Market/Grocery capabilities | Main + staged | incomplete | incomplete | existing dispatcher eligibility | source-specific | service capability editor with authoritative guards |
| Ride/Express capabilities | Staged | job screens/review where available | no unsafe force-enable | existing staged driver/vehicle/licence guards | staged | Production controlled activation |
| Laundry platform Rider | Main optional flow | bounded Laundry jobs | no forced assignment | existing Laundry invitation/acceptance | existing events | operational platform option UI |
| Rider credit/top-up | Staged | Credit menu after deployment | existing reverse-fee flow only | queuego_admin_credit_accounts/reverse_credit | staged ledger | Production ledger/provider release gate |
| Orders state/rider assignment | Main | Orders/trace, new bounded Dispatch view | existing safe trace/actions only | existing dispatcher ownership | existing trace | manual reassignment policy/RPC not implemented |
| Targeted offers/timeout/retry | Main dispatcher | partial via order state | dispatcher remains authoritative | qg_rider_order_offers not publicly exposed | history exists | guarded operational queue/health reader |
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
| Market Master | Main | existing Market/Catalog entry incomplete in Admin | source-dependent | existing Market master APIs | source receipts/audit | dedicated full Admin master editor inventory |
| Category enable/images/master visibility | Main | existing catalog/banner tools | existing reviewed controls | existing catalog/storage RLS | source-specific | full category coverage/security audit |
| Home/category/service banners | Main | Settings existing managers | existing banner CRUD | existing settings/storage policies | not fully certified | independent Content page extraction |
| Notifications | Main | own latest100 + broadcast | existing role-targeted broadcast | existing notification policies | source-specific | delivery health and paging |
| Maintenance/feature flags | Main + staged | Settings | existing allowlisted toggles | existing platform rules | source-specific | full inventory/maintenance Admin controls |
| Audit logs | Main | actual audit_logs latest100 | read only | existing audit RLS | live | pagination/server search |
| Notification/job health | Partial | System explicitly reports saved state only | no fabricated worker controls | existing real counters | n/a | authoritative push/worker health reader |
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
