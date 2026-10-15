-- Cancellation replacement lifecycle and fair shortfall fallback.
-- Reuses the SAME continuous dispatcher and funding obligation. No new queue.

alter table qg_private.rider_cancellation_funding
  add column if not exists replacement_covered_amount numeric not null default 0
    check(replacement_covered_amount>=0),
  add column if not exists replacement_finalized_at timestamptz;


-- The existing linker remains the only bridge from a claimed NEXT order to
-- cancellation funding. Never attach a replacement after fallback was finalized.
create or replace function qg_private.link_cancellation_replacement()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
begin
  if new.rider_id is null or new.next_after_order_id is null then return new; end if;

  update qg_private.rider_cancellation_funding f
  set replacement_order_id=new.id,
      updated_at=clock_timestamp()
  where f.order_id=new.next_after_order_id
    and f.rider_id=new.rider_id
    and f.status='reserved'
    and f.replacement_target_amount>0
    and f.replacement_finalized_at is null
    and f.replacement_order_id is null;

  return new;
end $$;

revoke all on function qg_private.link_cancellation_replacement()
from public,anon,authenticated;

create or replace function qg_private.finalize_cancellation_replacement()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  f qg_private.rider_cancellation_funding%rowtype;
  v_protected_status text;
  v_replacement_payout numeric;
  v_covered numeric;
  v_shortfall numeric;
  v_next_status text;
  v_rider_user uuid;
begin
  if old.status is not distinct from new.status
     or new.status not in ('completed','cancelled') then
    return new;
  end if;

  -- Replacement terminal state.
  for f in
    select x.*
    from qg_private.rider_cancellation_funding x
    where x.replacement_order_id=new.id
      and x.status='reserved'
      and x.replacement_target_amount>0
      and x.replacement_finalized_at is null
    for update
  loop
    if new.status='completed' then
      v_replacement_payout:=greatest(
        coalesce(new.rider_delivery_payout,0),
        coalesce(new.delivery_fee,0),
        0
      );
      v_covered:=least(f.replacement_target_amount,v_replacement_payout);
      v_shortfall:=greatest(round(f.replacement_target_amount-v_covered,2),0);
      v_next_status:=case when f.amount+v_shortfall>0 then 'reserved' else 'void' end;

      update qg_private.rider_cancellation_funding
      set amount=round(amount+v_shortfall,2),
          replacement_covered_amount=v_covered,
          replacement_finalized_at=clock_timestamp(),
          status=v_next_status,
          updated_at=clock_timestamp()
      where id=f.id;

      insert into public.audit_logs(
        user_id,action,entity_type,entity_id,description,metadata
      ) values(
        null,'rider_cancellation_replacement_complete','rider_cancellation_funding',f.id,
        'Replacement order completed; only uncovered Rider income remains QueueGo funded',
        jsonb_build_object(
          'replacement_order_id',new.id,
          'replacement_target',f.replacement_target_amount,
          'replacement_payout',v_replacement_payout,
          'covered',v_covered,
          'platform_shortfall',v_shortfall
        )
      );
    else
      select o.status into v_protected_status
      from public.orders o
      where o.id=f.order_id;

      if v_protected_status='completed' then
        -- The original route already ended, so another chained NEXT can no longer
        -- preserve the promised continuous route. QueueGo funds the uncovered target.
        update qg_private.rider_cancellation_funding
        set amount=round(amount+replacement_target_amount,2),
            replacement_covered_amount=0,
            replacement_finalized_at=clock_timestamp(),
            status='reserved',
            updated_at=clock_timestamp()
        where id=f.id;

        insert into public.audit_logs(
          user_id,action,entity_type,entity_id,description,metadata
        ) values(
          null,'rider_cancellation_replacement_failed_after_route','rider_cancellation_funding',f.id,
          'Replacement cancelled after protected route ended; QueueGo shortfall became due',
          jsonb_build_object(
            'replacement_order_id',new.id,
            'platform_shortfall',f.replacement_target_amount
          )
        );
      else
        -- Protected delivery is still live. Release only the replacement link;
        -- the SAME continuous dispatcher can fill the one NEXT slot again.
        update qg_private.rider_cancellation_funding
        set replacement_order_id=null,
            replacement_covered_amount=0,
            updated_at=clock_timestamp()
        where id=f.id;

        select rp.user_id into v_rider_user
        from public.rider_profiles rp
        where rp.id=f.rider_id;

        if v_rider_user is not null then
          insert into public.notifications(user_id,title,message,type,reference_id)
          values(
            v_rider_user,
            'กำลังหางานต่อใหม่',
            'งานที่ระบบนำมาเติมถูกยกเลิก ระบบเปิดช่องหา NEXT ใหม่ให้แล้ว',
            'order',
            f.order_id
          );
        end if;

        insert into public.audit_logs(
          user_id,action,entity_type,entity_id,description,metadata
        ) values(
          null,'rider_cancellation_replacement_reopen','rider_cancellation_funding',f.id,
          'Replacement cancelled while protected route is live; reopen SAME NEXT refill',
          jsonb_build_object('replacement_order_id',new.id,'protected_status',v_protected_status)
        );
      end if;
    end if;
  end loop;

  -- Protected order ended without any replacement being secured.
  if new.status='completed' then
    for f in
      select x.*
      from qg_private.rider_cancellation_funding x
      where x.order_id=new.id
        and x.status='reserved'
        and x.replacement_target_amount>0
        and x.replacement_finalized_at is null
        and x.replacement_order_id is null
      for update
    loop
      update qg_private.rider_cancellation_funding
      set amount=round(amount+replacement_target_amount,2),
          replacement_covered_amount=0,
          replacement_finalized_at=clock_timestamp(),
          status='reserved',
          updated_at=clock_timestamp()
      where id=f.id;

      insert into public.audit_logs(
        user_id,action,entity_type,entity_id,description,metadata
      ) values(
        null,'rider_cancellation_replacement_unfilled','rider_cancellation_funding',f.id,
        'Protected route completed without a replacement; QueueGo shortfall became due',
        jsonb_build_object('platform_shortfall',f.replacement_target_amount)
      );
    end loop;
  elsif new.status='cancelled' then
    -- If even the protected order is cancelled before completion there is no
    -- completed discounted delivery to fund from this obligation.
    update qg_private.rider_cancellation_funding
    set status='void',
        replacement_finalized_at=coalesce(replacement_finalized_at,clock_timestamp()),
        updated_at=clock_timestamp()
    where order_id=new.id
      and status='reserved'
      and replacement_finalized_at is null;
  end if;

  return new;
