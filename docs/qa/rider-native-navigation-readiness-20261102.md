# Rider Native Navigation Readiness — 2026-11-02 (isolated P1)

## Verified problem
- Exact Native PR #736 HEAD at branch creation: `74856f8aca63987054fcc9357c5672486a419e58`; PRs #743/#744 merged. Reviewed Issue #740, CI Recovery #759/#760, Issue #750 and Issue #801 and the current `docs/PROJECT-MEMORY.md`.
- Existing `RiderDiagnosticsSnapshot.readyForDelivery` counted the external `com.google.android.apps.maps` installation as mandatory. The authenticated Rider diagnostics screenshot (11-screen capture in Actions run 41367905563) shows Google Maps red even though QueueGo already embeds native MapLibre map. This wrongly blocks the UI readiness summary for users without an optional third-party navigation app.
- QueueGo's own online route provider remains HTTPS `/map-api/route`. **PMTiles does not provide nationwide offline route calculation**; Issue #801 remains P0 and this patch does not implement it.

## Change
- Require only the four real operational checks for the diagnostic readiness summary: validated internet for server dispatch, location permission, device location service and notifications. Google Maps installation is explicitly optional; keep the external-app install link available to users who choose it.
- Correct the visible diagnostic count to 4, and make optional overlay/background guidance vendor-neutral.
- Extend `RiderDiagnosticsPolicyTest` to prove that toggling Google Maps availability cannot change native readiness, while failure of any core check still blocks it.

## Validation required before merge
1. Exact-commit GitHub Native Pilot: Kotlin compile, Rider JVM unit tests (including `RiderDiagnosticsPolicyTest`), full regression and three-role APK packaging.
2. Installed authenticated Rider diagnostic screenshot: Google Maps appears only under optional items; summary reads 4/4 when four required checks pass. Do not claim screenshot parity from JVM tests.
3. Confirm Customer/Merchant remain unchanged and no Production or Supabase migration occurred.
4. Retain Issue #750 (invisible offline labels), #801 (no nationwide offline routing), Customer screenshot/E2E, physical GPS/road acceptance, and signing/release gates as **OPEN**.

This is a source/UI correctness fix, not an APK release or offline-routing certification.
