# QueueGo — Google Play Closed Beta Readiness

Last reviewed: 2026-10-07  
Scope: Android Customer, Merchant, Rider apps.

## Current release gate

Closed Beta release remains **BLOCKED** until every hard gate below passes:

- Account deletion + privacy: PASS
- Service area: PASS
- Security code gate: PASS
- Core production E2E: PASS
- UGC / chat safety implementation: PASS
- Android target API 36 static gate: PASS
- Backup/Restore drill: BLOCKED — GitHub backup secrets not configured
- Supabase Auth leaked-password protection: BLOCKED — platform setting still disabled
- Native Firebase background notification physical test: BLOCKED — credentials/device test missing
- Android release signing: BLOCKED — release signing secrets missing

The release workflow is intentionally configured to fail while hard gates are not certified.

## Google Play policy checkpoints

### Target API

For phone/tablet apps submitted after 2026-08-31, Google Play requires new apps and updates to target Android 16 / API 36 or newer.

QueueGo uses Capacitor Android 8.x, whose supported target SDK is API 36. Both the Pilot APK and Closed Beta workflows verify the generated Android project has:

- compileSdkVersion = 36
- targetSdkVersion = 36

Builds fail if the generated project is not API 36.

Policy sources:
- https://support.google.com/googleplay/android-developer/answer/11926878
- https://next.capacitorjs.com/docs/next/android/setting-target-sdk

### Android permissions

QueueGo release builds are limited to the permissions required by the active feature set:

Expected:
- INTERNET
- ACCESS_NETWORK_STATE
- ACCESS_COARSE_LOCATION
- ACCESS_FINE_LOCATION
- VIBRATE
- POST_NOTIFICATIONS (native push plugin)

Explicitly forbidden by the release workflow:
- CAMERA
- RECORD_AUDIO
- READ_EXTERNAL_STORAGE
- WRITE_EXTERNAL_STORAGE
- READ_MEDIA_IMAGES
- ACCESS_BACKGROUND_LOCATION

Image selection uses the Android system picker / WebView file chooser instead of broad media-library permission.

QueueGo does not request background location. Location is used only for foreground service functionality such as customer delivery location, shop coordinates, Rider location while using the work flow, service-area checks, distance calculations and navigation.

Policy source:
- https://support.google.com/googleplay/android-developer/answer/16558241

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

Google Play treats data transmitted off-device from an app-controlled WebView as app collection. QueueGo must therefore declare data collected by the web application running inside Capacitor.

Google Play also states that transfers to qualifying service providers, legal-purpose transfers, and some user-initiated transfers are not necessarily declared as "sharing." QueueTech must confirm the contractual role of infrastructure/map providers before final submission.

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
| Photos | Pickup/delivery proof and chat/evidence images | App functionality, safety | Required for proof steps / optional for chat |
| Device or other IDs | Push token, session/device identifiers | Notifications, security | Optional/functional |

### Provider sharing classification — conservative submission decision (2026-10-07)

Google Play defines "sharing" as transferring user data to a third party, including transfers from an app-controlled WebView. A transfer to a qualifying service provider that processes data on the developer's behalf and instructions does not have to be declared as sharing.

Current QueueGo decision:

| Provider | QueueGo use / transfer | Public contractual evidence | Play Data Safety treatment |
|---|---|---|---|
| Supabase | Auth, database, Storage, Realtime, account/order/location/chat/support data | Supabase DPA states Supabase acts as processor/service provider and processes Covered Data on behalf of and under Customer instructions | **Collected: Yes. Shared: service-provider exception may be used** for Supabase processing under the applicable Supabase agreement/DPA |
| Firebase Cloud Messaging / Google | Native push transport; FCM/Installations SDK metadata, installation ID/token and app version as applicable | Firebase Data Processing and Security Terms govern Customer Data; Firebase privacy guidance states Google generally operates as processor/service provider for Firebase customer data | **Collected: Yes** for applicable FCM/Installations data. **Shared: service-provider exception may be used** for Firebase processing covered by those terms |
| Longdo Map / Metamedia Technology | Customer reverse geocoding, map rendering, merchant/shop pins, Rider route calculation; exact origin/destination coordinates are sent to Longdo endpoints | Public Longdo API terms point to Longdo privacy policy. The public privacy policy says Longdo may collect IP/usage/location data, but the public terms reviewed do not establish that all API personal-data processing is solely on QueueTech's behalf/instructions | **Conservative answer: Shared = Yes for location, purpose App functionality.** Do not claim the service-provider exception unless a QueueTech–Longdo commercial agreement/DPA explicitly supports it |
| External navigation app opened by the user | Explicit "navigate" action where the user chooses to open an external navigation service | Google Play has a user-initiated-transfer exception when the user reasonably expects the transfer | Can rely on the user-initiated exception for that explicit navigation handoff, but this does **not** remove the Longdo sharing declaration above |

Code evidence:
- Customer loads Longdo Map and sends selected latitude/longitude to Longdo reverse-geocoding.
- Rider loads Longdo Map and sends current Rider coordinates plus destination coordinates to Longdo RouteService.
- Merchant loads Longdo Map for shop location/pin workflows.
- `android-build/package.json` includes Capacitor Push Notifications 8.0.0 and does not declare Firebase Analytics or Crashlytics.

Current public-policy sources reviewed:
- Google Play Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Supabase DPA: https://supabase.com/legal/customer-resources/data-processing-addendum
- Firebase Data Processing terms: https://firebase.google.com/terms/data-processing-terms
- Firebase Play Data disclosure: https://firebase.google.com/docs/android/play-data-disclosure
- Longdo API terms: https://map.longdo.com/api/terms/
- Longdo privacy policy: https://www.longdo.com/en/privacy

### Per-app sharing answer

- **Customer:** declare Approximate/Precise location as shared with Longdo for App functionality where applicable.
- **Merchant:** conservatively declare location/shop-coordinate transfer as shared with Longdo for App functionality because a shop location may be linked to an individual/sole proprietor.
- **Rider:** declare Approximate/Precise location as shared with Longdo for App functionality because route requests transmit the Rider origin and delivery destination.
- Supabase/Firebase transfers still count as **collection** where applicable even when the service-provider sharing exception is used.
- Recheck the Firebase Play Data disclosure page whenever the native SDK/plugin version changes.

## Encryption and deletion answers

Current code/release controls support these intended Play answers, subject to final release verification:

- Data encrypted in transit: **Yes** — release client endpoints use HTTPS and cleartext Android traffic is blocked.
- Users can request deletion: **Yes** — in-app and external deletion path exist.
- Data collection: **Yes** — QueueGo necessarily collects account/order/location/service data.
- Data sharing: **Yes (conservative)** — declare location sharing with Longdo for App functionality. Supabase/Firebase processing may use the service-provider exception where the current agreements apply.

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

1. Enable Supabase leaked-password protection.
2. Configure backup secrets and run the encrypted Backup/Restore drill successfully.
3. Configure Firebase Android + Edge credentials.
4. Configure Android release signing secrets.
5. Build physical-test APKs and certify background notifications on real Android devices.
6. Enter the finalized Data Safety answers in Play Console using the conservative Longdo location-sharing classification above.
7. Enter Contains Ads declarations: Customer **Yes**, Merchant **No**, Rider **No**.
8. Enter Target Audience as **18+ only** for all three apps and enable **Restrict Minor Access** for Closed Beta.
9. Confirm Google Play developer account type/date to determine whether the 12-testers/14-days requirement applies.
10. Only then build the Closed Beta AABs.
