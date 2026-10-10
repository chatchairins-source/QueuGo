# QueueGo project memory

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


## Native Android migration (2026-10-08)

- Explicit Play Store direction: the owner rejected a WebView/website-wrapper app and requested real Android applications. Native Android is now the Play Store migration track; current web Production remains available while features are migrated.
- Backup before native work: `backup-pre-native-android-20261008-2103`. Working branch: `queuego-native-android-v1`. Do not replace `main` until native regression/device gates pass.
- Native stack: Kotlin + Jetpack Compose, compileSdk/targetSdk 36, minSdk 26. CI rejects WebView UI usage in `native-android/**`.
- Three native application IDs now build from one Gradle project: `com.queuego.customer`, `com.queuego.merchant`, `com.queuego.rider`.
- Customer and Merchant native shells share encrypted Android-Keystore session storage and the existing Supabase Production authentication / active-session RPCs. Role validation remains `customer` and `shop`; these shells are not yet feature-parity replacements for the web apps.
- Rider native is farther along: existing sequential server dispatch (`get_rider_delivery_pool` + `qg_get_my_rider_offer`), accept/decline, active-order states, arrival RPC, pickup/delivery photo proof, existing `qg-evidence` bucket, and existing idempotent rider action RPC are reused. Manual cash-confirmation screens are not reintroduced.
- Market multi-stop Rider native reuses `market_pickup_route_summary`, `market_claim`, `qg_market_pickup_with_photo`, market deliver action, and `qg_complete_market_with_photo`; no parallel order state machine was created.
- Android navigation return feature is now implemented in native Rider: foreground service + draggable Q overlay when draw-over-other-apps permission is granted, with notification return fallback. It is stopped when returning to QueueGo, losing the active job, completing delivery, session loss, or logout.
- Native CI builds Customer / Merchant / Rider debug APKs together and rejects WebView. Full QueueGo regression must run before native artifacts are accepted.
- Still required before Play Closed Beta release: migrate Customer/Merchant feature parity, native push/FCM integration for the new native project, production release signing/AAB workflow for this native project, approved QueueGo launcher assets in each module, physical Android testing of login/session/GPS/camera/file upload/overlay/notification/Maps lifecycle, real three-role E2E, and final Play Console declarations/reviewer access.


## Native parity audit resumed (2026-10-09)

- Owner requires Kotlin/Compose Customer/Merchant/Rider to preserve every Production web screen and flow; no WebView, mock, shell, placeholders or new UI interpretation. Work only on `queuego-native-android-v1`; do not merge main or alter web blueprint.
- Resume head: `c9a7c5b76aa9c425129d91313f2b203a5a053e7c`. Backup: `backup-native-before-parity-gate-20261009`. Production main inspected at `9026bf445650dbeeb0fd254582481a08a16108fc`.
- Public Home rendered in the browser. Merchant and Rider rendered sign-in pages; authenticated operational screens have NOT been visually inspected. Mobile screenshot comparison is NOT certified.
- Fixed Rider dock to wrap content with a maximum of 62% of available height instead of forcing every dock to occupy 62%. Native screenshot verification remains pending.
- Removed commit-message-triggered public APK release from native build workflow. Internal build artifacts are QA inputs, not Owner deliverables. Keep public APK publication disabled until visual and live functional parity are proven and P0/P1 are zero.
- `npm test` passes; native source integrity passes 88 checks. Neither certifies pixel match, GPS/camera, physical devices, true Realtime recovery or real Production deliveries.
- Audit found polling loops and no native Realtime socket implementation. Functional parity must include actual Production Realtime integration and lifecycle/recovery testing.
- Outstanding: authenticated screen inventory and measurements, all-page native parity repairs, professional three-icon preview/adaptive assets, screenshots side by side, live normal/market/laundry flows, physical lifecycle/notification tests. Do not mark the three apps complete based on README coverage or source-string assertions.

- Additional audit gap: Customer tracking currently renders Rider coordinate text instead of a native map; Merchant source has no native map/location picker. These missing web capabilities remain parity blockers, even when source integrity passes. Merchant secure sign-in was offered but browser remains on the sign-in page; no authenticated screen certification is claimed.

- Build verification: all three `assembleDebug` tasks passed locally with Gradle 9.6.0, JDK 17 and Android SDK 36 (126 tasks; BUILD SUCCESSFUL). These internal APKs are NOT approved for Owner delivery. Visual and Production functional gates remain unpassed.
- Customer normal tracking now stops its repeating context requests after a terminal order (`completed`, `cancelled`, `no_rider_available`); the final snapshot is fetched once before stopping. No backend/state transition changes.


## Customer native tracking map (2026-10-09)

- Backup: `backup-native-before-tracking-map-20261009` at `c7b41f5946dd872071ffe19ce9de651f843c90de`. Work stays on `queuego-native-android-v1`; web and database are unchanged.
- Added the native Longdo OpenGL tracking map using existing order pickup/delivery snapshot coordinates and Rider coordinates from `qg_customer_order_context`. Verified the four snapshot columns exist in Production via a read-only schema query.
- Native normal Customer tracking now shows shop/customer/Rider pins, excludes invalid/swapped/outside-Thailand coordinates using the web's existing bounds, hides Rider markers during server search, and removes the live map on terminal orders. Missing coordinates remain missing; no fabricated movement or map points.
- Map height follows the web CSS breakpoints: 145dp for compact-height screens, otherwise 160dp through 420dp width and 178dp above that. This is source-derived sizing, NOT a pixel-match certification.
- Native map pauses/resumes with Activity lifecycle, keeps manual map panning on marker refresh, releases pins on disposal, and offers retry after a bounded map initialization timeout.
- The verified Longdo AAR is shared by the three modules through `:shared`, preserving each launcher label in manifest merging. Rider continues to use its existing map/job flow.
- Added five actual JVM unit tests for terminal-order cached-location suppression, searching-state Rider suppression, missing coordinates, invalid coordinate boundaries and incomplete pairs. CI runs these before the three-app builds.
- Full `npm test` passes and native source integrity passes 88 checks. Build/output directories are excluded from source-integrity scanning so generated Gradle HTML does not masquerade as website source.
- Outstanding: authenticated screenshot comparison/real map tile rendering; Customer location/checkout maps; Merchant settings/location picker; Market/Laundry native tracking map parity; native Realtime, icons, live E2E/device gates. No Owner APK delivery is approved.

## Merchant native Realtime (2026-10-09)

- Backup: `backup-native-before-realtime-20261009` at `f8fd7efb4575de0bad5c9bf9714038e78d9af49d`. No Production web/database edits or main merge.
- Read-only Production publication inspection confirms `public.orders` is already in `supabase_realtime`; no publication/RLS changes are needed.
- Added native OkHttp WebSocket transport following Supabase Realtime protocol v1: user JWT channel authentication, `orders` subscription filtered by the signed-in shop ID, acknowledged subscription validation, Phoenix heartbeats, bounded join timeout, heartbeat recovery and capped reconnect delay. Payloads only invalidate; existing owned API reads remain the source of state. Credentials and row data are not logged.
- Merchant reconnect triggers a fresh order read. Realtime and the existing polling fallback share a mutex and the existing pending-order sound/selected-detail refresh behavior. Native socket runs only while Activity is STARTED; token rotation or shop changes cancel/rejoin the channel. No UI positions or order actions changed.
- Added two actual local WebSocket transport tests: authenticated/shop-filtered join with a fresh read signal after reconnect; rejected join and pre-join change must not signal successful subscription. These isolated test fixtures are not app/backend data. CI runs shared and Customer JVM tests before builds.
- Local verification: two transport tests and five Customer policy tests pass; all three debug builds pass; full npm test/source integrity passes. These do NOT certify physical background/offline recovery, Production Realtime/RLS isolation, notification delivery or completed deliveries.
- Merchant authenticated Production screens are still unavailable in the browser, so no guessed store-settings UI was added. Settings/location picker, Customer/Rider Realtime, icons, all-screen visual comparisons and normal/market/laundry live E2E remain outstanding. Public APK delivery remains blocked by the requested release gates.

## Rider owned Realtime and Merchant notification recovery (2026-10-09)

- Backup: `backup-native-before-rider-realtime-20261009` at `6e4c3ce774ea3184947a4739d42e95fbbfd24a2c`. Work remains on `queuego-native-android-v1`; no web, database, RPC or RLS changes.
- Re-read current Supabase protocol/changelog. Read-only Production checks confirmed existing `notifications.user_id` and `orders.rider_id` publication entries, notification ownership RLS, assigned-order Rider RLS and the targeted-offer SELECT policy. Current `get_rider_delivery_pool` and `qg_get_my_rider_offer` both return only the server-selected Rider's live offer; their existing behavior is preserved.
- Generalized the native transport to validate every acknowledged table/ownership filter. Only event IDs belonging to acknowledged subscriptions invalidate data; partial/rejected joins cannot report connected. No event row payload becomes app state.
- Rider subscribes to notifications filtered by QueueGo user ID and assigned orders filtered by Rider profile ID. Profile ID comes from the existing owned profile lookup. Each signal refreshes the existing authoritative rider snapshot/offer RPC flow. Snapshot polling and Realtime refresh share a mutex; token rotation restarts effects. Both stop at Activity background and resume with a fresh read. Existing summary/history/location cadence, map layout, offer actions and multi-stop API flow remain unchanged.
- Production Merchant SELECT RLS intentionally excludes some unassigned delivery states. Added own-user notifications alongside shop-filtered orders, with `get_my_shop_orders` remaining the source of visible Merchant orders. No broader subscriptions or weakened RLS.
- Shared tests now cover reconnect/authenticated Merchant filtering, rejected/pre-join events, Rider user/profile filter separation, accepted event-ID checks, partial acknowledgement rejection and Merchant own-notification filtering (six tests). Customer policy tests remain five.
- Verification: full npm regression, native source integrity (88 checks), source No WebView gate and all three debug builds pass. Eleven JVM tests pass. Local transport tests do not certify Production notification timing or physical Android lifecycle behavior.
- Outstanding: Customer Realtime, actual authenticated screen inventory/visual comparisons, store-settings/location picker, professional icons, native laundry Rider parity, normal/market/laundry live Production E2E and physical-device background/GPS/camera/notification gates. No Owner APK delivery or readiness certification.

## Customer owned Realtime and terminal context guard (2026-10-09)

