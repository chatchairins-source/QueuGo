# QueueGo — Google Play Closed Beta Readiness

Technical source reconciliation: 2026-11-21 (Integration `73f9d57212fd8c4029a0e3341fe2d007675ccee9`); final artifact/network/Play review remains pending  
Scope: Native Android Customer, Merchant, Rider apps. Legacy Capacitor/Web build notes are historical context only and do not certify the Native release.

## Current release gate

Closed Beta release remains **BLOCKED** until every hard gate below passes:

- Account deletion + privacy: PASS
- Service area: PASS
- Security code gate: PASS
- Core production backend/Web E2E: PASS
- Native full physical E2E / Blueprint parity: BLOCKED — device evidence is incomplete
- UGC / chat safety implementation: PASS
- Android target API 36 static gate: PASS
- Native microphone foreground-service source + accelerated Android lifecycle: PASS — physical two-device audio/TURN remains BLOCKED
- Backup/Restore drill: BLOCKED — latest observed secret preflight 42630768316 / job 136766268117 on 2026-11-21 still lacked QG_SUPABASE_DB_URL, QG_SUPABASE_SERVICE_ROLE_KEY and QG_BACKUP_PASSPHRASE; encrypted backup and actual restore proof remain required.
- Supabase Auth: PASS WITH FREE-PLAN CONTROLS — 12-character upper/lower/number/symbol signup policy is shared across all roles; leaked-password protection remains a Pro-only deferred hardening item
- Native Firebase push source/transport + system notification tap routing: PASS — Production `queuego-push` v20 is ACTIVE and source-identical to GitHub, Android uses audible `queuego_orders_v2`, and Customer/Merchant/Rider cold/warm notification tap routing passes Native Pilot; physical background receipt remains BLOCKED until exact-release real-device receipt, sound and tap evidence is certified; validated Firebase inputs alone do not close this gate
- Play Data Safety + foreground-service declarations: BLOCKED — Play Console certification/evidence not completed
- Android developer verification + package registration: BLOCKED — Play Console evidence must confirm developer identity verification and registration of all three QueueGo package names
- Android signing inputs: VALIDATED by secret preflight run 42630768316 / job 136766268117 on 2026-11-21: Firebase config, keystore, alias and passwords passed. This is input validation only; no signed final AAB or exact-release certification is established.
- Native release certification intake: BLOCKED — Production private bucket `queuego-native-release-evidence` now exists on project `pkypiqhlrmzocysgeqew` and is ZIP-only with no client Storage policy; Actions evidence-storage credentials/readiness are still not configured and no physical evidence bundle is certified

Current Production push evidence (2026-10-13): `queuego-push` version 24 is ACTIVE with `verify_jwt=false` under the reviewed worker-token/authenticated-user custom boundary; both deployed `index.ts` and `rider-offer-message.mjs` match the Rider working branch byte-for-byte. Production currently has two enabled Rider Native token rows, both carrying a session id, but only one is tied to a live non-revoked active session under the same server-side session check used by dispatch. In the preceding 24-hour review window the Native Rider outbox recorded 32 order deliveries and 3 `push_test` deliveries with FCM HTTP 200. These facts prove Production transport/session wiring only; they do **not** certify foreground/background/screen-off receipt, sound, vibration or notification-tap behavior on a physical Android device.

The final Native release workflow is manual-only and fail-closed. It cannot package signed APK/AAB outputs until a trusted exact-HEAD certification artifact, complete Firebase/signing secrets, observed Play versionCode history, Backup/Restore certification, physical push/voice/lifecycle evidence, Blueprint parity, Play declarations and P0/P1=0 are all certified. The trusted producer is `.github/workflows/native-release-certification.yml`; it accepts only a SHA-256-pinned bundle from private Supabase Storage, validates every gate, and binds release metadata before `.github/workflows/build-native-release.yml` can package. Source readiness does not make Closed Beta unblocked.

### Supabase Auth / password hardening

QueueGo's current Supabase project remains on the Free plan. Supabase Security Advisor reports leaked-password protection disabled as a **WARN**, and current Supabase documentation states that leaked-password protection is available on **Pro Plan and above**.

Closed Beta therefore uses a documented compensating-control decision rather than pretending the feature is enabled:

- shared `queuego-password-policy.js`
- minimum 12 characters
- uppercase + lowercase + number + symbol
- enforced in Customer, Merchant and Rider registration UI/runtime
- covered by `tests/password-policy.cjs`
- release gate value: `PASS_FREE_PLAN_CONTROLS`

