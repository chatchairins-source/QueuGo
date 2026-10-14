# GrabFood reference → QueueGo Customer / Marketplace

วันที่ตรวจ: 14 ตุลาคม 2026 (Asia/Bangkok)

## ข้อสรุป

APK ที่เจ้าของส่งเป็น package `com.grab.food.pax` version `1.0.32` แต่ Application คือ `com.dd.doordash.DoorDashApp` และมี implementation จำนวนมากใต้ `com.dd.doordash` จึงใช้เป็น reference ของ Customer commerce patterns เท่านั้น ไม่ใช่หลักฐาน architecture หรือ UX ของ Grab รุ่นปัจจุบัน

ประโยชน์หลักที่พบ: Explore แบบ section/template และ pagination, cart/checkout repositories, critical/non-critical checkout errors, partial reorder, address refinement และ post-checkout actions จาก state ของออเดอร์ QueueGo มี commerce core และ recovery อยู่แล้ว จึงควรเสริม validation/refresh/continuity มากกว่าสร้างระบบสั่งซื้อใหม่

ชุด implementation ที่เลือกในรอบนี้: P0 delivery eligibility guard ใน backend เดิม + Web checkout blockers/stock preview ส่วน Reorder, Unified Search และ continuity เป็นงานถัดไปที่ออกแบบไว้ ยังไม่ได้อ้างว่าทำแล้ว

## Source และวิธีตรวจ

- ค้น Library ด้วยชื่อเต็มตรงตัว พบ `GrabFood+-+Food+Delivery+App_1.0.32_APKPure.zip`
- Library identity: `libfile_413a998d603081918b8eb652fdc0315f`
- ZIP 38,992,958 bytes; มี APK หนึ่งไฟล์ ไม่มี split APK
- ZIP SHA256: `49f6c3784ebe884ade2675fb46c72f6e459e0863e34d3d58bfd3f0924a37905b`
- APK SHA256: `ee35f46f6adbf74d0a2743d4fc53fae760600fb15c5ca4ab239939cb017fc956`
- ตรวจ Android manifest, DEX class/method/field inventory และ resource filenames; พบ 12,506 classes และ 59 activities ทั้งแอปและ SDK
- ไม่รันแอปด้วยบัญชี Grab/DoorDash ไม่เรียก private API ไม่คัดลอก source/assets/text/icons ไม่เก็บ extracted proprietary code ใน repo
- ไม่ได้ยืนยัน layout, animation, ranking algorithm, network response, server implementation, cache TTL จริง หรือฟีเจอร์ที่เปิดใช้ในแต่ละประเทศ

ระดับหลักฐาน: **S** = พบ symbol/contract โดยตรงใน APK; **I** = ข้ออนุมานจาก symbol หลายส่วน; **U** = ยังยืนยันไม่ได้จากการตรวจนี้ คำว่า “พบ” หมายถึงมี implementation/contract ในไฟล์ ไม่เท่ากับเปิดใช้งานจริง

## QueueGo source จริงและความต่อเนื่อง

| ฐาน | HEAD ที่ตรวจ | สถานะ |
|---|---|---|
| Production Web main | `8ceba03eee1a8d18260fde77ddfab23b70ae5182` | ฐาน Web ล่าสุดเมื่อ fetch |
| Grocery PR #168 | `6537074df0b9bc108399e258223649a438d3dae0` | Draft; ยังไม่รวมใน main; ไม่ใช่ catalog ที่ rollout สมบูรณ์ |
| Native main | `9f675d0755b5be34105c6180bc3a8ed7d45cdafc` | เก่ากว่า Native successors; ไม่ใช้เป็นฐานแก้ Native |
| Latest Native successor / PR #323 | `74f99ffbafd5dcf365b621d2dbb9d841900995e9` | อ่าน Customer source จากสายล่าสุดนี้; ไม่แก้ Rider |
| Web successor รอบนี้ | `work-customer-marketplace-validation-20261014` | รวม main + Grocery เป็น descendants ของทั้งสองสาย |

