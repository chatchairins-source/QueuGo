create table if not exists qg_private.rider_benefits (
  id uuid primary key default gen_random_uuid(),
  category text not null,
  provider_name text not null,
  title text not null,
  description text,
  starts_on date not null default current_date,
  expires_on date,
  action_label text,
  action_url text,
  support_phone text,
  enabled boolean not null default true,
  recorded_by uuid references public.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint rider_benefits_category_check
    check (category in ('fuel','maintenance','food','telecom','health','other')),
  constraint rider_benefits_provider_check
    check (length(btrim(provider_name)) between 2 and 120),
  constraint rider_benefits_title_check
    check (length(btrim(title)) between 2 and 160),
  constraint rider_benefits_description_check
    check (description is null or length(btrim(description)) between 1 and 1200),
  constraint rider_benefits_date_check
    check (expires_on is null or expires_on >= starts_on),
  constraint rider_benefits_action_label_check
    check (action_label is null or length(btrim(action_label)) between 1 and 60),
  constraint rider_benefits_action_url_check
    check (action_url is null or action_url ~ '^https://'),
  constraint rider_benefits_phone_check
    check (support_phone is null or support_phone ~ '^[+]?[0-9]{8,15}$')
);

create index if not exists rider_benefits_active_idx
  on qg_private.rider_benefits(enabled, starts_on, expires_on);

revoke all on table qg_private.rider_benefits from public, anon, authenticated;
grant all on table qg_private.rider_benefits to service_role;

create or replace function qg_private.rider_benefits()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid := qg_private.current_rider_profile_id();
  v_result jsonb;
begin
  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;
  select coalesce(jsonb_agg(jsonb_build_object(
    'benefit_id',b.id,'category',b.category,'provider_name',b.provider_name,'title',b.title,
    'description',b.description,'starts_on',b.starts_on,'expires_on',b.expires_on,
    'action_label',b.action_label,'action_url',b.action_url,'support_phone',b.support_phone
  ) order by b.expires_on nulls last,b.provider_name,b.title,b.id),'[]'::jsonb)
  into v_result
  from qg_private.rider_benefits b
  where b.enabled
    and b.starts_on<=current_date
    and (b.expires_on is null or b.expires_on>=current_date);
  return v_result;
end $$;

revoke all on function qg_private.rider_benefits() from public, anon;
grant execute on function qg_private.rider_benefits() to authenticated;

create or replace function public.qg_rider_benefits()
returns jsonb language sql stable security invoker set search_path=''
as $$ select qg_private.rider_benefits(); $$;
revoke all on function public.qg_rider_benefits() from public, anon;
grant execute on function public.qg_rider_benefits() to authenticated, service_role;

create or replace function qg_private.admin_rider_benefits()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare v_result jsonb;
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then raise exception 'admin required' using errcode='42501'; end if;
  select coalesce(jsonb_agg(jsonb_build_object(
    'benefit_id',b.id,'category',b.category,'provider_name',b.provider_name,'title',b.title,
    'description',b.description,'starts_on',b.starts_on,'expires_on',b.expires_on,'enabled',b.enabled,
    'status',case when not b.enabled then 'disabled' when b.starts_on>current_date then 'upcoming'
      when b.expires_on is not null and b.expires_on<current_date then 'expired' else 'active' end,
    'action_label',b.action_label,'action_url',b.action_url,'support_phone',b.support_phone
  ) order by b.enabled desc,b.expires_on nulls last,b.provider_name,b.title,b.id),'[]'::jsonb)
  into v_result from qg_private.rider_benefits b;
  return v_result;
end $$;

revoke all on function qg_private.admin_rider_benefits() from public, anon;
grant execute on function qg_private.admin_rider_benefits() to authenticated;

create or replace function public.qg_admin_rider_benefits()
returns jsonb language sql stable security invoker set search_path=''
as $$ select qg_private.admin_rider_benefits(); $$;
revoke all on function public.qg_admin_rider_benefits() from public, anon;
grant execute on function public.qg_admin_rider_benefits() to authenticated, service_role;

