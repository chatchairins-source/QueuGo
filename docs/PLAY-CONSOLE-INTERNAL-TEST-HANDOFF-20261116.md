# QueueGo — Play Console Internal Test Handoff (2026-11-16)

Goal: replace GitHub Debug APK sideloading with easier Google Play Internal Testing opt-in links for all three *native* Kotlin/Jetpack Compose apps. This is **not** a Production approval.

## Exact source and packages

- Integration PR #1072: https://github.com/chatchairins-source/QueuGo/pull/1072 ; observed SHA d7e84b0b14f6ee421d453e76a409c443eac1532e on non-main branch work-native-reconcile-main-20261114. Re-check exact latest source and branch ancestry before building.
- Customer: com.queuego.customer
- Merchant (QueueMarket): com.queuego.merchant
- Rider (Queue Rider): com.queuego.rider
- All three modules target API level 36, as required for new Google Play phone apps after 2026-08-31.
- The public GitHub APKs dated 2026-11-16 are Debug builds and **not** valid Play upload AABs.

## Engineering deliverables before the owner uses Play Console

1. Run exact-head Native CI and outstanding auth, registration, GPS, notification, offline routing, QA and security gates. Existing production-data/finance/backup/physical-device restrictions remain intact. Do not invent test evidence or bypass release certification.
2. Run the Android/Firebase/signing secret preflight. Inputs and safe handling are specified in docs/closed-beta-secrets-checklist.md. Keep Firebase configuration, private keys, keystore passwords and service-role credentials out of git, logs and messages. Do not generate throwaway signing identity for an intended updateable Play package.
3. Once release evidence is independently certified, use the existing manual .github/workflows/build-native-release.yml on an authorized exact main HEAD. It produces the three signed Release AABs, APKs and native-release-artifacts.json. This workflow is deliberately fail-closed: a successful Debug APK CI is not its substitute.
4. Download/extract the certified workflow artifact locally and run:

    python3 -m unittest discover -s tests -p 'test-play-certified-export.py' -v
    python3 ops/play/export-certified-play-aabs.py --artifact-dir /path/to/certified-artifact --output-dir /path/to/new-empty/play-upload --expected-source-sha FULL_40_CHARACTER_CERTIFIED_COMMIT_SHA

   The required `--expected-source-sha` must be the exact commit independently verified against the release run; it rejects certified-looking artifacts from any other commit before writing output. The exporter re-verifies each AAB checksum, bundle structure and actual signing certificate against the certified report, refuses stale versionCodes and outputs three role-named AABs, PLAY-UPLOAD-MANIFEST.json and SHA256SUMS.txt. It never compiles, signs, claims certification, uploads or publishes on the owner's behalf.
5. Review Play descriptions, native screenshots, data safety, account deletion, ad and content declarations, specialUse/microphone foreground-service disclosures and role-based reviewer access from the exact final artifacts. Use existing docs/play-closed-beta-readiness.md, docs/play-store-listing-draft.md and docs/play-app-access-content-rating.md drafts; they are not a final Console approval.

## The owner's account actions (outside engineering)

- Complete Play Console account registration/payment/verification.
- Create three separate apps using the exact application IDs above and set up Play App Signing/upload certificate.
- Submit the appropriate AAB to each **Internal testing** track, configure tester invitations and publish test opt-in links. This is limited testing, not public search/install.
- Confirm fresh install, registration and order/job flow. Afterward follow Closed Testing / Production access requirements applicable to the account. New personal developer accounts typically need at least 12 opted-in testers continuously for 14 days before requesting Production access.

## Real blockers (do not misreport as complete)

- Owner confirmed the Play Console registration fee was not yet paid. No Play listing or internal test was submitted.
- No signed final AAB or reusable upload-key certificate was verified in this work. Old release readiness docs recorded absent Firebase and Android signing secrets; recheck the **current** secrets preflight rather than relying on historical status.
- Pending exact-head functional/security/backup/role E2E, real-device push and Play declarations cannot be marked passed based solely on Debug APK success.
- Do not alter Production, merge main, bypass a security/financial release gate or share signing material.

Official references:
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/11926878
- https://developer.android.com/studio/publish/app-signing