When QueueGo upgrades to Pro, enable leaked-password protection and promote the Auth gate to full `PASS`.

Supabase reference:
- https://supabase.com/docs/guides/auth/password-security

Google Play's User Data policy requires appropriate security measures for personal/sensitive data, including modern cryptography in transit; it does not name Supabase leaked-password protection as a mandatory Play feature:
- https://support.google.com/googleplay/android-developer/answer/10144311


## Google Play policy checkpoints

### Android developer verification / package registration

Effective 2026-09-30 in Thailand, Google Play developers must have a verified developer identity and Play package names must be registered for Android developer verification. Google may auto-register eligible Play apps, but QueueGo release evidence must confirm the actual Play Console state rather than infer it.

The `play_store_preflight` evidence envelope must explicitly certify all four checks:
- `developer_identity_verified`
- `customer_package_registered` for `com.queuego.customer`
- `merchant_package_registered` for `com.queuego.merchant`
- `rider_package_registered` for `com.queuego.rider`

It must also bind `registered_packages` to exactly:
`["com.queuego.customer","com.queuego.merchant","com.queuego.rider"]`.
The evidence artifact should be a current Play Console export or screenshot set that shows the verified developer/account state and the package registration state; the envelope must keep the exact release `version_name`, release versionCodes, observed Play maximum versionCodes, UTC observation timestamp, and SHA-256-hashed artifact references required by the canonical release gate.

Official references:
- https://support.google.com/googleplay/android-developer/answer/16984799
- https://developer.android.com/developer-verification


### Target API

For phone/tablet apps submitted after 2026-08-31, Google Play requires new apps and updates to target Android 16 / API 36 or newer.

The Native Android Customer, Merchant and Rider modules each use:

- compileSdk = 36
- targetSdk = 36

The Native release gate and CI are the certification source. Earlier Capacitor-generated Android checks remain historical only and must not be used to certify the Native apps.

Policy sources:
- https://support.google.com/googleplay/android-developer/answer/11926878

### Android permissions

QueueGo Native release builds are limited to permissions required by the active feature set.

Expected across the Native apps as applicable:
- INTERNET
- ACCESS_NETWORK_STATE
- ACCESS_COARSE_LOCATION
- ACCESS_FINE_LOCATION
- POST_NOTIFICATIONS
- RECORD_AUDIO for optional order-scoped audio calls
- FOREGROUND_SERVICE and FOREGROUND_SERVICE_MICROPHONE for an active user-started voice call

Rider additionally uses SYSTEM_ALERT_WINDOW and FOREGROUND_SERVICE_SPECIAL_USE for the approved Floating Q / active Rider return-control flow.

Still forbidden unless a future reviewed feature explicitly requires them:
- CAMERA
- READ_EXTERNAL_STORAGE
- WRITE_EXTERNAL_STORAGE
- READ_MEDIA_IMAGES
- ACCESS_BACKGROUND_LOCATION

Image selection/upload uses the system picker or scoped file chooser rather than broad media-library access. QueueGo does not request background location.

The microphone foreground service is started only from visible outgoing/answer user actions before media creation. Incoming FCM does not start microphone access in the background. The service keeps an already-started call eligible through background lifecycle and stops on call/session termination. Physical two-device/background audio certification and the Play foreground-service declaration remain OPEN.

#### Play Console foreground-service declaration draft

Google Play requires Android 14+ apps to declare each foreground-service type used in Play Console, describe the feature and user impact if it is deferred/interrupted, and provide a demo video showing how the user triggers the feature. Source readiness below does not complete the external Console declaration.

**Customer / Merchant / Rider — `microphone`**
- Functionality: order-scoped in-app WebRTC voice call between authorized QueueGo order participants.
- User initiation/perception: starts only after the user taps the visible call/answer action; an ongoing QueueGo call notification remains visible and provides a hang-up action.
- Why immediate: deferring or interrupting the service would stop microphone capture and break the active real-time call the user explicitly started or answered.
- Stop boundary: service ends with call/session termination; incoming FCM alone never starts microphone capture.
- Demo video evidence required: show the visible call/answer action, Android microphone permission where applicable, active-call UI, foreground notification, backgrounding the app while the already-started call remains active, and user hang-up returning the service to stopped state.