Web main กับ Grocery เป็น sibling จาก `7595d764dac8a14d28162248d33f640d0a614870` จึง merge โดยเก็บ approved banners และ Grocery ทั้งหมด แก้ conflict package.json ด้วยการรวม test/scripts ของทั้งสองฝ่าย ไม่มี reset/revert Native ถูกแยกตรวจเพราะมี Rider mission อิสระ ไม่ merge Rider source เข้าสาย Web

Backup ก่อนรวม: `backup/customer-marketplace-before-unify-20261014`; ก่อน P0: `backup/customer-marketplace-before-p0-20261014` ไม่แก้ข้อมูลร้าน/สินค้า/ออเดอร์จริงในการทดสอบ

## แหล่งหลักฐานที่ใช้ในตาราง

| รหัส | APK reference | QueueGo source ที่ตรวจ |
|---|---|---|
| H | FoodExplorePresenter: popular items, reorderable carts, carousel/template conversion, loadNextPage | index.html: V.home/category routes; Native QueueGoCustomerApp/CustomerHomeBanner |
| S | ExploreAutoCompleteResult, SearchStore.Menu.Item, RequestStoreItem, FoodExplorePresenter search methods | index.html: V.search/globalShopSearch; Native CustomerSearchScreen |
| M | FoodStoreMenuActivity, Menu2Activity, StoreInfoBottomSheet, MenuCategory, Extra/ExtraOption/NestedExtra | V.shop/loadMenu/add; catalog/customer-grocery; Native CustomerShopScreen/CustomerApi |
| C | BaseOrderCart.DeliveryAvailability, OrderCartActivity, CheckoutOrderCart, cart repositories | readCart/saveCart, V.cart/checkout, Market cart; Native CustomerCartStore/CustomerCheckoutRecovery |
| X | CheckoutPresenterV2: critical/non-critical lists, submitCart, setup address/payment/ETA, delivery time selected | V.checkout/placeOrder; Production cash-order core + existing stock/service guards |
| T | PostCheckoutPresenterV2: delivered state, call courier, cancel, Help, rate, receipt | customer-features.js; Native CustomerOrderTrackingScreen/CustomerTrackingMap |
| R | ReorderDetailedStoreOrderCart, ReorderStoreOrderCartResponse.ALL/PARTIAL, ReorderOverlay | V.orders/order_items; Native order history; ไม่พบ customer reorder action ใน source ที่ตรวจ |
| A | AddressBookActivity, AddressRefineFragment.isDistanceValid, AddressConfirmation/Input fragments | openCustomerLocationPicker/currentPos; Native CustomerLocationPicker/CustomerLocationStore |
| P | DataCache.getCacheValidLength/refreshCache, MenuRepository.FilterCache, Retrofit, subscribe/unsubscribe | cached shops, routeVersion, location cleanup, recovery journal; Native scoped refresh/cart/location stores |

## 1. Home / Explore inventory

