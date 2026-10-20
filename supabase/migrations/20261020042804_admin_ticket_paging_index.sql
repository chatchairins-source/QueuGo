-- Admin complaint modal reads newest tickets globally using
-- ORDER BY created_at DESC, id DESC with a 100/101 keyset sentinel.
-- Add only the matching read index; no RLS, grants, data, or ticket-state changes.
create index if not exists qg_support_tickets_recent_idx
  on public.qg_support_tickets (created_at desc, id desc);
