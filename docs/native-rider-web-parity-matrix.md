# Rider Web ↔ Native parity audit

Web blueprint: origin/main 7e036a0735cc49905e9b0a2434492470551a0c9d.
Native integration base: 3c91d8e3b8b88976869c4c501ec40830baaa9b76, includes PR729 ac50b768.

| Web surface | Android counterpart | Status | Priority | Evidence / action |
|---|---|---|---|---|
| Home map/puck/FOLLOW | Rider Home + QueueGoMap | Integrated from current map owner PR729 | P1 visual review open | Do not duplicate; exact integrated CI and fresh captures required |
| Earnings period/retry | RiderEarningsScreen | Bounded fix pending CI | P1 error recovery | Web reruns summary/ledger on every period click. Native same-period click wrote unchanged days and left errors stuck. Add refresh generation to existing effects and period control |
| Messages/notifications | RiderMessagesScreen | Source entry present; audit open | P1 | Compare sections, reference navigation, retries and expiry |
| Profile/settings/safety/support/diagnostics | RiderProfileScreen | Source entries present; audit open | P1 | Compare entry hierarchy and all error states |
| Offer/active job/pickup/proof/cash/navigation/complete | Existing Rider workflow | Audit open | P1 | Verify current backend transitions and paired captures |
| Session/process restore | existing saved activeTab and durable snapshots | Present; authenticated review open | P1 | Session cancellation during prior review remains account/session evidence, not missing Profile tab |

Physical-device and release parity are unverified. NEXT: exact CI for earnings recovery; complete non-map source inventory and authenticated capture using existing authorized workflow.