end $$;

revoke all on function qg_private.finalize_cancellation_replacement()
from public,anon,authenticated;

drop trigger if exists zz_orders_finalize_cancellation_replacement on public.orders;
create trigger zz_orders_finalize_cancellation_replacement
after update of status on public.orders
for each row
when (
  old.status is distinct from new.status
  and new.status in ('completed','cancelled')
)
execute function qg_private.finalize_cancellation_replacement();

-- Settled proof remains completely immutable, and settlement cannot occur while
-- replacement protection is still unresolved.
create or replace function qg_private.guard_cancellation_funding_settlement()
returns trigger
language plpgsql
set search_path=public,pg_temp
as $$
begin
  if old.status='settled' and (
    new.status is distinct from old.status
    or new.amount is distinct from old.amount
    or new.replacement_target_amount is distinct from old.replacement_target_amount
    or new.replacement_covered_amount is distinct from old.replacement_covered_amount
    or new.replacement_order_id is distinct from old.replacement_order_id
    or new.replacement_finalized_at is distinct from old.replacement_finalized_at
    or new.protected_route_payout is distinct from old.protected_route_payout
    or new.reason is distinct from old.reason
    or new.order_id is distinct from old.order_id
    or new.rider_id is distinct from old.rider_id
    or new.bundle_id is distinct from old.bundle_id
    or new.settled_at is distinct from old.settled_at
    or new.settled_by is distinct from old.settled_by
    or new.settlement_reference is distinct from old.settlement_reference
    or new.settlement_note is distinct from old.settlement_note
    or new.created_at is distinct from old.created_at
    or new.updated_at is distinct from old.updated_at
  ) then
    raise exception 'settled Rider funding is immutable';
  end if;

  if new.status='settled' and old.status is distinct from new.status then
    if current_setting('queuego.funding_settle',true) is distinct from 'rpc' then
      raise exception 'Rider funding settlement requires authoritative RPC';
    end if;
    if new.replacement_target_amount>0 and new.replacement_finalized_at is null then
      raise exception 'replacement protection is unresolved';
    end if;
    if new.amount<=0
       or new.settled_at is null
       or new.settled_by is null
       or length(trim(coalesce(new.settlement_reference,'')))<4 then
      raise exception 'settlement proof is incomplete';
    end if;
  end if;

  return new;
end $$;

revoke all on function qg_private.guard_cancellation_funding_settlement()
from public,anon,authenticated;

create or replace function public.qg_admin_rider_cancellation_funding_queue(
  p_status text default 'reserved'
) returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  v_status text:=lower(trim(coalesce(p_status,'reserved')));
  v_rows jsonb;
begin
  if not public.is_active_admin() then raise exception 'admin required'; end if;
  if v_status not in ('reserved','settled','all') then
    raise exception 'invalid funding status';
  end if;

  select coalesce(jsonb_agg(jsonb_build_object(
    'funding_id',f.id,
    'bundle_id',f.bundle_id,
    'order_id',f.order_id,
    'order_number',o.order_number,
    'rider_id',f.rider_id,
    'rider_name',coalesce(u.name,'Rider'),
    'amount',f.amount,
    'status',f.status,
    'replacement_target_amount',f.replacement_target_amount,
    'replacement_covered_amount',f.replacement_covered_amount,
    'replacement_finalized_at',f.replacement_finalized_at,
    'protected_route_payout',f.protected_route_payout,
    'replacement_order_id',f.replacement_order_id,
    'replacement_order_number',ro.order_number,
    'reason',f.reason,
    'created_at',f.created_at,
    'settled_at',f.settled_at,
    'settlement_reference',f.settlement_reference,
    'settlement_note',f.settlement_note
  ) order by
      case when f.status='reserved' then 0 else 1 end,
      f.created_at asc
  ),'[]'::jsonb)
  into v_rows
  from qg_private.rider_cancellation_funding f
  join public.orders o on o.id=f.order_id
  join public.rider_profiles rp on rp.id=f.rider_id
  join public.users u on u.id=rp.user_id
  left join public.orders ro on ro.id=f.replacement_order_id
  where f.status<>'void'
    and (v_status='all' or f.status=v_status);

  return v_rows;
end $$;

revoke all on function public.qg_admin_rider_cancellation_funding_queue(text)
from public,anon;
grant execute on function public.qg_admin_rider_cancellation_funding_queue(text)
to authenticated;
