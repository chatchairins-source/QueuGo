create table if not exists qg_private.rider_insurance_policies (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.rider_profiles(id) on delete cascade,
  coverage_type text not null,
  provider_name text not null,
  product_name text,
  policy_number text,
  starts_on date not null,
  expires_on date not null,
  coverage_summary text,
  support_phone text,
  support_url text,
  recorded_by uuid references public.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint rider_insurance_type_check check (coverage_type in ('personal_accident','income_protection','vehicle','other')),
  constraint rider_insurance_provider_check check (length(btrim(provider_name)) between 2 and 120),
  constraint rider_insurance_product_check check (product_name is null or length(btrim(product_name)) between 1 and 160),
  constraint rider_insurance_policy_number_check check (policy_number is null or length(btrim(policy_number)) between 1 and 100),
  constraint rider_insurance_date_check check (expires_on >= starts_on),
  constraint rider_insurance_summary_check check (coverage_summary is null or length(coverage_summary) <= 1000),
  constraint rider_insurance_phone_check check (support_phone is null or support_phone ~ '^[+]?[0-9]{8,15}$'),
  constraint rider_insurance_url_check check (support_url is null or support_url ~ '^https://')
);
create index if not exists rider_insurance_policies_rider_idx on qg_private.rider_insurance_policies(rider_id,expires_on desc);
revoke all on table qg_private.rider_insurance_policies from public, anon, authenticated;
grant all on table qg_private.rider_insurance_policies to service_role;

create or replace function qg_private.rider_insurance_policies()
returns jsonb language plpgsql stable security definer set search_path = ''
as $$
declare v_rider uuid := qg_private.current_rider_profile_id(); v_result jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object(
    'policy_id',p.id,'coverage_type',p.coverage_type,'provider_name',p.provider_name,
    'product_name',p.product_name,'policy_number',p.policy_number,'starts_on',p.starts_on,
    'expires_on',p.expires_on,'status',case when p.starts_on>current_date then 'upcoming'
      when p.expires_on<current_date then 'expired' else 'active' end,
    'coverage_summary',p.coverage_summary,'support_phone',p.support_phone,'support_url',p.support_url
  ) order by (p.starts_on<=current_date and p.expires_on>=current_date) desc,p.expires_on desc,p.id),'[]'::jsonb)
  into v_result from qg_private.rider_insurance_policies p where p.rider_id=v_rider;
  return v_result;
end $$;
revoke all on function qg_private.rider_insurance_policies() from public, anon;
grant execute on function qg_private.rider_insurance_policies() to authenticated;
create or replace function public.qg_rider_insurance_policies()
returns jsonb language sql stable security invoker set search_path=''
as $$ select qg_private.rider_insurance_policies(); $$;
revoke all on function public.qg_rider_insurance_policies() from public, anon;
grant execute on function public.qg_rider_insurance_policies() to authenticated, service_role;

create or replace function qg_private.admin_rider_insurance_queue()
returns jsonb language plpgsql stable security definer set search_path=''
as $$
declare v_result jsonb;
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then raise exception 'admin required' using errcode='42501'; end if;
  select coalesce(jsonb_agg(jsonb_build_object(
    'policy_id',p.id,'rider_id',p.rider_id,'rider_name',coalesce(nullif(r.rider_name,''),u.name,'Rider'),
    'coverage_type',p.coverage_type,'provider_name',p.provider_name,'product_name',p.product_name,
    'policy_number',p.policy_number,'starts_on',p.starts_on,'expires_on',p.expires_on,
    'status',case when p.starts_on>current_date then 'upcoming' when p.expires_on<current_date then 'expired' else 'active' end,
    'coverage_summary',p.coverage_summary,'support_phone',p.support_phone,'support_url',p.support_url
  ) order by r.rider_name,p.expires_on desc,p.id),'[]'::jsonb)
  into v_result from qg_private.rider_insurance_policies p
  join public.rider_profiles r on r.id=p.rider_id
  join public.users u on u.id=r.user_id
  where u.role='rider' and u.status<>'deleted';
  return v_result;
end $$;
revoke all on function qg_private.admin_rider_insurance_queue() from public, anon;
grant execute on function qg_private.admin_rider_insurance_queue() to authenticated;
create or replace function public.qg_admin_rider_insurance_queue()
returns jsonb language sql stable security invoker set search_path=''
as $$ select qg_private.admin_rider_insurance_queue(); $$;
revoke all on function public.qg_admin_rider_insurance_queue() from public, anon;
grant execute on function public.qg_admin_rider_insurance_queue() to authenticated, service_role;