- Backup: `backup-native-before-customer-realtime-20261009` at `182284f75c586df5f95157339e0853412964b340`. Work remains on native development branch; no Production web, schema, RPC, RLS or main changes.
- Read-only Production inspection confirmed existing publication/ownership columns for `orders.customer_id`, `market_orders.customer_id`, `laundry_orders.customer_id` and `notifications.user_id`.
- Customer now receives native Realtime invalidations for its own notifications and normal/market/laundry orders. Existing Customer APIs/RPCs still read all data; event row payloads are not applied. Refresh does not toggle the Home loading state, rebuild the cart or redirect screens.
- Realtime, notification fallback and order fallback share a refresh mutex. Socket and repeating fallback requests run only while Activity is STARTED, restart on token changes and read fresh data after foreground/reconnect.
- Normal detail context is guarded per detail visit: live orders may refresh, terminal orders attempt a final context once and suppress repeated location-context requests from later notifications. Cancellation restores the unfinished final attempt for resume. Detail item/context responses are applied only if that order is still open. Reopening a detail starts a new single-final-snapshot visit. Terminal map hiding remains intact.
- Added one Customer subscription ownership test and three terminal-context gate tests, including repeated notifications, all terminal states and interrupted final snapshot recovery. Shared JVM suite now has seven tests; Customer suite has eight.
- Verification: fifteen JVM tests, all three debug builds, full npm regression, native integrity (88 checks), source No WebView and whitespace checks pass. These isolated checks do NOT certify true Production Realtime timing, role isolation under real accounts or physical Android lifecycle recovery.
- Outstanding: authenticated operational screen inventory/mobile screenshots, native/web visual match repairs, Merchant store-settings/location picker, professional three-icon preview/adaptive assets, native laundry Rider parity and real normal/market/laundry three-role/device E2E. Keep Owner APK delivery blocked until visual/functional gates and P0/P1 are cleared.

## Native launcher family and preview (2026-10-09)

- Backup: `backup-native-before-launcher-family-20261009` at `076ead089402712ea94b9646ceabd19c92348415`. Production web artwork and main are unchanged.
- Device inventory: no attached Android device and no `/dev/kvm`; no mobile native/web screenshot comparison was performed or certified. Authenticated web operational screens remain inaccessible. Continued the independently authorized icon work instead of guessing screen layouts.
- Replaced native legacy glossy icon frames/external role badges with one source-generated vector family: shared red Q, white background, Customer fork/spoon, Merchant storefront and Rider wheel/flame. Red `#EF3340` is from Production Customer `--red`. No app-name text, emoji or detailed motorcycle in the icons.
- Rendered/reviewed side-by-side preview before integrating resources. Adjusted Merchant stroke after measuring heavier role coverage. Final coverage spread 8.44%; all three foregrounds have maximum radius 31.67dp within the 33dp adaptive safe circle. Preview includes small sizes and circular center crops. These are asset review metrics, not Owner approval or a physical-launcher certification.
- Added distinct adaptive foreground/background resources, updated icon/roundIcon references, exported five Android launcher density sizes per app and opaque Play Store 512×512 PNGs. Vector/SVG master generation, raster exports and preview are in `native-android/branding`.
- Added 61 launcher asset checks to native CI for actual PNG signatures/dimensions, removed old resources, manifest/adaptive layers, shared Q geometry and recorded safe-area/coverage metrics.
- First incremental build failed with duplicate cached dex/class metadata after resource changes/rebases. Clean rebuild succeeded for all three apps; 15 JVM tests pass. Full npm regression, native source integrity (88 checks), launcher asset checks and source No WebView gate pass.
- Outstanding: physical launcher checks, authenticated mobile/web/native visual comparisons and parity repairs, Merchant settings/location picker, native laundry Rider parity, live normal/market/laundry E2E and physical GPS/camera/background/notifications gates. No Owner APK delivery; P0/P1 zero and visual/functional parity are not certified.

## Customer category blueprint repairs (2026-10-09)

- Backup: `backup-native-before-category-parity-20261009` at `3f1b26a3b5800f39edc8834a2182aa6df8226cac`. Main fetched/read at `9026bf445650dbeeb0fd254582481a08a16108fc`; no web/main/database changes.
- Rendered and inspected Production Food, Drink (actual route `#cafe`) and Grocery. Recorded DOM measurements in `native-android/qa/category-blueprint-20261009.md`. Browser viewport was 1363×936, NOT mobile; no Native screenshot/device pixel match is certified.
- Repaired the three native category bodies from observed/source blueprint: 38dp arrow back button, original category headings/helper/count/empty text, 720:343 banner ratio, 20dp clipping, original gradient/padding/typography, and category cards with 72/78dp images at the web's <=420dp breakpoint. Existing Home card is unchanged and still requires visual review.
- Extracted the existing three inline WebP images from Production main without re-encoding. Native uses these exact originals when config lacks a valid image; disabled banners disappear, failed configured images retain gradient treatment. Corrected Drink settings lookup from `drink` to the web's canonical `cafe` key.
- Restored category/time metadata on category shop cards, selecting existing `public_open_time`/`public_close_time` columns (verified read-only in Production). No invented hours or catalog data. Removed unreachable duplicate Food/Drink/Grocery rendering from the old generic category renderer, retaining existing Shopping rendering separately.
- Added nine actual image-byte checks to CI and two JVM tests for category resource/content separation and configured-image fallback handling. Shared image helper gained optional corner radius/no-letter fallback parameters; other callers retain defaults.
- Production Grocery's server-provided shop opened into public Shop. Observed real product, share/favorite actions and folded customer reviews. These public Shop features and Guest-before-login flow are additional native parity gaps; no cart/order mutation or test data creation was performed.
- Cached/incremental Gradle builds failed with duplicate generated R/dex artifacts even after clean. A clean, fully executed build with `--no-build-cache --rerun-tasks` passed all three apps and 17 JVM tests. Native CI disables task build-cache reuse for unit/build steps. Full npm regression, native integrity (88 checks), launcher checks (61), category assets (9) and source No WebView pass.
- Outstanding: mobile/Native screenshots and all visual gates, global headers/navigation, Guest launch behavior, banner link navigation, public Shop feature/layout repairs, other pages, Merchant store settings/location picker, native Laundry Rider, live three-role E2E/device gates. Native shadows are Android approximations of CSS shadows, not proven pixel matches. No Owner APK delivery; P0/P1 zero is not certified.

## Native Customer Shop body milestone — 2026-10-09

- Backup `backup-native-before-shop-parity-20261009` at `e59101cc63f343d73f618558cb20bf6c52b12ef5`, created locally and on GitHub. Work stays on `queuego-native-android-v1`; Production main remains `9026bf445650dbeeb0fd254582481a08a16108fc`.
- Re-inspected actual rendered public Shop and expanded its reviews. DOM measurements and pending checks are recorded in `native-android/qa/shop-blueprint-20261009.md`. Desktop only; no Android/mobile screenshot comparison.
- Replaced old native Shop body with cover-only image, 18dp overlapping rounded store intro, original heading/category/address/pill structure, back/share controls, product row proportions and floating cart dock. Back returns to the originating Home/category/search screen.
- Connected shop/product favorites to the existing owner-filtered `qg_customer_favorites` table and customer reviews to existing `qg_public_shop_reviews`, preserving latest-review and folded independently scrollable remainder. Requests/state are keyed to actor/shop and favorite controls block concurrent taps. Android shares the original public Shop URL through its native chooser.
- Products that are unavailable now remain visible with “หมด”, matching the web; add is absent for unavailable products and disabled for closed stores. No invented menu categories: the Production loadMenu query currently omits category/menu_category, so only All is rendered.
- New back/plus vectors use web SVG paths; heart/share vectors avoid emoji or Canvas primitives. Precise glyph/shadow parity, global navigation/header, Guest launch, loading/error menu races, actual favorite write/session recovery, share sheet, live review retrieval and device screenshots remain pending. No Production user data, web UI, schema or RPC modified.
- Release gates remain blocked: no Owner APK until actual Visual/Functional Parity and P0/P1 zero. Full npm regression, Native integrity88, launcher61, category9 and No WebView pass. Final clean uncached build of Customer/Merchant/Rider and 17 JVM tests passed (142 tasks executed). Live device and Production functional checks remain pending.

## Native catalog and checkout recovery — 2026-10-09

- Backup `backup-native-before-catalog-recovery-20261009` at `763040f97545088d1f8e9a376d6a3e530d28274f`, local and GitHub. No main merge, Production web change or database mutation.
- Closed Customer Shop catalog races: one scoped loader marks real loading/error/empty separately, cancels previous requests and rejects stale success/error responses even if cancellation is ignored. Leaving Shop or changing actor discards old data; retry replaces a previous request in the same shop.
- Replaced per-tap checkout UUID generation with a durable owner-specific journal of the exact Production RPC body plus serialized submitted cart. Journal commit precedes transmission; unknown outcomes, cancellation, 401/403, timeout and throttling preserve the original ID/items/quote/address for retry and process restart. Concurrent taps are guarded synchronously and by an atomic coordinator.
- Successful replies require the same order ID and a real order number. Errors reconcile through an explicit owned orders SELECT before being shown. Foreground/session restoration polls only the saved request's receipt, without resubmitting an order; background stops this read. Acknowledged server success is not changed into an error by local cleanup failure. Pending cleanup and unchanged submitted cart removal are one SharedPreferences edit; later cart edits remain. Successful UI navigates to Tracking.
- Keyed the whole Customer authenticated subtree to QueueGo and Auth user IDs, disposing requests/state on account change. Cart retry remains available even when current cart/location was edited or lost, using the existing request rather than creating a new one.
- Read-only Production verification: cash order core uses a customer advisory lock and returns the existing owned order for the same p_order_id before fresh-order validation; orders_customer_select uses customer_id=get_my_user_id(). Existing RPC/state machine/RLS are unchanged.
- Added four asynchronous catalog-race tests and six checkout recovery tests covering lost reply, concurrent taps, process restart with edited cart, cancellation, failed journal write, post-commit cleanup failure, and definitive rejection after receipt lookup. These are isolated JVM tests, not Production order/device tests.
- Native menu loading/error treatment remains unproven by mobile screenshot; no Visual Parity/Functional Parity pass or P0/P1-zero certification. Physical storage/process/lifecycle and authenticated E2E still required. Final clean uncached three-app build passed (142 tasks executed), 27 JVM tests with zero failures/errors, full npm test, Native source88, launcher61, category9, whitespace and No WebView checks passed. No APK delivered.

