-- Production version 20261020022520. Add only ordering indexes used by bounded Admin keyset pages.
create index if not exists audit_logs_created_id_idx
  on public.audit_logs (created_at desc, id desc);

create index if not exists notifications_user_created_id_idx
  on public.notifications (user_id, created_at desc, id desc);
