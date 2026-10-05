# QueueGo project continuity

Before planning or changing QueueGo, read `docs/PROJECT-MEMORY.md` and carry its pending work and constraints into the current plan. Keep that document current when its work is implemented or its requirements change. The navigation return overlay is deferred until the user explicitly requests an APK; do not implement it as part of unrelated work.

GitHub and the connected Supabase project are the source of truth. Preserve the existing order state machine, audit trail, and business model. Do not add manual payment confirmation steps or a duplicate financial system.
