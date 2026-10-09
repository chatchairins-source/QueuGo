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
