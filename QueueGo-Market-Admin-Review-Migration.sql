-- QueueGo admin review flow for requested markets.
alter table public.market_requests
  add column if not exists created_market_id uuid references public.markets(id) on delete set null,
  add column if not exists review_note text;

create or replace function public.queuego_admin_review_market_request(
  p_request_id uuid,
  p_approve boolean,
  p_reason text default null,
  p_assignment_radius_km numeric default 2
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_admin uuid;
  v_req public.market_requests%rowtype;
  v_market uuid;
  v_shop_id uuid;
  v_radius numeric;
begin
  if not public.is_active_admin() then raise exception 'admin required'; end if;
  v_admin:=public.get_my_user_id();
  v_radius:=greatest(0.1,least(coalesce(p_assignment_radius_km,2),10));

  select * into v_req
  from public.market_requests
  where id=p_request_id
  for update;
  if not found then raise exception 'market request not found'; end if;
  if v_req.status<>'pending' then raise exception 'market request already reviewed'; end if;

  if coalesce(p_approve,false) then
    if v_req.latitude is null or v_req.longitude is null then
      raise exception 'request location required before approval';
    end if;

    select m.id into v_market
    from public.markets m
    where lower(trim(m.name))=lower(trim(v_req.requested_name))
      and lower(trim(coalesce(m.province,'')))=lower(trim(v_req.province))
      and lower(trim(coalesce(m.district,'')))=lower(trim(coalesce(v_req.district,'')))
    order by m.created_at
    limit 1;

    if v_market is null then
      insert into public.markets(
        name,address,latitude,longitude,active,province,district,subdistrict,
        source,verified,assignment_radius_km
      ) values(
        left(trim(v_req.requested_name),160),
        nullif(left(trim(coalesce(v_req.address,'')),500),''),
        v_req.latitude,v_req.longitude,true,
        left(trim(v_req.province),100),
        nullif(left(trim(coalesce(v_req.district,'')),100),''),
        nullif(left(trim(coalesce(v_req.subdistrict,'')),100),''),
        'Merchant market request approved by QueueGo Admin',
        false,
        v_radius
      ) returning id into v_market;
    end if;

    update public.market_requests
    set status='approved',
        created_market_id=v_market,
        reviewed_by=v_admin,
        reviewed_at=now(),
        review_note=nullif(left(trim(coalesce(p_reason,'')),500),''),
        updated_at=now()
    where id=p_request_id;

    select s.id into v_shop_id
    from public.shop_profiles s
    where s.user_id=v_req.requester_shop_user_id
    limit 1;

    if v_shop_id is not null then
      update public.shop_profiles
      set market_id=v_market,
          market_suggested_id=v_market,
          market_suggested_distance_km=public.queuego_market_distance_km(
            latitude,longitude,
            (select latitude from public.markets where id=v_market),
            (select longitude from public.markets where id=v_market)
          ),
          market_membership_status='pending',
          market_confirmed_at=coalesce(market_confirmed_at,v_req.created_at,now()),
          market_reviewed_by=null,
          market_reviewed_at=null,
          market_rejection_reason=null,
          updated_at=now()
      where id=v_shop_id
        and latitude is not null and longitude is not null;
    end if;

    return jsonb_build_object(
      'status','approved',
      'market_id',v_market,
      'shop_id',v_shop_id,
      'membership_status',case when v_shop_id is null then null else 'pending' end
    );
  end if;

  if length(trim(coalesce(p_reason,'')))<2 then
    raise exception 'rejection reason required';
  end if;

  update public.market_requests
  set status='rejected',
      reviewed_by=v_admin,
      reviewed_at=now(),
      review_note=left(trim(p_reason),500),
      updated_at=now()
  where id=p_request_id;

  return jsonb_build_object('status','rejected','market_id',null,'shop_id',null);
end $$;

-- Require a real shop/market image before Admin can approve market membership.
create or replace function public.queuego_admin_review_market_membership(
  p_shop_id uuid,
  p_approve boolean,
  p_reason text default null
) returns text
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_admin uuid;v_shop public.shop_profiles%rowtype;v_proof text;
begin
 if not public.is_active_admin() then raise exception 'admin required'; end if;
 v_admin:=public.get_my_user_id();
 select * into v_shop from public.shop_profiles where id=p_shop_id for update;
 if not found then raise exception 'shop not found'; end if;
 if v_shop.market_membership_status<>'pending' or v_shop.market_id is null then
   raise exception 'market membership is not pending';
 end if;

 if coalesce(p_approve,false) then
   v_proof:=nullif(trim(coalesce(
     v_shop.market_proof_path,
     v_shop.public_cover,
     v_shop.metadata->>'cover',
     v_shop.metadata->>'profileImage',
     v_shop.metadata->>'profile_image',
     ''
   )),'');
   if v_proof is null then
     raise exception 'shop front/stall image required before approval';
   end if;

   update public.shop_profiles
   set market_membership_status='approved',
       market_reviewed_by=v_admin,
       market_reviewed_at=now(),
       market_rejection_reason=null,
       updated_at=now()
   where id=p_shop_id;
   return 'approved';
 else
   if length(trim(coalesce(p_reason,'')))<2 then raise exception 'rejection reason required'; end if;
   update public.shop_profiles
   set market_membership_status='rejected',
       market_id=null,
       market_reviewed_by=v_admin,
       market_reviewed_at=now(),
       market_rejection_reason=left(trim(p_reason),500),
       updated_at=now()
   where id=p_shop_id;
   return 'rejected';
 end if;
end $$;

revoke all on function public.queuego_admin_review_market_request(uuid,boolean,text,numeric) from public,anon;
grant execute on function public.queuego_admin_review_market_request(uuid,boolean,text,numeric) to authenticated,service_role;