| Feature A: reference | หลักฐาน | B/C/D: QueueGo ปัจจุบันและช่องว่าง | E: ควรทำ | F: ชะลอ | G |
|---|---|---|---|---|---|
| Home sections/templates | H S | Web มี carousel, category tiles, nearby list; Native มี Home | คงโครงสร้างและทำ data refresh ให้สม่ำเสมอ | redesign ทั้งหน้า | P2 |
| Personalized content | H I/U: curated/experiment contracts ไม่ยืนยัน algorithm | ไม่พบ personalization engine ใน source ที่ตรวจ | ใช้ประวัติจริงเมื่อข้อมูลเพียงพอ | ML recommendations | Later |
| Nearby stores | H I | Web/Native sort ด้วยระยะเมื่อมี location | แสดง location/availability ที่เชื่อถือได้ | invented ETA/distance | P1 |
| Popular stores | H S: popular tag | มี featured presentation; ไม่ยืนยัน popularity จากยอดจริง | ใช้ aggregation ของข้อมูลจริงเท่านั้น | แสดง “ยอดนิยม” โดยไม่มีหลักฐาน | P2 |
| Popular items | H S: getPopularItems | ไม่พบ popular-item feed กลาง | real demand aggregation หลัง core เสถียร | demo popularity | P2 |
| Previous orders on discovery | H S | มี order history; Home ยังไม่มี history feed เต็ม | เพิ่ม shortcut จากข้อมูลเจ้าของออเดอร์ | แสดงทุกออเดอร์ใน Home | P1 |
| Reorder carousel | H/R S | ไม่พบ safe reorder action | ทำ validated reorder ก่อน card | carousel ก่อน reorder core | P1 |
| Category/cuisine navigation | H S | มีหมวด QueueGo และ Market | reuse shared core | clone Grab category taxonomy | P2 |
| Merchant carousel | H S | มี list/featured; carousel merchants ไม่ครบเท่า reference | เพิ่มเฉพาะเมื่อข้อมูลจริงพอ | carousel ซ้ำหลาย section | P2 |
| Pagination/lazy data | H S: loadNextPage | ร้านทั่วไปโหลด list; Grocery candidate มี 50+1 paging | keyset/page contracts + scroll retention | แก้ด้วยโหลดทุกแถว | P1 |
| Loading/cached Home | H/P S/I | Web cached shops ใน process; Native refresh อยู่แล้ว; freshness contract ยังไม่ครบ | stale snapshot + background refresh พร้อม timestamp | อ้างว่า cached เท่ากับสด | P1 |

## 2. Search inventory

| Feature A | หลักฐาน | B/C/D: QueueGo | E | F | G |
|---|---|---|---|---|---|
| Store search | S S | Web/Native มี global store search | ขยายผลแบบชนิดร้าน/สินค้า/ตลาด | search bar ซ้ำทุกหมวด | P1 |
| Product search | S S: SearchStore.Menu.Item | global search ปัจจุบันเน้น shop; menu อยู่ในร้าน | server-scoped product results ที่เปิดขายจริง | scrape private competitor API | P1 |
| Category search | S S/I | Web match category text; ยังไม่มี category result type | route หมวดจาก search กลาง | checkout ใหม่แยกหมวด | P2 |
| Autocomplete | S S | ไม่พบ autocomplete service ใน path ที่ตรวจ | debounce + cancellation + typed suggestions | ML ranking | P2 |
| Recent search | S U: ไม่ยืนยัน persistence ของ history | query ใน memory; ไม่พบ durable recent-search list | on-device bounded history พร้อม clear | เก็บ history server โดยไม่จำเป็น | P2 |
| Ranking | S U: server algorithm ไม่อยู่ใน APK | มี sort ระยะ + substring filter | availability/type/term score ที่อธิบายได้ | อ้าง ranking ของ Grab | P2 |
| Empty result | S S: request-store item/error UI | มี empty state | แยก request failure ออกจาก zero results | empty result เมื่อ network fail | P1 |
| Request missing store | S S: onRequestStoreItemClick | Support ทั่วไปมี; ไม่พบ store-request flow เฉพาะ | reuse Support เมื่อจำเป็น | new merchant onboarding system | P2 |
| Market search | S U: reference market entity ไม่ยืนยัน | global search QueueGo excludes dedicated Market categories | typed market result → existing Market route | ทำ Market เหมือนร้านเดี่ยว | P1 |

## 3. Store / Menu inventory

