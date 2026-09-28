begin;
create or replace function public.market_verify_rider_vehicle(p_rider uuid,p_capacity numeric,p_status text)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare v_before jsonb;v_after jsonb;
begin
 if not public.is_active_admin() or p_status not in ('active','suspended')
 or (p_status='active' and (p_capacity is null or p_capacity<=0 or p_capacity>1000)) then
   raise exception 'vehicle approval denied';
 end if;
 select to_jsonb(r) into v_before from public.rider_profiles r where r.id=p_rider for update;
 if v_before is null or v_before->>'status'<>'active'
 or v_before->>'vehicle_type' not in ('motorcycle','car','saleng') then
   raise exception 'active rider vehicle not found';
 end if;
 update public.rider_profiles set vehicle_capacity_kg=case when p_status='active' then p_capacity else vehicle_capacity_kg end,
 vehicle_status=p_status,vehicle_verified_at=case when p_status='active' then now() else null end where id=p_rider
 returning to_jsonb(rider_profiles.*) into v_after;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(public.get_my_user_id(),'MARKET_VEHICLE_VERIFICATION','rider_profile',p_rider,'Admin checked Market rider vehicle',
 jsonb_build_object('before',jsonb_build_object('capacity',v_before->'vehicle_capacity_kg','status',v_before->'vehicle_status'),'after',jsonb_build_object('capacity',v_after->'vehicle_capacity_kg','status',v_after->'vehicle_status'),'admin_auth_id',auth.uid()));
end $$;
revoke all on function public.market_verify_rider_vehicle(uuid,numeric,text) from public,anon;
grant execute on function public.market_verify_rider_vehicle(uuid,numeric,text) to authenticated;
create or replace function public.market_rider_job_weights()
returns table(order_id uuid,weight_kg numeric)
language sql stable security definer set search_path=public,pg_temp as $$
 select o.id,public.market_order_weight(o.id) from public.orders o
 join public.rider_profiles r on r.user_id=public.get_my_user_id()
 where r.status='active' and o.fulfillment_vertical in ('market','grocery')
 and ((o.rider_id=r.id and o.status in ('assigned','picked_up','in_progress'))
 or (o.status='ready' and o.rider_id is null and r.vehicle_status='active'
 and r.vehicle_verified_at is not null and r.vehicle_capacity_kg>=public.market_order_weight(o.id)
 and exists(select 1 from public.deliveries d where d.order_id=o.id and d.status='pending' and d.rider_id is null)))
 order by o.created_at desc limit 100;
$$;
revoke all on function public.market_rider_job_weights() from public,anon;
grant execute on function public.market_rider_job_weights() to authenticated;
commit;
