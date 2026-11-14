# QueueGo Native Rider Map / Delivery checkpoint

Updated: 2026-10-17
Branch: `work-native-rider-queuego-route-authority-20261017`
PR: #414
Current verified HEAD: `b76b8541c9d789ab10d8b9c36702d5283f18c4df`

## Source of truth

Continue only from the newest descendant HEAD of this branch / PR #414.

Before every new edit:
1. Fetch PR #414 and current branch HEAD again.
2. If a newer descendant exists, use it immediately.
3. Never resume from a SHA remembered from chat.
4. Never reset/revert/cherry-pick an older implementation unless ancestry and necessity are proven.
5. Do not start unrelated work while this gate is open.

The current HEAD `b76b8541...` is a direct descendant of the completed delivery checkpoint `3981ec045a9e17b1543bb9594b8a64b31f45c0cd` (ahead by 1, behind by 0). Therefore the delivery fixes below are part of the current successor and must not be recreated or rolled back.

## Completed and locked in this successor line

### Rider delivery
- One pickup proof photo only.
- One delivery proof photo only.
- Delivery proof and cash amount stay on the same verification screen.
- Delivery uses one simple `จบงาน` button after a valid proof; no slide-to-complete.
- `RiderCollectionScreen`, `verifyCollection`, `showCollection`, and the `cash_collection` process-recovery stage were removed.
- `RiderVerificationResume` no longer contains a collection sub-stage.
- Confirmed proof remains durable and recoverable after process death.
- Successful server mutation promotes local state immediately so stale refresh cannot send Rider back to the same proof step.
- Regression tests now forbid reintroducing collection/slider delivery flow.

### QueueGo Map / navigation
- Rider navigation renderer uses QueueGo MapLibre.
- Shared Customer/Merchant native map surfaces use QueueGo MapLibre.
- QueueGo map service metadata is wired into Customer, Merchant, and Rider.
- Longdo API keys removed from Native Android manifests.
- Longdo SDK removed from shared and rider-navigation-map dependencies.
- Legacy Longdo native map files/layouts/helpers removed.
- Native map regression tests now forbid Longdo from certified Native release and assert QueueGo MapLibre contracts.
- QueueGo routing remains the route authority through `/map-api`.
- No fabricated straight-line route fallback.

## Permanent no-regression rules

- Never reintroduce Longdo renderer, SDK, key, layout, helper, native CI download, SHA pin, or Longdo runtime gate.
- Never restore Rider slide-to-complete.
- Never restore a second delivery collection/completion screen.
- Never restore `cash_collection`, `verifyCollection`, or `showCollection`.
- Never require the owner to rediscover one of these removed regressions manually.
- Never start a parallel map implementation.
- Never treat chat stream state as project source of truth.

## Current open gate at this HEAD

Latest verified CI evidence on `b76b8541...`:
- Native Pilot fails before Rider-specific assertions because source-integrity finds a broken web reference:
  `admin/index.html -> ../catalog/grocery.css?v=20261012`.
- Buriram runtime hits the same broken reference before its map runtime gate.
- `navigation-map-runtime` and `maplibre-osm-runtime` were still running at the last inspection.

This broken reference is not permission to revert Rider Delivery or QueueGo Map work. Fix only the actual broken reference on the newest successor, then rerun the exact same gates.

## Next actions

1. Refetch newest successor HEAD.
2. Fix the broken `admin/index.html` grocery CSS reference only if it still exists on that newest HEAD.
3. Re-run/read Native Pilot, navigation-map-runtime, maplibre-osm-runtime, and Buriram runtime.
4. For every failure, inspect the exact log before editing; never guess.
5. Do not produce a release claim until the exact newest HEAD is green.
6. Only after green automated gates, produce the physical Rider test APK from that exact HEAD.

This file exists so a chat/work stream interruption cannot make QueueGo resume from stale work again.
