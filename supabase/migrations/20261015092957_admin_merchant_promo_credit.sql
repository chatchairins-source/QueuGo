-- Owner-authorized free Merchant GP credit. This is promotion, never a bank payment.
-- Reuse the immutable ledger; do not seed balances or enable unrelated paid topups.
create function public.queuego_admin_merchant_credit_targets(p_query text default '')
returns jsonb language plpgsql security definer set search_path='' as $$
begin
 perform qg_private.assert_live_session();
 if not exists(select 1 from public.users where auth_user_id=auth.uid() and role='admin' and status='active') then
  raise exception 'active admin required' using errcode='42501';
 end if;
 if p_query is null or length(p_query)>100 then raise exception 'search must be at most 100 characters';end if;
 return coalesce((select jsonb_agg(to_jsonb(t)) from (
  select u.id user_id,u.name,string_agg(s.shop_name,' / ' order by s.shop_name,s.id) shops,
   coalesce(c.promo_minor,0) promo_minor
  from public.users u join public.shop_profiles s on s.user_id=u.id
  left join qg_private.credit_accounts c on c.user_id=u.id and c.kind='merchant'
  where u.role='shop' and u.status='active' and s.status='active' and s.archived_at is null
   and (btrim(p_query)='' or position(lower(btrim(p_query)) in lower(coalesce(u.name,'')||' '||coalesce(s.shop_name,'')))>0)
  group by u.id,u.name,c.promo_minor order by u.name,u.id limit 50
 )t),'[]'::jsonb);
end $$;
revoke all on function public.queuego_admin_merchant_credit_targets(text) from public,anon,service_role;
grant execute on function public.queuego_admin_merchant_credit_targets(text) to authenticated;

create function public.queuego_admin_grant_merchant_credit(p_request uuid,p_user uuid,p_amount_minor bigint,p_reason text)
returns uuid language plpgsql security definer set search_path='' as $$
declare a uuid;e qg_private.credit_entries%rowtype;r uuid;
begin
 perform qg_private.assert_live_session();
 select id into a from public.users where auth_user_id=auth.uid() and role='admin' and status='active' for share;
 if a is null then raise exception 'active admin required' using errcode='42501';end if;
 if p_request is null or p_user is null or p_amount_minor is null or p_amount_minor not between 1 and 10000000 then
  raise exception 'credit amount must be 0.01 to 100000 THB';
 end if;
 if length(btrim(coalesce(p_reason,''))) not between 5 and 500 then raise exception 'grant reason must be 5 to 500 characters';end if;
 -- Serialize the same durable request even when different targets are submitted concurrently.
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('admin-promo:'||p_request::text,0));
 select * into e from qg_private.credit_entries where event_key='admin-promo:'||p_request::text;
 if found then
  if e.user_id<>p_user or e.kind<>'merchant' or e.event_type<>'promo' or e.promo_delta<>p_amount_minor or e.actor_id<>a or e.reason<>btrim(p_reason) then
   raise exception 'credit grant request mismatch';
  end if;
  return e.id;
 end if;
 perform 1 from public.users where id=p_user and role='shop' and status='active' for share;
 if not found then raise exception 'active merchant required';end if;
 perform 1 from public.shop_profiles where user_id=p_user and status='active' and archived_at is null for share;
 if not found then raise exception 'active unarchived shop required';end if;
 insert into qg_private.credit_accounts(user_id,kind) values(p_user,'merchant') on conflict do nothing;
 perform 1 from qg_private.credit_accounts where user_id=p_user and kind='merchant' for update;
 insert into qg_private.credit_entries(user_id,kind,event_key,event_type,promo_delta,actor_id,reason)
 values(p_user,'merchant','admin-promo:'||p_request::text,'promo',p_amount_minor,a,btrim(p_reason)) returning id into r;
 update qg_private.credit_accounts set promo_minor=promo_minor+p_amount_minor where user_id=p_user and kind='merchant';
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(a,'merchant_credit_grant','credit',r,btrim(p_reason),jsonb_build_object('request_id',p_request,'merchant_user_id',p_user,'promo_minor',p_amount_minor));
 return r;
end $$;
revoke all on function public.queuego_admin_grant_merchant_credit(uuid,uuid,bigint,text) from public,anon,service_role;
grant execute on function public.queuego_admin_grant_merchant_credit(uuid,uuid,bigint,text) to authenticated;

-- Show the issuer and reason alongside existing credit history.
create or replace function public.queuego_admin_credit_accounts() returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
begin
 perform qg_private.assert_live_session();
 if not exists(select 1 from public.users where auth_user_id=auth.uid() and role='admin' and status='active') then raise exception 'active admin required' using errcode='42501'; end if;
 return coalesce((select jsonb_agg(to_jsonb(a)) from (select c.*,u.name,
 (select coalesce(jsonb_agg(to_jsonb(e) order by e.created_at desc),'[]'::jsonb) from (select id,event_type,cash_delta,promo_delta,order_id,created_at,actor_id,reason from qg_private.credit_entries where user_id=c.user_id and kind=c.kind order by created_at desc limit 20)e) entries
 from qg_private.credit_accounts c join public.users u on u.id=c.user_id order by u.id,c.kind limit 200)a),'[]'::jsonb);
end $$;
revoke all on function public.queuego_admin_credit_accounts() from public,anon;
grant execute on function public.queuego_admin_credit_accounts() to authenticated;

