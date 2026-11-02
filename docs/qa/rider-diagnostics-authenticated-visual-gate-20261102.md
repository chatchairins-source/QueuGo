# Rider diagnostics authenticated visual gate — 2026-11-02

## Source and prior acceptance
- Reviewed fresh Native PR #736 HEAD `74856f8aca63987054fcc9357c5672486a419e58`, merged #743/#744, current successor #819 `e98d72e5f798dbf4465ffc823f5747aa41356b08`, Issue #740, CI Recovery issues and `docs/PROJECT-MEMORY.md` before editing.
- PR #819 exact-head [Native Pilot #41374719807](https://github.com/chatchairins-source/QueuGo/actions/runs/41374719807) **completed SUCCESS**: `native-pilot` and `rpc-catalog-regression` passed; country/offline and route-bundle jobs were **skipped**. This is Kotlin/build/JVM acceptance, **not** authenticated screenshot acceptance.
- Existing [authenticated run #41367905563](https://github.com/chatchairins-source/QueuGo/actions/runs/41367905563) captured real Rider/Merchant screens at a predecessor commit, not #819's new 4/4 diagnostics UI.

## This isolated successor
- Trigger the existing secret-backed Rider Android Emulator screenshot workflow on this branch, retaining the existing shared-account concurrency group and secret-presence gate. No credentials are written into screenshots or metadata.
- Check actual visible UIAutomator nodes for all four core diagnostics, a `ผ่าน N/4 รายการหลัก` summary and `Google Maps (ทางเลือก)` marked `รายการแนะนำ` before counting the real `11-diagnostics.png` screenshot. Reject stale/invisible hierarchy nodes and old five-check summary. Keep the original full screenshot review.
- New Python regression tests cover 0/4–4/4, missing required checks, optional Maps, old summary and stale bounds. The screenshot workflow runs them before compiling/installing real APKs.
- The existing screenshot workflow uses a **pinned Buriram regional debug archive** for this branch. Its screenshot is acceptable to review **diagnostics UI only**, not nationwide map, offline routing or Thai label acceptance.

## Release boundaries / next actions
1. Verify exact-head authenticated screenshot CI, inspect the retained actual `11-diagnostics.png` and metadata. Do not claim UI accepted if capture is skipped or fails.
2. Preserve PR #819's source fix; integrate only after real screenshot acceptance. Recheck current Native #736 HEAD before any merge.
3. Keep [Issue #750](https://github.com/chatchairins-source/QueuGo/issues/750) visible Thai labels, [Issue #801](https://github.com/chatchairins-source/QueuGo/issues/801) nationwide offline routing, Customer/Merchant E2E, GPS/physical road, Auth/RLS/finance and release-signing gates **OPEN**.
4. No Supabase Production, order, financial, auth or migration changes in this successor. No Production APK publication.
