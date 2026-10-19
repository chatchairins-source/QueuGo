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
