-- Admin Support inbox reads the newest 200/201 rows globally using
-- ORDER BY created_at DESC, id DESC. This additive index matches that
-- keyset order without changing RLS, grants, data, or Support behavior.
create index if not exists shop_support_messages_recent_idx
  on public.shop_support_messages (created_at desc, id desc);
