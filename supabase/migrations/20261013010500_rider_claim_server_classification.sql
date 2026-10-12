-- Server-authoritative Rider claim classification.
-- Compatibility hardening for installed/cached Native Rider builds that may classify JSON null
-- market_order_id as the literal string "null" and send market_claim for an ordinary order.
-- The live offer remains mandatory; only the server decides normal-vs-market from orders.market_order_id.

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
  v_order uuid;
  v_market_order uuid;
begin
  select u.id
  into v_user
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

  select *
  into v_existing
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

  if p_kind in ('claim','market_claim') then
    v_order:=(p_payload->>'p_order_id')::uuid;

    -- Preserve the selected-Rider lease/concurrency guard before classifying the order.
    perform public.qg_require_live_rider_offer(v_order);

    -- Client claim kind is only a hint. Production orders are the source of truth.
    select o.market_order_id
    into v_market_order
    from public.orders o
    where o.id=v_order;

    if not found then
      raise exception 'order no longer available';
    end if;

    if v_market_order is null then
      select to_jsonb(public.rider_claim_order(v_order))
      into v_result;
    else
      select to_jsonb(public.market_rider_claim_group(v_order))
      into v_result;
    end if;
  else
    case p_kind
      when 'bundle_claim' then
        select to_jsonb(public.queuego_claim_route_bundle((p_payload->>'p_order_id')::uuid)) into v_result;
      when 'order' then
        select to_jsonb(public.rider_order_action((p_payload->>'p_order_id')::uuid,p_payload->>'p_action')) into v_result;
      when 'market' then
        select to_jsonb(public.market_rider_group_action((p_payload->>'p_market_order_id')::uuid,p_payload->>'p_action')) into v_result;
      when 'market_pickup' then
        select to_jsonb(public.market_rider_confirm_pickup_cash((p_payload->>'p_pickup_id')::uuid,(p_payload->>'p_amount')::numeric)) into v_result;
      when 'laundry_mode' then
        select to_jsonb(public.queuego_set_laundry_rider_mode((p_payload->>'p_enabled')::boolean)) into v_result;
      when 'laundry_invite' then
        select to_jsonb(public.queuego_laundry_rider_invite_action((p_payload->>'p_invite_id')::uuid,(p_payload->>'p_accept')::boolean)) into v_result;
      when 'laundry_claim' then
        select to_jsonb(public.queuego_claim_laundry_job((p_payload->>'p_job_id')::uuid)) into v_result;
      when 'laundry_action' then
        select to_jsonb(public.queuego_laundry_rider_action((p_payload->>'p_job_id')::uuid,p_payload->>'p_action')) into v_result;
      else
        raise exception 'unsupported rider action';
    end case;
  end if;

  if p_kind in ('claim','market_claim') then
    perform public.qg_accept_offer_receipt(v_order);
  end if;

  insert into public.qg_rider_action_receipts(request_id,user_id,kind,payload,result)
  values(p_request_id,v_user,p_kind,p_payload,coalesce(v_result,'null'::jsonb));

  return v_result;
end
$function$;

revoke all on function public.qg_rider_action_once(uuid,text,jsonb) from public,anon;
grant execute on function public.qg_rider_action_once(uuid,text,jsonb) to authenticated;

-- Keep the underlying privileged claim functions inaccessible to direct app RPC calls.
revoke execute on function public.rider_claim_order(uuid) from authenticated,anon;
revoke execute on function public.market_rider_claim_group(uuid) from authenticated,anon;