**Rider only — `specialUse`**
- Manifest subtype: `Active QueueGo Rider navigation return control`.
- Functionality: while an authenticated Rider account is active and the Rider leaves QueueGo for navigation/work context, `RiderReturnService` keeps a visible notification and optional Floating Q overlay that returns the Rider to the active QueueGo work screen.
- User perception: persistent notification provides the return action; Floating Q is shown only when overlay permission is granted.
- Why immediate: interruption while the Rider is actively navigating/working removes the user-visible return control to the live job.
- Stop boundary: the service is `START_NOT_STICKY`, refuses pending/suspended/inactive Rider sessions, stops if the cached Rider is not active, and removes its overlay on destroy.
- Demo video evidence required: show active Rider state, user-triggered navigation/work handoff, foreground notification, optional Floating Q when permission is granted, tap-to-return behavior, then the service/overlay stopping outside the active-work boundary.

Play Console declaration status remains **BLOCKED** until real-device videos and final Console entries exist.

Policy sources:
- https://support.google.com/googleplay/android-developer/answer/13392821
- https://support.google.com/googleplay/android-developer/answer/16559646
- https://developer.android.com/about/versions/14/changes/fgs-types-required
- https://developer.android.com/develop/background-work/services/fgs/service-types

### Account deletion

QueueGo provides both required deletion paths:

1. In-app account deletion from account/profile settings.
2. External web deletion page:
   - `docs/account-deletion.html`

The external page authenticates the account owner and performs the same server-side deletion request as the in-app flow. Active work prevents deletion until the order/job is completed or cancelled.

Privacy policy:
- `docs/privacy.html`

Policy sources:
- https://support.google.com/googleplay/android-developer/answer/13327111
- https://support.google.com/googleplay/android-developer/answer/10144311

### User Generated Content / order chat

QueueGo Customer ↔ Rider order chat is UGC and 1:1 interaction.

Implemented controls:
- Users must accept QueueGo chat/community rules before posting.
- Prohibited behavior is defined in `docs/community-guidelines.html`.
- Users can report another user.
- Users can report an individual message.
- Users can block/unblock the counterpart.
- Either-side blocking stops new chat messages server-side.
- Admin has a moderation queue and can mark reports under review / resolved / dismissed.
- A report stores an immutable content snapshot so moderation still works after normal chat retention expires.
- Reported images are reviewable only from the report snapshot.
- Direct client access to moderation tables is denied.
- Anonymous users cannot call UGC moderation RPCs.
- Anonymous Data API table privileges on `order_chat_messages` are revoked; signed-in chat access remains explicit and RLS-controlled (`20261007023348_ugc_chat_revoke_anon_table_privileges`).
- Dead Merchant legacy order-chat implementation has been removed.

Policy sources:
- https://support.google.com/googleplay/android-developer/answer/9876937
- https://support.google.com/googleplay/android-developer/answer/12923286

### Closed testing

If the Google Play developer account is a **personal account created after 2023-11-13**, current Play policy requires:
- at least 12 testers,
- opted in continuously for at least 14 days,
- then an application for Production access.

This requirement is conditional on the Play developer account type/date. Do not mark it required for QueueTech until the Play Console account is checked.

Policy source:
- https://support.google.com/googleplay/android-developer/answer/14151465

### Target Audience / minor access

Closed Beta decision for all three apps:
- **Customer: 18 and over only**
- **Merchant: 18 and over only**
- **Rider: 18 and over only**
- **Restrict Minor Access: Enable for Closed Beta/Pilot**

This is a Pilot compliance boundary, not a permanent product promise. Any later decision to target users under 18 requires a fresh Families/privacy/UGC/ads review before changing the Play Console audience.

Policy source:
- https://support.google.com/googleplay/android-developer/answer/9867159

## Data Safety draft

QueueGo Closed Beta certification scope is the three Native Android apps. Native CI rejects WebView, so Data Safety answers must describe data collected or transmitted by the Native Customer, Merchant and Rider clients together with the QueueGo backend and active infrastructure providers. Legacy Capacitor/WebView behavior is not release evidence and must not be used to answer the Native Play Console questionnaire.

Google Play states that transfers to qualifying service providers, legal-purpose transfers, and some user-initiated transfers are not necessarily declared as "sharing." QueueTech must confirm the contractual role of infrastructure/map providers before final submission.

Policy source:
- https://support.google.com/googleplay/android-developer/answer/10787469

### Customer app — expected collected data

