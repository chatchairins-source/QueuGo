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
