# Nationwide QueueGo Map, Registration and Ordering Coverage

Status: architecture proposal only. It does not change Production or create a migration.

## Current gate

`QueueGo-Pilot-Service-Area.sql` seeds one `Buriram Pilot` area with center `14.99292, 103.10804`, radius 20 km and delivery cap 15 km. `queuego_service_area_route_status` requires both pickup and delivery coordinates to fit that same circle. `queuego_assert_service_route` is called by Food checkout, Market checkout and Laundry order creation. A location elsewhere in Thailand therefore fails with `OUTSIDE_SERVICE_AREA`, even when it is a valid Thai coordinate.

The map and registration capability must not depend on this order gate. Customer location selection, Rider registration and Merchant registration already accept nationwide coordinates in current local source.

## Target authorities

Keep three decisions independent:

| Capability | Authority | Nationwide rule |
| --- | --- | --- |
| `MAP_AVAILABLE` | country capability for `TH` plus map runtime health | Available across Thailand; no order polygon lookup |
| `REGISTRATION_AVAILABLE` | country capability for `TH` plus role eligibility | Customer, Merchant and Rider signup do not require ordering coverage |
| `ORDERING_AVAILABLE` | active order-service-area rule, vertical, route distance and operating supply | Evaluated per request; no source-code province allowlist |

An area without active service supply still permits map, place search, address pinning and registration. Ordering can report no active service coverage for that route without blocking those other capabilities.

## Order-area representation

Replace radius circles as the authority with an additive, versioned order-area registry. Use one of two scalable representations per rule:

1. an administrative area reference (`queuego_places.place_id`) for a verified country/province/district/subdistrict Place; resolve its reviewed multipolygon boundary; or
2. a QueueGo-reviewed custom multipolygon for a service boundary that does not match an administrative boundary.

A rule carries country code, vertical, allow/deny mode, active state, effective timestamps, maximum direct delivery distance, source/provenance, revision and audit metadata. Add a GiST spatial index to custom polygon geometry. Do not create a single enormous radius to cover Thailand. Keep the route-distance cap separate from geographic service coverage.

A rule must fail closed when its boundary is missing, invalid, stale where freshness is required, or not verified for service use. For overlapping rules, the most specific matching area wins (`subdistrict` → `district` → `province` → custom boundary); an explicit deny at that same or greater specificity takes precedence over a broader allow. Store the resolved rule/revision in the order record or immutable order event so later edits cannot rewrite why an order was accepted.

## Server route decision

The existing route-status response remains the single checkout contract, with explicit `capability` and reason codes:

- validate both coordinates and vertical;
- require Thai endpoints for Thailand ordering;
- resolve active rules covering both pickup and delivery using the same vertical;
- apply explicit deny/allow specificity;
- measure direct distance and enforce the matched rule’s separate route cap;
- check operational supply/readiness through its own existing server authority;
- return `ORDERING_AVAILABLE=false` with a stable reason if any order prerequisite fails.

Map search and registration never call this function. Checkout continues to call an authoritative server function inside the order transaction. Cash-first flow and its payment records stay unchanged.

## Safe migration sequence

1. Add the polygon/admin-reference registry, indexes, RLS, provenance and Admin-only management RPCs without dropping or mutating the current table/functions.
2. Read back the existing `queuego_places` extension and boundary inventory; do not assume the Place importer contains verified polygons.
3. Seed reviewed coverage polygons for the existing Buriram area only, preserving the current 15 km route limit. Compare old and new decisions in shadow mode for representative in/outside coordinates.
4. Add explicit nationwide `TH` map and registration capability independent of order-area rows.
5. Add the new route decision as a shadow result behind the current checkout gate. Compare Food, Market and Laundry vertical decisions; resolve any differences before activation.
6. Switch checkout calls to the new authority only after Production schema/RLS/advisor and exact-head RC gates pass. Retain old function/table for rollback until all call sites are verified.
7. Enable an additional area through reviewed database data, tests and audit. Never require a province-specific source change.

## Required verification

- Phuket, Chiang Mai, Bangkok and border/island coordinates remain map-visible and registration-valid when ordering is disabled.
- Routes inside each reviewed polygon pass only when the vertical is enabled; points outside fail closed.
- A long route inside a polygon can still fail its independent distance cap.
- More-specific deny/allow precedence and overlap behavior are deterministic.
- All current checkout call sites use the server authority; no UI or local state can override it.
- RLS, Admin-only writes, SECURITY DEFINER `auth.uid()`/role checks, explicit `search_path` and restricted `EXECUTE` grants pass review.
- Audit records capture the selected rule revision and decision reason.

## Blockers

The current Supabase migration-creation path was auto-review blocked because the CLI telemetry may send local environment/command metadata. Do not bypass that review or apply Production DDL. Polygon data is not yet imported or verified. The existing Supabase security/performance advisor findings still need endpoint-level review. Until these gates clear, the old Buriram route gate remains live, and nationwide ordering is not available.