| Feature A | หลักฐาน | B/C/D: QueueGo | E | F | G |
|---|---|---|---|---|---|
| Store detail/info | M S | Web/Native มีชื่อ รูป ที่อยู่ เวลาเปิด reviews | ตรวจข้อมูลจริงและ readiness | copy store layout | P2 |
| Menu categories | M S | Web category tabs; Native shop catalog; Grocery paging | reuse existing categorization | categories ซ้ำอีก schema | P2 |
| Popular menu | M/H S/I | ไม่พบ authoritative popularity service | real order aggregates ภายหลัง | fake badges | P2 |
| Featured item | M I/U | ไม่ยืนยัน merchandising capability ครบ | ใช้ existing campaign metadata ถ้ามี | promotional ranking engine | Later |
| Modifiers/extras | M S: radio/checkbox/nested/quantity views | ordinary cart ใช้ product_id/qty; ไม่พบ modifier selections ส่งผ่าน cash RPC | server-priced modifier identity ใน shared line model | ใช้ note แทนราคา options | P1 |
| Quantity | M S | Web/Native มี quantity; qty bounded | คงข้อจำกัดและ stock validation | quantity UI ใหม่ | P0 existing |
| Item notes | M S/I | QueueGo มี order-level note; ไม่พบ item-note contract ครบ | optional per-line note หลัง schema audit | clone competitor wording | P2 |
| Substitutions | M S: AddItemSubstitutionOptionsItem | ไม่พบ Customer substitution policy ใน checkout payload | explicit consent + stock/price revalidation | auto replace โดยไม่แจ้ง | P2 |
| Unavailable item | M/C S | product available/delivery flags + server checks; Web stock preview ยังขาดก่อนรอบนี้ | รอบนี้เพิ่ม Grocery stock preview; keep cart | silently remove items | P0 |
| Opening/closing | M S: daily hours/store status | Web list caches open state; core checks manual closure | checkout fresh check + shared server guard | บอกเปิดเมื่อ query fail | P0 |
| ETA | M/X S: ETA row/contracts | state progress มี; dynamic ETA ไม่ยืนยันครบ | derive only from real timestamps/provider | hardcoded promises | P2 |
| Minimum order | M U: ไม่ยืนยัน policy ที่ active | core rejects zero total; ไม่มี min-order policy ที่ยืนยัน | เพิ่มเฉพาะ business decision | arbitrary new minimum | Later |
| Delivery availability | C S | delivery_enabled + product flag/service route; missing Market gate ordinary path | guard เดิมบังคับร้าน/Market | only hide client button | P0 |

## 4. Cart inventory

| Feature A | หลักฐาน | B/C/D: QueueGo | E | F | G |
|---|---|---|---|---|---|
| Cart architecture | C S: repository/models | ordinary + Market adapters ใช้ existing products/order RPC; ไม่ใช่แอปใหม่ | normalized shared line policy, preserve Market trip | rebuild cart/order system | P1 |
| Item update/quantity | C S/I | Web chg/remove; Native callbacks | preserve pending-journal lock | optimistic server order mutation | P0 existing |
| Options identity | M/C S | product-only identity ไม่มี priced options | product+option-set line key ก่อน modifiers | merge option variants เป็น product เดียว | P1 |
| Price refresh | X S | Web checkout โหลด menu ราคาล่าสุด; Native checkout refresh มี; server rejects changed totals | keep explicit customer review | silent accept changed amount | P0 existing |
| Product unavailable | C/X S | cart ถูกเก็บ, checkout disabled; server transaction guards | item-specific feedback | clear cart on error | P0 |
| Merchant closed | C S: store status | backend normal core checks close; Web preflight ยังไม่ครบ | รอบนี้ fresh open-state read | force order to closed shop | P0 |
| Cart persistence | C I: repository ไม่พิสูจน์ disk semantics | Web per-account localStorage; Native durable cart store | retain existing identity isolation | backend cart sync ก่อนจำเป็น | P1 existing |
| Cross-screen state | C I | Web shared cart; Native scoped stores | tests account switch/restart | duplicate screen-specific carts | P1 |
| Suggested products | H/M I/U | ไม่พบ cart recommendation feed | real menu suggestions เมื่อ core ผ่าน | upsell ก่อนแก้ validation | Later |
| Validation before checkout | X S | normal price/product checks; Market/stock partly downstream | selected P0 รอบนี้ | rely on cached Home state | P0 |