## Native Customer navigation and Home banner — 2026-10-09

- Backup `backup-native-before-customer-navigation-20261009` at `dd9a5fc0f9aaab5eaf68f8ecfa06e7610fa88166`, local/GitHub. Work remains isolated to Native branch, with no Production web/backend changes.
- Re-inspected rendered Production Shop and Home. Measured ordinary nav49px and Home59px, distinct paddings/icon areas/label fonts; extracted exact web vector paths with stroke1.8 for all four nav icons. Desktop evidence only, recorded in `native-android/qa/customer-navigation-blueprint-20261009.md`.
- Customer bottom nav now remains present on Shop/category and other screens instead of disappearing outside four main pages. Separate49/59dp layouts, top-only border, fixed Cart label and quantity badge replace the previous generic58dp Canvas icon nav. Native system bottom insets are applied; device behavior still needs testing.
- Removed the invented red text-only Home fallback. Bundled byte-identical Production Home WebP (45722 bytes; SHA2561149f10b2608e9e183200d233676795dcb1200a065386c6b5cc6b6624c723972), preserving actual web photo behind remote loading/errors. Restored662:386 aspect and20dp radius; slide dots are inside the image at bottom9dp with the original active/inactive proportions and support selecting a slide.
- Extended exact asset comparison to Home; now12 checks. Home location/search/header/categories/cards, remote banner links, configurable timing, mobile/native screenshots and live functionality remain pending. Do not infer overall Home/Navigation parity or release readiness from source/build checks.

- Final validation: clean uncached Customer/Merchant/Rider build passed (142 tasks executed), 27 JVM tests zero failures/errors, full npm regression, Native integrity88, launcher61, image assets12, whitespace and source No WebView passed. Integrity search-navigation gate now checks the integrated Customer nav and real screen handler instead of the removed generic QgNavItem literal. No release APK delivered.


## Native Rider automatic sequential assignment + push transport — 2026-10-09

- Backups: `backup-native-before-rider-auto-offer-20261009`, `backup-native-before-rider-auto-navigation-20261009`, `backup-native-before-rider-fcm-20261009`.
- Production Supabase already owns sequential dispatch. Verified `qg_dispatch_rider_offers()`, `qg_get_my_rider_offer()`, guarded accept through `qg_rider_action_once`, explicit decline through `qg_rider_decline_offer`, the order trigger on SEARCHING_RIDER, and the active 5-second dispatch cron. Offer lease is 30 seconds. The selected Rider alone owns the live offer; decline/expiry hands off to the next eligible Rider. No shared-pool race was introduced.
- Native Rider now renders only `qg_get_my_rider_offer`; the old pool fallback is removed. New server offers alert once in foreground, use the real server expiry countdown, refresh immediately on expiry, and release immediately when the Rider goes offline or logs out.
- Accept remains an explicit Rider action; “automatic assignment” means Server automatically chooses and offers one Rider at a time, not unsafe auto-accept. Accept refreshes authoritative state then opens navigation to the shop. Normal pickup proof refreshes state then opens navigation to the customer. Existing market multi-stop start-delivery navigation remains intact.
- Added pure-native Firebase Messaging transport wired to existing QueueGo `/functions/v1/queuego-push` `subscribe-native` / `unsubscribe-native` backend, stable UUID device id, Android high-importance `queuego_orders` notification channel, FCM service, token refresh sync, notification permission request, and logout unsubscribe. Firebase Android BoM 35.0.0 and google-services plugin 4.5.0 are used.
- Repository secret `QG_FIREBASE_GOOGLE_SERVICES_JSON_B64` is currently absent in the native pilot workflow environment. QA builds therefore conditionally compile without applying google-services; the FCM code is present but physical background-push delivery is NOT certified until a real Firebase config is supplied. The secret/config is never committed.
- CI repair removed a duplicate Merchant market-category declaration and fixed nullable Merchant module-cycle state exposed by the full three-app build.
- Final native pilot run `37884000618` PASS: npm regression, native source integrity, launcher/category asset checks, JVM tests, all three native APK debug builds, No-WebView gate and artifact upload all succeeded.

## Rider Realtime CI repair continuation — 2026-10-09

- Continued from authoritative branch HEAD ac489d0cd83ceb4277307cb676a6f6acdf697ccb. Preserved unfinished local Customer Home edits in git stash before fast-forwarding; did not overlay them onto the newer Native implementation.
- Backup backup-native-before-rider-realtime-tests-20261009 at ac489d0, local and GitHub.
- Verified Rider subscriptions already correctly scope notifications to QueueGo user ID and orders/Laundry jobs/invites/preferences to Rider profile ID. Production code unchanged.
- Updated outdated shared tests to require all five subscriptions, assert every table/filter, and verify all five accepted event IDs invalidate while an unknown ID does not. Existing partial-ack rejection coverage is retained.
- Initial run37886328274 verified failed at shared unit tests, with build/WebView/artifact steps skipped. Current local environment no longer has the previous Gradle/Android SDK installation; remote CI will provide actual JVM/build evidence. Source integrity180 and whitespace checks pass. Firebase configuration/background device push and real visual/Production E2E remain unverified; no Pilot/Play Store readiness claim.

- Actual CI run37888000000 at 5b562d8 PASS: regression, integrity180, assets, shared/customer/rider JVM tests, three APK builds, No WebView and artifact upload. No remaining compile error was reported in that run.
- Found and repaired a Production Realtime publication omission: laundry_rider_preferences was absent, while jobs/invites were present. Backup backup-native-before-laundry-preferences-realtime-20261009 at 5b562d8; applied server migration20261009052132 and checked all three published with preferences RLS preserved. Committed matching additive SQL; no policy/grant/data/RPC/dispatch change.
- Four-tab audit against main9026bf4 verifies names/basic dock dimensions and selected-offer Home takeover only; icon/label rendered parity remains pending. See native-android/qa/rider-ci-recovery-20261009.md.
- Actual CI log confirms Firebase config secret absent. Background FCM and device/reconnect/30s/Laundry/Production E2E plus screenshot parity remain blocked by missing configuration/device/authenticated sessions. No APK Pilot/Play readiness claimed.

## Rider bottom-navigation vector parity — 2026-10-09

- Latest baseline c5720c0 CI37888454332 PASS through all three APK builds and No WebView. Backup backup-native-before-rider-nav-vectors-20261009 local/GitHub before editing.
- Inspected live rendered Production Rider: unauthenticated login page, so working-screen visual verification remains unavailable. No synthetic login or mock operational state introduced.
- Replaced four generic navigation Canvas icons with Native vector drawable paths from main9026bf4 QTICON home/chat/earn/user, preserving 24-unit viewBox, 19dp size, 1.9 stroke and round caps/joins. User circle is the equivalent two SVG arcs. Labels now8sp with2dp gap; removed invented2dp gaps between grid columns. Native font scaling remains supported.
- Source integrity180 and whitespace pass. Full build is delegated to real CI, not assumed from source. Native screenshot parity, responsive dock height/shadow/badge and authenticated/device/E2E/FCM gates remain pending.

- Follow-up local full npm test PASS. Actual CI37889041290 passed JVM test step and reached three-app build; completion is not yet assumed.
- Additional source parity gap found: web qgUpdateBadge/readRiderChat implements unseen incoming-message badge and foreground message notice, while current Native bottom nav/inbox has no unread count. Must implement against real scoped chat messages (including post-completion 30-minute rooms), with per-user/order seen state and lifecycle guards; do not fabricate a count. Current vector change does not close this gap.

- Final actual CI37889041290 at b815baafe9173cb801e1a27b5aa28b8af21e57f2 PASS: regression, integrity, assets, shared/customer/rider tests, all three APK builds, No WebView and artifact upload. No device visual/background-push/live E2E certification inferred.

## Rider unread-message Native parity — 2026-10-09

- Backup backup-native-before-rider-message-badge-20261009 at874fa1f local/GitHub. Followed current main9026bf4 readRiderChat/qgUpdateBadge/watchCompletedChat; no backend or web changes.
- Added RLS-scoped reads of existing orders/customer ownership, deliveries and order_chat_messages, bounded to existing active/history IDs (100 rooms/500 messages as web). Active-room first read is a baseline; new latest counterpart messages increment once, self messages do not, opening chat clears active count.
- Completed-room count includes only the actual order customer, after delivered_at, before30-minute deadline and after the user's persisted inbox-seen timestamp. Opening messages updates that timestamp. Badge caps display at9+, using web red/white/8sp styling; foreground active-message notice follows web dark card and3800ms lifetime.
- Tracker/count scoped to user+session, coroutine to current token+STARTED lifecycle; cancellation propagates and stale active-order reads are discarded. Failed reads preserve previous count, retry5s active/15s idle. Added JVM tests for baseline/duplicate/self/open-room behavior, customer/seen/deadline boundaries, changed room/account.
- Source integrity180/whitespace PASS. Await actual CI JVM/build gate; do not claim visual/live RLS/reconnect/device/background notification certification. Existing native inbox and badge polling can issue separate reads; completed alert toast/notification behavior and screenshot parity still need verification.

- Actual final CI37889890946 at2fea8cdffab6835cde0b2977304d2985f4273f02 PASS: full regression, integrity, assets, JVM tests including new badge cases, three native APK builds, No WebView and artifact upload. Production data/FCM/device/visual parity remain untested; no Owner APK delivery.

## Rider proof preview and fixed actions — 2026-10-09

- Backup backup-native-before-rider-proof-preview-20261009 at ef2b7be local/GitHub. Compared main9026bf4 pickup/delivery qg-proof-single and fixed camera/confirm buttons. Previously Native showed only a success text with no actual camera preview; actions scrolled with content.
- Native now decodes the actual camera URI off the UI thread, bounds image memory to1280px and honors EXIF rotation/mirroring. Real decode/loading/error states; failed preview requires retaking instead of enabling confirmation. Camera file remains original for existing evidence upload/RPC.
- Verification header is fixed with exact web back-vector, centered title/order number; items scroll with92dp footer clearance. Camera or confirmation is fixed54dp at bottom12dp/10dp side margins/radius18 and web#f04455. Actual preview uses web responsive108dp short-screen or160–215dp tall-screen height, Crop/radius15 and overlaid38dp retake action. Native Back is blocked while committing. No cash-confirm/OTP flow added; original pickup/market/completion actions preserved.
- Item/market rows retained. Full shop/customer/note/contact/image density and native screenshots still need work; this checkpoint does not certify full verification-screen visual parity. Source integrity180/whitespace pass; actual build/CI pending.

