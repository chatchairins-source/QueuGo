alter table public.orders
  add column if not exists rider_arrived_customer_at timestamptz;

comment on column public.orders.rider_arrived_customer_at
  is 'First time the assigned rider explicitly marked arrival at the customer delivery point. GPS distance is advisory only in the client.';

create or replace function public.qg_rider_mark_arrival(
  p_order_id uuid,
  p_target text,
  p_lat double precision default null,
  p_lng double precision default null
)
returns text
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_rider uuid;
  v_user uuid;
  v_order public.orders%rowtype;
  v_target text := lower(trim(coalesce(p_target,'')));
  v_first boolean := false;
begin
  select r.id,r.user_id into v_rider,v_user
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=(select auth.uid())
    and u.role='rider'
    and u.status='active';

  if v_rider is null then
    raise exception 'active rider login required';
  end if;

  select * into v_order
  from public.orders
  where id=p_order_id and rider_id=v_rider
  for update;

  if not found then
    raise exception 'order unavailable';
  end if;

  if (p_lat is null) <> (p_lng is null)
     or (p_lat is not null and (abs(p_lat)>90 or abs(p_lng)>180))
  then
    raise exception 'invalid GPS';
  end if;

  if v_target='shop' then
    if v_order.status not in ('rider_assigned','assigned','preparing','ready') then
      raise exception 'order is not at pickup stage';
    end if;

    v_first := v_order.rider_arrived_shop_at is null;
    if v_first then
      perform set_config('queuego.v22_transition','rpc',true);
      update public.orders
      set rider_arrived_shop_at=now(), updated_at=now()
      where id=p_order_id;

      insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
      values(
        v_user,'rider_arrived_shop','order',p_order_id,'rider marked arrival at pickup',
        jsonb_build_object(
          'rider_id',v_rider,'shop_id',v_order.shop_id,'customer_id',v_order.customer_id,
          'order_status',v_order.status,'gps_lat',p_lat,'gps_lng',p_lng
        )
      );

      insert into public.notifications(user_id,title,message,type,reference_id)
      select sp.user_id,'ไรเดอร์ถึงร้านแล้ว',
             'ไรเดอร์มาถึงร้านเพื่อรับออเดอร์ '||coalesce(v_order.order_number,p_order_id::text),
             'order',p_order_id
      from public.shop_profiles sp
      where sp.id=v_order.shop_id;
    end if;
    return 'shop';
  end if;

  if v_target='customer' then
    if v_order.status<>'in_progress' then
      raise exception 'order is not at delivery stage';
    end if;

    v_first := v_order.rider_arrived_customer_at is null;
    if v_first then
      perform set_config('queuego.v22_transition','rpc',true);
      update public.orders
      set rider_arrived_customer_at=now(), updated_at=now()
      where id=p_order_id;

      insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
      values(
        v_user,'rider_arrived_customer','order',p_order_id,'rider marked arrival at delivery point',
        jsonb_build_object(
          'rider_id',v_rider,'shop_id',v_order.shop_id,'customer_id',v_order.customer_id,
          'order_status',v_order.status,'gps_lat',p_lat,'gps_lng',p_lng
        )
      );

      if v_order.customer_id is not null then
        insert into public.notifications(user_id,title,message,type,reference_id)
        values(
          v_order.customer_id,'Rider ถึงจุดส่งแล้ว',
          'Rider มาถึงจุดส่งของออเดอร์ '||coalesce(v_order.order_number,p_order_id::text),
          'order',p_order_id
        );
      end if;
    end if;
    return 'customer';
  end if;

  raise exception 'unsupported arrival target';
end
$function$;

revoke execute on function public.qg_rider_mark_arrival(uuid,text,double precision,double precision) from public;
revoke execute on function public.qg_rider_mark_arrival(uuid,text,double precision,double precision) from anon;
grant execute on function public.qg_rider_mark_arrival(uuid,text,double precision,double precision) to authenticated;
