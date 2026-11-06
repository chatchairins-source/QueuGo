# Rider in-process routing implementation — NOT release acceptance

Based on PR #872 `859e1d33fcbdc8a84983a1e023b3d63dcd38c01d`; integrates #866 without dropping #872 metrics validation. Ancestry checked locally: #736/#824/#867/#871 are ancestors of #872; #866 was not. None of those PRs was merged into main at inspection. Main was `2c44d6f9313d2efdeb6dceef4c03d3f1ea9aa27e`.

## Runtime changes

Rider now selects an in-process Valhalla provider when its APK contains `assets/routing/`. A missing package retains existing online routing. A partial/bad package fails closed, with no online fallback. `ValhallaOfflineRouteProvider` requests real motorcycle routes; it converts polyline6, metrics and maneuver narratives into the existing NavigationRouteProvider contract, preserving the coordinator, reroute path, cache, matching, GPS and camera. Roundabout narratives retain exit numbers in the HUD. The graph is copied with bounded streaming to app-private storage, checked by SHA-256 and size, and atomically installed; each new process rechecks the cached bytes and creates its actor. Tile URL, traffic and elevation network sources are disabled. No PMTiles are interpreted as routing data.

`QG_OFFLINE_ROUTING_ASSETS` adds the graph only to Rider; Customer/Merchant/POS are unchanged. `ops/maps/offline_package.py android-routing` packages the existing Valhalla graph build. The new workflow builds a real, pinned Thailand graph, tests JVM code, builds Rider and runs its actual installed graph in two separate airplane-mode instrumentation processes. The workflow does not publish an owner APK or certify map rendering/auth/push.

## Actual local evidence

- OSM source: Geofabrik Thailand `261104`; timestamp `2026-11-04T21:21:03Z`; 329,816,563 bytes; SHA-256 `eed0526676882ba1b94c0f9b0dd49ed25b7495150af4040822157203f2ba3457`; header bounds `[97.135615,5.597877,105.639210,20.468752]`; ODbL-1.0, © OpenStreetMap contributors.
- Existing build_engine_graph.py + pyvalhalla 3.9.1 built a real graph, exit 0, 227.918 seconds, peak RSS 3,617,804 KiB. Final tar 1,110,384,640 bytes; exact hashes in graph-manifest.json. This is road graph data, separate from rendering PMTiles.
- All six probes ran against the **packaged tar**, using a local Valhalla actor: Buriram, Bangkok, Chiang Mai, Khon Kaen, Hat Yai, Bangkok–Chiang Mai (883.099 km). Detailed summaries in linux-probes.json. Initial cross-region probe hit Valhalla's 500 km default; package config now allows 2,000 km.
- Kotlin compiler 2.3.21 compiled all navigation non-UI source, including the real Valhalla AAR API and Android package loader, against Android 36 API; direct JUnit 4.13.2 passed 176 tests. This isolated fallback omits three renderer-dependent test classes and is **not** Gradle/Android Build acceptance. Exact Gradle uses repository-pinned toolchain versions.
- `node tests/native-android-source.cjs`: 489 checks, 0 failures.
- Valhalla 0.6.4 AAR ELF LOAD alignment: arm64-v8a and x86_64 are 16384; 32-bit libraries are 4096. Final APK packaging/signature checks remain required.
- Local Gradle 9.6.0 was attempted. Plugin resolution failed because the Java process cannot reach the configured proxy (`Network is unreachable`; an attempted loopback proxy gives `Connection refused`). The Android plugin POM is downloadable with curl; this is not evidence that repository-pinned AGP is invalid. No toolchain downgrade or test weakening was made.

## Still blocked

Exact-head hosted Gradle/JVM + Debug Build; Android native engine/tile-format compatibility; installed airplane-mode routing/rerouting and process restart; directed one-way/turn-restriction source probes; actual map-label/UI screenshots; GPS camera/speed zoom; authenticated orders/login/push and backend read-only reconciliation; physical road testing; complete release APK size/hash/signature. The new routing workflow intentionally fails if its real package or native engine cannot run. Six Linux probes do not prove nationwide address coverage, all road restrictions or Android behavior. Issue #801 remains open. No main merge or Production data/schema mutation.

Concurrent continuation: PR #872 advanced to e02e76e4 while this implementation ran. Preserve its explicit missing-steps legacy compatibility, reject explicit null/non-array steps, and integrate its new regressions. Final hosted SHA must be read after connector publication; local Git push has no credentials.