## 5. Checkout inventory

| Feature A | หลักฐาน | B/C/D: QueueGo | E | F | G |
|---|---|---|---|---|---|
| Address selection | X/A S | Web pin + address; Native location picker | keep manual house detail | map-only address | P0 existing |
| Pin refinement | A S: isDistanceValid | Web/Native actual pin; service-area validation backend | preserve selected pin on retry | reset pin on every refresh | P1 |
| Delivery instructions | A/X S/I | address + order note; distinct rider instruction not confirmed | clearly scoped fields when extending | new flow for every category | P2 |
| ASAP/scheduled | X S: time selected/setup ETA | immediate operation ปัจจุบัน | retain ASAP | schedule before dispatch support | Later |
| ETA | X S | lifecycle/timer ไม่เท่ากับ dynamic delivery ETA | real estimate with stale indication | pretend accuracy | P2 |
| Fee calculation | X S: price manager/surge contracts | cash RPC rechecks expected fee; Market uses existing rules/snapshot | retain authoritative rules | import surge/tip economics | P0 existing |
| Promotion | X S: promo repository | existing shop campaigns; coupon engine not established | use current campaigns | new wallet/subscription | Later |
| Payment architecture | X S: PaymentActivity/card/GooglePay | QueueGo cash-first payments/audit | keep cash model | cards/GooglePay/tip adoption | Later |
| Checkout refresh | X S: fetched-data/submit handlers | prices refreshed on open; submit rejects stale | next: preserve fields + explicit refresh after conflict | blind replay with new prices | P1 |
| Critical/non-critical errors | X S: explicit methods | existing catch mostly generic toast | typed causes over existing RPC/errors | UI duplication for each failure | P1 |
| Price changed | X I from price/checkout handlers | normal expected_subtotal/fee reject; Market payload has no displayed expected quote | normal retained; audit Market quote contract next | silently charge changed Market price | P0 next |
| Product/stock unavailable | X/C S | normal flags + Market stock + Grocery reservation triggers | preserve atomic guards; preview stock | only client validation | P0 |
| Merchant unavailable | X/C S | shop core active check; ordinary missing membership gate | extend existing guard | new checkout RPC | P0 selected |
| Market selected/active | QueueGo-specific; competitor U | existing order guard only when market_order_id non-null | apply same guard to QueueGo delivery single+multi-shop | Market-less checkout | P0 selected |
| Area validation | A/X I | queuego_assert_service_route already Production | do not duplicate formula | guessed polygons | P0 existing |
| Atomic/idempotent submit | X I: submit/status polling; exact server semantics unknown | owned request UUID/journal/reconcile already Web/Native | retain receipt recovery | new UUID on ambiguous timeout | P0 existing |

## 6. Tracking / post-checkout inventory

| Feature A | หลักฐาน | B/C/D: QueueGo | E | F | G |
|---|---|---|---|---|---|
| Order status | T S | Web/Native derive progress from order status | single selector with arrival events | independent UI state machine | P1 |
| Dynamic ETA | T I/U | not certified in QueueGo | real provider/timestamp model | guessed moving countdown | P2 |
| Courier location | T S/I | owned context + GPS; completed no live coordinates | freshness + incremental markers | invented rider movement | P1 |
| Merchant progress | T I | accepted/preparing/ready already | state-driven copy | separate progress database | P1 existing |
| Map behavior | T U: smoothness not runtime verified | Web rebuilds map on full order rendering; Native map lifecycle work exists | retain map per order/account, update markers | recreate every status tick | P1 |
| Contact merchant | T S/I | existing chat/voice launch | keep order-bound authorization | public contacts leakage | P1 existing |
| Contact rider | T S: onCallDasher | existing Customer calls/chat | test ownership/terminal expiry | duplicate chat system | P1 existing |
| Help | T S: onHelpButtonClick | existing Support + order binding | reuse ticket recovery | new Help backend | P1 existing |
| Cancel | T S | existing allowed customer cancel RPC | keep server permissions/reasons | cancel arbitrary stages | P0 existing |
| Receipt | T S | existing item/cash order summary | improve export later | financial system duplication | P2 |
| Rating | T S | reviews + gold stars exist | verify order ownership | app ratings replace order review | P1 existing |
| Reorder after delivery | R/T S | no safe action found | validated cart preview | auto checkout | P1 |
| Background/foreground | T/P S/I: lifecycle methods, unknown actual behavior | Web poll cleanup/account guards; Native scoped loops/store recovery | reconcile silent snapshot before actions | claim physical smoothness from symbols | P1 |
| App restart | T/P U for reference disk recovery | Native cart/pending request/location durable; full tracking screen process gate not certified | restore order ID → fresh owned context | offline fake success | P1 |
| Terminal order map | T S: isOrderDelivered | completed/cancelled/no-rider terminal logic exists | preserve no live map | continue polling closed order | P0 existing |

