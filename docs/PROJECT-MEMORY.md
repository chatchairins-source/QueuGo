# QueueGo project memory

Updated: 2026-10-05 (Asia/Bangkok).

## User instruction

Keep the navigation return feature below in project memory and consider it in subsequent QueueGo development, especially native Android/iOS releases. This is pending work, not an implemented feature. Incorporate it when relevant without replacing unrelated requested work.

## Pending: return to the active rider order while navigating

- Android installed app: add a draggable QueueGo Q overlay button while a rider has an active job and opens external navigation. Tapping returns to the same active order, preserving the session and current state. Request the OS draw-over-other-apps permission explicitly. Provide a notification return action when overlay permission is unavailable. Hide the overlay on completion, cancellation, logout, or loss of ownership. Implement in the native app, not only webpage CSS.
- iPhone installed app: use a delivery Live Activity and Dynamic Island on supported devices, with a link back to the same active order. Use notifications as appropriate for other devices. Do not promise an Android-style free-floating cross-app button on iOS. Requires native iOS integration; not implemented in the web app.
- Web/PWA: do not represent an in-page floating control as a cross-app overlay.
- Use the existing order and authenticated rider identity. Returning to the app must never claim another order or change order/payment state automatically.
- Navigation before pickup targets the shop. After pickup it targets the customer's actual delivery location. Existing actions remain: arrive at shop, pickup, start delivery, complete delivery. No new mandatory confirmation step.

## Existing flow constraints

Customer order → PENDING → merchant accept → SEARCHING_RIDER → rider claim → RIDER_ASSIGNED → merchant preparing → PREPARING → merchant ready → READY → rider pickup → PICKED_UP → start delivery → IN_PROGRESS → complete → COMPLETED.

Shop arrival is an audited event (`rider_arrived_shop_at`), not a new order state. Manual payment confirmations were removed; existing backend cash/audit records are created by order actions. Keep this behavior.

## Verification needed before calling the pending feature complete

- Physical Android device: permission allowed/denied, Google Maps navigation, dragging/tapping overlay, active-order restoration, completion/logout cleanup, and app lifecycle.
- Physical iPhone: supported Live Activity presentations, tap to restore the correct order, lifecycle and completion cleanup.
- Customer/Merchant/Rider regression checks; preserve order ownership, state machine, notifications, and audit history.

## Last implementation reference

Commit `52c41aaf7863b916214c8baa63ffe374f8865afd` added a prominent customer navigation link after pickup and prevented market pickup coordinates from overriding the delivery destination. Cross-app overlay and iOS Live Activity remain pending.