- Follow-up in the same proof work: retained product imageUrl now renders actual existing images at46dp/42dp short-screen, web11dp radius. Restored horizontal image/name/description/quantity/price rows, count, separators and actual shopCash total (only when present). No invented product or monetary fallback.

- Re-fetched main: still9026bf445650dbeeb0fd254582481a08a16108fc. Actual final CI37890715086 at15573e8e068ad85295bc125a673bbd2c0b82b7e2 PASS: regression, source integrity/assets, shared/customer/rider JVM tests, three native APK builds, No WebView and artifact upload. Still no physical camera/EXIF/retake, screenshot comparison, background FCM or real Production E2E certification.

## Rider verification real details/contact parity — 2026-10-09

- Backup backup-native-before-rider-proof-details-20261009 at5fd4a3d local/GitHub. Followed current main9026bf4 pickup meta and delivery customer/contact/address/note sections.
- Added authenticated RLS-scoped reads of existing order shop_id/customer_id/note, shop_profiles shop_name/public_logo and users name/phone, only by IDs from that order. Item/details hydration is parallel and a failed read keeps the existing job screen with an actionable error; no new policy/grant/backend.
- Pickup shows actual40dp shop logo/name, QT order and customer rows with original jobs/user vectors. Delivery shows actual customer/name,44dp phone/chat controls, address/location vector and optional customer note before items; pickup note remains after items. Exact phone/location/jobs vectors converted from Production QTICON, stroke1.9/round joins.
- Phone uses ACTION_DIAL (no direct call/permission); chat closes the proof page as web does and opens existing RiderChatScreen for the same job. Cleared details on close/market point entry; no stale prior-order metadata. Existing proof upload/atomic action RPCs unchanged.
- Notes remove only lines beginning __QT_ORDER_STATUS__= as web; added JVM coverage for hidden status lines and preserving customer text. Source integrity180/whitespace pass; await actual CI. Physical call/chat/proof UX, live user RLS and screenshots remain uncertified.

- Actual final CI37891622343 at80cc73cd12b515ed7545e1453a782670558d5f46 PASS through regression, integrity/assets, JVM tests including note cases, all three APK builds, No WebView and artifact upload.
- Production catalog-only RLS audit found a real blocker: users SELECT policies permit own auth user, active approved shop users and active admin, but do NOT permit an assigned Rider to read the order customer. Orders has orders_rider_select for assigned Rider; shop_profiles permits approved active shops. Therefore protected customer name/phone GET returns no row under current policies; the wired UI cannot certify actual customer contact parity. No private user records queried and no RLS/grant/function changed.
- Existing qg_customer_order_context(uuid) strictly requires an active Customer and its owned order; it cannot be reused by Rider. Catalog search found no customer/phone view and inspected get_rider_delivery_pool/qg_get_my_rider_offer: neither supplies customer contact. Do not work around this with admin/service-role credentials, relaxed users RLS or invented data. Must resolve an authorized scoped contact reader within Owner backend constraints before calling Rider contact parity complete; physical call/chat/live JWT checks remain unpassed.

## Scoped Rider order contact reader — 2026-10-09

- Backup backup-native-before-rider-contact-reader-20261009 atd57259d local/GitHub. Additive reader resolves missing assigned-Rider customer contact access without changing existing users/orders/profile policies or grants, ordering/dispatch/RPC state-machine logic or Production web UI.
- Used Supabase skill/current function docs and changelog. CLI2.120.0 generated migration20261009061432; applied via Production MCP and aligned repository filename to server history version20261009061634_rider_order_contact.sql.
- Public qg_rider_order_contact(uuid) is SECURITY INVOKER; dedicated qg_private.rider_order_contact is a narrow SECURITY DEFINER lookup with empty search_path and fully qualified tables. PUBLIC/anon EXECUTE revoked, only authenticated allowed; runtime checks auth.uid(), live auth.sessions row belonging to uid and not_after, active database Rider/user/profile, assigned order ownership and permitted actual status. Completed contacts expire30minutes after delivered_at, missing/future delivery proof fails closed. Only existing active customer's name/phone returned, no bulk/private profile fields.
- Native verificationDetails uses that RPC, removing the blocked direct customer users read. Existing shop/order/items reads, contact UI and proof mutation RPCs preserved. No demo Production rows/accounts, no service-role client credential.
- Isolated PGlite security regression22 checks PASS: ownership/other role/direct private entry/session mismatch/revocation/expiry/suspended/deleted/status/deadline; users direct read still blocked. Added to npm test. Production catalog checks: policies hash before/after d2335be977bdd286cdb967e77c4b19af identical, users ACL unchanged, anon_execute false, authenticated_execute true, public wrapper_definer false. Production unauthenticated execution denied. Security advisors checked before/after (baseline findings retained; no new reader finding).
- Positive real JWT/device contact read, physical phone/chat and Production E2E remain unverified. This resolves reader availability/authorization design, not live contact or visual certification. Native CI pending.

- Production REST anonymous call reached qg_rider_order_contact and was denied HTTP401/code42501 (schema cache exposes the RPC; no private data returned). Security advisors comparison excluding observation timestamps: zero new groups/findings, baseline6 INFO policyless backups/9 anonymous definer/134 authenticated definer/1 password-protection warning unchanged.
- Actual final CI37892846686 atc1cf00c165450445e994ad72868279fedcc0ad26 PASS: npm regression including new22 SQL security checks, source integrity/assets, JVM tests, all three APK builds, No WebView and artifacts. Positive live Rider JWT/contact read, phone/chat/camera/device/visual/FCM/Production E2E still required; no Pilot readiness claim.

## Rider Inbox and chat recovery — 2026-10-09

- Backup backup-native-before-rider-chat-recovery-20261009 atf95c256 local/GitHub. Compared actual main9026bf4 qg-inbox CSS/render and riderChatDeadline/open rules; Production web unchanged.
- Inbox now follows web27sp title,14dp sides/18dp top/78dp bottom,720dp width cap,13dp row padding,18dp radius/1dp border,13sp title/9sp preview/4dp gap and8dp row gaps. Removed invented subtitle/arrow/empty card; real loading/empty/error and retry retained. Actual authenticated order/message/delivery reads unchanged.
- Inbox/chat polls only at STARTED, refresh immediately on return, rethrow cancellation and reject cancelled read responses before committing UI. Inbox state scoped to user/session; chat composition/scopes additionally isolated by order. No fake messages or data.
- Chat checks actual order status/delivery timestamp, closes cancelled/expired room and enforces web30minute post-completion deadline (delivery timestamp preferred, existing updated_at fallback). Deadline timer resumes with lifecycle; send rechecks live access before existing idempotent send. No mutation/state-machine/backend changes. Added JVM tests for exact deadline boundary, authoritative timestamp precedence/fallback, malformed/missing completed timestamp, active and cancelled rooms.
- Source integrity180 and whitespace PASS. Full CI pending; native screenshots, real room/background/reconnect/device behavior and Production E2E remain uncertified.

- Reconnect follow-up separates transient chat read error from action notices and clears it after a successful authorized refresh. Actual final CI37894042162 atc216a7d27137606df8badf348bd5d38753dead22 PASS: full regression, integrity/assets, shared/customer/rider JVM tests including chat-window cases, three APK builds, No WebView and artifact upload. No device/visual/FCM/Production E2E certification or APK delivery.

## Rider chat actual images and confirmed delivery — 2026-10-09

- Backup backup-native-before-rider-chat-images-20261009 at9785300 local/GitHub. Source main9026bf4 uses __IMG__ data URI, file5MB cap and compressRiderImage1000/.7. Read current Supabase changelog/docs; catalog confirms existing message primary key and no text-length check. No Production mutation or RLS/backend change.
- Native Photo Picker selects actual image, validates type/5MB before decoding, bounds decode memory, honors EXIF orientation and emits JPEG1000px/70% using the same protocol. Incoming image messages render actual data URI/HTTPS content with bounded reads/decoding and loading/error states, not raw base64. Lazy list avoids decoding every image in a long room at once. No fabricated image.
- Sending preserves existing user/order/RLS path and uses scoped confirmation ID/sender. Successful POST representation confirms send immediately; response interruption checks the same ID before showing failure. Ambiguous retry reuses ID scoped to room/user/session, removes it only after confirmation. Cancellation propagates. Post-send refresh failure is a read error, never a false send failure; input is cleared only after actual confirmation. No automatic resending or backend replacement.
- Added JVM cases for interrupted response without duplicate insert, authoritative response without extra read, absent confirmation/retained retry ID, cancellation and separate text/image validation. Native source integrity180/whitespace PASS. Actual CI pending. Camera/gallery/image rendering and send/reconnect require device/real JWT checks; full chat header/safety/quick-message visual parity remains pending. No APK/Pilot readiness claim.

## Rider saved quick messages — 2026-10-09

- Backup backup-native-before-rider-quick-messages-20261009 at6004c6e local/GitHub. Followed main9026bf4 riderQuickMessages/quickMessagesHTML/editor: five exact Production default templates; user-configured max5 and100character messages; selecting fills composer and does not send automatically. These are existing user-selectable text templates, not fake backend/chat records.
- Added native shelf and editor, add/remove/empty/disabled-at5 states; long press opens editor. Persistence uses app-private Rider preferences keyed by actual user ID. Original send/moderation/expiry/ownership paths unchanged. No Production data change. Added JVM boundary cases for sixth-message rejection, remove/replace, trim/100character limit and blank rejection. Native source integrity180/whitespace PASS; runtime persistence/keyboard/layout/screenshots still unverified. CI pending.

## Rider chat header, Back and safety controls — 2026-10-09

