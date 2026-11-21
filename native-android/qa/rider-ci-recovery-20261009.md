# Rider CI recovery and remaining release gates

Source HEAD: 5b562d8514e22ce107db95a7b1bf57545b4aedc2.
CI: https://github.com/chatchairins-source/QueuGo/actions/runs/37888000000

The actual CI job passed regression, source integrity, both asset checks, shared/customer/rider JVM tests, all three APK builds, No WebView and artifact upload. Production Kotlin subscriptions were unchanged; the stale shared tests now cover all five owned subscriptions and all five accepted event IDs.

## Production Realtime publication repair

Backup ref: backup-native-before-laundry-preferences-realtime-20261009 at the source HEAD above. Before the change, supabase_realtime contained 26 tables including laundry_rider_jobs and laundry_rider_invites, but omitted laundry_rider_preferences. The preferences table already had RLS enabled and authenticated SELECT granted. Its existing owner policy scopes rows through the Rider profile user ID.

Applied migration 20261009052132_rider_laundry_preferences_realtime adds only that missing publication member, guarded against duplicate application. Verified all three Laundry tables are published afterward and preferences RLS remains enabled. No data, grants, policies, dispatch functions or web UI changed.

## Four-tab source audit

Compared current main:rider/index.html at 9026bf445650dbeeb0fd254582481a08a16108fc with Native Rider. Both expose Home / messages / earnings / profile; native selected-offer handling closes chat and returns to Home for a new server offer. Web dock uses 58px height, 8px side margins, 20px outer radius and 16px active-item radius, which Native specifies correspondingly. This is source evidence only. Native label typography and icon paths still need rendered comparison with the web (web labels are 8px); four tab names alone do not establish visual parity.

## Unpassed gates

CI explicitly warns that QG_FIREBASE_GOOGLE_SERVICES_JSON_B64 is absent. The build success does not certify background FCM. Real device background/foreground, notification permission/token delivery, 30-second targeted offer, Laundry exclusivity, reconnect recovery, authenticated mobile web/native screenshots and Production end-to-end order/Laundry/market flows remain unverified. No emulator/device or authenticated test accounts were available for these checks in this session. APKs are not certified for Pilot or Play Store and are not delivered to Owner.
