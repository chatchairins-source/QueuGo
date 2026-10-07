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

### Sharing decision — must be confirmed before Play submission

Do not automatically answer "No sharing" until QueueTech verifies the provider relationship and data flow for:
- Supabase
- Firebase Cloud Messaging / Google
- Longdo map/routing services
- any other production map/navigation provider or infrastructure processor

If those providers process data only on QueueTech's behalf and meet Google's service-provider definition, those transfers may fall under the Data Safety sharing exception. User-initiated navigation transfers may also qualify for the user-initiated exception. The Play declaration must match the final production contracts and behavior.

## Encryption and deletion answers

Current code/release controls support these intended Play answers, subject to final release verification:

- Data encrypted in transit: **Yes** — release client endpoints use HTTPS and cleartext Android traffic is blocked.
- Users can request deletion: **Yes** — in-app and external deletion path exist.
- Data collection: **Yes** — QueueGo necessarily collects account/order/location/service data.
- Data sharing: **Do not submit yet** — verify provider/service-provider classification first.

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
6. Confirm Data Safety provider-sharing classification.
7. Confirm Google Play developer account type/date to determine whether the 12-testers/14-days requirement applies.
8. Only then build the Closed Beta AABs.