| Play data type | QueueGo use | Typical purpose | Collection |
|---|---|---|---|
| Name | Customer account/profile | Account management, app functionality | Required |
| Email address | Authentication when provided / generated auth identity | Account management, security | Required by auth path |
| User IDs | QueueGo/Auth user identifiers | Account management, security, order ownership | Required |
| Address | Delivery address | App functionality | Required when ordering delivery |
| Phone number | Account/contact | Account management, order coordination | Required |
| Approximate location | Location permission may expose coarse location | App functionality | Optional until location feature used |
| Precise location | Delivery point, service-area and distance checks | App functionality | Required when using location-assisted delivery |
| Purchase history | Orders and transaction history | App functionality, fraud prevention, accounting | Required when ordering |
| Other in-app messages | Customer ↔ Rider order chat | App functionality, safety | Optional |
| Voice or sound recordings | Order-scoped peer-to-peer WebRTC call media | App functionality | **Not declared as collected while the Google Play E2EE exception remains satisfied**; re-review if any intermediary can read media, or if recording/transcription/SFU/server media processing is introduced |
| Photos | Chat images, delivery/support evidence | App functionality, safety/support | Optional |
| Other user-generated content | Reviews, notes, support descriptions | App functionality, safety/support | Optional |
| Device or other IDs | Push token, session/device identifiers | Notifications, security/fraud prevention | Optional/functional |

### Merchant app — expected collected data

| Play data type | QueueGo use | Typical purpose | Collection |
|---|---|---|---|
| Name / business name | Shop account/profile | Account management, app functionality | Required |
| Email address | Authentication when applicable | Account management, security | Required by auth path |
| User IDs | QueueGo/Auth identifiers | Account management, security | Required |
| Address | Shop pickup/store address | App functionality | Required for active delivery shop |
| Phone number | Merchant account/contact | Account management, order coordination | Required |
| Approximate / Precise location | Shop coordinates and pin | App functionality | Required for location-based delivery |
| Purchase history | Orders/merchant transactions | App functionality, accounting | Required |
| Other financial info | GP, cash settlement/receipt state where applicable | Accounting, app functionality | Functional |
| Other in-app messages | Merchant ↔ Admin support | App functionality/support | Optional |
| Voice or sound recordings | Order-scoped peer-to-peer WebRTC call media | App functionality | **Not declared as collected while the Google Play E2EE exception remains satisfied**; re-review if any intermediary can read media, or if recording/transcription/SFU/server media processing is introduced |
| Photos | Shop media, evidence, GP slip where used | App functionality/accounting/support | Optional |
| Device or other IDs | Push/session identifiers | Notifications, security | Optional/functional |

### Rider app — expected collected data

| Play data type | QueueGo use | Typical purpose | Collection |
|---|---|---|---|
| Name | Rider profile | Account management | Required |
| Email address | Authentication when applicable | Account management, security | Required by auth path |
| User IDs | QueueGo/Auth/Rider identifiers | Account management, job ownership | Required |
| Address | Rider profile if supplied | Account management | Optional |
| Phone number | Rider account/contact | Account management, job coordination | Required |
| Approximate / Precise location | Availability, assignment, route and delivery workflow | App functionality | Required while using Rider work flow |
| Other financial info | Rider earnings/cash advance/accounting records where applicable | App functionality/accounting | Functional |
| Other in-app messages | Customer ↔ Rider order chat | App functionality, safety | Optional |
| Voice or sound recordings | Order-scoped peer-to-peer WebRTC call media | App functionality | **Not declared as collected while the Google Play E2EE exception remains satisfied**; re-review if any intermediary can read media, or if recording/transcription/SFU/server media processing is introduced |
| Photos | Pickup/delivery proof and chat/evidence images | App functionality, safety | Required for proof steps / optional for chat |
| Device or other IDs | Push token, session/device identifiers | Notifications, security | Optional/functional |

### Provider sharing classification — current Native source, final network review pending (2026-11-21)

Google Play defines "sharing" as transferring user data to a third party. A transfer to a qualifying service provider that processes data on the developer's behalf and instructions does not have to be declared as sharing. QueueGo applies this definition to the Native Android data flows below.

Current Native draft: **PENDING_FINAL_NATIVE_NETWORK_REVIEW**. Complete the final AAB dependency/network and contractual review before entering provider-specific answers. Source-only SDK inventory is not proof of all transfers. Longdo answers from the previous draft are superseded for the current Native source; re-assess Longdo only if a still-active artifact or backend path actually uses it.

