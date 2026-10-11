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
- Production push is ACTIVE v16. Its deployed `index.ts` was read back on
  2026-10-10 and matches the GitHub Source of Truth byte-for-byte; the observed
  Edge bundle SHA-256 is
  `e1ae93f6cc142a77fa7408b894703ed9ac8ad7f9fdaa9b32d40cee2a28758079`.
  `verify_jwt=false` is intentional because the function implements custom
  authentication: dispatch requires the server worker token; user actions require
  Bearer auth resolved through `admin.auth.getUser`, an active QueueGo role, and
  Native token registration binds an active app session. Production currently has
  0 Native tokens, 0 enabled Native tokens, 0 web subscriptions, 0 push outbox rows
  and 0 push config rows, so Firebase Edge-secret readiness and real FCM delivery
  remain UNVERIFIED and the physical push gate stays OPEN.
- Production `queuego-turn` was checked live on 2026-10-10: ACTIVE version 2,
  `verify_jwt=true`, bundle SHA-256
  `747116e17f5c4a2feb2852851e1e346b5a8e456c5a6b521682ab1a5f02ed894c`,
  and deployed `index.ts` exactly matches the GitHub Source of Truth. No
  `queuego-turn` request was observed in the Production log window reviewed,
  so Cloudflare TURN secret readiness, forced-relay behavior and real two-device
  bidirectional audio remain OPEN and must not be inferred from deployment alone.
  Both voice and push tables retain RLS; anon/authenticated direct SELECT is denied.
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
- Production Supabase project `pkypiqhlrmzocysgeqew` now contains the
  private Storage bucket `queuego-native-release-evidence`. It was observed on
  2026-10-10 with `public=false`, ZIP-only MIME types and no anon/authenticated
  client policy for this bucket. Certification and release-readiness workflows
  pin `QG_SUPABASE_URL=https://pkypiqhlrmzocysgeqew.supabase.co`, verify the
  bucket identity/privacy before use and fail closed on any other project.
  Runtime certification remains BLOCKED on Actions credentials plus real
  physical evidence; bucket provisioning itself is no longer a blocker.

## Structured physical evidence semantic contract v2

Physical release evidence remains **OPEN** until real-device capture exists. The
release verifier now requires more than a generic PASS envelope: every physical
gate must be explicitly operator-certified, identify real non-emulator Android
devices with role binding, and include the exact observed checks for that gate.

- Customer/Merchant/Rider push: foreground, background, killed, refresh/login,
  logout revocation, stale-token exclusion, order notification and call notification.
- Voice: real Customer + Rider devices, two distinct networks, bidirectional
  audio, foreground/background continuity, complete controls and hang-up cleanup.
- TURN: forced relay plus observed relay candidate and selected relay path on
  two real devices/two distinct networks.
- Session/order/block authorization: real order, session/order/block
  authorization, session/block revocation and private-topic revocation.
- Rider Floating Q: overlay granted/denied, tap-to-return, notification return
  and automatic stop outside active work.
- Blueprint: all required screens/states observed, pixel-diff reviewed and
  blocking_differences = 0 for Customer, Merchant and Rider.
- Lifecycle: permissions, single-back-to-Home, upload, location, session
  persistence, offline/timeout, reconnect and background/foreground for all 3 roles.

These checks validate semantic completeness and evidence identity only; they do
not infer that screenshots/videos are truthful and do not close any physical gate.

`native-android/qa/capture-physical-release-evidence.py` is the supported
real-device capture helper. It imports the canonical gate roles/checks directly
from `verify-native-release-gate.py`, rejects emulator identities, hashes device
and network identifiers with a per-capture random 256-bit salt that is not stored,
copies/hashes evidence artifacts and can capture an ADB
screenshot. It writes `status=DRAFT` and `operator_certified=false` by default;
`--certify` is accepted only after every canonical check for that gate is
explicitly supplied. Evidence output is required outside the source checkout.
The helper reduces formatting/capture mistakes; it does not observe semantics on
behalf of the operator and therefore does not make a physical gate PASS by itself.

After every gate envelope has been independently certified,
`native-android/qa/finalize-native-release-evidence.py` is the supported bundle
finalizer. It requires a clean exact-source checkout, P0=0/P1=0, derives the full
gate list from the canonical verifier, re-validates every envelope and referenced
artifact, rejects symlinks and reserved certification output, enforces the same
1000-file / 4 GiB bounds as certification, writes `native-release-evidence.json`
and creates the external ZIP plus SHA-256 required by the private Storage intake.
It never promotes DRAFT evidence or invents a gate PASS; its successful output is
only `READY_FOR_PRIVATE_STORAGE_UPLOAD`.

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
8. Play developer identity verification plus registration of all three QueueGo
   package names, with the Play-registered public signing-certificate SHA-256
   bound to the same certified release signing identity used by the APK/AAB build.
9. P0/P1 zero based on observed evidence, not static source checks.

## Android 16 KB page-size readiness

All three Native apps compile and target API 36 on AGP 9.4.0. CI must still
verify the packaged artifacts rather than infer compatibility from the toolchain.
`verify-16kb-page-size.py` runs Android build-tools `zipalign -P 16` and
checks every 64-bit arm64-v8a/x86_64 shared library LOAD segment for alignment
of at least 0x4000. The signed AAB verifier independently requires bundletool
to report `PAGE_ALIGNMENT_16K`. Emulator/physical runtime on a 16 KB page-size
device remains separate observed evidence and is not certified by these static
packaging checks.

