create or replace function qg_private.ride_fare(p_distance_km numeric)
returns numeric
language plpgsql
stable security definer
set search_path to 'public','pg_temp'
as $function$
declare c jsonb;d numeric;fare numeric;
begin
 c:=public.queuego_ride_config();
 if not coalesce((c->>'enabled')::boolean,false) or not coalesce((c->>'configured')::boolean,false) then
   raise exception 'ride service is not configured or enabled';
 end if;
 d:=greatest(coalesce(p_distance_km,0),0);
 if d<=0 then raise exception 'valid ride distance required'; end if;
 if d<=(c->>'included_km')::numeric then
   fare:=(c->>'base_fare')::numeric;
 elsif d<=(c->>'long_distance_threshold_km')::numeric then
   fare:=(c->>'base_fare')::numeric+(d-(c->>'included_km')::numeric)*(c->>'per_km')::numeric;
 else
   fare:=d*(c->>'long_distance_per_km')::numeric;
 end if;
 return round(greatest((c->>'minimum_fare')::numeric,fare),0);
end
$function$;

create or replace function qg_private.ride_order_action(p_order_id uuid,p_action text)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare v_rider uuid;v_user uuid;o public.orders%rowtype;v_action text:=lower(trim(coalesce(p_action,'')));v_next text;
begin
 perform qg_private.assert_live_session();
 select r.id,r.user_id into v_rider,v_user
 from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active'
 for update of r;
 if v_rider is null or not qg_private.ride_owner_authorized(v_rider) then raise exception 'passenger ride access required'; end if;
 select * into o from public.orders where id=p_order_id and rider_id=v_rider and fulfillment_vertical='ride' for update;
 if not found then raise exception 'assigned passenger ride required'; end if;
 if o.status='completed' then return jsonb_build_object('status',o.status,'fare',o.total_amount); end if;
 perform set_config('queuego.v22_transition','rpc',true);
 perform set_config('queuego.ride_action','rpc',true);
 if v_action='arrive_pickup' and o.status='rider_assigned' then
   if o.rider_arrived_shop_at is null then update public.orders set rider_arrived_shop_at=now(),updated_at=now() where id=o.id; end if;
 elsif v_action='start' and o.status='rider_assigned' and o.rider_arrived_shop_at is not null then
   update public.orders set status='in_progress',picked_up_at=coalesce(picked_up_at,now()),delivering_at=coalesce(delivering_at,now()),updated_at=now() where id=o.id;
   update public.deliveries set status='picked_up',updated_at=now() where order_id=o.id;
 elsif v_action='arrive_dropoff' and o.status='in_progress' then
   if o.rider_arrived_customer_at is null then update public.orders set rider_arrived_customer_at=now(),updated_at=now() where id=o.id; end if;
 elsif v_action='complete' and o.status='in_progress' then
   update public.payments set status='paid',paid_at=coalesce(paid_at,now()),updated_at=now()
   where order_id=o.id and payment_method='cash' and status='pending';
   if not found and not exists(select 1 from public.payments where order_id=o.id and payment_method='cash' and status='paid') then
     raise exception 'cash payment record required';
   end if;
   update public.orders
   set rider_arrived_customer_at=coalesce(rider_arrived_customer_at,now()),
       status='completed',completed_at=coalesce(completed_at,now()),updated_at=now()
   where id=o.id;
   update public.deliveries set status='delivered',delivered_at=coalesce(delivered_at,now()),updated_at=now() where order_id=o.id;
 elsif v_action='start' and o.status='in_progress' then
   return jsonb_build_object('status',o.status,'fare',o.total_amount);
 else
   raise exception 'ride stage changed; refresh';
 end if;
 select status into v_next from public.orders where id=o.id;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(v_user,'ride_order_action','order',o.id,v_action,jsonb_build_object('from',o.status,'to',v_next,'fare',o.total_amount));
 insert into public.notifications(user_id,title,message,type,reference_id)
 values(o.customer_id,'อัปเดตการเดินทาง',
   case v_action when 'arrive_pickup' then 'คนขับถึงจุดรับแล้ว'
                 when 'start' then 'เริ่มการเดินทางแล้ว'
                 when 'arrive_dropoff' then 'ถึงจุดหมายแล้ว'
                 else 'จบการเดินทางแล้ว · ชำระเงินสด ฿'||o.total_amount end,
   'order',o.id);
 return jsonb_build_object('status',v_next,'fare',o.total_amount);
end
$function$;