## 7. Order history / Reorder inventory

| Feature A | หลักฐาน | B/C/D: QueueGo | E | F | G |
|---|---|---|---|---|---|
| Previous orders | R S: RecentOrdersActivity | history Web/Native already | bounded paging/owned reads | duplicate history schema | P1 |
| Reorder action | R S | no safe reorder button/core found | plan below | copy old total | P1 |
| Discontinued items | R S/I: partial response | normal checkout detects missing; no reorder-specific filter | per-line reasons | silently omit | P1 |
| Current price | R S: detailed cart/subtotal contract; exact checks U | existing checkout refresh can be reused | show old→new price in reorder review | reuse historic price | P1 |
| Current stock | R I/U: backend behavior unknown | existing Grocery/Market guards | fresh availability preview + transactional final guard | reserve stock on history tap | P1 |
| Shop closed | R S: store status/isAsapAvailable | existing open guards can be reused | keep history, disable submit | delete old order | P1 |
| Partial reorder | R S: ALL/PARTIAL/error fields | not implemented | confirm available remainder | auto replacement | P1 |
| Substitution/errors | R/M S/I | no Customer substitution consent policy found | flag original option/item mismatch | silently substitute | P2 |
| Market reorder | competitor U | preserve same-market constraint/trip fees | group eligible shops under existing Market cart | treat multi-shop as ordinary order | P1 |

## 8. Address / Location inventory

| Feature A | หลักฐาน | B/C/D: QueueGo | E | F | G |
|---|---|---|---|---|---|
| Current location | A S | Web/Native GPS and denied/manual paths | real position + fallback pin | guess coordinate | P0 existing |
| Saved addresses | A S: AddressBook | Web last location cached; Native LocationStore; multi-address book not found | named home/work addresses after core | duplicate location service | P2 |
| Map pin | A S | existing picker | preserve pin | new map engine | P1 existing |
| Refine pin | A S | existing drag/manual selection | keep address text + refinement guard | overwrite custom house detail | P1 |
| Apartment/house detail | A I/U: address input; dedicated field not confirmed | address textarea retains manual details | structured optional details if useful | mandatory large form | P2 |
| Delivery notes | A/X S/I | current address/order note | clarify merchant vs rider scope | copy reference wording | P2 |
| Location persistence | A I/U for reference | existing last-location cache/Native store | account/ownership/privacy audit | cross-account hidden sharing | P1 |
| Service area | A S: isDistanceValid; backend exact policy U | Production service route validation exists | reuse actual rules | reference service polygons | P0 existing |

## 9. Smoothness and architecture

