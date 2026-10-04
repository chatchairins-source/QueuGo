-- Serialize claims for the same rider before checking active work.
-- Order/group locks still arbitrate competing riders; prices and states are unchanged.
BEGIN;

CREATE OR REPLACE FUNCTION public.market_rider_claim_group(p_order_id uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_rider uuid;v_user uuid;v_group uuid;v_weight numeric;
begin
 select r.id,r.user_id into v_rider,v_user from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active'
 and coalesce((r.metadata->>'online')::boolean,false) and coalesce((r.metadata->>'available')::boolean,false)
 for update of r;
 if v_rider is null then raise exception 'active online rider required'; end if;
 if exists(select 1 from public.orders where rider_id=v_rider and status in('rider_assigned','preparing','ready','assigned','picked_up','in_progress')) then raise exception 'finish current order before claiming another'; end if;
 select market_order_id into v_group from public.orders where id=p_order_id;
 if v_group is null then raise exception 'market order unavailable'; end if;
 perform pg_advisory_xact_lock(hashtextextended(v_group::text,313));
 perform 1 from public.orders where market_order_id=v_group order by id for update;
 if not exists(select 1 from public.orders where market_order_id=v_group and status<>'cancelled') then raise exception 'market group unavailable'; end if;
 if exists(select 1 from public.orders where market_order_id=v_group and status not in('searching_rider','cancelled')) then raise exception 'all shops must accept before rider claim'; end if;
 if exists(select 1 from public.orders where market_order_id=v_group and status<>'cancelled' and rider_id is not null) then raise exception 'market group already claimed'; end if;
 select coalesce(sum(public.market_order_weight(id)),0) into v_weight from public.orders where market_order_id=v_group and status<>'cancelled';
 if v_weight<=0 then raise exception 'market group weight unavailable'; end if;
 if not exists(select 1 from public.rider_profiles where id=v_rider and vehicle_type in('motorcycle','car','saleng') and vehicle_status='active' and vehicle_verified_at is not null and vehicle_capacity_kg>=v_weight) then raise exception 'vehicle capacity insufficient for market group'; end if;
 perform set_config('queuego.v22_transition','rpc',true);
 update public.orders set rider_id=v_rider,status='rider_assigned',rider_assigned_at=coalesce(rider_assigned_at,now()),updated_at=now() where market_order_id=v_group and status='searching_rider';
 update public.deliveries d set rider_id=v_rider,status='assigned',updated_at=now() from public.orders o where o.id=d.order_id and o.market_order_id=v_group and d.status='pending';
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata) values(v_user,'market_rider_claim','market_order',v_group,'rider assigned to market group',jsonb_build_object('rider_id',v_rider,'weight_kg',v_weight));
 return v_group;
end $function$
;

CREATE OR REPLACE FUNCTION public.rider_claim_order(p_order_id uuid)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_rider uuid; v_user uuid; v_order public.orders%rowtype;
begin
 select r.id,r.user_id into v_rider,v_user from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active'
 and coalesce((r.metadata->>'online')::boolean,false)=true and coalesce((r.metadata->>'available')::boolean,false)=true
 for update of r;
 if v_rider is null then raise exception 'active online rider required'; end if;
 if exists(select 1 from public.orders where rider_id=v_rider and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')) then raise exception 'finish current order before claiming another'; end if;
 select * into v_order from public.orders where id=p_order_id for update;
 if not found or v_order.order_type<>'shopping' or v_order.status<>'searching_rider' or v_order.rider_id is not null then raise exception 'order no longer available'; end if;
 if v_order.fulfillment_vertical<>'food' and not exists(select 1 from public.rider_profiles r where r.id=v_rider and r.status='active' and r.vehicle_type in ('motorcycle','car','saleng') and r.vehicle_status='active' and r.vehicle_verified_at is not null and r.vehicle_capacity_kg>=public.market_order_weight(p_order_id) and public.market_order_weight(p_order_id)>0) then raise exception 'vehicle capacity or availability insufficient for market order'; end if;
 perform set_config('queuego.v22_transition','rpc',true);
 update public.orders set rider_id=v_rider,status='rider_assigned',rider_assigned_at=coalesce(rider_assigned_at,now()),updated_at=now(),
 note='__QT_ORDER_STATUS__=rider_assigned'||E'\n'||regexp_replace(coalesce(note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g')
 where id=p_order_id;
 update public.deliveries set rider_id=v_rider,status='assigned',updated_at=now() where order_id=p_order_id and rider_id is null and status='pending';
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata) values(v_user,'rider_claim','order',p_order_id,'rider_assigned',jsonb_build_object('from','searching_rider','to','rider_assigned'));
 if v_order.customer_id is not null then insert into public.notifications(user_id,title,message,type,reference_id) values(v_order.customer_id,'พบไรเดอร์แล้ว','มีไรเดอร์รับออเดอร์แล้ว ร้านค้าจะเริ่มเตรียมสินค้า','order',p_order_id); end if;
 insert into public.notifications(user_id,title,message,type,reference_id)
 select sp.user_id,'พบไรเดอร์แล้ว','ไรเดอร์รับงานแล้ว สามารถเริ่มเตรียมสินค้าได้','order',p_order_id from public.shop_profiles sp where sp.id=v_order.shop_id;
 return 'rider_assigned';
end $function$
;

COMMIT;
