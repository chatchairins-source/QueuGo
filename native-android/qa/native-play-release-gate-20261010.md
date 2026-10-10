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
  direct SELECT is denied. Push was subsequently upgraded to ACTIVE v16 after
  backup-native-push-v15-explicit-session-20261010 preserved its exact source
  and metadata. v16 source matches the gated commit; unauthenticated POST
  returned HTTP401. Security advisor categories/counts stayed at 1 INFO/3 WARN.
  Authenticated physical registration/delivery remains untested.
- Firebase Console redirected to Google sign-in; this browser received
  `502 Bad Gateway / Connection refused`. No Firebase project/config was read.
- Local Firebase/TURN/signing credentials and adb/Android SDK are absent.
  Supabase connector exposes no secret read/write operation. These facts do
  not prove secrets absent in Production.
- Native main HEAD 28663031 completed QueueGo Native Android Pilot run
  38042432314 successfully. The Native voice background lifecycle source
  contract and accelerated Android runtime step both passed; launch evidence
  artifact 11666493683 was saved. This closes the software/CI staging gate for
  the microphone foreground service only. Physical two-device background audio
  and Play foreground-service declaration evidence remain OPEN.
- Native main HEAD 559ea783 completed QueueGo Native Android Pilot run
  38043983595 successfully. Three-role fresh-session phone/small-phone/tablet
  launch matrix passed; corrected Longdo Home map mount/background/recreation
  passed; microphone foreground-service background/owned cleanup passed.
  Launch evidence artifact 11667161963 was saved with SHA256
  f6d47055e64c92546126bcbf797e93fca7ee6c31255eda4e127c62ae4f2deaf0.
  The runtime log explicitly leaves authenticated E2E/visual parity, physical
  FCM and real two-device voice audio unverified.
- Backup/Restore run 38039279454 failed at secret validation with
  QG_SUPABASE_DB_URL, QG_SUPABASE_SERVICE_ROLE_KEY and QG_BACKUP_PASSPHRASE
  absent from the Actions environment.
- Release Secret Readiness run 38039197325 failed with
  QG_FIREBASE_GOOGLE_SERVICES_JSON_B64, QG_ANDROID_KEYSTORE_B64,
  QG_ANDROID_STORE_PASSWORD, QG_ANDROID_KEY_ALIAS and
  QG_ANDROID_KEY_PASSWORD absent from the Actions environment.

## Still OPEN

1. Real Firebase project with complete Customer/Merchant/Rider clients and
   QG_FIREBASE_GOOGLE_SERVICES_JSON_B64 configured in GitHub Actions. All three
   packages become required for release; warning-mode debug CI is not certification.
2. Cloudflare TURN environment secret verification and real two-device,
   two-network bidirectional audio, forced relay and complete voice controls,
   including physical foreground/background behavior.
3. Physical foreground/background/killed push, refresh/login/logout/revocation,
   call/order notifications and stale-token exclusion for all three apps.
4. Real order/session/block/private-topic revocation and physical Floating Q.
5. Every Native screen/status compared to live Production Blueprint.
6. Physical permission, back, upload, location, persistence, timeout and lifecycle QA.
7. Actual upload keystore identity/passwords, Play versionCode history, three
   package app names/icons, Data Safety/privacy/account deletion, foreground
   service declaration evidence, and store preflight.
8. P0/P1 zero based on observed evidence, not static source checks.

## Native release packaging protection

One root Gradle signing configuration covers all three apps. Release is explicitly
non-debuggable, with no debug-keystore fallback. Signing inputs are environment-only:
QG_ANDROID_KEYSTORE_PATH, QG_ANDROID_STORE_PASSWORD, QG_ANDROID_KEY_ALIAS,
QG_ANDROID_KEY_PASSWORD, plus QG_ANDROID_EXPECTED_CERT_SHA256 containing the
certified upload/signing certificate fingerprint observed from the intended Play
identity. The pre-build verifier rejects a valid-but-wrong keystore whose
certificate fingerprint does not match. Keystore files/configs are ignored by git.

Release requires QG_NATIVE_VERSION_NAME (x.y.z), per-app release codes
QG_CUSTOMER_VERSION_CODE, QG_MERCHANT_VERSION_CODE, QG_RIDER_VERSION_CODE,
and the observed highest Play Console codes
QG_CUSTOMER_PLAY_MAX_VERSION_CODE, QG_MERCHANT_PLAY_MAX_VERSION_CODE,
QG_RIDER_PLAY_MAX_VERSION_CODE. The verifier fails closed unless every release
code is greater than the explicitly supplied Play maximum; it no longer trusts
hard-coded historical versionCode values.

Every application task containing Release, including direct internal packaging
and signing tasks, depends on verifyNativeReleaseGate. The verifier fails closed without operator-certified
QG_NATIVE_RELEASE_EVIDENCE. The JSON report must reference the exact git HEAD,
p0=0, p1=0, and every gate named in verify-native-release-gate.py with
evidence_file and sha256 of the actual recorded evidence. Every gate requires
status=PASS except security_platform_auth, which may use the documented
PASS_FREE_PLAN_CONTROLS status while QueueGo remains on the Supabase Free plan.
No example PASS report is supplied. Files/hashes prove evidence identity; they do not prove
physical behavior. The operator remains responsible for truthful certification.

The verifier requires all three complete Firebase clients in one Production
project plus the actual keystore. It verifies the keystore alias using keytool
without logging secrets. Debug CI remains usable while physical certification is
pending. Final signed build/inspection and Play Console submission remain gated.

After the hard gate authorizes release packaging, `verify-native-release-artifacts.py`
must inspect the actual signed Customer/Merchant/Rider APK/AAB outputs. It checks
the expected applicationId, per-app versionCode, shared versionName, non-debuggable
APK state, APK signing certificate, AAB JAR signature, SHA-256 for both artifacts,
and the AAB signer certificate fingerprint. It also requires a clean exact-source
checkout and re-checks each artifact versionCode against the observed Play maximum.
Each role's APK/AAB signer must match, each artifact must match
QG_ANDROID_EXPECTED_CERT_SHA256, and the same certified signer identity is
enforced across all three apps.
Source readiness for this verifier does not close the signed-artifact gate; PASS
requires the real release outputs.

## Native voice privacy and Data Safety preflight

All three apps use RECORD_AUDIO for optional order-scoped audio-only calls.
There is no video/camera permission and no audio recording/storage. WebRTC media
is transmitted to the other participant; TURN may relay encrypted traffic.
Supabase stores call/session/order/participant/status/timing metadata, not audio,
SDP or ICE candidates. Private Realtime carries ephemeral signaling; Firebase
transports notifications. Cloudflare TURN is an infrastructure provider.

Privacy disclosure is published: web main b4bb6df5, Pages run38040711859,
HTTP200 and exact reviewed text verified at the public privacy URL. Complete the
Play Data Safety assessment for Native FCM and voice transmission, including
whether ephemeral processing/processor exceptions apply under current Play
definitions. Do not reuse the old Capacitor rule forbidding RECORD_AUDIO or its
SDK inventory. Do not claim a fixed call-metadata retention period until the
actual backend policy is confirmed. Data Safety, retention-policy and Play Console declarations remain OPEN.
The microphone foreground service also requires an accurate Play foreground
service declaration with real user-flow evidence; source permission is insufficient.

Signing reference: https://developer.android.com/build/build-variants
