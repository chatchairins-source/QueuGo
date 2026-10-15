-- Owner: all old Orders were test data. Credit is the ONLY GP collection path.
-- No backfill, retrospective debit, fake balance or Production data purge.
-- Remove execute access and replace obsolete handlers with inert compatibility tombstones.
do $migration$
declare r record;
begin
 for r in select n.nspname,p.proname,pg_get_function_arguments(p.oid) args,
 pg_get_function_identity_arguments(p.oid) signature,pg_get_function_result(p.oid) result
 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
 where n.nspname='public' and p.proname in ('report_shop_gp','admin_review_gp') and p.prokind='f'
 loop
  execute format('create or replace function %I.%I(%s) returns %s language plpgsql security invoker set search_path=public,pg_temp as $retired$ begin raise exception ''Legacy GP collection is retired; use credit'' using errcode=''0A000''; end $retired$',r.nspname,r.proname,r.args,r.result);
  execute format('revoke all on function %I.%I(%s) from public,anon,authenticated,service_role',r.nspname,r.proname,r.signature);
 end loop;
end $migration$;

-- Dormant test-era rows are not part of an app API and cannot be turned into new collections.
create function qg_private.legacy_gp_writes_retired() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin raise exception 'Legacy GP collection is retired; use credit' using errcode='0A000';end $$;
revoke all on function qg_private.legacy_gp_writes_retired() from public,anon,authenticated,service_role;
do $$
begin
 if to_regclass('public.settlements') is not null then
  revoke all on public.settlements from public,anon,authenticated,service_role;
  create trigger legacy_gp_writes_retired before insert or update or delete on public.settlements for each row execute function qg_private.legacy_gp_writes_retired();
 end if;
 if exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='settlements') then
  alter publication supabase_realtime drop table public.settlements;
 end if;
end $$;

-- Cached old apps cannot upload, modify, delete or newly sign old GP slips.
create policy gp_slips_retired on storage.objects as restrictive for all to anon,authenticated
using(bucket_id<>'gp-slips') with check(bucket_id<>'gp-slips');

create function qg_private.credit_only_gp_gate() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if new.shop_id is not null and new.order_type in ('shopping','DELIVERY') and coalesce(new.gp_amount,0)>0 and not new.credit_gp_required then
  if tg_op='INSERT' then raise exception 'Merchant credit is not enabled; legacy GP collection is retired' using errcode='0A000'; end if;
  if new.status is distinct from old.status and new.status not in ('cancelled','no_rider_available') then raise exception 'Legacy test Order is retired; create a new credit-mode Order' using errcode='0A000';end if;
 end if;
 return new;
end $$;
revoke all on function qg_private.credit_only_gp_gate() from public,anon,authenticated,service_role;
create trigger zzzz_credit_only_gp_gate before insert or update on public.orders for each row execute function qg_private.credit_only_gp_gate();

-- Report GP accrual from Orders. There is no outstanding/manual-payment status anymore.
do $migration$
declare f text;
begin
 select pg_get_functiondef(to_regprocedure('public.merchant_revenue_days(date,date)')) into f;
 if f is null or position(' filter(where not o.credit_gp_required)' in f)=0 then raise exception 'Credit revenue predecessor drift; inspect before retirement';end if;
 f:=replace(f,' filter(where not o.credit_gp_required)','');
 f:=replace(f,$old$coalesce((select s.status from public.settlements s where s.shop_id=v_shop and s.date_key=days.d order by s.updated_at desc limit 1),'pending')$old$,$new$'credit_only'::text$new$);
 execute f;
end $migration$;
