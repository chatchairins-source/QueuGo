# Offline Thailand label visual acceptance — 31 October 2026

Status: **BLOCKED — not visually certified**. This evidence is for diagnosis, not a release claim.

- Source inspected: #753 at `9a17439d98909facd2cfbcb888ac858cdf696347`, hosted installed-country run [41197769844](https://github.com/chatchairins-source/QueuGo/actions/runs/41197769844), artifact `13041319603` (`queuego-installed-country-runtime`).
- Downloaded the actual ZIP and opened `country-screenshots/bangkok.png`, SHA-256 `a63fd16620f0bcd100c19b95c8bbc13006268497384dcc1fe54f428e06798a1a`. Roads/buildings are visible around Democracy Monument, but **no readable street/place labels**. The five instrumentation tests passed, which does **not** certify label visibility.
- Logcat verifies bundled Noto Sans Latin and Thai PBF digests, `asset://fonts/{fontstack}/{range}.pbf`, zoom 18/pitch 40, and 27 queried symbol features including ถนนราชดำเนินกลาง and ถนนดินสอ. These establish data presence, not painted text. The serialized base JSON has no appended label layers, so runtime `Style.getLayer` diagnostics are required.
- This clean successor carries #753 and #756's Android instrumentation/runtime symbol-layer assertions and PR-head country workflow pinning onto unreleased Native #736 at `0d7e5aae14f1e7d24f92c55926944b82b0dd12e6`, without replacing its `docs/PROJECT-MEMORY.md` or touching Production.

## Required next actions

1. Wait for exact-head Native Pilot and Installed Country CI, download the new screenshot/logcat artifact, inspect runtime layer visibility, text field/font/size/opacity and glyph-load errors, and compare the **actual pixels**. Do not infer label visibility from `queryRenderedFeatures`.
2. Fix the identified MapLibre glyph or symbol rendering root cause in a separate reviewed patch and rerun offline airplane-mode tests plus visual screenshots in Bangkok and other regions.
3. Keep #750 open until visible Thai/Latin labels are verified; separately require real-device routing, Auth/finance E2E and release gates. PMTiles road rendering is not offline routing.

No Production writes, mock overlay, fake screenshot or APK publication occurred in this change.