| Provider | QueueGo use / transfer | Public contractual evidence | Play Data Safety treatment |
|---|---|---|---|
| Supabase | Auth, database, Storage, Realtime, account/order/location/chat/support data | Supabase DPA states Supabase acts as processor/service provider and processes Covered Data on behalf of and under Customer instructions | **Collected: Yes. Shared: service-provider exception may be used** for Supabase processing under the applicable Supabase agreement/DPA |
| Firebase Cloud Messaging / Google | Native push transport; FCM/Installations SDK metadata, installation ID/token and app version as applicable | Firebase Data Processing and Security Terms govern Customer Data; Firebase privacy guidance states Google generally operates as processor/service provider for Firebase customer data | **Collected: Yes** for applicable FCM/Installations data. **Shared: service-provider exception may be used** for Firebase processing covered by those terms |
| Cloudflare Realtime TURN | TURN fallback for Native WebRTC voice calls. Cloudflare documents that WebRTC media is encrypted end-to-end between peers before reaching the TURN relay, so Cloudflare relays encrypted packets and cannot inspect media content; it still processes relay metadata such as client IPs, ports and session timing. | Cloudflare Realtime TURN FAQ documents the media-encryption and relay-metadata boundary | **Voice media:** use the Google Play E2EE collection exception only while this peer-only architecture remains unchanged. **Relay metadata:** include in the applicable device/network metadata assessment and complete final Play/contractual classification before submission. |
| MapLibre / OpenStreetMap raster endpoint | Shared Native online map/picker uses MapLibre; the configured public raster template is `https://tile.openstreetmap.org/{z}/{x}/{y}.png`. Supabase `qg_search_places` handles Native reverse lookup. Rider also has packaged offline map data and on-device Valhalla routing. | Verify the final merged dependencies and actual endpoint traffic, including IP/tile coordinates and any backend forwarding; MapLibre library presence alone does not establish a third-party transfer. | **PENDING_FINAL_NATIVE_NETWORK_REVIEW** for actual online-map/network data; do not copy the retired Longdo-only answer. Offline capability alone does not mean the complete app transmits no data. |
| Google Maps app opened by the user | Optional explicit external-map handoff, where enabled; Native Rider also implements its own offline routing and turn guidance | Assess the actual user action and destination against the applicable Play definition | Review that specific transfer separately from online tiles, Supabase, Firebase and other active providers; external handoff is not the only Native navigation path. |

