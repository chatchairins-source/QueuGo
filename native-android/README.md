# QueueGo Native Android

True native Android migration for QueueGo using Kotlin + Jetpack Compose. No WebView UI.

## Production foundation
- Supabase Production remains authoritative.
- Existing RLS, RPCs, order state machine, session rules, dispatch and idempotency remain in use.
- Customer, Merchant and Rider have separate native application IDs.
- Admin remains web only.

## Native coverage
- Customer: Home, dedicated service pages with banners, shops, products, persistent cart, cash checkout, orders/tracking, Market multi-shop, Laundry, search, notifications, support, Rider chat, cancellation, review, account deletion.
- Merchant: Dashboard, store open/close, daily revenue, orders and preparation flow, product catalog/create/edit/archive, native counter POS, Laundry operations, admin support messaging, account deletion.
- Rider: server-targeted offers with accept countdown, online/location heartbeat, market multi-stop, navigation return control, arrival, pickup/delivery photo proof, cash responsibility display, Customer chat, daily stats/history, account deletion.

The web production app is not replaced or modified by this native branch.

Latest native pilot head is release-gated by full QueueGo regression, native source integrity, three-app APK build and WebView rejection.
