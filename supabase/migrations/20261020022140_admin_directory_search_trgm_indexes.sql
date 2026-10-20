create index if not exists users_name_trgm_idx
  on public.users using gin (name gin_trgm_ops)
  where name is not null;

create index if not exists users_phone_trgm_idx
  on public.users using gin (phone gin_trgm_ops)
  where phone is not null;

create index if not exists shop_profiles_current_name_trgm_idx
  on public.shop_profiles using gin (shop_name gin_trgm_ops)
  where archived_at is null and shop_name is not null;

create index if not exists shop_profiles_current_phone_trgm_idx
  on public.shop_profiles using gin (phone gin_trgm_ops)
  where archived_at is null and phone is not null;
