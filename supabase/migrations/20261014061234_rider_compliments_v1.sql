create table if not exists qg_private.rider_review_compliments (
  review_id uuid primary key references public.reviews(id) on delete cascade,
  order_id uuid not null references public.orders(id) on delete cascade,
  rider_id uuid not null references public.rider_profiles(id) on delete cascade,
  customer_id uuid not null references public.users(id) on delete cascade,
  compliments text[] not null default '{}'::text[],
  updated_at timestamptz not null default now(),
  constraint rider_review_compliments_max_three
    check (cardinality(compliments) between 1 and 3)
);

create index if not exists rider_review_compliments_rider_idx
  on qg_private.rider_review_compliments(rider_id);

revoke all on table qg_private.rider_review_compliments from public, anon, authenticated;
grant all on table qg_private.rider_review_compliments to service_role;

create or replace function qg_private.assert_live_session()
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_session_id text := auth.jwt()->>'session_id';
begin
  if auth.uid() is null
     or v_session_id is null
     or v_session_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    raise exception 'active session required' using errcode='42501';
  end if;

  if not exists (
    select 1
    from auth.sessions s
    where s.id=v_session_id::uuid
      and s.user_id=auth.uid()
      and (s.not_after is null or s.not_after > now())
  ) then
    raise exception 'active session required' using errcode='42501';
  end if;
end $$;

revoke all on function qg_private.assert_live_session() from public, anon;
grant execute on function qg_private.assert_live_session() to authenticated;

