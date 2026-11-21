-- Reuse the targeted-offer SELECT policy; no pool and no broader unassigned reads.
alter policy rider_orders_offered_select on public.orders to authenticated using (
 public.get_my_role()='rider' and (order_type='shopping' or (order_type='service' and fulfillment_vertical='ride'))
 and status='searching_rider' and rider_id is null and (select public.qg_has_live_rider_offer(orders.id))
);
create or replace function public.queuego_ride_quote_context(p_auth_user_id uuid,p_pickup_lat double precision,p_pickup_lng double precision,p_delivery_lat double precision,p_delivery_lng double precision)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare c jsonb;u uuid;a jsonb;
begin
 select id into u from public.users where auth_user_id=p_auth_user_id and role='customer' and status='active';
 if u is null then raise exception 'active customer required'; end if;
 c:=public.queuego_ride_config();
 if not coalesce((c->>'enabled')::boolean,false) or not coalesce((c->>'configured')::boolean,false) then raise exception 'ride unavailable'; end if;
 if exists(select 1 from public.orders where customer_id=u and fulfillment_vertical='ride' and status not in ('completed','cancelled','no_rider_available')) then raise exception 'finish current ride first'; end if;
 if (select count(*) from qg_private.ride_quotes where customer_id=u and created_at>now()-interval '1 minute')>=5 then raise exception 'wait before requesting another quote'; end if;
 a:=public.queuego_service_area_route_status(p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,'ride');
 if not coalesce((a->>'available')::boolean,false) then raise exception 'outside ride service area'; end if;
 return jsonb_build_object('available',true);
end $$;
revoke all on function public.queuego_ride_quote_context(uuid,double precision,double precision,double precision,double precision) from public,anon,authenticated;
grant execute on function public.queuego_ride_quote_context(uuid,double precision,double precision,double precision,double precision) to service_role;

create or replace function public.queuego_admin_configure_ride(p_enabled boolean,p_pricing jsonb)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare u uuid;k text;n numeric;at_time timestamptz:=clock_timestamp();
begin
 perform qg_private.assert_live_session();
 select id into u from public.users where auth_user_id=auth.uid() and role='admin' and status='active';
 if u is null or p_enabled is null then raise exception 'active admin and explicit availability required' using errcode='42501'; end if;
 if p_pricing is null or jsonb_typeof(p_pricing)<>'object' or (select count(*) from jsonb_object_keys(p_pricing))<>5 then raise exception 'complete explicit ride pricing required'; end if;
 foreach k in array array['base_fare','included_km','per_km','minimum_fare','platform_fee_percent'] loop
  if jsonb_typeof(p_pricing->k) is distinct from 'number' then raise exception 'numeric ride pricing required'; end if;
  n:=(p_pricing->>k)::numeric;
  if n<0 or n>100000 or (k='minimum_fare' and n<=0) or (k='platform_fee_percent' and n>=100) then raise exception 'invalid ride pricing'; end if;
 end loop;
 if p_enabled and (p_pricing->>'platform_fee_percent')::numeric>0 and to_regprocedure('qg_private.rider_credit_order_authorized(uuid,uuid,numeric)') is null then raise exception 'existing rider credit integration is required before enabling charged rides'; end if;
 lock table public.queuego_platform_rules in share row exclusive mode;
 delete from public.queuego_platform_rules where (rule_key='feature.ride' or rule_key like 'ride.%') and effective_from>at_time;
 foreach k in array array['base_fare','included_km','per_km','minimum_fare','platform_fee_percent'] loop
  insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by) values('ride.'||k,p_pricing->k,at_time,'Explicit Admin passenger policy',u);
 end loop;
 insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by) values('feature.ride',to_jsonb(p_enabled),at_time,'Admin passenger availability',u);
 insert into public.audit_logs(user_id,action,entity_type,description,metadata) values(u,'ride_policy_update','system_settings','Passenger pricing and availability',jsonb_build_object('enabled',p_enabled,'pricing',p_pricing));
 return public.queuego_ride_config();
end $$;
revoke all on function public.queuego_admin_configure_ride(boolean,jsonb) from public,anon;
grant execute on function public.queuego_admin_configure_ride(boolean,jsonb) to authenticated;

create or replace function public.queuego_admin_public_licenses()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
begin
 perform qg_private.assert_live_session();
 if not public.is_active_admin() then raise exception 'active admin required' using errcode='42501'; end if;
 return coalesce((select jsonb_agg(jsonb_build_object('rider_id',r.id,'name',u.name,'expires_on',d.expires_on,'approved',qg_private.ride_public_license_valid(r.id,now())) order by u.name)
 from public.rider_profiles r join public.users u on u.id=r.user_id
 left join qg_private.rider_document_expiries d on d.rider_id=r.id and d.document_key='public_driver_license'
 where u.role='rider' and u.status='active'),'[]'::jsonb);
end $$;
revoke all on function public.queuego_admin_public_licenses() from public,anon;
grant execute on function public.queuego_admin_public_licenses() to authenticated;

-- Include passenger cash in the SAME existing Rider ledger.
CREATE OR REPLACE FUNCTION public.qg_rider_cash_ledger(p_from date, p_to date)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_rider uuid;
  v_rows jsonb;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active';

  if v_rider is null then
    raise exception 'active rider required';
  end if;

  if p_from is null or p_to is null or p_to<p_from or p_to>p_from+31 then
    raise exception 'invalid date range';
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'order_id',o.id,
        'order_number',o.order_number,
        'status',o.status,
        'customer_id',o.customer_id,
        'shop_id',o.shop_id,
        'rider_id',o.rider_id,
        'subtotal',o.subtotal,
        'delivery_fee',coalesce(o.rider_delivery_payout,o.delivery_fee),
        'customer_delivery_fee',o.delivery_fee,
        'ride_platform_fee',o.ride_platform_fee,
        'gp_amount',o.gp_amount,
        'total',o.total_amount,
        'cash_paid_merchant',case when o.fulfillment_vertical='ride' then 0 else a.amount end,
        'cash_paid_at',a.paid_at,
        'merchant_confirmed',mr.amount,
        'merchant_confirmed_at',mr.received_at,
        'cash_refunded_merchant',coalesce(rr.amount,0),
        'cash_refunded_at',rr.refunded_at,
        'cash_collected_customer',case when p.status='paid' then p.amount else 0 end,
        'customer_paid_at',p.paid_at,
        'payment_id',p.id,
        'payment_status',p.status,
        'proof_verified_at',d.verified_at,
        'created_at',o.created_at
      )
      order by o.created_at desc
    ),
    '[]'::jsonb
  )
  into v_rows
  from public.orders o
  left join public.rider_cash_advances a on a.order_id=o.id
  left join public.merchant_cash_receipts mr on mr.order_id=o.id
  left join public.qg_rider_cash_return_refunds rr on rr.order_id=o.id
  left join public.payments p on p.order_id=o.id and p.payment_method='cash'
  left join public.qg_delivery_proofs d on d.order_id=o.id
  where o.rider_id=v_rider
    and (o.order_type='shopping' or (o.order_type='service' and o.fulfillment_vertical='ride'))
    and (o.created_at at time zone 'Asia/Bangkok')::date between p_from and p_to;

  return v_rows;
end
$function$;