- Backup backup-native-before-rider-chat-safety-20261009 atd82900b local/GitHub. Followed Production main9026bf4 qg-chat-head, safety/report overlays and actual community-guidelines URL. Header now64dp,44dp controls/12dp sides/8dp vertical, centered15sp actual customer name and8sp QT label, original QT back vector, web ellipsis safety button, white/f8f7f8 background and post-completion notice. Removed invented secondary title and inline safety button row. Passed existing Scaffold inset modifier into chat and added IME handling/Android Back, blocking Back during mutation as proof page already does.
- Customer name uses existing scoped qg_rider_order_contact, never direct private users access. Safety opens report/block/community rules; block now requires the existing web confirmation. Report is a native modal with all six actual reasons and optional1000character details, sent through unchanged qg_report_chat with selected reason. Existing moderation/terms/block/unblock and deadlines retained; guidelines also available before terms acceptance. No backend changes, no fake contact/message.
- Source integrity180/whitespace PASS. Actual CI pending. Authenticated rendered web/native screenshots, device dialog/keyboard/back/contact flow and Production E2E remain required; this is source/layout/function restoration, not certified visual parity.

- Combined media/quick-message CI37896921027 atd82900b42b6f695814f850725eaf30cf27113300 PASS: full regression, integrity/assets, JVM tests including delivery/outbox/quick-message cases, all three APK builds, No WebView and artifacts. Earlier image-only CI37896622575 was cancelled when superseded; do not count it as a completed pass. Header/safety follow-up still awaits its own CI.

- Follow-up before final gate: terms acceptance/block/unblock now share confirmed-action refresh recovery with sending. Once the existing mutation RPC confirms, a later read failure is displayed as read failure instead of incorrectly claiming the mutation failed. Original RPC calls, moderation and cancellation retained.

- Actual final combined CI37897608875 at597bbaffdbab6ce94d5594e9d3e165b267c94602 PASS: full QueueGo regression, source integrity180/assets, shared/customer/rider JVM tests, three native APK builds, No WebView and artifacts. Superseded header-only run37897476146 was cancelled, not a pass. Re-fetched main still9026bf445650dbeeb0fd254582481a08a16108fc.
- Continuing release blockers: no local Android SDK/emulator/adb or physical device available; authenticated rendered chat/other role pages and real JWT end-to-end flow not accessible in this work session. Rider Production Firebase config remains absent in CI; QA builds do not prove background FCM. No Owner APK/Pilot/Play Store readiness claim. Pending visual work includes actual chat composer/bubble/quick-editor comparison (source-backed header restoration is not screenshot evidence), remaining role-screen comparisons and actual customer→merchant→targeted Rider→proof→completion/Market/Laundry lifecycle tests.

## Rider chat composer and small-screen forms — 2026-10-09

- Backup backup-native-before-rider-chat-composer-20261009 at6b5a538 local/GitHub. Followed main9026bf4 qg-chat-compose grid44px/1fr/48px,7px gap,46px input,14px radius,10px horizontal/9px vertical padding, white background/top border and exact web send glyph. Removed floating Material input label/extra height. Native photo text remains accessible in the same44dp slot pending authenticated screenshot comparison; no invented camera icon.
- Keyboard Send and button now use one existing busy/expiry/confirmed-delivery handler. Editing while a send is in flight remains possible; original guarded clear only removes the sent unchanged text. Report textarea is four lines as web; long quick-message editor content scrolls within native dialog on small screens. No backend/Production web changes.
- Source integrity/whitespace checks pending; full CI and keyboard/small-screen/native-vs-rendered-web screenshots remain required. No visual certification or APK delivery.

## Customer chat lifecycle and shared confirmed delivery — 2026-10-09

- Backup backup-native-before-customer-chat-recovery-20261009 at5302cb3 local/GitHub. Existing Customer chat polled in background and could show send failure after a confirmed insert when refresh failed.
- Moved already-tested Rider confirmation/outbox algorithm into shared NativeChatDelivery; Rider thin aliases preserve existing callers/tests, Customer uses same implementation. Both use actual order_chat_messages primary key and sender-scoped confirmation reads, retaining ID on ambiguous retry and consuming authoritative POST rows. No backend/RLS/state-machine changes or fake data.
- Customer room/scopes keyed by actual user/session/order; foreground-only polling with cancellation rejection and immediate resume refresh, atomically applied moderation/messages and cleared read error. Deadline uses existing live parent order updates/completedAt/updatedAt rules, closes at expiry/cancellation and prevents expired sends; Android Back restores parent order and blocks during mutation. Confirmed send/terms/block/unblock refresh failures are read errors. Typing during send is preserved unless text still equals confirmed payload.
- Existing Rider delivery regression exercises shared algorithm; added Customer exact terminal-window boundary and missing-data cases. Source/JVM/full CI pending; actual Production JWT/background/reconnect/device/visual checks not certified.

## Merchant Support lifecycle/read-state recovery — 2026-10-09

- Backup backup-native-before-merchant-support-recovery-20261009 at6e5e117 local/GitHub. Native Support used background5second polling and initial loading/error could display a false empty conversation.
- Scoped room/coroutines by actual Merchant user/session, token-keyed STARTED-only polling/immediate foreground refresh, cancellation propagation and guard before applying read results. Added real initial loading state, suppressed false empty state on read error, preserved previous messages on failure and clears errors on successful reads. Android Back restores caller and is blocked during mutation; input edits made while sending are preserved. Removed implementation-only Production subtitle in favor of actual web QueueGo Support label.
- Existing shop_support_messages filtered shop_user_id and original send path untouched. No schema/RLS/backend or Production web mutation. Support slips/visual layout and actual request idempotency still need parity work; no assumption about table ID type. Source integrity/whitespace and full CI pending; device/account switch/reconnect not certified.

## Merchant support GP slip and confirmed retry — 2026-10-09

- Backup backup-native-before-merchant-support-slip-20261009 at314dd27 local/GitHub. Production catalog-only check confirmed shop_support_messages.id UUID, slip_path text and RLS enabled; gp-slips remains private. No private records read and no DB grant/policy/schema changed. Followed actual web qgmOpenGpSlip and current Supabase signed-URL guidance.
- Reads actual slip_path with existing shop-scoped support messages and exposes exact ดูสลิป GP control only when present. Existing private Storage signing endpoint with current authenticated JWT and expiresIn300 returns only same-project private gp-slips signed path; launches ACTION_VIEW for real image/PDF, no WebView/public-bucket workaround. Cancellation checked before external viewer launch.
- Support sends now reuse shared already-tested confirmation/outbox algorithm and actual UUID primary key, scoped by message ID/shop user/sender user. Confirmed response clears only unchanged input; ambiguous retry retains same ID. Existing table/RLS/message notification behavior preserved, no manual financial confirmation or new backend.
- Source/CI pending. Real Merchant JWT signing/viewer/ownership denial and physical image/PDF behavior remain unverified; no slip signed or opened in this work session.

## Combined recovery gate and Customer actual chat images — 2026-10-09

- Actual combined CI37900208088 atcc8484acd5fb25c1e9fb1aeef91e21f093019275 PASS: full regression, source/assets, shared/customer/rider JVM tests (Customer deadline and shared delivery), three APK builds, No WebView/artifacts. Superseded intermediate runs are not passes.
- Backup backup-native-before-customer-chat-images-20261009 atcc8484a local/GitHub. Production customer-features.js uses __IMG__, JPEG/PNG/WebP5MB, JPEG1000px/.7 compression, explicit file selection followed by send, and latest100 messages reversed for display.
- Moved tested bounded image renderer/EXIF/compression and payload validation to shared helpers; Rider thin delegates preserve behavior. Customer now renders actual incoming images, uses lazy rows, reads latest100 rather than oldest100, and selects actual file then sends with existing shared confirmed ID path. Form uses original ส่งข้อความ / ตรวจผลข้อความเดิม label; photo overrides text as web and resets only after confirmed send. No fake image/data, WebView, backend or RLS change. Source/CI pending; physical image selection/send/retry and visual parity remain uncertified.

- Actual final CI37901191100 atfc74e85fcb5fafe130986f422419ebfce2f5e3b0 PASS: full QueueGo regression, source integrity180/assets, shared/customer/rider JVM tests, Customer/Merchant/Rider APK builds, No WebView and artifacts. Local final npm test also PASS. Customer photo selection guards unavailable filename metadata; supported picker types JPG/PNG/WebP.
- No authenticated rendered-web/native screenshot or actual Production send/slip/E2E/device/FCM claim. Source and build evidence do not certify visual or runtime parity. Production web and DB/RLS unchanged throughout this batch; catalog-only checks verified existing support UUID/private GP bucket policies.
- Next concrete parity gaps found: Customer web persists pending chat payload/ID across navigation/restart in localStorage; Native shared outbox currently retains ID only within room lifetime (must add scoped durable recovery, cleanup and test before claiming that parity). Rider message reader still takes first100 ascending while Production web reads full available history; needs safe history/late-message parity without losing image-memory bounds. Customer report dialog/controls and remaining role pages still require actual rendered comparison. Runtime release blockers remain absent Android device/emulator/authenticated Production test session and missing CI Rider Firebase config; no Owner APK delivery.

## Rider chat history cutoff — 2026-10-09

- Backup backup-native-before-rider-chat-history-20261009 atf097be3 local/GitHub. Actual main Rider loadChatMessages requests ascending order history without a client limit; Native incorrectly requested only the first100, hiding later replies. Removed that client cutoff to use the same Production server-capped history as web. Existing authenticated order filter, RLS, foreground refresh, lazy image rows and bounded image decoding remain intact. This does not promise unbounded history beyond the server cap.
- Local regression/source/build CI pending. No Production data/schema/web change. Customer durable pending payload recovery is still pending; no restart-idempotency, visual/device/E2E/FCM certification or APK delivery claim.

## Customer durable pending chat — 2026-10-09

- Backup backup-native-before-customer-chat-durable-20261009 at5d39821 local/GitHub. Followed customer-features.js pending localStorage flow: persist actual UUID/payload before POST, recover by actual user/order, existing pending wins over new input, retry only via explicit original button, clear on confirmation/permanent4xx except408/409/429 and closed room. New text selected while resolving recovered payload is preserved.
- App-private noBackupFilesDir storage uses hashed scope paths, bounded validated payload, sync/atomic rename and IO dispatcher. Corrupt or unwritable storage fails closed before network; no automatic resend or new backend. Account deletion clears only the actual deleted user’s local pending directory after confirmed deletion; another signed-in account’s pending payload is preserved. Existing auth/RLS/confirmation reads remain unchanged.
- Added JVM persistence/reopen/text and image-protocol/scoped isolation/corruption/write-failure/permanent-vs-transient cases. Native integrity180 and whitespace PASS; full CI pending. Restart/process death/photo/account deletion behavior still needs actual device validation. No visual/Production E2E/FCM certification or Owner APK delivery.

