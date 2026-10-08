create schema if not exists queuego_private;
revoke all on schema queuego_private from public, anon, authenticated;

create table if not exists queuego_private.store_lifecycle_pre_reconcile_20261008_1034 as
select sp.*, now() as backed_up_at
from public.shop_profiles sp;
revoke all on table queuego_private.store_lifecycle_pre_reconcile_20261008_1034 from public, anon, authenticated;

create temporary table qg_incomplete_shop_ids on commit drop as
select sp.id
from public.shop_profiles sp
join public.users u on u.id=sp.user_id
where sp.archived_at is null
  and sp.status='active'
  and sp.onboarding_status='approved'
  and not (
    (
      nullif(trim(coalesce(sp.shop_name,'')),'') is not null
      and nullif(trim(coalesce(u.name,'')),'') is not null
      and nullif(trim(coalesce(sp.phone,u.phone,'')),'') is not null
      and nullif(trim(coalesce(sp.public_category,sp.metadata->>'category','')),'') is not null
      and nullif(trim(coalesce(sp.address,'')),'') is not null
      and (
        (
          nullif(trim(coalesce(sp.public_open_time,sp.metadata->>'openTime','')),'') is not null
          and nullif(trim(coalesce(sp.public_close_time,sp.metadata->>'closeTime','')),'') is not null
        )
        or exists (
          select 1 from public.shop_business_hours h
          where h.shop_id=sp.id
            and h.is_closed=false
            and h.opens_at is not null
            and h.closes_at is not null
        )
      )
    )
    and (
      coalesce(
        nullif(trim(sp.public_logo),''),
        nullif(trim(sp.metadata->>'logo'),''),
        nullif(trim(sp.metadata->>'profileImage'),''),
        nullif(trim(sp.metadata->>'profile_image'),''),
        nullif(trim(sp.metadata->>'avatar'),'')
      ) ~ '^https://.+/storage/v1/object/'
      and lower(coalesce(
        nullif(trim(sp.public_logo),''),
        nullif(trim(sp.metadata->>'logo'),''),
        nullif(trim(sp.metadata->>'profileImage'),''),
        nullif(trim(sp.metadata->>'profile_image'),''),
        nullif(trim(sp.metadata->>'avatar'),'')
      )) not like '%placeholder%'
      and lower(coalesce(
        nullif(trim(sp.public_logo),''),
        nullif(trim(sp.metadata->>'logo'),''),
        nullif(trim(sp.metadata->>'profileImage'),''),
        nullif(trim(sp.metadata->>'profile_image'),''),
        nullif(trim(sp.metadata->>'avatar'),'')
      )) not like '%default-shop%'
    )
    and (
      coalesce(
        nullif(trim(sp.public_cover),''),
        nullif(trim(sp.metadata->>'cover'),''),
        nullif(trim(sp.metadata->>'coverImage'),'')
      ) ~ '^https://.+/storage/v1/object/'
      and lower(coalesce(
        nullif(trim(sp.public_cover),''),
        nullif(trim(sp.metadata->>'cover'),''),
        nullif(trim(sp.metadata->>'coverImage'),'')
      )) not like '%placeholder%'
      and lower(coalesce(
        nullif(trim(sp.public_cover),''),
        nullif(trim(sp.metadata->>'cover'),''),
        nullif(trim(sp.metadata->>'coverImage'),'')
      )) not like '%default-shop%'
    )
    and sp.latitude between 5 and 21
    and sp.longitude between 97 and 106
    and (
      case
        when lower(trim(coalesce(sp.public_category,sp.metadata->>'category','')))='laundry'
          then exists (
            select 1
            from public.laundry_services ls
            join public.laundry_hubs lh on lh.id=ls.hub_id
            where lh.shop_id=sp.id
              and lh.active=true
              and ls.active=true
              and nullif(trim(coalesce(ls.name,'')),'') is not null
              and coalesce(ls.price,0)>0
          )
        else exists (
          select 1
          from public.products p
          where p.shop_id=sp.id
            and p.available=true
            and nullif(trim(coalesce(p.name,'')),'') is not null
            and coalesce(p.price,0)>0
        )
      end
    )
  );

do $$
begin
  if exists (
    select 1
    from public.orders o
    join qg_incomplete_shop_ids i on i.id=o.shop_id
    where o.status not in ('completed','cancelled','no_rider_available')
  ) then
    raise exception 'RECONCILE_BLOCKED_ACTIVE_ORDERS';
  end if;
end
$$;

select set_config('queuego.store_review_context','on',true);

update public.shop_profiles sp
set status='pending',
    onboarding_status='needs_changes',
    submitted_for_review_at=null,
    approved_at=null,
    delivery_enabled=false,
    metadata=coalesce(sp.metadata,'{}'::jsonb)
      || jsonb_build_object(
        'approval_note','กรุณากรอกข้อมูลร้าน รูปหน้าร้าน รูปหน้าปก ตำแหน่ง เวลาเปิด-ปิด และรายการพร้อมขายให้ครบ แล้วส่งตรวจอีกครั้ง',
        'readiness_reconciled_at',now()
      ),
    updated_at=now()
where sp.id in (select id from qg_incomplete_shop_ids);

update public.users u
set status='pending',updated_at=now()
where u.role='shop'
  and exists (
    select 1 from public.shop_profiles sp
    join qg_incomplete_shop_ids i on i.id=sp.id
    where sp.user_id=u.id
  );

insert into public.shop_open_states(shop_id,is_open,resume_at,updated_at)
select i.id,false,null,now()
from qg_incomplete_shop_ids i
on conflict(shop_id) do update
set is_open=false,resume_at=null,updated_at=excluded.updated_at;

insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
select null,'shop_readiness_reconcile','shop_profiles',i.id,
       'legacy approved shop returned to readiness review',
       jsonb_build_object('reason','incomplete readiness after lifecycle backfill','reconciled_at',now())
from qg_incomplete_shop_ids i;
