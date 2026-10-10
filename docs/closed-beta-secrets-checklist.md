# QueueGo Closed Beta Secret Checklist

This file contains **names and locations only**. Never commit secret values to the repository.

## GitHub Actions — Backup / Restore

Repository → Settings → Secrets and variables → Actions → Repository secrets

- `QG_SUPABASE_DB_URL`
  - Supabase Session Pooler or direct Postgres connection string for project `pkypiqhlrmzocysgeqew`.
- `QG_SUPABASE_SERVICE_ROLE_KEY`
  - Supabase server secret/service-role key. Required only by the encrypted Storage backup job.
- `QG_BACKUP_PASSPHRASE`
  - A unique high-entropy passphrase of at least 20 characters. Used to encrypt the off-site backup artifact.

After these three are configured, run **QueueGo Encrypted Backup Restore Drill** manually. A passing run must complete database restore, Storage restore, checksum verification, and encrypted artifact creation before `restore_drill_certified` may be set to true.

## GitHub Actions — Android / Firebase / Signing

- `QG_FIREBASE_GOOGLE_SERVICES_JSON_B64`
  - Base64-encoded Android `google-services.json` for the QueueGo Firebase project.
- `QG_ANDROID_KEYSTORE_B64`
  - Base64-encoded Android release JKS/keystore.
- `QG_ANDROID_STORE_PASSWORD`
- `QG_ANDROID_KEY_ALIAS`
- `QG_ANDROID_KEY_PASSWORD`

Do not reuse the backup passphrase for Android signing.

### Release secret preflight

After the five GitHub Android/Firebase secrets above are configured, run the GitHub Actions workflow **QueueGo Release Secret Readiness**. The actual Native Closed Beta artifacts must then be built only by **QueueGo Native Android Closed Beta** (`.github/workflows/build-native-closed-beta.yml`) after all physical/runtime gates are certified.

The preflight intentionally validates secret material without printing it:
- decodes `google-services.json` in the ephemeral runner only;
- requires Firebase Android clients for `com.queuego.customer`, `com.queuego.merchant`, and `com.queuego.rider`;
- decodes the release keystore only in the ephemeral runner;
- validates the keystore store password, configured alias, and key password;
- removes all decoded temporary credential files with an `always()` cleanup step.

A green preflight proves the GitHub-side Firebase/signing inputs are structurally usable. It does **not** certify the Supabase Edge secret `FIREBASE_SERVICE_ACCOUNT_JSON` or a physical background notification; those remain separate gates.

## Supabase Edge Functions

Set these Edge Function secrets:

- `FIREBASE_SERVICE_ACCOUNT_JSON`
  - Firebase service account JSON used by `queuego-push` to authenticate to FCM HTTP v1.
- `CLOUDFLARE_TURN_KEY_ID`
  - Cloudflare TURN key identifier used only by `queuego-turn`.
- `CLOUDFLARE_TURN_KEY_API_TOKEN`
  - Server-side API token used only by `queuego-turn` to request short-lived ICE/TURN credentials.

These secret values must remain only in Supabase Edge secrets. Do not put them in GitHub, Android resources, the APK/AAB, Postgres tables, or the web bundle.

### TURN release certification

Secret presence alone is not a release pass. After TURN secrets are configured:
1. Sign in on two real Android devices using valid participants on one real active order.
2. Start a QueueGo in-app voice call from the supported order screen.
3. Accept the call on the second device.
4. Verify two-way audio with the apps on separate network paths where TURN relay can be required.
5. Verify mute, speaker, hang-up, logout/session revocation and terminal-order shutdown.
6. Confirm no audio file or call recording is stored.
7. Only then set `voice.turn_relay_certified=true` and `voice.two_device_audio_e2e_certified=true` in the recovery manifest for the exact release commit.

## Supabase Auth platform setting

Security Advisor currently reports **Leaked Password Protection Disabled** at WARN level. Current Supabase documentation lists leaked-password protection as available on **Pro Plan and above**.

QueueGo Closed Beta on the current Free plan uses the explicit gate value `PASS_FREE_PLAN_CONTROLS` with compensating controls:
- shared `queuego-password-policy.js`
- minimum 12 characters
- uppercase + lowercase + number + symbol
- Customer, Merchant and Rider new registrations
- regression coverage in `tests/password-policy.cjs`

This does **not** mean leaked-password protection is enabled. After a future Pro upgrade, enable it, re-run Security Advisor and promote `security_platform_auth` to full `PASS`.

Reference:
- https://supabase.com/docs/guides/auth/password-security

## Physical Native Android notification certification

After Firebase and signing credentials are configured:

1. Build a Pilot test APK.
2. Sign in as Customer, Merchant, and Rider.
3. Enable **การแจ้งเตือนเบื้องหลัง**.
4. Tap **ทดสอบการแจ้งเตือน**.
5. Immediately put the app in the background.
6. Confirm the QueueGo notification appears after about 7 seconds.
7. Repeat after screen lock and after app process restart.
8. Confirm logout disables the token and the old account no longer receives notifications.

Only after the physical-device test passes should `physical_background_notification_certified` be set to true.


## Native Closed Beta artifact rule

- Do not use `.github/workflows/build-queuego-apks.yml` or historical Capacitor/WebView artifacts for the current QueueGo Native release.
- The only Closed Beta artifact workflow for the current source of truth is `.github/workflows/build-native-closed-beta.yml`.
- The workflow is fail-closed: it will not build signed release artifacts while backup/restore, physical FCM, TURN relay, or two-device voice certification remains incomplete.


## Native Closed Beta dispatcher

GitHub exposes `workflow_dispatch` from the repository default branch, which remains `main`. Therefore the finalized `.github/workflows/build-native-closed-beta.yml` must also exist on `main` as the dispatcher definition. The workflow itself always checks out `queuego-native-android-v1` explicitly for validation and build, and release evidence records the checked-out Native commit SHA rather than the default-branch SHA.
