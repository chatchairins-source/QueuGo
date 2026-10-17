# QueueGo Native Rider Map checkpoint

Updated: 2026-10-17
Branch: `work-native-rider-queuego-route-authority-20261017`
Checkpoint HEAD before this file: `c6736b08f8168a373acb8b3a7c64f83ab4816cc8`

## Source of truth

Continue only from the newest descendant HEAD of this branch / PR #414.
Do not restore or cherry-pick an older Rider map implementation unless ancestry and necessity are proven first.

## Completed in this successor line

- Rider navigation renderer replaced with QueueGo MapLibre.
- Shared Customer/Merchant map surfaces switched to QueueGo MapLibre.
- QueueGo map service metadata added to Customer, Merchant, and Rider.
- Longdo API keys removed from Native Android manifests.
- Longdo SDK removed from shared and rider-navigation-map dependencies.
- Legacy Longdo map source/layout/helper files deleted.
- Rider navigation runtime host and instrumentation tests moved to MapLibre fixtures.
- Native CI workflows updated so Rider/native builds no longer download or certify Longdo.

## Invariants / do not regress

- No Longdo renderer, SDK, key, layout, helper, CI download, or Longdo runtime gate may be reintroduced.
- QueueGo routing remains authoritative; do not add Google/Longdo/public routing fallback.
- No fabricated straight-line route on routing failure.
- Existing Rider delivery regression gates still matter: one proof photo, one completion action, no duplicate pickup/delivery flow.
- Do not reset/revert newer successor work.
- Do not start a parallel map implementation.

## Next gate

1. Build/test exact newest HEAD.
2. Fix compile/runtime issues only on the newest successor.
3. Verify Native APK dependency graph contains no Longdo AAR/classes.
4. Verify Customer/Merchant/Rider map surfaces use QueueGo MapLibre source.
5. Verify CI no longer downloads Longdo.
6. Only after green automated gates, produce physical Rider test APK.

This file exists so a chat/work stream interruption cannot become the source of truth.
