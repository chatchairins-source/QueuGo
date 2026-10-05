-- Edit existing master locations with Admin authorization and the existing audit trail.
create or replace function public.qg_admin_update_location(
 p_entity_type text,p_entity_id uuid,p_lat double precision,p_lng double precision,
 p_reason text,p_expected_lat double precision,p_expected_lng double precision
) returns jsonb language plpgsql security definer set search_path = public,pg_temp as $$
declare v_admin uuid;v_lat double precision;v_lng double precision;v_before jsonb;v_after jsonb;
begin
 if auth.uid() is null or public.is_active_admin() is not true then raise exception 'active admin required';end if;
 v_admin:=public.get_my_user_id();
 if p_entity_type not in ('market','shop') or p_entity_type is null then raise exception 'invalid location type';end if;
 if p_lat is null or p_lng is null or not(p_lat between -90 and 90) or not(p_lng between -180 and 180) or (p_lat=0 and p_lng=0) then raise exception 'invalid coordinates';end if;
 if length(trim(coalesce(p_reason,'')))<2 or length(p_reason)>500 then raise exception 'reason must be 2-500 characters';end if;
 if p_entity_type='market' then
  select latitude,longitude into v_lat,v_lng from public.markets where id=p_entity_id for update;
 else
  select latitude,longitude into v_lat,v_lng from public.shop_profiles where id=p_entity_id for update;
 end if;
 if not found then raise exception 'location not found';end if;
 if v_lat is not distinct from p_lat and v_lng is not distinct from p_lng then
  return jsonb_build_object('id',p_entity_id,'latitude',v_lat,'longitude',v_lng,'changed',false);
 end if;
 if v_lat is distinct from p_expected_lat or v_lng is distinct from p_expected_lng then raise exception 'location changed; reopen and review latest coordinates';end if;
 v_before:=jsonb_build_object('latitude',v_lat,'longitude',v_lng);
 if p_entity_type='market' then
  update public.markets set latitude=p_lat,longitude=p_lng,updated_at=now() where id=p_entity_id;
  update public.shop_profiles set market_suggested_distance_km=public.queuego_market_distance_km(latitude,longitude,p_lat,p_lng),updated_at=now()
   where market_suggested_id=p_entity_id and latitude is not null and longitude is not null;
 else
  update public.shop_profiles set latitude=p_lat,longitude=p_lng,updated_at=now(),
   metadata=coalesce(metadata,'{}'::jsonb)||jsonb_build_object('lat',p_lat,'lng',p_lng)
   where id=p_entity_id;
  update public.shop_profiles s set market_suggested_distance_km=public.queuego_market_distance_km(s.latitude,s.longitude,m.latitude,m.longitude)
   from public.markets m where s.id=p_entity_id and m.id=s.market_suggested_id;
 end if;
 v_after:=jsonb_build_object('latitude',p_lat,'longitude',p_lng);
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(v_admin,'admin_location_update',case when p_entity_type='market' then 'markets' else 'shop_profiles' end,p_entity_id,trim(p_reason),
  jsonb_build_object('before',v_before,'after',v_after,'actorId',v_admin,'actorRole','admin','reason',trim(p_reason)));
 return jsonb_build_object('id',p_entity_id,'latitude',p_lat,'longitude',p_lng,'changed',true);
end $$;
revoke all on function public.qg_admin_update_location(text,uuid,double precision,double precision,text,double precision,double precision) from public,anon;
grant execute on function public.qg_admin_update_location(text,uuid,double precision,double precision,text,double precision,double precision) to authenticated;
notify pgrst,'reload schema';
