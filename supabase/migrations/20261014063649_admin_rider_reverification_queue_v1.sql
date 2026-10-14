create or replace function qg_private.admin_rider_reverification_queue()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_result jsonb;
begin
  perform qg_private.assert_live_session();

  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'rider_id', r.id,
        'request_id', rr.id,
        'status', rr.status,
        'reason', rr.reason,
        'selfie_path', rr.selfie_path,
        'requested_at', rr.requested_at,
        'submitted_at', rr.submitted_at,
        'reviewed_at', rr.reviewed_at,
        'review_note', rr.review_note
      )
      order by coalesce(rr.updated_at, r.updated_at) desc, r.id
    ),
    '[]'::jsonb
  )
  into v_result
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  left join lateral (
    select x.*
    from qg_private.rider_reverification_requests x
    where x.rider_id=r.id
    order by x.requested_at desc
    limit 1
  ) rr on true
  where u.role='rider'
    and u.status<>'deleted';

  return v_result;
end $$;

revoke all on function qg_private.admin_rider_reverification_queue() from public, anon;
grant execute on function qg_private.admin_rider_reverification_queue() to authenticated;

create or replace function public.qg_admin_rider_reverification_queue()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.admin_rider_reverification_queue(); $$;

revoke all on function public.qg_admin_rider_reverification_queue() from public, anon;
grant execute on function public.qg_admin_rider_reverification_queue() to authenticated, service_role;