create or replace function qg_private.admin_save_rider_insurance(
  p_policy_id uuid,p_rider_id uuid,p_coverage_type text,p_provider_name text,p_product_name text,
  p_policy_number text,p_starts_on date,p_expires_on date,p_coverage_summary text,p_support_phone text,p_support_url text
)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare
  v_id uuid; v_admin_user uuid; v_type text:=lower(btrim(coalesce(p_coverage_type,'')));
  v_provider text:=nullif(btrim(coalesce(p_provider_name,'')),'');
  v_product text:=nullif(btrim(coalesce(p_product_name,'')),'');
  v_policy_number text:=nullif(btrim(coalesce(p_policy_number,'')),'');
  v_summary text:=nullif(btrim(coalesce(p_coverage_summary,'')),'');
  v_phone text:=nullif(regexp_replace(coalesce(p_support_phone,''),'[ -]','','g'),'');
  v_url text:=nullif(btrim(coalesce(p_support_url,'')),'');
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then raise exception 'admin required' using errcode='42501'; end if;
  select u.id into v_admin_user from public.users u
    where u.auth_user_id=auth.uid() and u.role='admin' and u.status='active' limit 1;
  if not exists(select 1 from public.rider_profiles r join public.users u on u.id=r.user_id
    where r.id=p_rider_id and u.role='rider' and u.status<>'deleted') then raise exception 'rider unavailable'; end if;
  if v_type not in ('personal_accident','income_protection','vehicle','other') then raise exception 'invalid insurance coverage type'; end if;
  if v_provider is null or length(v_provider) not between 2 and 120 then raise exception 'insurance provider required'; end if;
  if v_product is not null and length(v_product)>160 then raise exception 'insurance product too long'; end if;
  if v_policy_number is not null and length(v_policy_number)>100 then raise exception 'insurance policy number too long'; end if;
  if p_starts_on is null or p_expires_on is null or p_expires_on<p_starts_on then raise exception 'invalid insurance coverage dates'; end if;
  if v_summary is not null and length(v_summary)>1000 then raise exception 'insurance coverage summary too long'; end if;
  if v_phone is not null and v_phone !~ '^[+]?[0-9]{8,15}$' then raise exception 'invalid insurance support phone'; end if;
  if v_url is not null and v_url !~ '^https://' then raise exception 'insurance support url must use https'; end if;
  if p_policy_id is null then
    insert into qg_private.rider_insurance_policies(
      rider_id,coverage_type,provider_name,product_name,policy_number,starts_on,expires_on,
      coverage_summary,support_phone,support_url,recorded_by
    ) values(
      p_rider_id,v_type,v_provider,v_product,v_policy_number,p_starts_on,p_expires_on,
      v_summary,v_phone,v_url,v_admin_user
    ) returning id into v_id;
  else
    update qg_private.rider_insurance_policies set
      coverage_type=v_type,provider_name=v_provider,product_name=v_product,policy_number=v_policy_number,
      starts_on=p_starts_on,expires_on=p_expires_on,coverage_summary=v_summary,support_phone=v_phone,
      support_url=v_url,recorded_by=v_admin_user,updated_at=now()
    where id=p_policy_id and rider_id=p_rider_id returning id into v_id;
    if v_id is null then raise exception 'insurance policy unavailable'; end if;
  end if;
  return jsonb_build_object('policy_id',v_id);
end $$;
revoke all on function qg_private.admin_save_rider_insurance(uuid,uuid,text,text,text,text,date,date,text,text,text) from public, anon;
grant execute on function qg_private.admin_save_rider_insurance(uuid,uuid,text,text,text,text,date,date,text,text,text) to authenticated;
create or replace function public.qg_admin_save_rider_insurance(
  p_policy_id uuid,p_rider_id uuid,p_coverage_type text,p_provider_name text,p_product_name text,
  p_policy_number text,p_starts_on date,p_expires_on date,p_coverage_summary text,p_support_phone text,p_support_url text
)
returns jsonb language sql security invoker set search_path=''
as $$ select qg_private.admin_save_rider_insurance(
  p_policy_id,p_rider_id,p_coverage_type,p_provider_name,p_product_name,p_policy_number,p_starts_on,
  p_expires_on,p_coverage_summary,p_support_phone,p_support_url
); $$;
revoke all on function public.qg_admin_save_rider_insurance(uuid,uuid,text,text,text,text,date,date,text,text,text) from public, anon;
grant execute on function public.qg_admin_save_rider_insurance(uuid,uuid,text,text,text,text,date,date,text,text,text) to authenticated, service_role;

create or replace function qg_private.admin_delete_rider_insurance(p_policy_id uuid)
returns void language plpgsql security definer set search_path=''
as $$
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then raise exception 'admin required' using errcode='42501'; end if;
  delete from qg_private.rider_insurance_policies where id=p_policy_id;
  if not found then raise exception 'insurance policy unavailable'; end if;
end $$;
revoke all on function qg_private.admin_delete_rider_insurance(uuid) from public, anon;
grant execute on function qg_private.admin_delete_rider_insurance(uuid) to authenticated;
create or replace function public.qg_admin_delete_rider_insurance(p_policy_id uuid)
returns void language sql security invoker set search_path=''
as $$ select qg_private.admin_delete_rider_insurance(p_policy_id); $$;
revoke all on function public.qg_admin_delete_rider_insurance(uuid) from public, anon;
grant execute on function public.qg_admin_delete_rider_insurance(uuid) to authenticated, service_role;
