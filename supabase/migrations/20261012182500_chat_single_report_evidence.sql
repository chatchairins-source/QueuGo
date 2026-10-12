-- Single-action chat reporting with screenshot evidence.
-- Keeps the existing qg_report_chat RPC intact for backwards compatibility.
-- Screenshot evidence is compressed client-side and stored in the existing
-- content_snapshot field as __IMG__<data-url>, which the Admin UGC viewer
-- already renders.

CREATE OR REPLACE FUNCTION public.qg_report_chat_with_evidence(
  p_order_id uuid,
  p_reason text DEFAULT 'other'::text,
  p_details text DEFAULT NULL::text,
  p_evidence_data text DEFAULT NULL::text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_actor uuid:=public.get_my_user_id();
  v_reported uuid;
  v_id uuid;
  v_snapshot text;
begin
  if v_actor is null or not public.qg_chat_read_allowed(p_order_id) then
    raise exception 'chat unavailable';
  end if;
  if p_reason not in ('spam','harassment','inappropriate','fraud','safety','other') then
    raise exception 'invalid report reason';
  end if;
  if char_length(coalesce(p_details,''))>1000 then
    raise exception 'report details too long';
  end if;

  v_reported:=public.qg_chat_counterpart_user(p_order_id,v_actor);
  if v_reported is null or v_reported=v_actor then
    raise exception 'reported user unavailable';
  end if;

  if p_evidence_data is not null then
    if p_evidence_data !~ '^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$' then
      raise exception 'invalid report evidence';
    end if;
    if char_length(p_evidence_data)>900000 then
      raise exception 'report evidence too large';
    end if;
    v_snapshot:='__IMG__'||p_evidence_data;
  end if;

  if exists(
    select 1 from public.qg_ugc_reports r
    where r.reporter_user_id=v_actor
      and r.reported_user_id=v_reported
      and r.order_id=p_order_id
      and r.created_at>now()-interval '5 minutes'
  ) then
    raise exception 'report already submitted';
  end if;

  insert into public.qg_ugc_reports(
    reporter_user_id,reported_user_id,order_id,message_id,reason,details,content_snapshot
  )
  values(
    v_actor,v_reported,p_order_id,null,p_reason,nullif(trim(p_details),''),v_snapshot
  )
  returning id into v_id;

  insert into public.notifications(user_id,title,message,type,reference_id)
  select u.id,'รายงานแชตใหม่','มีรายงานแชตพร้อมภาพหลักฐานที่ต้องตรวจสอบ','admin_alert',p_order_id
  from public.users u
  where u.role='admin' and u.status='active';

  return v_id;
end
$function$;

revoke all on function public.qg_report_chat_with_evidence(uuid,text,text,text) from public,anon;
grant execute on function public.qg_report_chat_with_evidence(uuid,text,text,text) to authenticated,service_role;
