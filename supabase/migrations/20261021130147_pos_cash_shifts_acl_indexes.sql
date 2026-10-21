-- Tighten new-table defaults explicitly; RLS is an additional guard, not a replacement
-- for column/table privileges. Add the missing FK-leading indexes reported by advisors.
begin;

revoke all on table public.pos_register_shifts,public.pos_register_cash_events from public,anon,authenticated;
grant select on table public.pos_register_shifts,public.pos_register_cash_events to authenticated;

create index if not exists pos_register_shifts_cashier_idx
  on public.pos_register_shifts(cashier_user_id);
create index if not exists pos_register_cash_events_shop_idx
  on public.pos_register_cash_events(shop_id,created_at desc);
create index if not exists pos_register_cash_events_actor_idx
  on public.pos_register_cash_events(actor_id);

commit;
