-- Admin UGC moderation paging.
-- Keep qg_ugc_reports client-table access denied; expose only a live-session,
-- active-Admin guarded read page through an invoker wrapper.

create or replace function qg_private.admin_ugc_reports_page(
  p_status text default null,
  p_limit integer default 100,
  p_cursor_created_at timestamptz default null,
  p_cursor_id uuid default null
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit,100),1),100);
  v_rows jsonb := '[]'::jsonb;
  v_more boolean := false;
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'active admin required' using errcode='42501';
  end if;
  if p_status is not null and p_status not in ('pending','under_review','resolved','dismissed') then
    raise exception 'invalid report status' using errcode='22023';
  end if;
  if (p_cursor_created_at is null) <> (p_cursor_id is null) then
    raise exception 'cursor requires created_at and id' using errcode='22023';
  end if;

  select coalesce(jsonb_agg(to_jsonb(x) order by x.created_at desc,x.id desc),'[]'::jsonb)
    into v_rows
  from (
    select r.*
    from public.qg_ugc_reports r
    where (p_status is null or r.status=p_status)
      and (
        p_cursor_created_at is null
        or r.created_at < p_cursor_created_at
        or (r.created_at = p_cursor_created_at and r.id < p_cursor_id)
      )
    order by r.created_at desc,r.id desc
    limit v_limit+1
  ) x;

  v_more := jsonb_array_length(v_rows)>v_limit;
  if v_more then
    v_rows := v_rows - v_limit;
  end if;

  return jsonb_build_object(
    'items',v_rows,
    'limit',v_limit,
    'more',v_more
  );
end
$$;

create or replace function public.qg_admin_ugc_reports_page(
  p_status text default null,
  p_limit integer default 100,
  p_cursor_created_at timestamptz default null,
  p_cursor_id uuid default null
)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select qg_private.admin_ugc_reports_page(
    p_status,p_limit,p_cursor_created_at,p_cursor_id
  );
$$;

revoke all on function qg_private.admin_ugc_reports_page(text,integer,timestamptz,uuid)
  from public,anon;
grant execute on function qg_private.admin_ugc_reports_page(text,integer,timestamptz,uuid)
  to authenticated,service_role;

revoke all on function public.qg_admin_ugc_reports_page(text,integer,timestamptz,uuid)
  from public,anon;
grant execute on function public.qg_admin_ugc_reports_page(text,integer,timestamptz,uuid)
  to authenticated,service_role;

create index if not exists qg_ugc_reports_recent_idx
  on public.qg_ugc_reports (created_at desc,id desc);
