# Existing-route evidence replenishment

This function writes short-lived road evidence for the existing dispatcher and route-bundle quote. It never assigns Orders, changes customer prices or pays a Rider. SQL quote/claim guards remain authoritative.

The staged replenishment migration schedules a 20-second wake. It does no network work while `dispatch.continuous_enabled` is absent/0 or when the dedicated Vault token is missing. Work is limited to two pairs per request, with a fenced 90-second lease. A dead/retried worker cannot release its successor's lease. Fresh evidence tied to exact Order revisions and Rider position is skipped.

## Deployment requirements (not performed by this change)

- Validate/apply all three staged continuous-dispatch migrations before shipping the matching Native build.
- Configure `QUEUEGO_LONGDO_KEY` in Edge secrets and validate real provider duration/distance responses. The adapter refuses coordinate-only/fabricated duration results.
- Provision a dedicated random worker token (at least 32 random bytes), with the same value in Edge secret `QUEUEGO_ROUTE_MATRIX_WORKER_TOKEN` and Vault secret named `queuego_route_matrix_worker_token`. Do not put the service-role key in cron commands or source.
- Deploy this function with gateway JWT verification disabled (`supabase functions deploy queuego-route-bundle-matrix --no-verify-jwt`). The handler still requires either the exact service-role bearer or the dedicated worker token, checked with fixed-length digest comparison. Anonymous/app tokens cannot claim evidence work. The service role remains available only inside the function environment.
- Keep continuous dispatch OFF and live capacity2 until financial/cancellation, physical background acceptance, provider, and exact-head CI gates pass. A configured worker alone does not authorize rollout.

A service-authorized `POST {"action":"refresh"}` runs bounded replenishment. The original explicit `{primaryOrderId,candidateOrderId}` request remains supported. Failures return no coordinates, credentials or provider URLs. The SQL dispatcher continues on its existing five-second cadence and refuses missing/stale evidence.


## Phase 2 multi-stop groundwork

The same service now accepts the service-authorized `{"action":"stack","primaryOrderId":"...","candidateOrderId":"..."}` request. It discovers the Rider's current active bundle, adds only the still-unassigned candidate, builds a bounded road matrix for Rider + pickup/dropoff points, and writes short-lived evidence through `queuego_cache_route_bundle_stack_matrix`.

This is groundwork only: the live claim RPC is unchanged, `route_bundle.max_orders` still defaults to 2, Saver still defaults OFF, and no Production flag is enabled. The generalized road adapter accepts 2–11 points (Rider plus at most 5 pickup/dropoff pairs) and rejects larger/untrusted point sets.


### Phase 2 read-only planner

A service-authorized `{"action":"plan","primaryOrderId":"...","candidateOrderId":"..."}` reads only the fresh stack matrix already cached by `action=stack`. The SQL reader rejects expired evidence, changed Order revisions, a candidate that is no longer unassigned/searching, or active Orders that no longer belong to the cached Rider.

The pure planner never calls Longdo and never writes Orders, bundles, prices or cache state. It chooses the minimum-duration route (distance and deterministic key order break ties) while preserving the current QueueGo policy that all pending pickups occur before any customer dropoff. Pickups already completed by `picked_up` / `in_progress` Orders are omitted. Capacity remains at the existing live default/cap of 2 until a later claim/pricing phase is explicitly gated.