create or replace function qg_private.admin_save_rider_benefit(
  p_benefit_id uuid,p_category text,p_provider_name text,p_title text,p_description text,
  p_starts_on date,p_expires_on date,p_action_label text,p_action_url text,p_support_phone text,p_enabled boolean
)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare
  v_id uuid; v_admin_user uuid; v_category text:=lower(btrim(coalesce(p_category,'')));
  v_provider text:=nullif(btrim(coalesce(p_provider_name,'')),'');
  v_title text:=nullif(btrim(coalesce(p_title,'')),'');
  v_description text:=nullif(btrim(coalesce(p_description,'')),'');
  v_action_label text:=nullif(btrim(coalesce(p_action_label,'')),'');
  v_action_url text:=nullif(btrim(coalesce(p_action_url,'')),'');
  v_phone text:=nullif(regexp_replace(coalesce(p_support_phone,''),'[ -]','','g'),'');
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then raise exception 'admin required' using errcode='42501'; end if;
  select u.id into v_admin_user from public.users u
  where u.auth_user_id=auth.uid() and u.role='admin' and u.status='active' limit 1;
  if v_category not in ('fuel','maintenance','food','telecom','health','other') then raise exception 'invalid rider benefit category'; end if;
  if v_provider is null or length(v_provider) not between 2 and 120 then raise exception 'benefit provider required'; end if;
  if v_title is null or length(v_title) not between 2 and 160 then raise exception 'benefit title required'; end if;
  if v_description is not null and length(v_description)>1200 then raise exception 'benefit description too long'; end if;
  if p_starts_on is null or (p_expires_on is not null and p_expires_on<p_starts_on) then raise exception 'invalid benefit dates'; end if;
  if v_action_label is not null and length(v_action_label)>60 then raise exception 'benefit action label too long'; end if;
  if v_action_url is not null and v_action_url !~ '^https://' then raise exception 'benefit action url must use https'; end if;
  if v_phone is not null and v_phone !~ '^[+]?[0-9]{8,15}$' then raise exception 'invalid benefit support phone'; end if;
  if p_benefit_id is null then
    insert into qg_private.rider_benefits(
      category,provider_name,title,description,starts_on,expires_on,action_label,action_url,support_phone,enabled,recorded_by
    ) values(
      v_category,v_provider,v_title,v_description,p_starts_on,p_expires_on,v_action_label,v_action_url,v_phone,coalesce(p_enabled,true),v_admin_user
    ) returning id into v_id;
  else
    update qg_private.rider_benefits set
      category=v_category,provider_name=v_provider,title=v_title,description=v_description,
      starts_on=p_starts_on,expires_on=p_expires_on,action_label=v_action_label,action_url=v_action_url,
      support_phone=v_phone,enabled=coalesce(p_enabled,true),recorded_by=v_admin_user,updated_at=now()
    where id=p_benefit_id returning id into v_id;
    if v_id is null then raise exception 'rider benefit unavailable'; end if;
  end if;
  return jsonb_build_object('benefit_id',v_id);
end $$;

revoke all on function qg_private.admin_save_rider_benefit(uuid,text,text,text,text,date,date,text,text,text,boolean) from public, anon;
grant execute on function qg_private.admin_save_rider_benefit(uuid,text,text,text,text,date,date,text,text,text,boolean) to authenticated;

create or replace function public.qg_admin_save_rider_benefit(
  p_benefit_id uuid,p_category text,p_provider_name text,p_title text,p_description text,
  p_starts_on date,p_expires_on date,p_action_label text,p_action_url text,p_support_phone text,p_enabled boolean
)
returns jsonb language sql security invoker set search_path=''
as $$ select qg_private.admin_save_rider_benefit(
  p_benefit_id,p_category,p_provider_name,p_title,p_description,p_starts_on,p_expires_on,
  p_action_label,p_action_url,p_support_phone,p_enabled
); $$;
revoke all on function public.qg_admin_save_rider_benefit(uuid,text,text,text,text,date,date,text,text,text,boolean) from public, anon;
grant execute on function public.qg_admin_save_rider_benefit(uuid,text,text,text,text,date,date,text,text,text,boolean) to authenticated, service_role;

create or replace function qg_private.admin_delete_rider_benefit(p_benefit_id uuid)
returns void language plpgsql security definer set search_path=''
as $$
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then raise exception 'admin required' using errcode='42501'; end if;
  delete from qg_private.rider_benefits where id=p_benefit_id;
  if not found then raise exception 'rider benefit unavailable'; end if;
end $$;
revoke all on function qg_private.admin_delete_rider_benefit(uuid) from public, anon;
grant execute on function qg_private.admin_delete_rider_benefit(uuid) to authenticated;

create or replace function public.qg_admin_delete_rider_benefit(p_benefit_id uuid)
returns void language sql security invoker set search_path=''
as $$ select qg_private.admin_delete_rider_benefit(p_benefit_id); $$;
revoke all on function public.qg_admin_delete_rider_benefit(uuid) from public, anon;
grant execute on function public.qg_admin_delete_rider_benefit(uuid) to authenticated, service_role;