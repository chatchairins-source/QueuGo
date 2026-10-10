# Native Google Play gate — 2026-10-10

This is the Native Customer/Merchant/Rider gate. Earlier Capacitor/Web E2E or
permission declarations in docs/play-closed-beta-readiness.md do not certify
these Native applications. No signed release may be packaged or delivered while
any requirement below is OPEN.

## Evidence currently observed

- Main b267f4c2 and RC 2353ca78 fetched; RC CI38038818671 succeeded.
- Three-way integration staging 40660cad retains main documentation and all
  unrelated features. Explicit malformed session IDs now fail closed; only an
  omitted ID may derive exactly one non-revoked session.
- Local full regression, Native voice/push/migration contracts, 207 source
  checks, 61 launcher checks and 12 banner checks passed.
- Production push ACTIVE v15 (verify_jwt=false), TURN ACTIVE v2
  (verify_jwt=true). Both voice and push tables have RLS; anon/authenticated
  direct SELECT is denied. Push v15 still needs the explicit-ID guard rollout.
- Firebase Console redirected to Google sign-in; this browser received
  `502 Bad Gateway / Connection refused`. No Firebase project/config was read.
- Local Firebase/TURN/signing credentials and adb/Android SDK are absent.
  Supabase connector exposes no secret read/write operation. These facts do
  not prove secrets absent in Production.

## Still OPEN

1. Exact combined-tree Full Native CI, then new main CI after integration.
2. Real Firebase project with complete Customer/Merchant/Rider clients and
   QG_FIREBASE_GOOGLE_SERVICES_JSON_B64 configured in GitHub Actions. All three
   packages become required for release; warning-mode debug CI is not certification.
3. Cloudflare TURN environment secret verification and real two-device,
   two-network bidirectional audio, forced relay and complete voice controls.
4. Physical foreground/background/killed push, refresh/login/logout/revocation,
   call/order notifications and stale-token exclusion for all three apps.
5. Real order/session/block/private-topic revocation and physical Floating Q.
6. Every Native screen/status compared to live Production Blueprint.
7. Physical permission, back, upload, location, persistence, timeout and lifecycle QA.
8. Actual upload keystore identity/passwords, Play versionCode history, three
   package app names/icons, Data Safety/privacy/account deletion, store preflight.
9. P0/P1 zero based on observed evidence, not static source checks.

## Native release packaging protection

One root Gradle signing configuration covers all three apps. Release is explicitly
non-debuggable, with no debug-keystore fallback. Signing inputs are environment-only:
QG_ANDROID_KEYSTORE_PATH, QG_ANDROID_STORE_PASSWORD, QG_ANDROID_KEY_ALIAS,
QG_ANDROID_KEY_PASSWORD. Keystore files/configs are ignored by git.

Release requires QG_NATIVE_VERSION_NAME (x.y.z) and per-app advancing
QG_CUSTOMER_VERSION_CODE, QG_MERCHANT_VERSION_CODE, QG_RIDER_VERSION_CODE.
The Play preflight must also verify each code exceeds the actual Play history.

assembleRelease/bundleRelease/packageRelease/validateSigningRelease depend on
verifyNativeReleaseGate. The verifier fails closed without operator-certified
QG_NATIVE_RELEASE_EVIDENCE. The JSON report must reference the exact git HEAD,
p0=0, p1=0, and every gate named in verify-native-release-gate.py, each with
status=PASS, evidence_file and sha256 of the actual recorded evidence. No example
PASS report is supplied. Files/hashes prove evidence identity; they do not prove
physical behavior. The operator remains responsible for truthful certification.

The verifier requires all three complete Firebase clients in one Production
project plus the actual keystore. It verifies the keystore alias using keytool
without logging secrets. Debug CI remains usable while physical certification is
pending. Final signed build/inspection and Play Console submission remain gated.

## Native voice privacy and Data Safety preflight

All three apps use RECORD_AUDIO for optional order-scoped audio-only calls.
There is no video/camera permission and no audio recording/storage. WebRTC media
is transmitted to the other participant; TURN may relay encrypted traffic.
Supabase stores call/session/order/participant/status/timing metadata, not audio,
SDP or ICE candidates. Private Realtime carries ephemeral signaling; Firebase
transports notifications. Cloudflare TURN is an infrastructure provider.

Before release, publish a privacy disclosure covering microphone use, media
transmission, call metadata and the Firebase/Cloudflare providers. Complete the
Play Data Safety assessment for Native FCM and voice transmission, including
whether ephemeral processing/processor exceptions apply under current Play
definitions. Do not reuse the old Capacitor rule forbidding RECORD_AUDIO or its
SDK inventory. Do not claim a fixed call-metadata retention period until the
actual backend policy is confirmed. These documentation items are OPEN.

Signing reference: https://developer.android.com/build/build-variants