create or replace function qg_private.customer_save_rider_compliments(
  p_order_id uuid,
  p_compliments text[]
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_customer uuid;
  v_review uuid;
  v_rider uuid;
  v_values text[];
begin
  perform qg_private.assert_live_session();

  select u.id into v_customer
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='customer'
    and u.status='active'
  limit 1;

  if v_customer is null then
    raise exception 'active customer required' using errcode='42501';
  end if;

  select rv.id, o.rider_id
  into v_review, v_rider
  from public.reviews rv
  join public.orders o on o.id=rv.order_id
  where rv.order_id=p_order_id
    and rv.customer_id=v_customer
    and o.customer_id=v_customer
    and o.status='completed'
    and o.rider_id is not null
    and rv.rider_rating between 4 and 5
  limit 1;

  if v_review is null or v_rider is null then
    raise exception 'positive rider review required' using errcode='42501';
  end if;

  select coalesce(array_agg(normalized order by first_ord), '{}'::text[])
  into v_values
  from (
    select lower(btrim(value)) as normalized, min(ord)::bigint as first_ord
    from unnest(coalesce(p_compliments, '{}'::text[])) with ordinality as x(value, ord)
    where btrim(value) <> ''
    group by lower(btrim(value))
  ) normalized_rows;

  if cardinality(v_values) > 3 then
    raise exception 'maximum three rider compliments';
  end if;

  if not (v_values <@ array[
    'polite','fast','careful','professional','friendly','helpful'
  ]::text[]) then
    raise exception 'invalid rider compliment';
  end if;

  if cardinality(v_values)=0 then
    delete from qg_private.rider_review_compliments
    where review_id=v_review;
    return jsonb_build_object('compliments','[]'::jsonb);
  end if;

  insert into qg_private.rider_review_compliments(
    review_id,order_id,rider_id,customer_id,compliments,updated_at
  )
  values(v_review,p_order_id,v_rider,v_customer,v_values,now())
  on conflict(review_id) do update
    set order_id=excluded.order_id,
        rider_id=excluded.rider_id,
        customer_id=excluded.customer_id,
        compliments=excluded.compliments,
        updated_at=now();

  return jsonb_build_object('compliments',to_jsonb(v_values));
end $$;

revoke all on function qg_private.customer_save_rider_compliments(uuid,text[]) from public, anon;
grant execute on function qg_private.customer_save_rider_compliments(uuid,text[]) to authenticated;

create or replace function public.qg_customer_save_rider_compliments(
  p_order_id uuid,
  p_compliments text[]
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.customer_save_rider_compliments(p_order_id,p_compliments); $$;

revoke all on function public.qg_customer_save_rider_compliments(uuid,text[]) from public, anon;
grant execute on function public.qg_customer_save_rider_compliments(uuid,text[]) to authenticated, service_role;

create or replace function qg_private.customer_rider_compliments(p_order_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_customer uuid;
  v_review uuid;
  v_values text[];
begin
  perform qg_private.assert_live_session();

  select u.id into v_customer
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='customer'
    and u.status='active'
  limit 1;

  if v_customer is null then
    raise exception 'active customer required' using errcode='42501';
  end if;

  select rv.id into v_review
  from public.reviews rv
  join public.orders o on o.id=rv.order_id
  where rv.order_id=p_order_id
    and rv.customer_id=v_customer
    and o.customer_id=v_customer
    and o.status='completed'
  limit 1;

  if v_review is null then
    return jsonb_build_object('compliments','[]'::jsonb);
  end if;

  select c.compliments into v_values
  from qg_private.rider_review_compliments c
  where c.review_id=v_review;

  return jsonb_build_object(
    'compliments',
    coalesce(to_jsonb(v_values),'[]'::jsonb)
  );
end $$;

revoke all on function qg_private.customer_rider_compliments(uuid) from public, anon;
grant execute on function qg_private.customer_rider_compliments(uuid) to authenticated;

create or replace function public.qg_customer_rider_compliments(p_order_id uuid)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.customer_rider_compliments(p_order_id); $$;

revoke all on function public.qg_customer_rider_compliments(uuid) from public, anon;
grant execute on function public.qg_customer_rider_compliments(uuid) to authenticated, service_role;

create or replace function qg_private.rider_compliment_summary()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_total integer := 0;
  v_items jsonb := '[]'::jsonb;
begin
  perform qg_private.assert_live_session();

  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active'
    and r.status='active'
  order by r.created_at desc
  limit 1;

  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;

  select count(distinct c.review_id)::integer
  into v_total
  from qg_private.rider_review_compliments c
  join public.reviews rv on rv.id=c.review_id
  join public.orders o on o.id=c.order_id
  where c.rider_id=v_rider
    and o.rider_id=v_rider
    and o.status='completed'
    and rv.rider_rating between 4 and 5;

  select coalesce(
    jsonb_agg(
      jsonb_build_object('key', ranked.compliment, 'count', ranked.compliment_count)
      order by ranked.compliment_count desc, ranked.compliment asc
    ),
    '[]'::jsonb
  )
  into v_items
  from (
    select compliment, count(*)::integer as compliment_count
    from qg_private.rider_review_compliments c
    join public.reviews rv on rv.id=c.review_id
    join public.orders o on o.id=c.order_id,
    lateral unnest(c.compliments) compliment
    where c.rider_id=v_rider
      and o.rider_id=v_rider
      and o.status='completed'
      and rv.rider_rating between 4 and 5
    group by compliment
    order by compliment_count desc, compliment asc
    limit 6
  ) ranked;

  return jsonb_build_object('total',v_total,'items',v_items);
end $$;

revoke all on function qg_private.rider_compliment_summary() from public, anon;
grant execute on function qg_private.rider_compliment_summary() to authenticated;

create or replace function public.qg_rider_compliment_summary()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_compliment_summary(); $$;

revoke all on function public.qg_rider_compliment_summary() from public, anon;
grant execute on function public.qg_rider_compliment_summary() to authenticated, service_role;

create or replace function qg_private.cleanup_rider_compliments_on_review_rating()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.rider_rating is null or new.rider_rating < 4 then
    delete from qg_private.rider_review_compliments
    where review_id=new.id;
  end if;
  return new;
end $$;

revoke all on function qg_private.cleanup_rider_compliments_on_review_rating() from public, anon, authenticated;

drop trigger if exists qg_reviews_cleanup_rider_compliments on public.reviews;
create trigger qg_reviews_cleanup_rider_compliments
after update of rider_rating on public.reviews
for each row
when (new.rider_rating is null or new.rider_rating < 4)
execute function qg_private.cleanup_rider_compliments_on_review_rating();