- Actual final durable/history CI37903097078 at77fea21ff27f6a4ec331f53f37a8720ea7273152 PASS: full regression, integrity/assets, shared/customer/rider JVM tests including persisted text/image-protocol/scopes/corruption/write failure/deleted-user isolation, three native builds and No WebView/artifacts. Earlier superseded runs are not final gate evidence. npm regression/source integrity also PASS locally. Device process-death/photo/runtime/visual/FCM/Production E2E remain uncertified.

## Customer report dialog source parity — 2026-10-09

- Backup backup-native-before-customer-chat-report-20261009 at77fea21 local/GitHub. Actual customer-features.js opens report dialog with six reason choices, harassment default, optional1000character details/four rows and cancel/send controls. Native previously used an inline form and always sent other. Restored native dialog, all exact labels/reason values, default reset and bounded scroll/details; sends selected reason through unchanged qg_report_chat and validates allowed reasons. Busy gates prevent double send/dismiss; cancellation propagates and errors keep report open for retry.
- No new backend/RLS/data or Production web changes. Source/CI pending; authenticated rendered-dialog comparison and actual report/moderation/device keyboard flow still required. No visual parity, E2E or APK release claim.

- Actual final report/recovery/history CI37903819443 atcb39964215997a481e07d0014d9a1d19e83f7032 PASS: Full QueueGo regression, integrity180/assets, shared/customer/rider JVM tests, all three native APK builds, No WebView and artifacts. Source/whitespace also PASS locally. Final repository worktree clean before this result-only memory update.
- Read-only Rider push/offer follow-up: existing logout disables subscription and revokes session; offer countdown uses actual offerExpiresAt. These source paths do not establish physical background FCM, account-switch race, timely30second expiry, Laundry exclusivity or reconnect behavior. CI still lacks QG_FIREBASE_GOOGLE_SERVICES_JSON_B64, no authenticated device/Production E2E session exists here. No APK delivered or Pilot/Play readiness claim.
- Pending release work: rendered mobile Production/native side-by-side for required Customer/Merchant/Rider pages including chat/report; actual Customer→Merchant→targeted Rider→pickup/photo→delivery/photo→complete plus Market/Laundry and session/offline/notifications. Source-backed restored dialogs/composers still require visual checks. Navigation-return overlay remains deferred per AGENTS until an explicit APK request. Current backend/Production web/main unchanged.

## Customer chat safety controls — 2026-10-09

- Resumed clean45c39ce and verified remote working branch agrees; main remains9026bf4. Backup backup-native-before-customer-chat-safety-20261009 local/GitHub. Actual customer-features.js has report/block/guidelines wrapping controls, explicit block confirmation and guidelines link before accepting UGC terms. Native missed guidelines and blocked immediately.
- Restored exact report-user label, wrapping safety controls, external ACTION_VIEW to existing community-guidelines page before terms and in room, and native block confirmation with exact existing web message/cancel/retry/busy states. Same qg_block_chat_counterpart RPC; confirmed block followed by failed read remains read failure. No automatic terms acceptance or payment confirmation/backend change.
- Corrected mutual-block moderation precedence: when blockedByMe and blockedMe are both true, Customer retains own unblock control as web; counterpart block alone never offers unblock. Added existing web explanation that blocking does not cancel delivery.
- Opened actual Production Customer Home and inspected rendered image/loading→loaded state (desktop viewport; unauthenticated real empty-shop state). No authenticated order chat rendered comparison available; no mobile/native screenshot match claimed. Local source/regression/CI pending. Device/Production E2E/FCM release gates remain required; no APK delivery or main/Production web/DB changes.

## Rider-first rendered visual release gate — 2026-10-09

- Latest Owner instruction changes priority to actual mobile web/native side-by-side, Rider Home/Offer/Active/Pickup/Delivery first; then Customer/Merchant and real normal/Market/Laundry E2E/device FCM. VoIP deferred. No new feature/source-only visual certification. Floating Q remains explicitly required by Owner.
- Fetched and fast-forwarded a368f7e→aa22b1830f122b495ff4b6e9a781435b7e8df6cc, retaining newer Customer cart/tracking and Merchant item-count work; verified94b297e is ancestor. CI37917468824 success at aa22b18. Backup backup-native-before-rider-visual-evidence-20261009 local/GitHub.
- Authenticated actual Production Rider through secure browserAuth and captured real Longdo Home at1363×936/DPR1, desktop only. Detailed evidence/gate matrix in native-android/qa/rider-visual-gate-20261009.md. All five required visual pairs FAIL due missing matching mobile/native captures, not a claimed measured UI defect. No fabricated order/offer/GPS.
- Runtime checks: no adb/emulator/SDK executable or connected device; /dev/kvm absent; official SDK repo reachable (not network outage). Browser API has no documented mobile viewport setter; one UI DevTools shortcut left viewport unchanged. Android test runtime with actual Rider session/order states and supported mobile browser viewport is needed; do not extract credentials/JWTs or emulate data to bypass. Existing icon-family preview inspected without redesign or physical-device/Owner approval claim.
- No Native source/Production web/DB changes in evidence batch; no release/FCM/P0-P1 clearance/Owner APK claim. Continue from HEAD, preserving all work.

- Actual evidence-batch CI37922547941 at3a13109d8bd1e76f1bbf14f7583c6f2aa7cbc796 PASS: full regression, source191/assets, JVM tests, Customer/Merchant/Rider builds, No WebView and artifact upload. Local npm test/source191/launcher61/whitespace also PASS. This does not change the Rider visual gate FAIL; matched mobile/native screenshots and physical-device/E2E evidence remain missing. Saved authenticated Rider tab for continuation; no credentials or browser JWT extracted.

## Rider real Android runtime preparation — 2026-10-09

- Re-fetched clean9d87b234654b3c7b3d4235aaeda0098432d80953; remote agrees. Backup backup-native-before-rider-runtime-evidence-20261009 local/GitHub. Installed official checksum-verified SDK/adb/emulator/API30 image and private JDK17/Gradle9.6 instead of treating absent local tools as permanent.
- Initial stale generated outputs caused duplicate Rider dex R classes and three CustomerHomeBlueprintTest NoSuchMethodErrors (that class is absent from current source). Cleaned generated outputs only; cache-disabled/rerun148-task build PASS for all three apps and63 JVM tests (shared13/customer31/rider19). npm regression/source191/launcher61/category12/No WebView/whitespace PASS. No app/test/Production source or backend edits.
- No local KVM; genuine Pixel4 API30 software boot exhausted45 checks, then18 read-only diagnostic checks. ADB became online and framework startup progressed but boot complete never reached1; no app install/launch/screenshot was claimed. Detailed versions/fingerprints/constraints in native-android/qa/rider-runtime-checkpoint-20261009.md. Never publish emulator RPC credentials or full private state files as evidence.
- Added portable official-SDK accelerated CI capture after original gates: fresh Rider login only, real process/activity/UI/PNG and commit/APK/viewport metadata, crash diagnostics/internal artifact. Runner-only KVM access, no security disabling, no credential entry/mock/backend mutation. New CI pending. This step cannot pass Home visual/E2E/physical FCM; secure Native Rider session, matching mobile browser and actual jobs still needed. All release constraints remain; no Owner APK.


## Actual Rider Android launch and CI recovery — 2026-10-09

- Preserved fetched9d87b23 and Owner checkpoint94b297e; backup backup-native-before-rider-runtime-evidence-20261009 remains local/GitHub. First accelerated CI37929722268 at292df52 passed original regression/assets/JVM/three-native-build/No WebView gates but failed before Android boot. Retrieved its actual artifact11615598142: Unknown AVD name due different default Android user-home/AVD registry resolution. No application crash was inferred.
- Fix986534696b8970d2a125fb695699ef001e7f426e shares explicit run-scoped ANDROID_AVD_HOME across official avdmanager/emulator and asserts the registry index before launch. Actual local avdmanager-create + emulator-list verification PASS. Local npm regression/source191/launcher61/category12/whitespace PASS; no Kotlin/application/test/Production web/DB/RPC/RLS/main change.
- Actual full CI37932248613 at9865346 PASS, including genuine API30 accelerated Android boot, fresh actual Rider APK install/cold launch, resumed activity/process/accessibility, no FATAL EXCEPTION and empty crash buffer. Retrieved artifact11616627950 and verified ZIP SHA256 d22be3449bb915663dca1469a0288d8f5515212f1421a5791158c7f41e22ecaa. Inspected unmodified1080x2280/440dpi screenshot: real Thai fresh-session login with branding/fields/disabled action, no credentials entered. APK SHA256 ad43817f0ca07b500f3fe0ad0b85039e1f75877669885abf51cd8a182fcff2d3; PNG SHA2562999f0951c11308e8fadad7901bd01c73e8bc8cb1ee383b7954bff2c97aa33fc.
- Evidence commit fe7b9e264f1621511f5c5b2367b9694b0d8a4cdc saves byte-identical screenshot/capture metadata plus updated runtime/visual gate records under native-android/qa. Final actual CI37933275263 atfe7b9e2 PASS: full regression, integrity/assets, shared/customer/rider JVM, Customer/Merchant/Rider native builds, Reject WebView and second genuine Android launch capture. This memory-only result record does not modify tested application/runtime sources.
- Rider Home/Offer/Active/Pickup/Delivery visual gate remains FAIL for incomplete matching authenticated mobile Web/Native pairs. Existing1363x936 Production desktop Home cannot substitute for a mobile pair; fresh-session Native login cannot substitute for authenticated Home. No browser credentials/JWT exported, mock state or fabricated order/GPS inserted. Need supported matching mobile Production browser viewport and permitted actual Native Rider session/real job states before pixel comparisons.
- Actual CI still warns Firebase config secret is absent. No physical Android test device/session is available here; physical background FCM/offline/session/role isolation and real normal/Market/Laundry Production E2E remain uncertified. No APK/RC/Pilot/Play readiness or P0/P1=0 claim. VoIP deferred; next work remains Rider Home visual pair, followed by remaining Rider screens, Customer, Merchant and real E2E.


## Native Rider registration, pending routing and INSERT authorization — 2026-10-09

