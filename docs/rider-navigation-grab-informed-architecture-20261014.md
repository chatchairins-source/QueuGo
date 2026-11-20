# QueueGo Rider Navigation — Grab-informed architecture notes

Date: 2026-10-14

## Scope and IP boundary

This note records high-level architecture observations from the user-supplied Grab Driver APK and maps them to original QueueGo components. It does not copy Grab source code, algorithms, assets, keys, endpoints, or proprietary implementation details.

## Observable Grab navigation building blocks

The supplied APK contains independently named modules/classes/resources that indicate a layered navigation architecture, including:

- MapLibre-based rendering and a separate Grab map rendering layer.
- Location-kit style location collection/providers.
- Map matching, including hybrid/full-map-matching related components.
- Deviation/off-route detection and reroute control.
- Vehicle-state / navigation-position concepts.
- Navigation behavior and navigation view-mode handling.
- Maneuver observation / turn-by-turn guidance.
- Last-mile navigation/nav-point concepts.
- Separate route selection and route-details modules.
- User-puck resources for normal, bearing and stale states.
- Recenter and map overview controls.
- Auto-route-update and live-location-pin modules.

These names strongly suggest separation between raw location acquisition, navigation estimation, route state, map rendering and camera/UI behavior.

## QueueGo state before this successor branch

QueueGo already has several strong pieces:

- Rider map holder preserves the map instance instead of recreating it on every Compose refresh.
- Rider marker motion is animated using update cadence and teleport guards.
- Camera bearing is smoothed and can prefer route bearing at low speed.
- Route progress is trimmed monotonically for display.
- Off-route reroute has sample and cooldown guards.
- PR #184 adds an isolated MapLibre/OSM/PMTiles renderer with lifecycle reuse tests.
- Business/order/cash state remains separate from renderer state.

The main gap is that the current matcher is a nearest-segment projection. At parallel roads, loops, U-turns and dense junctions, nearest-distance alone can choose the wrong segment.

## Successor branch additions

Branch: `work-native-rider-nav-confidence-20261014`

### 1. NavigationLocationFilter

Purpose:
- reject stale fixes;
- reject implausible teleports;
- hold tiny stationary jitter;
- lightly smooth accepted movement according to speed/accuracy;
- classify fix quality as GOOD / DEGRADED / WEAK / REJECTED.

### 2. NavigationHybridMatcher

Purpose:
- score route candidates using distance, heading and continuity;
- penalize excessive backtracking;
- penalize physically implausible forward jumps;
- return HIGH / MEDIUM / LOW confidence.

This is deliberately lightweight and deterministic. It is not a copy of Grab's matcher and is not an HMM implementation.

### 3. NavigationViewModeController

Explicit modes:
- FOLLOW
- OVERVIEW
- FREE_PAN

User gesture exits FOLLOW. Recenter deterministically returns to FOLLOW. This prevents the camera from fighting the rider after manual map interaction.

### 4. NavigationSignalPipeline

Critical rule:
- raw GPS remains available for business logic;
- filtered/snapped GPS is display-only until field confidence is proven.

The pipeline can hold the last good display puck when a fix is rejected, while retaining the raw fix separately for diagnostics and authoritative business checks.

## Recommended renderer visual states

QueueGo should implement its own assets for:

- ACTIVE: normal QueueGo rider puck with bearing.
- DEGRADED: normal puck with reduced confidence ring.
- WEAK_GPS: wider accuracy halo and no aggressive road snap.
- OFF_ROUTE: keep real filtered location visible and trigger reroute logic.
- HELD: retain last good puck briefly instead of teleporting.

Do not copy Grab icons.

## Integration order

1. Keep PR #184 MapLibre module isolated.
2. Land confidence/filter/matcher/view-mode tests.
3. Integrate NavigationSignalPipeline into the existing navigation coordinator behind an internal rollout switch.
4. Feed displayFix to map marker/camera only.
5. Keep raw fix for arrival/business state until field tests prove otherwise.
6. Add visual puck confidence states.
7. Add gesture-driven FREE_PAN and recenter.
8. Run real-device route tests in Buriram:
   - stationary;
   - slow village roads;
   - parallel roads;
   - U-turn;
   - GPS loss/recovery;
   - background/foreground;
   - Google Maps handoff and return.
9. Only after those gates, connect the MapLibre renderer from PR #184 to Rider Home.
10. Keep Google Maps external handoff as a fallback until internal navigation reaches release quality.

## Release gates

- MapView/renderer instance count remains 1 across refresh/background/reattach scenarios.
- No white map flash on normal tab/background transitions.
- No marker teleport on single bad GPS sample.
- No camera recenter after user gesture until explicit recenter.
- No route snap to opposite-direction parallel segment in the regression fixture.
- Reroute does not fire from weak GPS alone.
- Arrival/order state does not depend on display-only snapped coordinates.
- P0/P1 = 0 before production renderer swap.
