-- QueueGo policy: alcohol and tobacco products may remain POS products but must not be sold through Delivery.
-- This is additive: no product/order rows are deleted.

alter table public.products
  add column if not exists delivery_restriction text not null default 'none';

create or replace function public.queuego_detect_delivery_restriction(
  p_name text,
  p_category text,
  p_description text,
  p_metadata jsonb,
  p_explicit text default null
)
returns text
language plpgsql
immutable
set search_path to 'public','pg_temp'
as $function$
declare
  v_explicit text:=lower(trim(coalesce(p_explicit,p_metadata->>'delivery_restriction','none')));
  v_text text:=lower(concat_ws(' ',coalesce(p_name,''),coalesce(p_category,''),coalesce(p_description,''),coalesce(p_metadata->>'category','')));
begin
  if v_explicit in ('alcohol','tobacco') then
    return v_explicit;
  end if;

  if v_text ~ '(บุหรี่|ยาสูบ|ซิการ์|บุหรี่ไฟฟ้า)'
     or v_text ~ '(^|[^[:alnum:]_])(cigarette|cigar|tobacco|vape)([^[:alnum:]_]|$)' then
    return 'tobacco';
  end if;

  if v_text ~ '(เบียร์|เหล้า|ไวน์|วิสกี้|วอดก้า|บรั่นดี|สุราขาว|สุราพื้นบ้าน)'
     or v_text ~ '(^|[^[:alnum:]_])(beer|wine|vodka|rum|gin|brandy|whisky|whiskey)([^[:alnum:]_]|$)' then
    return 'alcohol';
  end if;

  return 'none';
end
$function$;

create or replace function public.queuego_products_delivery_policy_guard()
returns trigger
language plpgsql
set search_path to 'public','pg_temp'
as $function$
begin
  new.delivery_restriction:=public.queuego_detect_delivery_restriction(
    new.name,new.category,new.description,new.metadata,new.delivery_restriction
  );

  if new.delivery_restriction<>'none' then
    new.delivery_available:=false;
  end if;

  return new;
end
$function$;

drop trigger if exists qg_products_delivery_policy_guard on public.products;
create trigger qg_products_delivery_policy_guard
before insert or update of name,category,description,metadata,delivery_available,delivery_restriction
on public.products
for each row
execute function public.queuego_products_delivery_policy_guard();

-- Backfill existing catalog rows. Obvious restricted products are immediately removed from Delivery
-- while pos_available and POS prices remain untouched.
update public.products
set delivery_restriction=public.queuego_detect_delivery_restriction(
  name,category,description,metadata,delivery_restriction
);

do $$
begin
  if not exists(
    select 1 from pg_constraint
    where conname='products_delivery_restriction_check'
      and conrelid='public.products'::regclass
  ) then
    alter table public.products
      add constraint products_delivery_restriction_check
      check (delivery_restriction in ('none','alcohol','tobacco'));
  end if;

  if not exists(
    select 1 from pg_constraint
    where conname='products_restricted_not_delivery_check'
      and conrelid='public.products'::regclass
  ) then
    alter table public.products
      add constraint products_restricted_not_delivery_check
      check (delivery_restriction='none' or delivery_available=false);
  end if;
end
$$;

comment on column public.products.delivery_restriction is
  'QueueGo delivery policy: none, alcohol, or tobacco. Restricted products remain eligible for POS but not Delivery.';
