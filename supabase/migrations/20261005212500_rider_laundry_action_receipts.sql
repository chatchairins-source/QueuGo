-- Extend the durable Rider action receipt wrapper to Laundry operations.
-- This keeps one tap = one durable request across timeout/retry/reopen recovery.

create or replace function public.qg_rider_action_once(
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
  v_existing public.qg_rider_action_receipts%rowtype;
  v_result jsonb;
begin
  select u.id into v_user
  from public.users u
  join public.rider_profiles r on r.user_id=u.id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active';

  if v_user is null
     or p_request_id is null
     or p_payload is null
     or jsonb_typeof(p_payload)<>'object' then
    raise exception 'active rider request required';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,0));

  select * into v_existing
  from public.qg_rider_action_receipts
  where request_id=p_request_id;

  if found then
    if v_existing.user_id<>v_user
       or v_existing.kind<>p_kind
       or v_existing.payload<>p_payload then
      raise exception 'request id already in use';
    end if;
    return v_existing.result;
  end if;

  case p_kind
    when 'bundle_claim' then
      select to_jsonb(public.queuego_claim_route_bundle((p_payload->>'p_order_id')::uuid))
        into v_result;
    when 'claim' then
      select to_jsonb(public.rider_claim_order((p_payload->>'p_order_id')::uuid))
        into v_result;
    when 'market_claim' then
      select to_jsonb(public.market_rider_claim_group((p_payload->>'p_order_id')::uuid))
        into v_result;
    when 'order' then
      select to_jsonb(public.rider_order_action(
        (p_payload->>'p_order_id')::uuid,
        p_payload->>'p_action'
      )) into v_result;
    when 'market' then
      select to_jsonb(public.market_rider_group_action(
        (p_payload->>'p_market_order_id')::uuid,
        p_payload->>'p_action'
      )) into v_result;
    when 'market_pickup' then
      select to_jsonb(public.market_rider_confirm_pickup_cash(
        (p_payload->>'p_pickup_id')::uuid,
        (p_payload->>'p_amount')::numeric
      )) into v_result;
    when 'laundry_mode' then
      select to_jsonb(public.queuego_set_laundry_rider_mode(
        (p_payload->>'p_enabled')::boolean
      )) into v_result;
    when 'laundry_invite' then
      select to_jsonb(public.queuego_laundry_rider_invite_action(
        (p_payload->>'p_invite_id')::uuid,
        (p_payload->>'p_accept')::boolean
      )) into v_result;
    when 'laundry_claim' then
      select to_jsonb(public.queuego_claim_laundry_job(
        (p_payload->>'p_job_id')::uuid
      )) into v_result;
    when 'laundry_action' then
      select to_jsonb(public.queuego_laundry_rider_action(
        (p_payload->>'p_job_id')::uuid,
        p_payload->>'p_action'
      )) into v_result;
    else
      raise exception 'unsupported rider action';
  end case;

  insert into public.qg_rider_action_receipts(request_id,user_id,kind,payload,result)
  values(p_request_id,v_user,p_kind,p_payload,coalesce(v_result,'null'::jsonb));

  return v_result;
end
$function$;

revoke all on function public.qg_rider_action_once(uuid,text,jsonb) from public,anon;
grant execute on function public.qg_rider_action_once(uuid,text,jsonb) to authenticated;
