# Merchant Google Play handoff — 2026-10-23

## Current outcome

Status: **BLOCKED — no signed AAB produced or uploaded.**

### First-upload decision and generated key — 2026-10-23

- Owner confirmed that `com.queuego.merchant` has never been uploaded to Play
  Console. Prepare a first release; proposed metadata is `1.0.0`, versionCode
  `1`, with the no-previous-upload basis recorded as owner attestation.
- Generated one new Merchant upload key, RSA 3072 / SHA256withRSA, alias
  `queuego-merchant-upload`; private-key access and both generated passwords
  were verified by a keystore import. Certificate valid through 2054-03-10.
- Public signing certificate SHA-256:
  `341d7767b68f4f151714bf13bfed79b9cb69181f2f8c1336a52939038181b994`.
- The private backup `QueueGo-Merchant-Upload-Key-Backup-20261023.zip` is saved
  separately from the store asset pack. No private key, base64 keystore,
  passwords or secret-value file is committed to this repository.
- Installed all four signing values into this repository's GitHub Actions secrets;
  the settings list and readiness job `120207460634` confirm their presence.
  Exact source `e824e7cd` readiness run `39890564617`, job `120209043257`,
  passed keystore password/alias/private-key import and Firebase client checks.
  The CI certificate fingerprint matches the public certificate above.
- The existing certification, backend/backup and physical evidence gates remain
  open. Creating the key is not release certification or Play publication.

The Merchant app is `com.queuego.merchant`, with POS. Its current Native
successor is `feat/native-merchant-pos-offline-20261022` at
`382a746ad9176a03c4b7beafdfd1dbc2dad8b9ce`. Runtime source
`639bedea25710988c5f446ba6c16d1c0d3072a7d` passed Native Pilot
`39882985832`, including Kotlin/unit/APK/16 KB checks and the Android
cached-category instrumentation. This handoff does not change application code.

## Fresh prerequisite evidence

Release Secret Readiness run `39744773027` was retried as a fresh attempt on
2026-10-23. Job `120207460634` failed at `Check required secret presence`.
This is a current secret-presence check on the existing release workflow,
not exact-current-Merchant certification and not a build of an older app.

- `QG_FIREBASE_GOOGLE_SERVICES_JSON_B64`: present. Content validation was skipped
  after the earlier presence failure, so full Firebase readiness is not claimed.
- Present signing inputs: `QG_ANDROID_KEYSTORE_B64`,
  `QG_ANDROID_STORE_PASSWORD`, `QG_ANDROID_KEY_ALIAS`, `QG_ANDROID_KEY_PASSWORD`.
- Missing existing certification inputs: `QG_SUPABASE_SERVICE_ROLE_KEY`,
  `QG_SUPABASE_DB_URL`, `QG_BACKUP_PASSPHRASE`.
- Local release verifier reports `QG_NATIVE_RELEASE_EVIDENCE absent; physical
  and Play gates remain OPEN`.
- Owner confirms no previous Merchant upload. Proposed first metadata is
  `1.0.0` / `1`; Console readback and certification PASS remain unverified.

## Prepared deliverable

`QueueGo-Merchant-Play-Preparation-20261023.zip` contains Thai store listing
text, release notes, the existing 512×512 Merchant icon, a 1024×500 RGB brand
feature graphic, source/readiness metadata, a Data Safety review draft, and
the Console/build handoff checklists. It explicitly states that no AAB is
included and the package is not upload-ready.

Validation: title 16/30, short description 71/80, full description 1271/4000
UTF-16 characters; icon/feature dimensions and modes checked; ZIP CRC and
per-file SHA-256 manifest checked. The graphic was visually inspected. No
mock application screenshots were supplied.

## Remaining release blockers and exact next action

1. Signing/Firebase content validation is complete in run `39890564617`.
   PR #637 merged as `a5300b90`; all four signing secrets are installed.
   Do not regenerate the key or expose credentials. The overall readiness run
   correctly fails on the three missing backend/backup inputs listed above.
2. Observe this package's maximum Play versionCode and choose the next release
   metadata. The current development fallback is not approved release metadata.
3. Satisfy the existing release evidence and backup prerequisites; reconcile the
   current Merchant successor into the certified Native Source of Truth without
   replacing newer code. Preserve the shared signing implementation and gates.
4. Supply authorized Merchant/cashier/staff and real-device/hardware evidence,
   plus current real app screenshots, a dedicated reviewer account and an actual
   support email. Public privacy/deletion URL retrieval remains unverified in
   this session. Final Console declarations remain open.
5. Run the existing certified workflow, validate the signed AAB and fingerprint,
   and then upload to the selected Play track. No Play upload occurred here.

Native/Web lineages and older dirty worktrees remain preserved. No Production
schema, account, order, payment or stock data was changed. POS and the Merchant
Play release remain **NOT DONE**.

References: [preflight attempt 2](https://github.com/chatchairins-source/QueuGo/actions/runs/39744773027/attempts/2),
[Native Pilot](https://github.com/chatchairins-source/QueuGo/actions/runs/39882985832),
[Google signing](https://developer.android.com/studio/publish/app-signing),
[Play store setup](https://support.google.com/googleplay/android-developer/answer/9859152),
[graphic requirements](https://support.google.com/googleplay/android-developer/answer/9866151).

## Verified signing receipt

[Exact-source readiness run](https://github.com/chatchairins-source/QueuGo/actions/runs/39890564617)
validates Firebase clients for all three packages and the Merchant upload key.
Storage validation is skipped because service-role access is absent. Required
secret presence still fails for the three backend/backup inputs; cleanup passes.
This is signing readiness evidence, not physical certification or release PASS.
