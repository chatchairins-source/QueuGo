# Rider Home online lease vs GPS readiness — 2026-11-02

## Source and issue
- Re-read Native #736 HEAD `74856f8aca63987054fcc9357c5672486a419e58`, merged #743/#744, current successors #819 `ee200ee3` and #821 `9722e05c`, Issue #740, CI Recovery issues and `docs/PROJECT-MEMORY.md` before editing.
- Actual authenticated emulator images in [Issue #822](https://github.com/chatchairins-source/QueuGo/issues/822) show Rider Home "พร้อมรับงานแล้ว" while diagnostics later show GPS permission missing. Frames are separated in time, so this is a *presentation consistency risk*, **not proof that an unsafe dispatch occurred**.
- In `QueueGoRiderApp.kt`, the Home card and dock previously used `snapshot.online` alone to display "พร้อมรับงานแล้ว". This is a server lease, not evidence of current Android location readiness. The online switch must continue to reflect that server state truthfully.

## Narrow isolated change
- Add a pure Kotlin presentation function for the three states: offline; server-online with missing permission/location service; server-online with both checks present.
- Keep the server switch checked when the server reports online, but show an amber warning and explicitly say the online lease is still active when GPS is unavailable. Do not auto-disable a server lease or alter orders, dispatch, location permissions, backend, Auth/RLS, or financial data.
- Apply the same headline to the Home dock, avoiding contradictory "ready" copy. Add JVM tests for permission/service combinations and repeated permission restoration.
- This is a **UI consistency mitigation only**. It does not certify server availability leases or real dispatch safety.

## Required evidence before integration
1. Exact-head Native Pilot JVM/three APK/regression and RPC catalog jobs must complete successfully.
2. Authenticated emulator: timestamped Home + Diagnostics screenshots with GPS permission denied, granted, service disabled, and process restart; verify server online readback and no unsafe activation. No real customer orders.
3. Preserve independent #750 Thai offline labels, #801 nationwide offline routing, Customer/Merchant parity, physical GPS, E2E, finance/Auth/RLS and signing gates. No Production or public APK release from this change.