## Native release packaging protection

One root Gradle signing configuration covers all three apps. Release is explicitly
non-debuggable, with no debug-keystore fallback. Signing inputs are environment-only:
QG_ANDROID_KEYSTORE_PATH, QG_ANDROID_STORE_PASSWORD, QG_ANDROID_KEY_ALIAS,
QG_ANDROID_KEY_PASSWORD. Keystore files/configs are ignored by git.
The final release also requires QG_ANDROID_SIGNING_CERT_SHA256 and rejects a
keystore whose actual certificate fingerprint does not match that certified
identity. The release-secret preflight prints this non-secret SHA-256 fingerprint
after validating the store password, alias and key password.

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
Evidence file references must be relative paths contained inside the downloaded
certification bundle. Absolute paths, parent traversal, missing files and
malformed non-SHA-256 digests are rejected before release packaging. Each gate
evidence file must now be a JSON envelope bound to the exact gate, exact git HEAD,
certified status, UTC observation timestamp, non-empty all-true checks and at
least one separately hashed artifact file. Physical gates must include hashed
device identities (never raw serials), Android API level, model and QueueGo role.
Customer/Merchant/Rider push and Blueprint evidence must include the matching app
role; Floating Q requires Rider evidence; the lifecycle matrix must cover all
three roles. The two-device/two-network voice gate requires two distinct device
hashes plus two distinct network hashes and network types.
The envelope is also cross-bound to machine-verified release metadata where that
data is independently available: `full_native_ci.github_run_id` must equal the
exact-HEAD Native Pilot run attested by GitHub Actions;
`backup_restore.github_run_id` must equal a successful exact-HEAD
`QueueGo Encrypted Backup Restore Drill` manually dispatched from Native main
whose non-expired non-empty `queuego-backup-*` encrypted artifact still exists;
`production_backend` must identify Production Supabase project
`pkypiqhlrmzocysgeqew`;
`firebase_three_packages.firebase_project_id` must match the loaded Firebase
configuration; `play_store_preflight` must match release versionName, all three
versionCodes and all three observed Play maxima. It must also identify exactly
`com.queuego.customer`, `com.queuego.merchant` and `com.queuego.rider` as the
registered Play package names and record the Play-registered public signing
certificate SHA-256 for each package. Those three registered fingerprints must
match the same certified Native release signing identity. Finally,
`release_signing.signing_certificate_sha256` must match the certified keystore
fingerprint. Mismatched but correctly hashed evidence is rejected.

The verifier requires all three complete Firebase clients in one Production
project plus the actual keystore. It verifies the keystore alias using keytool
without logging secrets. Debug CI remains usable while physical certification is
pending. Final signed build/inspection and Play Console submission remain gated.

After the hard gate authorizes release packaging, `verify-native-release-artifacts.py`
must inspect the actual signed Customer/Merchant/Rider APK/AAB outputs. APK
applicationId/versionCode/versionName/non-debuggable state are checked with Android
build tools. Each AAB is independently inspected with bundletool 1.18.3 pinned to
SHA-256 `a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`
to confirm applicationId, versionCode and versionName. The verifier also re-checks
each release versionCode against the supplied Play maximum, requires a clean source
checkout, verifies APK/AAB signatures and SHA-256 hashes, requires both artifact
signers to match `QG_ANDROID_SIGNING_CERT_SHA256`, and writes release evidence
outside the source checkout.
Source readiness for this verifier does not close the signed-artifact gate; PASS
requires the real release outputs.

The final packaging entry point is `.github/workflows/build-native-release.yml`.
It is manual-only and does not publish a GitHub Release. Before any APK/AAB task
runs it requires a successful `queuego-native-release-certification` artifact
from the dedicated `.github/workflows/native-release-certification.yml` workflow
on the exact same git HEAD, manually dispatched from `queuego-native-android-v1`.
That certification workflow first verifies that the fixed Supabase Storage bucket
`queuego-native-release-evidence` exists and remains `public=false`, then downloads
the operator-produced evidence ZIP over HTTPS/TLS without following redirects while
service-role headers are present. It verifies the supplied bundle SHA-256, safely
rejects ZIP traversal, symlinks, duplicate paths and backslash paths, streams
per-file SHA-256 verification in bounded 1 MiB chunks, and runs
`verify-native-release-gate.py`, and writes `certified-release-metadata.json`.
Before the certification artifact is produced, the certification workflow also
queries GitHub Actions and requires a successful `QueueGo Native Android Pilot`
push run from `.github/workflows/build-native-rider-pilot.yml` on the exact same
git HEAD. That Native Pilot run ID is embedded into certification metadata, and
the final release independently resolves the run again and re-checks HEAD,
workflow path, branch, event and success conclusion.
It also requires a successful exact-HEAD
`.github/workflows/backup-restore-drill.yml` `workflow_dispatch` run and verifies
that at least one live non-empty encrypted `queuego-backup-*` Actions artifact
exists. The Backup Restore run ID is embedded into certification metadata. Final
release resolves that run again, re-checks HEAD/workflow/branch/event/success and
re-checks that the encrypted backup artifact has not expired before packaging.
The final release re-checks that versionName, all three release versionCodes,
all three observed Play maxima and the signing certificate fingerprint match the
certified metadata exactly before packaging. Signed outputs remain internal
Actions artifacts until the Play submission gate is separately completed. The
workflow source being present does not change the current BLOCKED runtime state.

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
