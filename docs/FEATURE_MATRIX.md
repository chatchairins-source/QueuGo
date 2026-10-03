# QueueGo delivery feature benchmark

Reviewed 2026-10-04 (Asia/Bangkok). This is a capability benchmark, not a copied design or a claim of complete feature parity. Features vary by country and merchant. QueueGo status below is based on the current source and database definitions, not a completed live E2E certification.

A = needed before public launch; B = after stable closed beta; C = optional; D = outside the current business model unless the owner approves it.

| Platform / official evidence | Capability | QueueGo evidence / gap | Decision | Practical benefit |
|---|---|---|---|---|
| [Grab scheduled orders](https://merchant.grab.com/th-th/guides/step-2-manage-your-store/scheduled-order), [delivery process](https://merchant.grab.com/th-th/guides/step-1-getting-started/delivery-process) | Scheduling and clear merchant preparation flow | Merchant order actions exist; scheduling is absent from current Customer checkout | A: reliable immediate orders first. B: scheduling after capacity and cancellation rules are defined | Customer plans meals; merchant plans preparation; rider avoids premature pickup; platform reduces failed handoffs |
| [LINE MAN pickup](https://linemanwongnai.my.salesforce-sites.com/lm/lmarticles?id=How-does-the-Pickup-feature-work) | Customer pickup | POS takeaway exists, but Customer delivery checkout does not offer customer pickup | B; do not treat POS takeaway as complete Customer pickup | Customer avoids delivery wait; merchant serves direct collection; rider receives only delivery jobs; platform distinguishes fulfillment |
| [foodpanda capabilities](https://www.foodpanda.com/about-foodpanda/), [item replacement](https://www.foodpanda.com/newsroom/foodpanda-redefines-convenient-shopping-with-item-replacement-feature/) | Grocery discovery and item replacement | Shop-first Market UI and stock module exist; replacement consent is not implemented in the current Customer flow | A: availability and honest price confirmation. B: replacement consent | Customer avoids unexpected substitutions; merchant resolves shortages; rider avoids pickup disputes; platform reduces cancellations |
| [ShopeeFood ordering](https://help.shopee.co.th/portal/4/article/80620) | Location-based shop discovery | Current Customer sorts shops by distance when a saved location exists; service-area enforcement still needs full verification | A: location choice, manual recovery, service-area validation. D: copying competitor fee policy | Customer sees relevant shops; merchant receives reachable orders; rider sees valid routes; platform prevents unsupported deliveries |
| [Uber Eats group orders](https://about.ubereats.com/us/en/how-it-works/group-order/) | Shared group cart and tracking | Current Customer cart is single-owner and single-shop; Market multi-shop pickup is a different feature | C: group ordering must not delay core release | Customer coordinates meals; merchant sees one coherent order; rider sees one dispatch; platform avoids fragmented deliveries |
| [DoorDash tracking](https://help.doordash.com/en-us/consumers/article/customer-where-is-my-order), [scheduled delivery](https://help.doordash.com/consumers/s/article/Can-I-schedule-a-delivery-in-advance) | Delivery status, rider contact, support, scheduling | Customer status labels and delivery PIN are now wired to current RPCs; live tracking, chat/support parity and reconnect E2E remain incomplete | A: tracking/contact/recovery. B: scheduling | Customer knows the next step; merchant and rider resolve handoffs; platform diagnoses stalled orders |
| [Lalamove business delivery](https://www.lalamove.com/en-th/business), [API/POD](https://developers.lalamove.com/) | Multi-stop pickup/delivery, vehicle eligibility, proof of delivery | Market group pickup, vehicle checks and PIN/evidence completion RPCs exist; actual concurrent claims and multi-shop E2E remain unverified | A: verify existing group pickup and proof. D: unrestricted new logistics/pricing models | Customer receives a verified delivery; merchants have separate pickup records; rider sees each pickup; platform retains an audit trail |

foodpanda ended Thailand platform operations on 23 May 2025 according to its [official service update](https://www.foodpanda.co.th/contents/important-service-update). Its international capabilities are benchmark evidence; it is not described here as a current Thai operator.

## QueueGo launch backlog

| Priority | Gap | Current action / activation boundary |
|---|---|---|
| A | Atomic, idempotent checkout | Replaced direct client order/item writes with existing `queuego_place_cash_order`; durable retry request and tap lock added. Live authenticated test still required |
| A | Rider state consistency | Active-job query includes `rider_assigned`, `preparing`, `ready`; pickup action uses server-required `ready` |
| A | Delivery completion independent of merchant acknowledgement | Migration applied and isolated SQL tests passed; actual authenticated E2E still required; retain rider advance, assignment, PIN and evidence checks |
| A | Guest discovery / cart continuity | Guest routes enabled using existing anonymous read policies; guest cart migrates only into an empty signed-in cart |
| A | Role isolation and malicious direct requests | Existing RLS and user privilege trigger inspected; authenticated negative tests still required |
| A | Realtime and network recovery across all roles | Reconnect generation/timer guard added; Merchant POS pauses general hydration. Actual socket recovery and sound dedup tests still required |
| A | Customer support, account deletion, privacy/data retention, notifications | Current Customer source does not expose the complete required flows. Owner-approved legal documents and authenticated integration are still needed |
| A | Current price/stock recovery, menu options/notes, service-area validation | Current Customer flow still requires deeper functional verification and implementation before launch |
| A | Android lifecycle and permissions, signed packages, physical-device QA | Existing permission override removed; native dependency packaging/signing configuration corrected. No release packages built |
| B | Reorder, favorites, recent shops/search, saved-address management | Do not activate while core release blockers remain |
| B | Scheduled orders / customer pickup / substitutions | Define operational semantics before activation; pricing/GP/payment changes require owner approval |
| C | Group ordering / loyalty / additional discovery polish | Does not block core reliability work |
| D | New wallets, payment models, dynamic pricing, regulated products, financing | No activation without business/legal approval |

No claim is made that all A gaps are closed. No new pricing, GP, rider economics, payment model or legal policy has been activated.
