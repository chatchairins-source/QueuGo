# QueueGo Android Web-Parity Rebuild — 2026-10-25

## Mission

Rebuild the visible Android experience for Customer, Merchant and Rider from the current Production Web experience as the Golden Master.

The current Native UI is not a release candidate. CI/build success alone does not count as visual or functional parity.

## Non-negotiable rules

1. Production Web is the visual and interaction Source of Truth.
2. Android must not invent alternate layouts, spacing, colors, map presentation, cards, navigation shells or flow states.
3. No WebView/TWA shortcut. Keep true Native Android for GPS/background/navigation/camera/push/reliability.
4. Reuse working core/runtime code. Do not rewrite stable backend/state machinery just to rebuild UI.
5. Once a new parity screen is wired, the legacy screen must not remain as a live fallback.
6. Every migrated screen needs:
   - matching Web reference/state;
   - Android screenshot at matching viewport/state;
   - functional-action parity check;
   - regression guard;
   - exact-HEAD CI/build evidence;
   - physical-device evidence where hardware/runtime behavior matters.
7. A green build with a visibly different screen is FAIL.
8. No APK may be called Release Candidate until Web Parity Gate passes.

## Keep / reuse

- Auth/session storage
- QueueGo API clients, Supabase/RPC contracts and models
- Production order state machines and idempotent mutations
- Push/FCM infrastructure
- GPS/location foreground services and location integrity
- QueueGo route/search/Places clients
- Navigation progress/reroute logic
- Camera/photo/file upload/proof persistence
- Credit/payment backend clients
- Background workers/recovery
- Android build/signing/CI/release pipeline
- security/runtime/unit tests that validate underlying authority

## Rebuild / retire from visible product

- Existing role-specific Compose presentation that differs from Web
- Existing Native navigation shells where hierarchy differs from Web
- Native-only map presentation/style
- Native-only cards, spacing, typography and color interpretations
- Legacy visual fallbacks after replacement screen becomes authoritative
- UI states that do not exist in the Web Golden Master

## Migration order

### Phase 1 — Rider critical path
1. Home map / Online state
2. Incoming offer/countdown
3. Accepted job
4. Merchant approach / arrival
5. Pickup/proof
6. Active navigation
7. Customer arrival
8. Completion
9. Messages
10. Earnings
11. Profile/settings

### Phase 2 — Customer critical path
1. Home
2. Search
3. Shop/catalog
4. Cart
5. Checkout/location
6. Tracking
7. Market/Grocery/Laundry
8. Ride/Express
9. Messages/orders/account

### Phase 3 — Merchant critical path
1. Home/dashboard
2. Orders/order detail
3. Products/menu/catalog
4. POS
5. KDS restaurant-only
6. Revenue/credit
7. Shop settings
8. Messages/support

## Web Parity Gate

For a screen to pass:
- same information architecture;
- same visible controls/actions;
- same copy unless Android OS requires different system wording;
- same brand hierarchy;
- matching typography scale;
- matching spacing/radius/shadows;
- matching loading/empty/error/disabled states;
- matching map base presentation, overlays and camera behavior;
- no Native-only duplicate controls;
- action result/state transition matches Web/server authority.

OS-controlled surfaces such as permission dialogs, keyboard, system status/navigation bars may differ by device/Android version and are excluded from pixel parity.

## Current first target

Rider Home + Map.

Owner physical-device evidence shows the current Native Rider:
- is visually different from Web;
- used a different basemap presentation;
- showed a generic pin instead of the intended QueueGo Rider puck;
- retained active route geometry while guidance already reported arrival;
- did not visually match the Production Web hierarchy.

These are release blockers, not polish items.

## Starting checkpoint

- Branch: `work-native-web-parity-rebuild-20261025`
- Parent: `65918d8ca7dbe720127e1c915a0483c3f209cb01`
- Legacy Native UI status: NOT RELEASE CANDIDATE
- NEXT: rebuild Rider Home/Map presentation against Production Web, then capture paired Web/Android evidence before migrating the next Rider state.
