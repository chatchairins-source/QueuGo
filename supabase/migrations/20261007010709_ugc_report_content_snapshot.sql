alter table public.qg_ugc_reports add column if not exists content_snapshot text;

CREATE OR REPLACE FUNCTION public.qg_report_chat(p_order_id uuid, p_message_id uuid DEFAULT NULL::uuid, p_reason text DEFAULT 'other'::text, p_details text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_actor uuid:=public.get_my_user_id();
  v_reported uuid;
  v_snapshot text;
  v_id uuid;
begin
  if v_actor is null or not public.qg_chat_read_allowed(p_order_id) then raise exception 'chat unavailable'; end if;
  if p_reason not in ('spam','harassment','inappropriate','fraud','safety','other') then raise exception 'invalid report reason'; end if;
  if char_length(coalesce(p_details,''))>1000 then raise exception 'report details too long'; end if;

  if p_message_id is not null then
    select m.sender_id,m.message into v_reported,v_snapshot
    from public.order_chat_messages m
    where m.id=p_message_id and m.order_id=p_order_id;
    if v_reported is null then raise exception 'message unavailable'; end if;
  else
    v_reported:=public.qg_chat_counterpart_user(p_order_id,v_actor);
  end if;
  if v_reported is null or v_reported=v_actor then raise exception 'reported user unavailable'; end if;

  if exists(
    select 1 from public.qg_ugc_reports r
    where r.reporter_user_id=v_actor and r.reported_user_id=v_reported
      and r.order_id=p_order_id and r.created_at>now()-interval '5 minutes'
  ) then
    raise exception 'report already submitted';
  end if;

  insert into public.qg_ugc_reports(reporter_user_id,reported_user_id,order_id,message_id,reason,details,content_snapshot)
  values(v_actor,v_reported,p_order_id,p_message_id,p_reason,nullif(trim(p_details),''),v_snapshot)
  returning id into v_id;

  insert into public.notifications(user_id,title,message,type,reference_id)
  select u.id,'รายงานแชตใหม่','มีรายงานเนื้อหาในแชตที่ต้องตรวจสอบ','admin_alert',p_order_id
  from public.users u where u.role='admin' and u.status='active';

  return v_id;
end $function$;
