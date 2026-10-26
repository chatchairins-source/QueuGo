# Merchant Web ↔ Native parity audit

Web blueprint: origin/main 7e036a0735cc49905e9b0a2434492470551a0c9d.
Native audit base: 3e7b5aa17995ab9d1ad93d5b635ffe945b3b5d04.
Owner now permits Merchant/Rider work while Customer CI runs. Source presence does not establish functional or visual parity.

| Web surface | Native source/route | Status | Priority | Evidence / next action |
|---|---|---|---|---|
| Login/session | Merchant auth/session host | Review open | P1 | Verify restoration and authenticated session lifecycle |
| Home/dashboard/open-close | DashboardScreen / home | Status counts aligned in source; runtime pending | P1 | Web `renderShopDashboardModern` shows today's all/completed/preparing/cancelled, store identity/open toggle, pause controls and main menu. Native previously showed revenue/GP cards, an extra active-order list and cover image. Native now computes Web-equivalent status groups from loaded orders in device-local day, retains open/pause authority, and routes orders through existing menu/nav. JUnit added for time-zone boundary, status groups and Thai date label. Hosted build/JUnit and authenticated screen capture pending. |
| Orders/detail | MerchantOrdersScreen / orders, order | Filter/search and common status labels aligned in source; local full regression PASS; runtime/detail audit pending | P1 | Web has six buckets (all/pending/preparing/searching/delivering/done), combined order/customer search, explicit no-results state, item count/customer/time/amount, status labels. Native now maps the existing order list to the same buckets/labels and reads customer_name only when the current RPC provides it. Exact-head hosted JUnit/build/emulator evidence is next; order detail actions and visual capture remain open. |
| Products/menu/categories | MerchantProductsScreen / products, product, catalog | Present; review open | P1 | Compare editor, category and channel price controls |
| Grocery/Market catalog | grocery-catalog, market-catalog | Present; review open | P1 | Preserve source authority and exclude kitchen |
| POS/cart/sales/reports/inventory | MerchantPosScreen / pos | Module present; nested inventory pending | P1 | Enumerate nested POS surfaces against latest Web |
| Kitchen | Existing restaurant flow | Review open | P1 | Verify restaurant-only visibility |
| Revenue/finance/credit/top-up | MerchantRevenueScreen / revenue | Present; review open | P1 | Compare one balance and real payment/recovery actions |
| Settings/shop/hours/location | profile, shop-setup, hours | Present; review open | P1 | Compare entry hierarchy, labels, map and save/retry |
| Notifications/support/media | notifications, support, media | Present; review open | P1 | Compare reference routing, failed reads and retry |
| Initial-load recovery | QueueGoMerchantApp refreshAll | Retry card added; hosted runtime pending | P1 | Initial shop read failure is separated from legitimate no-shop result and can retry via existing refreshAll; successful null shop clears error. Full regression/Pilot pending. |
| Process recreation | QueueGoMerchantApp screen | Source policy added; hosted runtime pending | P1 | screen used remember; reloadable destinations now save safely and selected detail/editor state returns to parent. Verify on exact-head emulator. |

NEXT: run exact-head Native Pilot on `b66b6a3b1b2a83613ed9984a7dc8e900135f0b07`; then audit Web order-detail actions and catalog behavior against Native. Capture critical screens after authenticated access; physical-device status remains unverified.