- Re-fetched actual remote be46db2dd1ff35497ea81985eabebd3b4a5f059f into a new workspace; no prior local registration files were present. Remote backup backup-native-before-registration-security-20261009 preserves that HEAD. Actual baseline CI37947192680 completed successfully at that exact SHA.
- Production inspection confirmed existing users privilege triggers and Rider UPDATE/vehicle guards. Added guard_rider_profile_insert_approval: authenticated INSERT requires owned Rider identity and profile approval matching authoritative users status (active legacy riders can recreate missing profiles; unapproved accounts remain pending). Existing UPDATE/vehicle guards, RLS, order RPC/state and Production Web unchanged. Real-account INSERT probes under authenticated role passed with transaction rollback; no lasting QA row/account/order was created. Eleven isolated PGlite cases pass for roles/ownership/status/admin/internal grants.
- Recreated Native four-step Rider registration from current rider/index.html: personal/profile image, vehicle/service area, six mandatory documents, explicit truth/Beta agreements; Kotlin/Compose and existing Auth/users/rider_profiles contract. Removed external-browser signup routing. Images are bounded to8MB, compressed JPEG1400/78 with EXIF handling; document PDFs remain allowed. Drafts encrypted with existing Keystore key in noBackupFilesDir; passwords/consent not persisted. A checkpoint after actual sign-in supports retry after profile-write failure; returning pending login can resume a matching draft when the profile is absent. No automatic signup replay. Pending account route mounts the dedicated web-based pending screen, so Home/map/GPS/job engine is not mounted for unapproved accounts.
- Rider session recovery preserves encrypted session on timeout/offline/429/5xx; cold restore stays behind the existing web connection-retry presentation until validated. Definitive401/revoked/replaced/invalid-role sessions require login. Added JVM recovery and registration validation cases. These are implementation/source facts, not visual/E2E/device proof.
- New source/build CI pending. Required matched authenticated mobile Web/Native visual pairs, real normal/Market/Laundry E2E, physical notifications/lifecycle, Firebase config and release signing still need actual evidence. Do not call this Play-ready or P0/P1=0. Owner requested new APKs; proceed with latest source build, never substitute c018750 artifacts.

- Follow-up live users transaction probe confirms owned non-admin role/status/auth_user_id escalation does not change stored values; rollback completed. No pending public.users Rider exists at this inspection, so pending-account INSERT denial is established by isolated PGlite tests, not represented as a live pending-user probe. Active Rider/Customer live INSERT cases were checked. SECURITY DEFINER grant audit confirms anonymous execution denied for inspected admin/claim/dispatch endpoints; get_my_role filters suspended/deleted and inactive Admins. Advisory warnings still include intentional public catalog/table-session APIs, authenticated guarded RPCs, and disabled leaked-password protection; this is not a blanket security PASS.
- CI37950826463 at8f046cf passed native JVM tests and reached all-three APK build. Extended real accelerated capture to phone/small-phone/tablet for each of the three actual apps plus actual Rider registration step-one navigation; no credentials, mock job/state or submitted account. Python syntax checked; runtime matrix pending new final CI. Physical Samsung hardware and authenticated visual/E2E cannot be certified by emulator login captures.

- Actual CI37950826463 at8f046cfb5765e1f3aa28cf6f7a45491e751b240c finished SUCCESS: full regression, source/assets, JVM registration/session tests, three native APKs, Reject WebView and genuine Rider cold launch. Follow-up snapshot adds the three-role viewport matrix and closes post-sign-in/draft exceptions leaving busy locked; final follow-up CI is required before APK delivery.


## Three-role Android launch matrix and first rendered registration correction — 2026-10-10

- Continued from unchanged remote15ad386537430913bf0496a53af3cad9243bd68e. CI38000408379 SUCCESS includes full regression/JVM/source/assets/three APKs/Reject WebView and actual accelerated phone/small-phone/tablet fresh-session launch for all roles (9 captures), plus actual Rider signup navigation (3 captures); no credentials entered or accounts/orders created. Artifact11649245750 APKs and11649560861 launch evidence were downloaded and matched each of9 metadata SHA256 values to the actual APK bytes. Official AGP9.4.0 apksig verifies all3 signatures for API26–36. They are DEBUG test builds, not Release/Play-ready.
- Fetched main without merging; served Production Customer/Merchant/Rider HTML bytes match working source. Actual rendered Rider signup exposed an extra nested profile-upload border/label and oversized file picker absent in web. Corrected profile upload to the original unboxed centred face flow, required section label,42dp/12sp file picker and9sp step numbers. This is a source-derived correction; final new APK/Android matrix must rerun.
- Actual Merchant fresh-session screenshot confirms its generic shared native login still differs substantially from Production Merchant login; Customer shares that generic auth host. These known presentation/function-parity gaps are not hidden by launch PASS. Full visual gate remains FAIL, not merely unmeasured. Authenticated normal/Market/Laundry E2E, physical Samsung/lifecycle/notifications, Firebase config and release signing remain required. New Owner APK milestone may only be described as test APKs; never claim UI100%, P0/P1=0, Full Gate, Release AAB or Play readiness from these login captures.


## Customer/Merchant session recovery — 2026-10-10

- Found shared AuthHost cleared encrypted credentials on all validation/heartbeat errors, including offline, timeout, 429 and 5xx. Replaced this with authoritative-only invalidation (401, confirmed session replacement, absent account, role mismatch, suspended/deleted account, explicit invalid refresh token codes). Cold recovery fails closed and offers retry without deleting credentials; warm recovery retries the heartbeat. Cancellation propagates and login resets busy in finally.
- Rotated refresh tokens are saved before subsequent authorization reads/heartbeat so transient downstream errors cannot discard the rotated credential. This does not grant offline account approval: cold app access still requires server validation.
- Added shared JVM regression cases for transient vs definitive errors. No account/order writes or Production UI changes. Actual authenticated offline/reconnect and expired-session device tests remain unproven.
- Customer/Merchant generic authentication UI remains a known Production blueprint mismatch, and all authenticated flow/physical-device/notification visual gates remain open. Latest APK build must include this follow-up; do not deliver previous artifacts as final.

- Follow-up review: malformed HTTP 200 session/account payloads must not look like confirmed revocation or a deleted account. Validation now checks actual Boolean/array shapes and treats unexpected shapes as recoverable errors. Added actual API response regression cases.

- Compared Production Customer login contract in index.html: it requires users.status=active. Native Customer login/restore now enforces the same rule, including pending accounts; Merchant pending onboarding remains allowed. Regression covers pending Customer rejection.


## Owner-requested direct APK sharing — 2026-10-10

- Owner explicitly requested externally shareable direct APK links, rather than chat-scoped links or sign-in-only Actions artifact downloads. CI38002405309 at68cf202 passed; those test APKs were verified and delivered, but full visual/E2E/FCM/Release signing gates remain open.
- Added a narrowly opt-in CI publisher: only successful pushes whose commit message includes [share-test-apks] can publish a clearly labelled GitHub prerelease. It rebuilds and tests all3 apps from the new HEAD, completes actual Android launch matrix first, verifies the branch has not advanced, and attaches those exact APKs plus matrix/checksums. Ordinary subsequent commits do not publish automatically.
- This explicit Owner sharing request supersedes the previous internal-artifacts-only comment for these DEBUG test APKs. No Production UI/backend/orders or Native app screens changed. No Full Gate, UI100, P0/P1=0, signed Release AAB or Play readiness claim. Publication and direct-download verification pending the new CI run.


## Rider exits immediately after login — 2026-10-10

- Owner reports latest published Rider exits after login. Re-fetched remote213289d without resets. Inspected the verified Longdo0.1.8 binary: MapGLSurfaceView constructor directly calls android/support/v4/view/GestureDetectorCompat; delivered APK defines only androidx/core/view/GestureDetectorCompat. The active Home map is mounted after login, unlike prior login-only CI captures.
- Enable AndroidX binary transformation for the existing verified native Longdo AAR (no WebView/SDK or UI substitution), bump Rider test version to5/0.4.1. Add a non-exported DEBUG-only component harness mounting the real RiderLongdoMap without accounts/orders. It is absent in release builds and has no navigation entry in ordinary app UI.
- CI now builds an untransformed baseline for exact runtime crash reproduction, then the corrected APKs and actual instrumented map creation/background/foreground/activity recreation. Publication requires this regression plus existing three-role launch matrix. Runtime proof/new APK publication pending; no authenticated E2E or physical device PASS claimed. Existing Full Gate/UI100/FCM/Release limitations remain open.

- CI38005370777 reproduced the exact native Longdo constructor NoClassDefFoundError in the untransformed baseline. Corrected APK instrumentation returned OK(1 test), including actual map creation, background/foreground and recreation. The final log gate rejected retained baseline crash-buffer entries (timestamp before corrected instrumentation); no distinct second corrected crash was observed. Clear all Android log buffers between intentional negative baseline and corrected run, retain the full before/after evidence, and rerun the entire latest-HEAD gate before publication.

- CI38006171269 again reports OK(1 test) for the corrected map but Android retains the baseline PID3483 crash in logcat despite all-buffer clear. Avoid associating one process's deliberate negative-test crash with another: corrected instrumentation emits its actual completed PID, and the strict fatal-exception check now uses Android logcat --pid for that process. A missing completion marker or any crash in the corrected process still fails; baseline reproduction remains required and preserved separately. Rerun/publication pending.


## Final three-app Mission resumed — 2026-10-10

- Owner now explicitly prohibits incomplete APK/AAB/source delivery, including test APK sharing. This supersedes earlier requests to share DEBUG APKs. All three apps must meet the full Production Blueprint, real backend/E2E, voice calling, security and release evidence requirements before final delivery.
- Fetched actual remote native branch HEAD `54c9ea77b39a52b94f2f20589e581ae8e12e8070`, preserving newer work after `be46db2`. Backup `backup-native-before-final-mission-20261010-54c9ea7` created locally and through GitHub.
- Removed the opt-in `[share-test-apks]` GitHub prerelease publisher from native CI and reduced workflow contents permission to read. CI builds/captures remain internal; build success cannot publish incomplete apps. Existing historical releases were not deleted.
- Shared Customer/Merchant auth now rejects duplicate submissions synchronously before launching a second sign-in/session registration. Existing backend authentication and session recovery stay authoritative.
- Known Customer/Merchant generic login UI and missing registration parity remain open. Native voice calling is absent in inspected Kotlin/Gradle source. Full authenticated normal/Market/Laundry E2E, matched visual inventory, background push, physical lifecycle, full Production security audit, signed AAB and Play Console are UNVERIFIED. No P0/P1=0, Native complete or Release claim.
- Current Mission explicitly includes Android floating Q and voice calls; earlier deferred-VoIP priority is superseded. Continue implementation from actual remote HEAD; no WebView, fake data, replacement state machine or manual cash confirmation.


