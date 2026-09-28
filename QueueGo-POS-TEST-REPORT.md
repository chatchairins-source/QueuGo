# QueueGo POS — verification, 28 September 2026

## Implemented

- Merchant POS uses the existing `orders`, `order_items`, `products` and `payments` tables. DINE_IN and TAKEAWAY have zero QueueGo GP at the database layer. Existing Delivery transitions remain in their current role apps.
- Mobile counter offers quick order entry, optional tables, product image grid, quantity and notes. Kitchen and bill lists update through Supabase Realtime. Paid bills store tendered cash, change, payment method, PAID payment status and CLOSED bill status. An identical retry returns the paid bill without inserting another payment.
- Owner can invite separate WAITER, CASHIER and KITCHEN accounts. Their default action rights are enforced in server RPCs. Owner-only report covers POS and Delivery, and history filters by date and channel.
- Delivery setup checklist reads the shop's real address, coordinates, menu, hours, approval and open state. New shops must explicitly enable Delivery through a server RPC that rechecks readiness. Existing active shops retain their Delivery state. POS works while Delivery is closed.
- The workflow, kitchen batch and Delivery opt-in refinements are in the five incremental POS migrations after the original POS migration. No table was dropped and no permanent test data was inserted.

## Verification

| Check | Result |
| --- | --- |
| Live schema, ownership, RLS, Realtime publication and function signatures reviewed | Passed |
| Both POS migrations applied to connected Supabase project | Passed |
| Anonymous role denied execution of critical POS RPCs | Passed, SQL privilege query |
| Existing active owner resolves only own shop and reads server report | Passed, authenticated-role transaction |
| Existing real product: create DINE_IN without table → send → cook → ready → serve → pay cash → CLOSED | Passed inside a rollback transaction; no order persisted |
| POS GP is zero; cash change matches server amount | Passed in that transaction |
| Add a second kitchen batch after serving the first; original lines remain served | Passed in a rollback transaction with a real existing product |
| Delivery opt-in readiness and open state for existing shop | Passed inside rollback, restored original state |
| Retry the same cash payment after the first completion | Passed in rollback: one payment row and the same closed bill |
| JavaScript syntax and POS entry routes | Node syntax passed; runtime route test verified unauthenticated POS and staff join rendering. Browser automation later stopped by its usage limit |

## Not yet verified on devices

- There are currently no POS staff accounts or persistent POS orders. Staff invitation, role-specific screens and multiple-device Realtime have not been exercised by real signed-in people.
- Payment dialog, table turnover, printing, staff permissions and Delivery checklist need an authenticated mobile/tablet check. The database transaction did not test a real device or payment provider; cash is recorded as a cashier confirmation.
- Delivery retains the legacy `order_type=shopping` because Rider/Admin rely on that value; new Delivery orders also carry `sales_channel=QUEUEGO_DELIVERY`. Coordinating a literal `DELIVERY` type requires a separate cross-role migration.
