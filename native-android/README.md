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
- Rider: Longdo map-first home, Android GPS rider location, shop/customer job pins, server-targeted offers with accept countdown, online/location heartbeat, market multi-stop, navigation return control, arrival, pickup/delivery photo proof, cash responsibility display, Customer chat, daily stats/history, account deletion.

The web production app is not replaced or modified by this native branch.

Latest native pilot head is release-gated by full QueueGo regression, native source integrity, three-app APK build and WebView rejection.


## Visual source of truth
- The approved QueueGo web production UI is the visual blueprint for the native apps.
- Native Compose may change implementation technology, but must preserve the approved hierarchy, proportions, spacing, cards, banners, controls and role flows.
- Customer Home keeps compact top actions, 54dp service circles, approved banner proportions, 92dp shop imagery and thin bottom navigation.
- Merchant keeps the approved store card / cover / KPI / menu-grid hierarchy.
- Rider keeps the approved compact topbar, bottom job-card hierarchy, accept countdown on the accept button and single-page item-check + photo-proof flow.
- Backend behavior remains governed by the existing Supabase Production RLS, RPCs and order state machine.

