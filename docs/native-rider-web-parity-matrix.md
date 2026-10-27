# Rider Web ↔ Native parity audit

Web blueprint: origin/main 7e036a0735cc49905e9b0a2434492470551a0c9d.
Native integration base: 3c91d8e3b8b88976869c4c501ec40830baaa9b76, includes PR729 ac50b768.

| Web surface | Android counterpart | Status | Priority | Evidence / action |
|---|---|---|---|---|
| Home map/puck/FOLLOW | Rider Home + QueueGoMap | Source mismatch corrected; visual review open | P1 | Web idle Home does not follow camera or enable perspective from online status alone. Native now enables follow/perspective only for active work (or the test flag). Paired Android capture is still required to verify rendered position, zoom, and controls. |
| Earnings period/retry | RiderEarningsScreen | Web-shaped source correction; local source regressions pass | P1 | Native now matches Web's four summary stats, four cash totals, status/fee recent rows, and avoids extra native-only sections/API reads. Both platforms rerun summary/ledger on period selection via refresh generation. Exact-head JUnit/build/emulator evidence pending. |
| Messages/notifications | RiderMessagesScreen | Source entry present; audit open | P1 | Compare sections, reference navigation, retries and expiry |
| Profile/settings/safety/support/diagnostics | RiderProfileScreen | Work-type options match; behavior correction in source, full audit open | P1 | The five service labels, auto-accept, bundle, capability gating and server RPC match Web. Native now also checks the selected service before auto-accepting an offer, as Web does. Compare remaining entry hierarchy/error states and paired captures. |
| Offer/active job/pickup/proof/cash/navigation/complete | Existing Rider workflow | Audit open | P1 | Verify current backend transitions and paired captures |
| Arrival confirmation | `requestArrival` / `RiderArrivalGuard` | Action parity confirmed in source; passive map HUD display remains open | P1 | Web and Android use the same 200 m direct-GPS threshold, show a non-blocking warning for farther/unknown GPS, and call `qg_rider_mark_arrival`; customer arrival enters proof capture after commit. Navigation HUD differs: Android's route engine can mark arrived using an accuracy-adjusted GPS radius plus remaining-route distance; Web renders the final maneuver when its instruction list is exhausted. Keep this visual display gap open pending paired screenshots. |
| Session/process restore | existing saved activeTab and durable snapshots | Present; authenticated review open | P1 | Session cancellation during prior review remains account/session evidence, not missing Profile tab |

Physical-device and release parity are unverified. NEXT: exact-head Native Pilot on the integrated three-app successor; complete Messages/Profile/active-job source inventory and authenticated paired capture using the existing authorized workflow.
