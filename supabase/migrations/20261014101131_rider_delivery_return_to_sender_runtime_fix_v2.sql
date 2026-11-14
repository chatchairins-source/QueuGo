create or replace function public.qg_rider_report_delivery_issue(
  p_case_id uuid,p_ticket_id uuid,p_order_id uuid,p_reason text,p_note text default null
) returns uuid
language plpgsql security definer set search_path=public,pg_temp
as $$
declare
  v_rider public.rider_profiles%rowtype;
  v_user public.users%rowtype;
  v_order public.orders%rowtype;
  v_existing uuid;
  v_details text;
  v_reason_label text;
begin
  select r.* into v_rider
  from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' limit 1;
  if v_rider.id is null then raise exception 'active rider required'; end if;
  select * into v_user from public.users where id=v_rider.user_id;

  if p_case_id is null or p_ticket_id is null or p_order_id is null then raise exception 'invalid request'; end if;
  if p_reason not in ('customer_unreachable','recipient_refuses_delivery','recipient_refuses_cash','wrong_dropoff','unsafe_dropoff','other')
    then raise exception 'invalid reason'; end if;
  if char_length(trim(coalesce(p_note,'')))>1200 then raise exception 'note too long'; end if;

  select * into v_order from public.orders where id=p_order_id and rider_id=v_rider.id for update;
  if not found then raise exception 'order unavailable'; end if;
  if v_order.status<>'in_progress' then raise exception 'delivery is not in progress'; end if;
  if v_order.rider_arrived_customer_at is null then raise exception 'arrive at customer before reporting delivery issue'; end if;

  select id into v_existing from public.qg_rider_delivery_returns
  where order_id=p_order_id and status in ('reported','return_required','returning')
  order by created_at desc limit 1;
  if v_existing is not null then return v_existing; end if;

  v_reason_label:=case p_reason
    when 'customer_unreachable' then 'ติดต่อลูกค้าไม่ได้'
    when 'recipient_refuses_delivery' then 'ลูกค้าปฏิเสธรับสินค้า'
    when 'recipient_refuses_cash' then 'ลูกค้าปฏิเสธชำระเงิน'
    when 'wrong_dropoff' then 'ที่อยู่หรือจุดส่งมีปัญหา'
    when 'unsafe_dropoff' then 'ไม่สามารถส่งได้อย่างปลอดภัย'
    else 'ปัญหาอื่น' end;

  v_details:='[DELIVERY_ISSUE:'||p_reason||'] '||coalesce(v_order.order_number,p_order_id::text)||
    ' · '||v_reason_label||case when trim(coalesce(p_note,''))='' then '' else ' · หมายเหตุ: '||left(trim(p_note),1200) end;
  if char_length(v_details) not between 10 and 2000 then raise exception 'invalid details'; end if;

  insert into public.qg_support_tickets(id,user_id,role,order_id,category,details,evidence_path)
  values(p_ticket_id,v_user.id,'rider',p_order_id,'rider',v_details,null) on conflict(id) do nothing;
  if not exists(select 1 from public.qg_support_tickets where id=p_ticket_id and user_id=v_user.id)
    then raise exception 'ticket id already in use'; end if;
  if not exists(select 1 from public.qg_ticket_events where ticket_id=p_ticket_id) then
    insert into public.qg_ticket_events(ticket_id,actor_id,to_status,note)
    values(p_ticket_id,v_user.id,'OPEN','delivery issue reported');
  end if;

  insert into public.qg_rider_delivery_returns(
    id,order_id,ticket_id,rider_id,shop_id,customer_id,reason,status,expected_refund_amount
  ) values(
    p_case_id,p_order_id,p_ticket_id,v_rider.id,v_order.shop_id,v_order.customer_id,p_reason,'reported',coalesce(v_order.subtotal,0)
  );

  insert into public.notifications(user_id,title,message,type,reference_id)
  select u.id,'มีเคสจัดส่งไม่สำเร็จ',coalesce(v_order.order_number,p_order_id::text)||' · '||v_reason_label,'order',p_order_id
  from public.users u where u.role='admin' and u.status='active';

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(v_user.id,'rider_delivery_issue_reported','order',p_order_id,v_reason_label,
    jsonb_build_object('return_case_id',p_case_id,'ticket_id',p_ticket_id,'reason',p_reason,'rider_id',v_rider.id,'shop_id',v_order.shop_id,'customer_id',v_order.customer_id));
  return p_case_id;
end $$;

revoke all on function public.qg_rider_report_delivery_issue(uuid,uuid,uuid,text,text) from public,anon;
grant execute on function public.qg_rider_report_delivery_issue(uuid,uuid,uuid,text,text) to authenticated;
