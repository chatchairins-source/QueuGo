-- Reuse the Native dispatch preference shape; Web auto-accept uses the existing atomic claim.
create table if not exists qg_private.rider_dispatch_preferences (
 rider_id uuid primary key references public.rider_profiles(id),
 auto_accept boolean not null default false,
 stop_after_work boolean not null default false,
 ready_until timestamptz not null default '-infinity',
 updated_at timestamptz not null default now()
);
alter table qg_private.rider_dispatch_preferences add column if not exists services text[] not null default array['food','grocery','market'];
alter table qg_private.rider_dispatch_preferences add column if not exists bundle_enabled boolean not null default false;
alter table qg_private.rider_dispatch_preferences enable row level security;
revoke all on qg_private.rider_dispatch_preferences from public,anon,authenticated;

create or replace function public.qg_rider_work_preferences(p_preferences jsonb default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare rr uuid:=qg_private.current_rider_profile_id(); pref qg_private.rider_dispatch_preferences%rowtype;
 selected text[]; caps jsonb; ride_ok boolean; express_ok boolean;
begin
 perform 1 from public.rider_profiles where id=rr for update;
 ride_ok:=public.queuego_feature_enabled('ride',now()) and qg_private.ride_owner_authorized(rr);
 express_ok:=public.queuego_feature_enabled('express',now()) and to_regprocedure('qg_private.express_order_action(uuid,text)') is not null;
 caps:=jsonb_build_object('food',true,'grocery',true,'market',true,'ride',ride_ok,'express',express_ok,'bundle',public.queuego_feature_enabled('route_bundle',now()));
 if p_preferences is not null then
  if jsonb_typeof(p_preferences)<>'object' or jsonb_typeof(p_preferences->'auto_accept') is distinct from 'boolean'
   or jsonb_typeof(p_preferences->'bundle_enabled') is distinct from 'boolean' or jsonb_typeof(p_preferences->'services') is distinct from 'array'
   or exists(select 1 from jsonb_object_keys(p_preferences) k where k not in ('auto_accept','bundle_enabled','services')) then
   raise exception 'explicit work preferences required' using errcode='22023';
  end if;
  if exists(select 1 from jsonb_array_elements(p_preferences->'services') v where jsonb_typeof(v)<>'string') then raise exception 'invalid service' using errcode='22023'; end if;
  select array_agg(distinct value order by value) into selected from jsonb_array_elements_text(p_preferences->'services');
  if coalesce(cardinality(selected),0)=0 or not selected <@ array['food','grocery','market','ride','express'] then raise exception 'choose a supported service' using errcode='22023'; end if;
  if ('ride'=any(selected) and not ride_ok) or ('express'=any(selected) and not express_ok) then raise exception 'service approval required' using errcode='42501'; end if;
  insert into qg_private.rider_dispatch_preferences(rider_id,auto_accept,services,bundle_enabled)
  values(rr,(p_preferences->>'auto_accept')::boolean,selected,(p_preferences->>'bundle_enabled')::boolean)
  on conflict(rider_id) do update set auto_accept=excluded.auto_accept,services=excluded.services,bundle_enabled=excluded.bundle_enabled,updated_at=now();
 end if;
 select * into pref from qg_private.rider_dispatch_preferences where rider_id=rr;
 selected:=coalesce(pref.services,array['food','grocery','market']);
 -- Eligibility is evaluated on every read; revoked Ride access cannot stay enabled.
 if not ride_ok then selected:=array_remove(selected,'ride'); end if;
 if not express_ok then selected:=array_remove(selected,'express'); end if;
 return jsonb_build_object('auto_accept',coalesce(pref.auto_accept,false),'bundle_enabled',coalesce(pref.bundle_enabled,false),'services',to_jsonb(selected),'capabilities',caps);
end $$;
revoke all on function public.qg_rider_work_preferences(jsonb) from public,anon,service_role;
grant execute on function public.qg_rider_work_preferences(jsonb) to authenticated;

create or replace function qg_private.rider_work_allows(p_rider uuid,p_order uuid)
returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.orders o left join qg_private.rider_dispatch_preferences p on p.rider_id=p_rider
 where o.id=p_order and coalesce(nullif(o.fulfillment_vertical,''),'food')=any(coalesce(p.services,array['food','grocery','market','ride']))
 and (o.fulfillment_vertical is distinct from 'ride' or (public.queuego_feature_enabled('ride',now()) and qg_private.ride_owner_authorized(p_rider))));
$$;
revoke all on function qg_private.rider_work_allows(uuid,uuid) from public,anon,authenticated;

-- All assignment entry points, including direct claims and bundle claims, enforce choices.
create or replace function qg_private.guard_rider_work_choice()
returns trigger language plpgsql security definer set search_path='' as $$
begin
 if new.rider_id is not null and new.rider_id is distinct from old.rider_id then
  perform 1 from public.rider_profiles where id=new.rider_id for update;
  if not qg_private.rider_work_allows(new.rider_id,new.id) then raise exception 'rider has not selected this service' using errcode='42501'; end if;
  if new.route_bundle_id is not null and not exists(select 1 from qg_private.rider_dispatch_preferences where rider_id=new.rider_id and bundle_enabled) then raise exception 'rider has disabled bundled jobs' using errcode='42501'; end if;
 end if;
 return new;
end $$;
revoke all on function qg_private.guard_rider_work_choice() from public,anon,authenticated;
create trigger orders_rider_work_choice before update of rider_id on public.orders for each row execute function qg_private.guard_rider_work_choice();

-- Preserve the installed dispatch/vehicle/credit/offer policy; add only the selection predicate.
do $$
declare code text; needle text; replacement text; fn text;
begin
 for fn,needle,replacement in select * from (values
 ('qg_dispatch_rider_offers','and r.latitude is not null','and qg_private.rider_work_allows(r.id,j.id)'||E'\n      and r.latitude is not null'),
 ('qg_get_my_rider_offer','where f.rider_id=rr and','where f.rider_id=rr and qg_private.rider_work_allows(rr,o.id) and'),
 ('get_rider_delivery_pool','where f.rider_id=rr and','where f.rider_id=rr and qg_private.rider_work_allows(rr,o.id) and'),
 ('queuego_route_bundle_candidates','if v_rider is null then return; end if;','if v_rider is null then return; end if;'||E'\n  if not exists(select 1 from qg_private.rider_dispatch_preferences where rider_id=v_rider and bundle_enabled) then return; end if;'),
 ('queuego_route_bundle_candidates','and o.id<>v_primary','and o.id<>v_primary and qg_private.rider_work_allows(v_rider,o.id)')
 ) as patches(name,old_text,new_text) loop
  select pg_get_functiondef(to_regprocedure('public.'||fn||'()')) into code;
  if code is null or (length(code)-length(replace(code,needle,'')))/length(needle)<>1 then raise exception 'installed dispatch source changed: %',fn; end if;
  execute replace(code,needle,replacement);
 end loop;
end $$;