Code evidence:
- Customer/Merchant shared map implementation: [QueueGoMap.kt](https://github.com/chatchairins-source/QueuGo/blob/73f9d57212fd8c4029a0e3341fe2d007675ccee9/native-android/shared/src/main/java/com/queuego/shared/QueueGoMap.kt) uses MapLibre and the OSM raster template for online maps/pickers.
- Rider includes [ValhallaOfflineRouteProvider.kt](https://github.com/chatchairins-source/QueuGo/blob/73f9d57212fd8c4029a0e3341fe2d007675ccee9/native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/ValhallaOfflineRouteProvider.kt) for on-device routing. Runtime/physical acceptance must be read from exact-source evidence; the presence of this code does not certify a release.
- Native reverse lookup uses Supabase `qg_search_places` via [QueueGoNativeApi.kt](https://github.com/chatchairins-source/QueuGo/blob/73f9d57212fd8c4029a0e3341fe2d007675ccee9/native-android/shared/src/main/java/com/queuego/shared/QueueGoNativeApi.kt). Inspect backend onward recipients before completing Data Safety.
- Customer, Merchant and Rider declare RECORD_AUDIO and use the shared NativeVoicePeer WebRTC audio path.
- NativeVoicePeer creates an audio-only peer connection and obtains authorized STUN/TURN ICE servers from queuego-turn; no recording, transcription or server-side media processing exists in the reviewed Native source.
- Production call metadata retention is now enforced: `qg_call_sessions` rows and `notifications.type='voice_call'` rows older than 30 days are purged automatically every day by the active `queuego-voice-call-retention` pg_cron job. The purge function is `SECURITY INVOKER`; app roles cannot execute it directly. This closes the backend retention-policy gap only; final Play Console Data Safety entry remains BLOCKED until the external questionnaire/evidence is completed.
- Cloudflare Realtime TURN documents that WebRTC media remains end-to-end encrypted between call peers and that the relay cannot inspect media content.
- Native Android release CI rejects WebView and validates the three production package IDs; FCM configuration remains a hard release gate and Firebase Analytics/Crashlytics are not used as certification evidence.

Current public-policy sources reviewed:
- Google Play Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Supabase DPA: https://supabase.com/legal/customer-resources/data-processing-addendum
- Firebase Data Processing terms: https://firebase.google.com/terms/data-processing-terms
- Firebase Play Data disclosure: https://firebase.google.com/docs/android/play-data-disclosure
- Cloudflare Realtime TURN FAQ: https://developers.cloudflare.com/realtime/turn/faq/
- Longdo API terms: https://map.longdo.com/api/terms/
- Longdo privacy policy: https://www.longdo.com/en/privacy

### Per-app sharing answer

- **Customer:** assess actual online map/picker tiles, Supabase address lookup and order/location flows from the release AAB; final provider classification is pending.
- **Merchant:** assess actual shop-coordinate/map and POS/order transfers; final provider classification is pending.
- **Rider:** distinguish on-device offline map/routing from actual online location/job/push/voice and optional external-map transfers. Final provider classification is pending; do not claim all navigation is Google Maps handoff.
- Supabase/Firebase transfers still count as **collection** where applicable even when the service-provider sharing exception is used.
- Recheck the Firebase Play Data disclosure page whenever the Native Firebase SDK version or enabled Firebase product set changes.

## Encryption and deletion answers

Current code/release controls support these intended Play answers, subject to final release verification:

- Data encrypted in transit: **Yes** — release client endpoints use HTTPS and cleartext Android traffic is blocked.
- Users can request deletion: **Yes** — in-app and external deletion path exist.
- Data collection: **Yes** — QueueGo necessarily collects account/order/location/service data.
- Data sharing: **Yes (conservative)** — draft only, pending final active-provider/network review. Do not assign Longdo to the current Native build without evidence of an actual transfer. Assess applicable Supabase/Firebase arrangements separately.

## Contains Ads declaration

Current submission answer from reviewed code + production state:
- **Customer: Yes** — paid merchant Promote placements can be exposed through `qg_public_promotions()`.
- **Merchant: No** — Promote is a management workflow, not an ad-display surface in the Merchant app.
- **Rider: No** — no ad-display surface exists in the reviewed Rider build.

Production evidence at review time: `public.promotions` has 0 rows, 0 active rows and 0 paid rows. The Customer answer remains **Yes** because the server-controlled paid Promote capability can be activated without shipping a new app version.

Google Play review reference:
- https://support.google.com/googleplay/android-developer/answer/9859455

## Store review evidence to retain

Before submitting Closed Beta, keep evidence/screenshots of:

- Customer account deletion menu
- External account deletion page working on public HTTPS URL
- Privacy policy public HTTPS URL
- Customer/Rider UGC terms acceptance
- Customer/Rider report controls
- Customer/Rider block controls
- Admin moderation queue
- Android foreground location disclosure/permission prompt
- Android notification permission prompt
- Physical background notification test
- Closed Beta build version, SHA-256 and signing certificate fingerprint
- Successful Backup/Restore drill run
- Successful QueueGo RC Tests run for the exact release commit

## Current UGC gate evidence

- Production migration: `20261007023348_ugc_chat_revoke_anon_table_privileges`
- Live privilege verification: `anon` has no SELECT/INSERT/UPDATE/DELETE privilege on `public.order_chat_messages`; `authenticated` retains those four privileges subject to RLS.
- Focused source verification after hardening: **45 checks / 0 failures** across UGC chat safety and Play readiness sources.
- The full QueueGo RC workflow must still pass for the exact final release commit before AAB release; do not reuse an older RC run as release evidence.

## Remaining external blockers

1. Configure backup secrets and run the encrypted Backup/Restore drill successfully.
2. Retain the Firebase Android input validation above; independently verify Edge configuration and real-device push/voice.
3. Retain the validated upload-key identity and revalidate it in the final exact-source release workflow; signed AAB generation remains blocked.
4. Build physical-test APKs and certify background notifications on real Android devices.
5. Complete final AAB dependency/network/provider review, then enter Data Safety for the actual current Native data flows. Keep **PENDING_FINAL_NATIVE_NETWORK_REVIEW** until evidence is retained.
6. Enter Contains Ads declarations: Customer **Yes**, Merchant **No**, Rider **No**.
7. Enter Target Audience as **18+ only** for all three apps and enable **Restrict Minor Access** for Closed Beta.
8. Confirm Google Play developer account type/date to determine whether the 12-testers/14-days requirement applies.
9. Create dedicated reusable Play reviewer accounts and capture current-app screenshots.
10. Only then build the Closed Beta AABs.
