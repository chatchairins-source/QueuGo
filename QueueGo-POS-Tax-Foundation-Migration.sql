-- QueueGo POS tax settings and immutable bill tax snapshot foundation.
-- Deliberately does not change live checkout totals until the authoritative checkout RPC is upgraded.
begin;

create table if not exists public.pos_tax_settings (
  shop_id uuid primary key references public.shop_profiles(id) on delete cascade,
  tax_enabled boolean not null default false,
  tax_rate numeric(5,2) not null default 7.00 check (tax_rate >= 0 and tax_rate <= 100),
  prices_include_tax boolean not null default true,
  tax_registration_number text,
  tax_branch_code text,
  tax_invoice_address text,
  updated_by uuid references auth.users(id),
  updated_at timestamptz not null default now(),
  constraint pos_tax_registration_number_length check (
    tax_registration_number is null or length(trim(tax_registration_number)) between 1 and 30
  ),
  constraint pos_tax_branch_code_length check (
    tax_branch_code is null or length(trim(tax_branch_code)) between 1 and 20
  ),
  constraint pos_tax_invoice_address_length check (
    tax_invoice_address is null or length(trim(tax_invoice_address)) between 1 and 1000
  )
);

alter table public.orders
  add column if not exists pos_tax_enabled_snapshot boolean,
  add column if not exists pos_tax_rate_snapshot numeric(5,2),
  add column if not exists pos_prices_include_tax_snapshot boolean,
  add column if not exists pos_taxable_base numeric(12,2),
  add column if not exists pos_tax_amount numeric(12,2),
  add column if not exists pos_tax_registration_snapshot text,
  add column if not exists pos_tax_branch_snapshot text,
  add column if not exists pos_tax_address_snapshot text;

alter table public.orders
  drop constraint if exists pos_tax_snapshot_rate_check;
alter table public.orders
  add constraint pos_tax_snapshot_rate_check check (
    pos_tax_rate_snapshot is null or (pos_tax_rate_snapshot >= 0 and pos_tax_rate_snapshot <= 100)
  );

alter table public.orders
  drop constraint if exists pos_tax_snapshot_amounts_check;
alter table public.orders
  add constraint pos_tax_snapshot_amounts_check check (
    (pos_taxable_base is null or pos_taxable_base >= 0)
    and (pos_tax_amount is null or pos_tax_amount >= 0)
  );

alter table public.pos_tax_settings enable row level security;
drop policy if exists pos_tax_settings_shop_read on public.pos_tax_settings;
create policy pos_tax_settings_shop_read on public.pos_tax_settings
  for select to authenticated
  using (shop_id = public.pos_my_shop());

-- No client-side direct writes: future settings updates must go through an owner-authorized RPC.
revoke insert, update, delete on public.pos_tax_settings from anon, authenticated;

commit;
