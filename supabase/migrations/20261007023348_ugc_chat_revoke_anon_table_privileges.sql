-- QueueGo UGC/chat least-privilege hardening before Closed Beta.
-- Anonymous users never participate in order chat. RLS already denied anon rows;
-- revoke the Data API table privilege layer as defense in depth.

revoke all privileges on table public.order_chat_messages from anon;

-- Preserve the existing signed-in and service access model explicitly.
grant select, insert, update, delete on table public.order_chat_messages to authenticated;
grant select, insert, update, delete on table public.order_chat_messages to service_role;
