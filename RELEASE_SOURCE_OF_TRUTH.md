# QueueGo Android Release Source of Truth

Timestamp: 2026-10-21T06:20:00+07:00
Repository: chatchairins-source/QueuGo

## Canonical refs

- Latest Web/main observed before this release pass: `2ef2ac18b23a14b5ad8e79d6bc2b0abad3f999e4`.
- Android Release base selected by ancestry + implementation: `9e182e0f5534238e2ca89facdeaff699f6297d09`.
- Release working branch: `work-native-route-bundle-play-release-20261021`.
- Customer HEAD at branch creation: `9e182e0f5534238e2ca89facdeaff699f6297d09`.
- Merchant HEAD at branch creation: `9e182e0f5534238e2ca89facdeaff699f6297d09`.
- Rider HEAD at branch creation: `9e182e0f5534238e2ca89facdeaff699f6297d09`.
- After any release-blocker fix, the single branch HEAD above becomes the Customer/Merchant/Rider Release HEAD together.

## Why this is the current Android implementation

Git ancestry proves:
- PR #491 Native Market merge `bb9f41e6...` is an ancestor of `9e182e0...` (40 commits behind / 0 ahead).
- PR #497 credit/dispatch rule reconciliation merge `f6558546...` is an ancestor of `9e182e0...` (38 behind / 0 ahead).
- PR #515 six-lane integrated HEAD `637d4fcb...` is a direct ancestor of `9e182e0...` (1 behind / 0 ahead).
- PR #515 was merged into the current carriageway line; PR #510 remains the active navigation PR.
- Older PR #426 / #412 and older Rider/Release branches are superseded by this descendant.
- `queuego-native-android-v1` is not the newest implementation: it diverged at `42a78964...`, is 935 commits behind the selected release successor, and contains only two unique old-line commits. One contains a still-valid Passenger Ride fail-closed safety fix; its old Longdo workflow delta is superseded and MUST NOT be copied.

## Active build source sets

Gradle source set authority:
- `native-android/settings.gradle.kts`
  - apps: `:customer`, `:merchant`, `:rider`
  - shared/runtime modules: `:shared`, `:rider-navigation-map`, `:rider-maplibre-osm`
- Shared release/signing authority: `native-android/build.gradle.kts`

Customer production implementation:
- `native-android/customer/build.gradle.kts`
- `native-android/customer/src/main/AndroidManifest.xml`
- `native-android/customer/src/main/java/com/queuego/customer/MainActivity.kt`
- `native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt`
- checkout/cart/tracking/location/Market/Ride/Express implementations under the same `com.queuego.customer` main source set.

Merchant production implementation:
- `native-android/merchant/build.gradle.kts`
- `native-android/merchant/src/main/AndroidManifest.xml`
- `native-android/merchant/src/main/java/com/queuego/merchant/MainActivity.kt`
- Merchant API/dashboard/orders/catalog implementations under the same `com.queuego.merchant` main source set.

Rider production implementation:
- `native-android/rider/build.gradle.kts`
- `native-android/rider/src/main/AndroidManifest.xml`
- `native-android/rider/src/main/java/com/queuego/rider/MainActivity.kt`
- `native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt`
- navigation implementation: `native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/**`
- shared map/voice/session implementation: `native-android/shared/src/main/java/com/queuego/shared/**`

## Release configuration already present

- Customer applicationId: `com.queuego.customer`
- Merchant applicationId: `com.queuego.merchant`
- Rider applicationId: `com.queuego.rider`
- compileSdk / targetSdk: 36
- minSdk: 26
- release signing: environment-only QueueGo keystore credentials; no debug fallback.
- Firebase: `google-services.json` is intentionally not committed; release workflows inject one protected config containing all three Android package clients.
- cleartext traffic: disabled in app manifests.
- release workflow: `.github/workflows/build-native-release.yml`
- certification workflow: `.github/workflows/native-release-certification.yml`
- secret readiness: `.github/workflows/release-secret-readiness.yml`

## Superseded / legacy — DO NOT EDIT OR COPY INTO RELEASE

- Any deleted/retired Longdo Rider renderer implementation such as `LongdoRiderMap.kt`, `RiderLongdoMap.kt`, `MapGLSurfaceView`-based paths.
- Old `queuego-native-android-v1` Longdo download / untransformed Longdo crash-baseline workflow blocks.
- Older Rider navigation branches/PRs (#412 and predecessors) when the same functionality exists in the `9e182e0...` descendant.
- Older unified release #426 implementation when newer six-lane/carriageway descendants contain it.
- Any Web main branch must not be wholesale merged onto the Native release line merely because its commit time is newer.

## Known release-blocker reconciliation before build

Forward-port ONLY the valid Passenger Ride fail-closed behavior from `be6c2db64de34c8f2e85c48f597f020b8cb9e9cd` into the selected current Customer Ride implementation:
- `native-android/customer/src/main/java/com/queuego/customer/CustomerRideScreen.kt`
- matching assertions in `tests/passenger-ride.cjs`

Do not forward-port that old line's Longdo workflow changes.

## Agent rule

Before changing Android release code:
1. Read this file.
2. Confirm the target path is included by current Gradle source sets/import/call sites.
3. Compare ancestry against this release branch HEAD.
4. Never copy a superseded implementation over the current QueueGo Map/MapLibre or current app source.
5. After every release-blocker fix: test/CI, commit, update RELEASE_CHECKLIST.md and this file's final Release HEAD.


## Current release HEAD after blocker reconciliation

- Current Release HEAD: `c76b9bc5c7e4d4a5f26356cba7bd7d94b18acc9e`
- Customer HEAD: `c76b9bc5c7e4d4a5f26356cba7bd7d94b18acc9e`
- Merchant HEAD: `c76b9bc5c7e4d4a5f26356cba7bd7d94b18acc9e`
- Rider HEAD: `c76b9bc5c7e4d4a5f26356cba7bd7d94b18acc9e`
- Passenger Ride fail-closed forward-port: APPLIED from valid old-line commit `be6c2db64de34c8f2e85c48f597f020b8cb9e9cd`.
- Old Longdo workflow delta from `queuego-native-android-v1`: REJECTED / SUPERSEDED by current QueueGo Map/MapLibre workflow.
- Next gate: exact-head Native Pilot + Map/route release checks on this HEAD. Do not edit another Native branch while this gate is active.
