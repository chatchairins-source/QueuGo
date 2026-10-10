-- QueueGo Production backup snapshot before merchant order-item idempotency extension.
-- Captured read-only from Supabase Production project pkypiqhlrmzocysgeqew on 2026-10-10.
-- This file is NOT a migration. It preserves the previous qg_merchant_action_once
-- definition so the exact pre-change behavior can be reviewed/recovered manually.

create or replace function public.qg_merchant_action_once(
  p_request_id uuid,
  p_kind text,
  p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare
  v_user uuid;
  v_existing public.qg_merchant_action_receipts%rowtype;
  v_result jsonb;
  v_status text;
  v_kind text:=lower(trim(coalesce(p_kind,'')));
  v_payload jsonb;
begin
  select u.id into v_user
  from public.users u
  join public.shop_profiles sp on sp.user_id=u.id
  where u.auth_user_id=auth.uid()
    and u.role='shop'
    and u.status='active'
  order by sp.created_at asc
  limit 1;

  if v_user is null then raise exception 'active shop request required'; end if;
  if p_request_id is null or p_payload is null or jsonb_typeof(p_payload)<>'object' then
    raise exception 'valid merchant request required';
  end if;

  if v_kind='order' then
    if nullif(p_payload->>'p_order_id','') is null or nullif(p_payload->>'p_action','') is null then
      raise exception 'order action payload required';
    end if;
    v_payload:=jsonb_build_object(
      'p_order_id',(p_payload->>'p_order_id')::uuid,
      'p_action',lower(trim(p_payload->>'p_action')),
      'p_reason',nullif(trim(coalesce(p_payload->>'p_reason','')),'')
    );
  else
    raise exception 'unsupported merchant action';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,0));

  select * into v_existing from public.qg_merchant_action_receipts where request_id=p_request_id;
  if found then
    if v_existing.user_id<>v_user or v_existing.kind<>v_kind or v_existing.payload<>v_payload then
      raise exception 'request id already in use';
    end if;
    return v_existing.result || jsonb_build_object('replayed',true);
  end if;

  select public.merchant_order_action(
    (v_payload->>'p_order_id')::uuid,
    v_payload->>'p_action',
    v_payload->>'p_reason'
  ) into v_status;

  v_result:=jsonb_build_object(
    'order_id',v_payload->>'p_order_id',
    'action',v_payload->>'p_action',
    'status',v_status,
    'replayed',false
  );

  insert into public.qg_merchant_action_receipts(request_id,user_id,kind,payload,result)
  values(p_request_id,v_user,v_kind,v_payload,v_result);

  return v_result;
end
$function$;
