# Native isolated backend builds

Customer, Merchant and Rider share one backend origin/public key for Auth,
PostgREST, Edge Functions, Storage and Realtime. Normal builds retain the current
Production settings.

To build a DEBUG APK against a real ephemeral Supabase backend, supply both:

- `QG_DEBUG_BACKEND_URL`: HTTPS origin, or HTTP at `10.0.2.2`, `127.0.0.1`,
  `localhost` or `::1` for emulator/loopback development.
- `QG_DEBUG_BACKEND_PUBLISHABLE_KEY`: that backend's publishable key or legacy
  `anon` JWT. Never supply a privileged/server key.

Partial pairs, Production mixing, unsafe origins and privileged keys fail at
Gradle configuration before BuildConfig generation. Runtime validates again.
A valid override only affects the debug variant. Release generated fields are
empty and release clients resolve the Production defaults. Debug-only Android
network security grants cleartext to the explicit local hosts; release policy
is unchanged. Realtime derives `ws`/`wss` from the same selected origin.

Run the six `QueueGoBackendEnvironmentTest` JVM cases and all-role Debug builds
when changing this boundary. A successful local SQL fixture is not a real UI
backend: actual GoTrue/Auth sessions, application tables/RLS, business RPCs,
Storage policies and Edge Functions must exist. The repository migration series
assumes an existing schema; do not substitute always-successful session or
business RPC stubs. The local-backend capability alone certifies neither full
Android/Web delivery flow nor Production parity.
