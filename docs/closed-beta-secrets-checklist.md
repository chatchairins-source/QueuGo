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

## Supabase Edge Functions

Set the Edge Function secret:

- `FIREBASE_SERVICE_ACCOUNT_JSON`
  - Firebase service account JSON used by `queuego-push` to authenticate to FCM HTTP v1.

The secret value must remain only in Supabase Edge secrets. Do not put the JSON file in GitHub or the web bundle.

## Supabase Auth platform setting

Security Advisor currently reports **Leaked Password Protection Disabled**.

Enable leaked-password protection in Supabase Auth before changing `security_platform_auth` to `PASS`.

## Physical Android notification certification

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
