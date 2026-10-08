# QueueGo Native Android

Native Android implementation for Google Play.

- Kotlin + Jetpack Compose
- No WebView, Capacitor, Cordova or TWA wrapper
- Supabase Production is the backend/source of truth
- Existing Order State Machine, RPC and RLS remain authoritative
- No mock/demo/fake business data
- Admin stays web-only

Apps:
- QueueGo: com.queuetech.queuego
- QueueGo Merchant: com.queuetech.queuego.merchant
- QueueGo Rider: com.queuetech.queuego.rider

Milestone 1 contains real Production authentication, role validation and Android Keystore encrypted session persistence. Runtime permissions are added only when their native feature is implemented.
