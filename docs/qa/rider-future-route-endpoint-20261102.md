# Future-route corrected destination visual guard — 2026-11-02

Based on exact Native #824 7b5e8826a768c421e591238fd16cd4d9590ffdf7. Isolated source-only P1.

## Defect
styledRemainingRoutes() previously trusted only toStopId and renderable geometry. A future delivery stop could be corrected to a new address while retaining its ID; the old line would remain visible until the stop became active, even though it ended at the wrong destination.

## Fix and tests
Resolve current incomplete stop by ID and require a renderable route to end within the existing QueueGo 750m road-snap tolerance. Retain valid near-stop snaps and route style; filter completed/unrelated routes as before. Existing model tests now use realistic endpoint fixtures instead of arbitrary lines. New Kotlin tests cover changed future destination and a legitimate 222m snap.

## Gates
Exact commit Native Pilot and 3-role APK/JVM + isolated RPC CI must pass before merging. This visual guard does not implement offline routing (#801), fix labels (#750), certify GPS (#822), reconcile RPCs (#740) or authorize road-test APK. No Production/data/Auth/RLS changes.
