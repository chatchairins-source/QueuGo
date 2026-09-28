# QueueGo POS — verification, 28 September 2026

## Implemented

- Merchant POS entry, tables, counter bill, kitchen queue, payment, receipt, staff invitation and channel-specific product settings.
- Supabase Auth staff signup/signin and one-use invitation; staff membership belongs to one shop. Staff rights are stored on the server.
- POS orders use the existing `orders`, `order_items`, `products`, and `payments` tables. `pos_tables`, `pos_staff`, `pos_invites`, and `pos_events` are new.
- DINE_IN and TAKEAWAY GP is forced to zero by the database trigger and integrity constraint. Legacy Delivery `shopping` orders remain unchanged; new Delivery orders carry `sales_channel=QUEUEGO_DELIVERY`.
- Server RPCs lock bill rows, read current product prices, enforce transitions and permissions, and calculate the final amount. POS financial records cannot be written directly by shop or staff via the Data API.
- PostgreSQL Realtime is enabled for orders, tables, staff and order items. The POS page subscribes to the current shop's changes and fetches the latest database state.

## Checks performed

| Check | Result |
| --- | --- |
| Existing schema, constraints, policies and triggers inspected before migration | Passed |
| Migration applied to the connected QueueGo Supabase project | Passed |
| POS tables have RLS; POS RPCs unavailable to anon | Passed by SQL inspection |
| Active shop owner can resolve own shop and read POS report | Passed using a read-only transaction with an existing active shop identity |
| Authenticated role without a user session cannot resolve a shop or create a POS bill | Passed |
| Authenticated role without POS sales permission cannot call the sales-report RPC | Passed; owner access checked separately |
| POS and GP columns, trigger, Delivery price function and Realtime publication present | Passed by SQL inspection |
| Customer and Merchant inline JavaScript plus POS module syntax | Passed with Node parser |

## Not yet exercised with a real transaction

- No POS tables, staff accounts, or POS orders existed at verification time. No sample orders or staff were added to the live database. A real owner and staff still need to configure their tables and run a complete order to verify invite, simultaneous screens, kitchen transitions, payment and receipt in their own devices.
- Realtime cross-device delivery and the iPhone payment UI were not verified with two authenticated devices.
- Legacy Delivery orders still use `order_type=shopping` to preserve Rider, proof, and cash transitions. They are identified as QueueGo Delivery by the existing type and `sales_channel` for new orders. Converting the legacy order type to the literal `DELIVERY` requires a separate coordinated migration of Rider and Admin checks.

## Operational note

Open an approved merchant account, choose **หน้าร้าน POS**, add actual tables under **พนักงาน**, and add actual products in **สินค้า**. Create a one-use staff invitation and register each employee through the merchant login page. Staff have order and kitchen access by default; the owner enables other permissions individually. The POS report counts paid bills only and never includes GP.
