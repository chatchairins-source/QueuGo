# Rider availability / native-map parity — 2026-11-02

## Verified defect
- Read current Native #736 `74856f8a`, merged #743/#744, Issue #740, [QueueGo CI Recovery] issues, and `docs/PROJECT-MEMORY.md` before changing source. Isolated parent is #819 successor after #820 integration: `ee200ee340fc12175fb1f36be0de1ebcf9f65cf1`.
- #819 fixed the **diagnostics** 4/4 readiness policy: external Google Maps is optional. #820 exact-source authenticated emulator run [41379270961](https://github.com/chatchairins-source/QueuGo/actions/runs/41379270961) passed; retained `11-diagnostics.png` SHA-256 `f00f1dbe6bc25b5db77ec7bee1067f29c35a03fed7162ae87e106c9e1d87f8dc` visually confirms 3/4 core checks, with missing GPS permission and Google Maps clearly optional.
- Separate `RiderAvailabilityReadiness.kt` still included `google_maps` and `overlay` in its **blocking** list. Thus the availability flow could reject a Rider with all real operational requirements satisfied solely because optional external-app conveniences were absent. This contradicts #819 and the native MapLibre design.

## Narrow fix
- Remove only those two optional external-app entries from the *blocking availability checks*. They remain visible as recommendations in Rider Diagnostics.
- Preserve validated network, Firebase dispatch notifications, Android notification permission, GPS/location permission/service, account identity, vehicle inspection, approved selected vehicle and document checks.
- Update exact count assertions from 11 to 9 and add a JVM regression: no Google Maps/overlay still permits readiness, but missing GPS permission blocks it. No online-order mutation or backend change.

## Acceptance and boundaries
1. Require exact-commit Native Pilot Kotlin/JVM/APK CI after PR creation; do not infer acceptance from predecessor #819/#820 green runs.
2. Inspect installed authenticated Rider Home **availability/toggle** behavior with Maps absent and GPS permission present/absent. #820's diagnostics screenshot alone is insufficient for this separate gate.
3. Preserve #750 offline Thai label visibility, #801 actual nationwide offline routing, Customer/Merchant E2E/screenshots, physical GPS/road test, Supabase RPC/finance/RLS and signing gates as OPEN. The Buriram debug archive used in #820 cannot certify nationwide map/routing.
4. No Production deployment, database migration, payment/order mutation, WebView or public APK release in this change.