| Pattern A | หลักฐาน | QueueGo gap | QueueGo adaptation | ไม่เลือกตอนนี้ | Priority |
|---|---|---|---|---|---|
| TTL/cache invalidation | P S/I | process shops cache has no consistent freshness contract | key account+area+query; stale snapshot, refresh version | infer APK TTL value | P1 |
| Durable state | P I/U | cart/request durable; full browse restoration incomplete | persist cart/location/order identity only; revalidate actions | persist stale authorization | P1 |
| Foreground recovery | P S/I | Web page/visibility handling exists; Customer process gate incomplete | owned reconcile once, retain map/list | rerun mutations automatically | P1 |
| Pagination | H S | Grocery paged; ordinary merchant feed unbounded | page sentinel/cursor + single-flight cancellation | duplicate paging backend | P1 |
| Image loading | P U: exact pipeline not established | Web photo helper lazy loads; Native QgRemoteImage | size budget + bounded cache + fallback | copy competitor assets | P2 |
| List restoration | P U | scroll positions not established across routes/process | per account/route/query anchor | cache whole rendered DOM | P1 |
| Map restoration | P U | Web full render can rebuild; Native retained map patterns | retained instance + camera + incremental overlays | reload tiles each status | P1 |
| Network retry | X/P S/I | timeout/recovery safeguards already exist | backoff reads; mutations retain owned journal | retry with new order UUID | P0 existing |
| Skeleton/loading | H S: showLoadingIndicatorWhileFetching | generic loading panel in routes | stale content + quiet local indicator | fake content/demo skeleton items | P2 |
| Optimistic update | P U: reference behavior not proved | local cart is immediate; server mutations guarded | local cart only; reconcile server mutations | optimistic payment/order completion | P1 |

APK concepts suggest presenter/contracts → repositories/managers → typed models → network adapter separation. This is an inference about the packaged client, not the competitor's server. The QueueGo adaptation is small domain policies + existing RPCs + per-platform UI adapters. No new Store/Cart/Checkout/Order system is warranted.

Shared core target:
`Store → Product → Modifier → Cart → Checkout → Order → Tracking`.
Market adds same-market grouping, parent/child order association, multi-shop fee and stock pack units. Grocery adds master→store product linkage and optional stock tracking. These are adapters around the same product/order identities, not separate financial engines.

## P0 validation audit against Production

| Check | ก่อนรอบนี้ | ชุดที่เลือก |
|---|---|---|
| Shop active | normal core joins active user/shop; Market product read checks shop | existing guard also verifies active delivery shop |
| Delivery opt-in | column exists; enforcement not uniform in both paths | require delivery_enabled=true for delivery inserts |
| Selected + approved Market | guard only if market_order_id non-null | extend same guard to QUEUEGO_DELIVERY ordinary and Market |
| Market active | not checked by audited order guard | read/lock actual Market; require active=true |
| Parent Market matches shop | same-market loop exists, guard lacked parent-match assertion | validate parent market_id against current shop |
| Shop open | normal core checks closure; Market core did not | shared guard checks manual closure including resume_at |
| Product active | normal core requires available + delivery_available | retain; Web fail-closed flags |
| Stock | Market/Grocery triggers reserve atomically; normal core locks products in UUID order | retain; add Grocery preview; no duplicate reservation |
| Price/fee | ordinary expected amounts rechecked | retain; Market displayed-quote mismatch remains next P0 audit |
| Delivery area | queuego_assert_service_route in actual Production | retain |

New guard uses the existing function/trigger identity. POS remains outside delivery policy. Changing status on an already-created job does not re-run opening checks, so closing a shop later cannot block delivery/cancellation. Function is not directly executable by anon/authenticated. Current Production audit found one active Grocery shop, with approved Market membership; this is data readiness evidence, not authenticated checkout QA.

This round stages a migration and Web code in PR. It does not claim Production deployment or a physical Native checkout test.

## State-driven tracking design

Keep backend values, including `accepted` and `searching_rider`; do not create a literal MERCHANT_ACCEPT state only for display.

