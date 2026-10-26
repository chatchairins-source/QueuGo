# Merchant Web ↔ Native parity audit

Web blueprint: origin/main 7e036a0735cc49905e9b0a2434492470551a0c9d.
Native audit base: 3e7b5aa17995ab9d1ad93d5b635ffe945b3b5d04.
Owner now permits Merchant/Rider work while Customer CI runs. Source presence does not establish functional or visual parity.

| Web surface | Native source/route | Status | Priority | Evidence / next action |
|---|---|---|---|---|
| Login/session | Merchant auth/session host | Review open | P1 | Verify restoration and authenticated session lifecycle |
| Home/dashboard/open-close | DashboardScreen / home | Present; review open | P1 | Compare current Web hierarchy and authoritative status action |
| Orders/detail | MerchantOrdersScreen / orders, order | Present; review open | P1 | Compare states/actions and detail Back |
| Products/menu/categories | MerchantProductsScreen / products, product, catalog | Present; review open | P1 | Compare editor, category and channel price controls |
| Grocery/Market catalog | grocery-catalog, market-catalog | Present; review open | P1 | Preserve source authority and exclude kitchen |
| POS/cart/sales/reports/inventory | MerchantPosScreen / pos | Module present; nested inventory pending | P1 | Enumerate nested POS surfaces against latest Web |
| Kitchen | Existing restaurant flow | Review open | P1 | Verify restaurant-only visibility |
| Revenue/finance/credit/top-up | MerchantRevenueScreen / revenue | Present; review open | P1 | Compare one balance and real payment/recovery actions |
| Settings/shop/hours/location | profile, shop-setup, hours | Present; review open | P1 | Compare entry hierarchy, labels, map and save/retry |
| Notifications/support/media | notifications, support, media | Present; review open | P1 | Compare reference routing, failed reads and retry |
| Process recreation | QueueGoMerchantApp screen | Proven source gap | P1 | screen uses remember; safe route context disappears on recreation. Preserve reloadable routes, prevent blank detail restoration |

NEXT: bounded Merchant safe route restoration, then exact-head tests/build. Capture critical screens after authenticated access; physical-device status remains unverified.
