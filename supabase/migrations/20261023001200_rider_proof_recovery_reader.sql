-- Recover ambiguous Rider proof mutations without reopening server-only proof tables.
-- Returns only an existence boolean after binding the authenticated identity to the
-- Rider currently assigned to the order.

create or replace function public.qg_rider_proof_committed(
  p_order_id uuid,
  p_stage text,
  p_photo_path text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_rider uuid;
  v_stage text := lower(trim(coalesce(p_stage,'')));
  v_path text := trim(coalesce(p_photo_path,''));
begin
  if p_order_id is null or v_path='' then
    return false;
  end if;

  if v_stage not in ('pickup','delivery') then
    raise exception 'unsupported proof stage';
  end if;

  select r.id
  into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=(select auth.uid())
    and u.role='rider';

  if v_rider is null then
    return false;
  end if;

  if not exists(
    select 1
    from public.orders o
    where o.id=p_order_id
      and o.rider_id=v_rider
  ) then
    return false;
  end if;

  if v_stage='pickup' then
    return exists(
      select 1
      from public.qg_pickup_proofs p
      where p.order_id=p_order_id
        and p.rider_id=v_rider
        and p.photo_path=v_path
    );
  end if;

  if v_stage='delivery' then
    return exists(
      select 1
      from public.qg_delivery_proofs p
      where p.order_id=p_order_id
        and p.rider_id=v_rider
        and p.photo_path=v_path
    );
  end if;

  return false;
end
$function$;

revoke all on function public.qg_rider_proof_committed(uuid,text,text) from public,anon;
grant execute on function public.qg_rider_proof_committed(uuid,text,text) to authenticated;