| Backend status/event | UI phase | Active actions/map |
|---|---|---|
| pending | รอรับคำสั่งซื้อ | existing eligible cancel |
| accepted / searching_rider | ร้านรับแล้ว / กำลังหา Rider | targeted search information from actual state |
| rider_assigned / assigned | Rider รับงาน | assigned identity, current GPS if available |
| preparing | ร้านกำลังเตรียม | actual merchant progress; existing preparation timer |
| ready | พร้อมรับ | pickup stage; no payment confirmation |
| picked_up / in_progress | กำลังจัดส่ง | actual live Rider marker with freshness |
| rider_arrived_customer_at | Rider ถึงแล้ว | derived arrival presentation, no new DB status |
| completed | ส่งสำเร็จ | receipt/rating/reorder; no live map |
| cancelled / no_rider_available | สิ้นสุดงาน | explanation/Help/history; no live tracking |

Use one pure status selector per platform with a shared cross-role contract. Preserve existing order lifecycle and audit; do not force a new transition sequence from the reference APK. Keep Market parent lifecycle mapped to its existing trip lifecycle and child order identities.

## Safe Reorder specification (P1, not implemented here)

1. Start from an owned historical order; fetch original product IDs/quantities/option identities, never use old prices as authority.
2. Re-read shop, approved active Market, availability, current product/stock/price and current modifier definitions.
3. Return `eligibleLines`, `blockedLines` with per-line reason, `priceChanges`, and store-level blockers. No order or stock reservation at this step.
4. Show only affected lines and price changes. Let the customer choose the remaining eligible lines; do not silently remove or replace anything.
5. Respect current cart/pending-order journal. Do not overwrite another-shop cart without an explicit choice.
6. Put confirmed lines into the existing ordinary/Market cart. Same-market rule and multi-shop fee remain authoritative. Missing/changed required modifiers send that item back to product options.
7. Existing checkout validates again atomically; closing shop/changed price/stock race must not create a partial order.

## Unified Search specification (P1, not implemented here)

One global entry point and typed results `shop | product | category | market`. Use only publicly sellable/approved entities and service-area context. Product result opens its real shop/product; Market result opens existing Market selector. Do not re-add category-page search bars. Debounce requests, ignore stale responses by actor/query/location version, paginate, show error separately from empty results. Recent queries can be bounded on-device and cleared. Avoid new search index until query/profile evidence justifies it.

## Prioritized delivery plan

- **P0 selected:** ordinary + Market eligibility guard, active Market, delivery opt-in, closure and parent match; Web blockers + stock preview; transaction/DOM regression.
- **P0 next:** Market quote acceptance contract (server price/fee changes after displayed quote), authenticated concurrent stock race, real Web/Native same-RPC verification.
- **P1:** safe Reorder; unified typed Search; single tracking selector; browse/scroll/map retention; bounded directory/menu paging; foreground reconcile; typed checkout conflict refresh preserving pin/address/note.
- **P2:** real popular-item feed, lightweight recent search/autocomplete, per-item notes/substitution consent, saved address book, image tuning and quiet loading.
- **Later:** scheduled delivery, subscriptions, card payment/wallet, coupons/tipping/surge economics, ML personalization, competitor-specific categories. These require business/backend support and are not needed for cash-first pilot.

## Verification and release boundaries

Full npm regression, checkout quote/recovery DOM checks, new actual checkout DOM blocker scenarios, and isolated SQL transactions/ACL tests are required on the exact candidate. Existing Grocery rendered checks are included in CI. Production read-only function/trigger/RLS/schema inspection is complete; no real customer order was created.

Native Customer source on latest successor uses the same cash-order RPC and durable journal. The shared database guard will cover Native upon deployment, but this round does not modify Native Kotlin or claim a new Native build/physical E2E. Authenticated customer/shop/Rider delivery, simultaneous stock checkout, process restart, real GPS/FCM/voice and final release remain unverified. No APK/AAB release is delivered.

Local Chromium component run could not launch in this environment (SIGSEGV before page execution); this is an environment blocker, not a rendered PASS. GitHub CI rendered checks must resolve that gate. All verification uses isolated test data only; no demo/fake data enters Production.
