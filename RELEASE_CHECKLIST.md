# QueueGo Android Release Checklist

Timestamp: 2026-10-21T06:24:00+07:00

Component: Shared Release
HEAD: c76b9bc5c7e4d4a5f26356cba7bd7d94b18acc9e
Build: PENDING exact-head CI
Tests: Passenger Ride fail-closed regression added; full exact-head gates pending
Release blocker: build/test evidence
Status: IN PROGRESS
NEXT: inspect exact-head Native Pilot and Map gates; fix only first real blocker.

Component: Customer
HEAD: c76b9bc5c7e4d4a5f26356cba7bd7d94b18acc9e
Build: PENDING exact-head CI
Tests: startup/login/core flow source + CI pending on exact HEAD
Release blocker: exact-head build/runtime evidence; physical authenticated acceptance remains HUMAN BLOCKER
Status: IN PROGRESS
NEXT: Customer debug/release/APK/AAB gate after exact CI.

Component: Merchant
HEAD: c76b9bc5c7e4d4a5f26356cba7bd7d94b18acc9e
Build: PENDING exact-head CI
Tests: Market/Grocery implementation inherited from green PR #491; exact release HEAD revalidation pending
Release blocker: exact-head build/runtime evidence; physical authenticated catalog acceptance remains HUMAN BLOCKER
Status: IN PROGRESS
NEXT: Merchant debug/release/APK/AAB gate after exact CI.

Component: Rider
HEAD: c76b9bc5c7e4d4a5f26356cba7bd7d94b18acc9e
Build: PENDING exact-head CI
Tests: carriageway/QueueGo Map successor inherited; Map Engine on parent 9e182e0 passed run 39433299476
Release blocker: exact-head Native build/runtime evidence; physical GPS/road/navigation remains HUMAN BLOCKER
Status: IN PROGRESS
NEXT: Rider online/offer/job flow automated gate, then physical acceptance.

Shared known external blockers:
- approved public HTTPS QueueGo Map runtime configuration
- release signing/Firebase secrets if not available to workflow
- Play Console observed max versionCodes and declarations
- physical Android Customer/Merchant/Rider acceptance
- signed AAB upload / Closed Testing owner actions

Rules:
- Read RELEASE_SOURCE_OF_TRUTH.md before edits.
- Do not reopen Admin: Admin source is frozen after green exact/post-merge gates.
- Do not merge/copy Longdo legacy implementation into current MapLibre release.
- Do not mark DONE without exact SHA + build/test evidence.