## Native Customer authentication restoration — 2026-10-10

- Continued from real `f4ef345` with successful CI38009233543; remote backup `backup-native-before-customer-auth-20261010-f4ef345`. Customer generic auth screen is replaced by source-derived login/register card: exact Customer labels, phone/email selector, conditional email field, name/phone, password confirmation and location requirement. Inputs use native Compose with keyboard traversal/submit and scrolling; passwords are not saved to instance state.
- Registration uses existing `/auth/v1/signup` metadata (fixed customer role, name, phone and real latitude/longitude), then existing sign-in, authoritative users role/active check and session claim. Password validation mirrors queuego-password-policy.js. No users/order/DB schema or Production web changes.
- Fresh Android OS location is requested only on validated registration submission. Permission denial, disabled providers and15-second timeout remain visible errors. Location listeners are removed on completion/cancellation; no default or fabricated coordinates.
- Signup followed by failed login routes back to login. Lost signup replies are explicitly ambiguous, not success; saved account-identity checkpoint recovers through sign-in instead of repeating signup. No password is persisted. Registration navigation/phone-email/back captures are added to actual emulator CI without submitting accounts or credentials. JVM HTTP tests cover payload, validation, active/role checks and no automatic replay. Compilation/runtime evidence pending the new CI; source191 and Python syntax/whitespace checks PASS.
- This is authentication implementation work, not full Customer completion. Public guest Home/search/cart/order-navigation parity and full header/bottom-nav behavior are still open; matched Production/Native mobile visual evidence, actual GPS/signup E2E and real sessions remain UNVERIFIED. Merchant generic auth/registration remains next. Do not deliver artifacts or declare full Native/Release PASS.


## Latest native compiler recovery and isolated Printer verification — 2026-10-10

- Re-fetched all remote refs; Native HEAD had advanced from owner checkpoint 8e86e65 to 4f597e7, while Printer was dd93494 and Production main was 9026bf4. Kept every new commit. Remote backups: backup-native-build-fix-20261010-4f597e7 and backup-printer-before-build-20261010-dd93494.
- Opened real failed job 114103787916 of CI38015191524. Fixed invalid explicit Compose weight imports, nullable QR snapshot access, and MerchantPosScreen onBack argument binding. Native commit 74b790f CI38022000575 completed SUCCESS: full regression, source/assets, shared/Customer/Rider JVM tests, all three APKs, Reject WebView and actual accelerated Android launch matrix/Longdo crash regression. This is DEBUG build and scoped emulator evidence, never full authenticated E2E or Release evidence.
- Printer branch incorporates the current Native fixes and runs the same CI independently; it must not be merged until its latest source passes. Initial Printer compilation at730afcc passed all three APKs and Reject WebView. Follow-up ca683ba exposed a parser edit accidentally supplying deniedPermissions to PosTable; corrected the compiler-reported location. Latest Printer unit/build/runtime verification remains pending this final follow-up.
- Printer now serializes queued automatic jobs instead of discarding them while busy, checks persisted completion within its lock, rejects manual double taps synchronously, separates kitchen batches, supports explicit reprint, and releases busy state on failure/cancellation. Before sending shop data, reads a fresh authenticated Production snapshot and verifies owned shop/bill eligibility. HTTPS redirects are rejected and width payload matches Production. A lost Bridge acknowledgement is ambiguous: no automatic replay; merchant checks printer before explicit reprint. Physical Bridge/printer success, persisted process-recreation behavior and exactly-once physical output are NOT certified.
- Added real JVM tests for concurrent same/different print jobs, Bridge failure, cancellation, explicit reprint/next batch, ticket batch/note/table data, cash/change/discount, refunded bills, HTTPS URL rejection, and role/explicit-denial/inactive permissions. New tests must pass in latest CI before merge.
- Read Production pos_allowed definition: explicit false overrides role defaults and inactive staff are denied. Native now preserves explicit denials and fails closed for absent/inactive staff; definitive access failures remove stale POS controls. Initial-load failure exits loading into retry. New-bill/payment/QR commit acknowledgements are separated from subsequent refresh failure and busy state clears in finally.
- Read-only Production audit: all76 literal Native RPC names exist; nine inspected POS/order/catalog/payment tables have RLS enabled; inspected POS APIs deny anon execution, and request-key table client policy denies all. Function definitions confirm owned-shop checks, new-bill advisory-lock deduplication and same-payment retry. These facts do not constitute complete cross-role RLS/session/E2E audit. No Production schema/data writes or migrations performed.
- Known remaining POS protection gap: existing-bill add/reduce uses delta pos_edit_bill without a request-key wrapper; ambiguous retry can duplicate a delta. Must resolve safely against the real schema/RPC before claiming idempotency/P1=0. No blanket POS parity PASS.
- Actual Merchant Android fresh-login evidence was inspected, and actual Production Merchant login rendered in browser. Native still lacks the Production input vectors/placeholder geometry and uses different positioning/control styles. Desktop web evidence is not a matched mobile pixel comparison. Visual gate remains open.
- Owner repeated continue: no APK/AAB/source/example/partial output until full three-app Gate. Authenticated normal/Market/Laundry/POS E2E, matched UI inventory, physical Samsung/permissions/lifecycle/background notification/printer tests, release signing and Play requirements remain open. Continue independent engineering work; never infer Gate PASS from CI green.


## Native continuation verified checkpoints — 2026-10-10
- Native compiler blocker fixed at 74b790f from actual failed Actions logs. Run 38022000575 passed regression/source/assets/shared tests, all three APK builds, Reject WebView and real accelerated Android launch/Longdo evidence. This is not full release certification.
- Printer 7e0af346 independently passed run 38023204980 attempt 2, including Merchant/shared tests, all three builds and real emulator/Longdo. Attempt 1 stopped at uiautomator exit137; no result was silently accepted. Safely fast-forwarded Native from74b790f to7e0af346 after remote HEAD/ancestry checks; backup backup-native-before-verified-printer-20261010-74b790f. Native run38023988098 passed.
- Native Merchant login follows actual Production qg-login-v08 responsive dimensions, existing photo/gradients, native inputs and SVG geometry. Initial run38023501704 caught an Android SVG compact-arc parsing crash;169c5d22 expands flags without changing geometry. Run38023889859 passed all three builds and Customer/Merchant/Rider phone/small-phone/tablet launch matrix, Merchant signup/single Back/staff-invite entry, plus Longdo. Downloaded runtime evidence ZIP SHA25685d506c87804e0e505ac4e4a48084657812876c9a888a7700a7f7b6abc793186 verified and screenshots inspected. No account or order submitted; authenticated/physical parity is not certified.
- Read-only Production RPC audit compared94 literal call sites/76 names and live signatures. Found required nullable arguments omitted by JSONObject.put(null): Customer checkout p_note, support p_order_id and Merchant Laundry service p_service_id/p_description/p_estimated_minutes. Fixes and actual checkout wire-serialization unit tests are isolated on work-native-rpc-null-contract-20261010, awaiting complete CI before integration. Other optional omitted nullable parameters have deployed defaults; dynamic dispatch/complete RLS audit remains open.
- Existing-bill POS quantity delta retry remains an open P1 on Native7e0af346. An additive pos_edit_bill_once ledger wrapper on work-native-pos-edit-idempotency-20261010 preserves deployed pos_edit_bill and guards actor/shop/permission/payload with a transaction advisory lock. Captured original function definitions before changes;15 isolated SQL checks pass (replay/add/reduce, payload/actor mismatch, permission revocation, failed-write rollback, anonymous/client access, repeat migration). Production schema has not been changed at this checkpoint. Durable no-backup client intent work is isolated and unverified, and must not be integrated before deployed-RPC and build/test gates.
- Release Gate remains OPEN: complete three-role Production blueprint/authenticated E2E, live concurrency/RLS/session, physical Android/notification/GPS/printer/background matrix, signing and Play readiness. No APK/AAB/source/UI deliverable is sent to the owner. CI build evidence does not certify those gates.

- Nullable RPC fix daa6ffaf: independent CI38024210955 PASS through checkout serialization unit tests, all three builds, Reject WebView and actual emulator/Longdo. Integrated only after that result; combined integration branch remains subject to its own full CI before Native main update.


## 2026-10-10 Customer guest branch resumed from failed compiler evidence
- Read failed run38014547015 job114101873806: CustomerGuestShell and CustomerPromotionScreen imported inaccessible Compose weight. Removed those imports while retaining Row/Column scope weight calls.
- Backed up remote guest5d50b58 before merging current verified Native db863c6, preserving all guest public catalog/cart/promotions work and all newer auth/printer/RPC fixes. Resolved workflow branch union and retained the newer staff authorization test assertions.
- Customer guest branch still requires full CI and real public/authenticated UI checks before integration. Build success alone is not visual/functional parity or release certification. No artifacts delivered.

- Updated actual emulator matrix for the restored Customer guest entry: capture public Home, explicitly open login, retain signup/email/back checks, then verify one Back returns to guest Home. No login/account/order submission or fabricated catalog state. This runtime evidence is pending CI.


## 2026-10-10 Customer guest delivery location continuity
- Production public REST reads succeeded with the app publishable key and no account token: shop directory/open state, real shop products/reviews and market directories/catalog. Actual anon RLS read-only probe confirms one visible shop/product, two markets, one market-member shop, no market-stock/promotions, and both allowed banner settings. No records changed or synthetic catalog introduced.
- Found the guest-selected pin/address disappeared at authentication and account-profile refresh could override a selected local pin. Production retains qg_customer_delivery_location across that transition. Added one shared Customer device-local cache with validated finite Thailand coordinates, used by guest and authenticated shells; profile location is only fallback when no valid local selection exists.
- Guest save writes durable storage on IO before navigation; errors remain retryable, no success claimed after failed storage. Authenticated save retains Production metadata PATCH, then updates local cache; a cache failure cannot turn an acknowledged account save into a false server failure. Cancellation propagates and busy clears in finally.
- Serialization/reopen, profile precedence, corrupt/missing cache and invalid-point tests added. Three-app compile/runtime CI pending on isolated work-native-customer-location-cache-20261010; no integration or full visual/device/E2E/Release certification.
