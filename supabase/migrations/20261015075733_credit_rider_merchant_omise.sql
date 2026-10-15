-- Staged only. One credit subsystem; existing GP/Orders/payment provider are reused.
-- Monetary values are integer satang. No live flag or real balance is seeded.
create table qg_private.credit_accounts(
 user_id uuid not null references public.users(id),kind text not null check(kind in ('rider','merchant')),
 cash_minor bigint not null default 0 check(cash_minor>=0),promo_minor bigint not null default 0 check(promo_minor>=0),
 held_cash_minor bigint not null default 0 check(held_cash_minor>=0 and held_cash_minor<=cash_minor),
 held_promo_minor bigint not null default 0 check(held_promo_minor>=0 and held_promo_minor<=promo_minor),
 primary key(user_id,kind),check(kind='merchant' or promo_minor=0)
);
create table qg_private.credit_topups(
 id uuid primary key,user_id uuid not null,kind text not null,amount_minor bigint not null check(amount_minor between 2000 and 10000000),
 charge_id text unique,qr_url text,expires_at timestamptz,status text not null default 'created' check(status in ('created','pending','successful','failed','expired')),
 created_at timestamptz not null default now(),foreign key(user_id,kind) references qg_private.credit_accounts(user_id,kind)
);
create index credit_topups_owner_idx on qg_private.credit_topups(user_id,kind,created_at desc);
create table qg_private.credit_holds(
 order_id uuid not null references public.orders(id),kind text not null,user_id uuid not null,
 cash_minor bigint not null check(cash_minor>=0),promo_minor bigint not null check(promo_minor>=0),
 state text not null check(state in ('held','captured','released')),generation integer not null default 1,primary key(order_id,kind),
 foreign key(user_id,kind) references qg_private.credit_accounts(user_id,kind)
);
create index credit_holds_owner_idx on qg_private.credit_holds(user_id,kind);
create table qg_private.credit_entries(
 id uuid primary key default gen_random_uuid(),user_id uuid not null,kind text not null,event_key text not null unique,
 event_type text not null check(event_type in ('topup','promo','reserve','release','capture','reversal')),
 cash_delta bigint not null default 0,promo_delta bigint not null default 0,held_cash_delta bigint not null default 0,held_promo_delta bigint not null default 0,
 order_id uuid references public.orders(id),topup_id uuid references qg_private.credit_topups(id),reverses_id uuid unique references qg_private.credit_entries(id),
 actor_id uuid references public.users(id),reason text,created_at timestamptz not null default now(),
 foreign key(user_id,kind) references qg_private.credit_accounts(user_id,kind)
);
create index credit_entries_owner_idx on qg_private.credit_entries(user_id,kind,created_at desc);
alter table qg_private.credit_accounts enable row level security;
alter table qg_private.credit_topups enable row level security;
alter table qg_private.credit_holds enable row level security;
alter table qg_private.credit_entries enable row level security;
revoke all on qg_private.credit_accounts,qg_private.credit_topups,qg_private.credit_holds,qg_private.credit_entries from public,anon,authenticated;

create function qg_private.credit_immutable() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin raise exception 'credit entries are immutable; use a reversal'; end $$;
create trigger credit_entries_immutable before update or delete on qg_private.credit_entries for each row execute function qg_private.credit_immutable();
revoke all on function qg_private.credit_immutable() from public,anon,authenticated;

create function qg_private.credit_owner(p_kind text) returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare u uuid;
begin
 perform qg_private.assert_live_session();
 select id into u from public.users where auth_user_id=auth.uid() and status='active' and role=case p_kind when 'merchant' then 'shop' when 'rider' then 'rider' end;
 if u is null then raise exception 'active credit owner required' using errcode='42501'; end if;
 return u;
end $$;
revoke all on function qg_private.credit_owner(text) from public,anon,authenticated;

create function public.queuego_credit_summary(p_kind text,p_topup uuid default null) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare u uuid;a qg_private.credit_accounts%rowtype;
begin
 u:=qg_private.credit_owner(p_kind);
 select * into a from qg_private.credit_accounts where user_id=u and kind=p_kind;
 return jsonb_build_object('enabled',public.queuego_feature_enabled('credit_'||p_kind,now()),'kind',p_kind,
 'cash_minor',coalesce(a.cash_minor,0),'promo_minor',coalesce(a.promo_minor,0),'held_minor',coalesce(a.held_cash_minor+a.held_promo_minor,0),
 'available_minor',coalesce(a.cash_minor+a.promo_minor-a.held_cash_minor-a.held_promo_minor,0),
 'entries',coalesce((select jsonb_agg(to_jsonb(e) - 'user_id' - 'actor_id' order by e.created_at desc) from (select * from qg_private.credit_entries where user_id=u and kind=p_kind order by created_at desc,id desc limit 50)e),'[]'::jsonb),
 'topups',coalesce((select jsonb_agg(to_jsonb(t) - 'user_id' order by t.created_at desc) from (select * from qg_private.credit_topups where user_id=u and kind=p_kind order by (id=p_topup) desc,created_at desc limit 10)t),'[]'::jsonb));
end $$;
revoke all on function public.queuego_credit_summary(text,uuid) from public,anon;
grant execute on function public.queuego_credit_summary(text,uuid) to authenticated;

create function public.queuego_credit_begin_topup(p_id uuid,p_kind text,p_amount_minor bigint) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare u uuid;t qg_private.credit_topups%rowtype;
begin
 u:=qg_private.credit_owner(p_kind);
 if not public.queuego_feature_enabled('credit_'||p_kind,now()) then raise exception 'credit is not enabled'; end if;
 if p_id is null or p_amount_minor is null or p_amount_minor not between 2000 and 10000000 then raise exception 'invalid topup amount'; end if;
 insert into qg_private.credit_accounts(user_id,kind) values(u,p_kind) on conflict do nothing;
 perform 1 from qg_private.credit_accounts where user_id=u and kind=p_kind for update;
 select * into t from qg_private.credit_topups where id=p_id;
 if found then
  if t.user_id<>u or t.kind<>p_kind or t.amount_minor<>p_amount_minor then raise exception 'topup request mismatch'; end if;
  return to_jsonb(t)-'user_id';
 end if;
 if (select count(*) from qg_private.credit_topups where user_id=u and created_at>now()-interval '1 hour')>=10 then raise exception 'topup rate limit'; end if;
 insert into qg_private.credit_topups(id,user_id,kind,amount_minor) values(p_id,u,p_kind,p_amount_minor) returning * into t;
 return to_jsonb(t)-'user_id';
end $$;
revoke all on function public.queuego_credit_begin_topup(uuid,text,bigint) from public,anon;
grant execute on function public.queuego_credit_begin_topup(uuid,text,bigint) to authenticated;

-- Service-only: call ONLY with a charge independently retrieved from the fixed Omise API.
-- Test charges cannot credit Production, even when a test key was accidentally configured.
create function public.queuego_credit_apply_charge(p_topup uuid,p_charge text,p_amount bigint,p_currency text,p_live boolean,p_status text,p_paid boolean,p_qr text,p_expires timestamptz)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare t qg_private.credit_topups%rowtype;
begin
 select * into t from qg_private.credit_topups where id=p_topup for update;
 if not found then raise exception 'unknown credit topup'; end if;
 if p_live is distinct from true or p_currency is distinct from 'thb' or p_amount is distinct from t.amount_minor
 or p_charge is null or p_charge !~ '^chrg_[A-Za-z0-9]+$' or p_status is null or p_status not in ('pending','successful','failed','expired') then raise exception 'charge verification mismatch'; end if;
 if t.charge_id is not null and t.charge_id<>p_charge then raise exception 'topup already bound to another charge'; end if;
 if p_status='successful' and p_paid is distinct from true then raise exception 'unpaid charge'; end if;
 if t.status='successful' then return jsonb_build_object('status','successful','id',t.id); end if;
 if p_qr is not null and p_qr !~ '^https://api[.]omise[.]co/charges/' then raise exception 'untrusted QR URL'; end if;
 update qg_private.credit_topups set charge_id=p_charge,status=p_status,qr_url=coalesce(p_qr,qr_url),expires_at=coalesce(p_expires,expires_at) where id=t.id;
 if p_status='successful' then
  perform 1 from qg_private.credit_accounts where user_id=t.user_id and kind=t.kind for update;
  insert into qg_private.credit_entries(user_id,kind,event_key,event_type,cash_delta,topup_id)
  values(t.user_id,t.kind,'topup:'||t.id,'topup',t.amount_minor,t.id);
  update qg_private.credit_accounts set cash_minor=cash_minor+t.amount_minor where user_id=t.user_id and kind=t.kind;
  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata) values(t.user_id,'credit_topup','credit',t.id,'Verified Omise credit topup',jsonb_build_object('charge_id',p_charge,'amount_minor',t.amount_minor,'kind',t.kind));
 end if;
 return jsonb_build_object('id',t.id,'status',p_status);
end $$;
revoke all on function public.queuego_credit_apply_charge(uuid,text,bigint,text,boolean,text,boolean,text,timestamptz) from public,anon,authenticated;
grant execute on function public.queuego_credit_apply_charge(uuid,text,bigint,text,boolean,text,boolean,text,timestamptz) to service_role;

-- One per-order hold; account locking serializes all spending across Orders.
create function qg_private.credit_hold(p_user uuid,p_kind text,p_order uuid,p_minor bigint) returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare a qg_private.credit_accounts%rowtype;h qg_private.credit_holds%rowtype;pm bigint;cm bigint;g integer:=1;
begin
 if p_minor is null or p_minor<0 then raise exception 'invalid credit fee'; end if;
 insert into qg_private.credit_accounts(user_id,kind) values(p_user,p_kind) on conflict do nothing;
 select * into a from qg_private.credit_accounts where user_id=p_user and kind=p_kind for update;
 select * into h from qg_private.credit_holds where order_id=p_order and kind=p_kind for update;
 if found then
  if h.state='held' and h.user_id=p_user and h.cash_minor+h.promo_minor=p_minor then return; end if;
  if h.state<>'released' then raise exception 'credit fee or assignment changed; release before replacing'; end if;
  g:=h.generation+1;
 end if;
 pm:=case when p_kind='merchant' then least(p_minor,a.promo_minor-a.held_promo_minor) else 0 end;cm:=p_minor-pm;
 if a.cash_minor-a.held_cash_minor<cm then raise exception 'insufficient credit'; end if;
 insert into qg_private.credit_holds values(p_order,p_kind,p_user,cm,pm,'held',g) on conflict(order_id,kind) do update set user_id=excluded.user_id,cash_minor=excluded.cash_minor,promo_minor=excluded.promo_minor,state='held',generation=excluded.generation;
 update qg_private.credit_accounts set held_cash_minor=held_cash_minor+cm,held_promo_minor=held_promo_minor+pm where user_id=p_user and kind=p_kind;
 insert into qg_private.credit_entries(user_id,kind,event_key,event_type,held_cash_delta,held_promo_delta,order_id) values(p_user,p_kind,'reserve:'||p_kind||':'||p_order||':'||g,'reserve',cm,pm,p_order);
end $$;
revoke all on function qg_private.credit_hold(uuid,text,uuid,bigint) from public,anon,authenticated;

create function qg_private.credit_finish(p_order uuid,p_kind text,p_capture boolean) returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare h qg_private.credit_holds%rowtype;
begin
 select * into h from qg_private.credit_holds where order_id=p_order and kind=p_kind;
 if not found then return; end if;
 perform 1 from qg_private.credit_accounts where user_id=h.user_id and kind=h.kind for update;
 select * into h from qg_private.credit_holds where order_id=p_order and kind=p_kind for update;
 if h.state<>'held' then return; end if;
 update qg_private.credit_accounts set held_cash_minor=held_cash_minor-h.cash_minor,held_promo_minor=held_promo_minor-h.promo_minor,
 cash_minor=cash_minor-case when p_capture then h.cash_minor else 0 end,promo_minor=promo_minor-case when p_capture then h.promo_minor else 0 end where user_id=h.user_id and kind=h.kind;
 update qg_private.credit_holds set state=case when p_capture then 'captured' else 'released' end where order_id=p_order and kind=p_kind;
 insert into qg_private.credit_entries(user_id,kind,event_key,event_type,cash_delta,promo_delta,held_cash_delta,held_promo_delta,order_id)
 values(h.user_id,h.kind,(case when p_capture then 'capture:' else 'release:' end)||h.kind||':'||p_order||':'||h.generation,case when p_capture then 'capture' else 'release' end,
 case when p_capture then -h.cash_minor else 0 end,case when p_capture then -h.promo_minor else 0 end,-h.cash_minor,-h.promo_minor,p_order);
end $$;
revoke all on function qg_private.credit_finish(uuid,text,boolean) from public,anon,authenticated;

create or replace function qg_private.rider_credit_order_authorized(p_rider uuid,p_order uuid,p_fee numeric) returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select p_fee is not null and p_fee>=0 and public.queuego_feature_enabled('credit_rider',now()) and exists(
 select 1 from public.rider_profiles r join public.users u on u.id=r.user_id join qg_private.credit_accounts a on a.user_id=u.id and a.kind='rider'
 where r.id=p_rider and u.status='active' and r.status='active' and (a.cash_minor-a.held_cash_minor>=round(p_fee*100)::bigint or exists(
 select 1 from qg_private.credit_holds h where h.order_id=p_order and h.kind='rider' and h.user_id=u.id and h.state='held' and h.cash_minor=round(p_fee*100)::bigint)))
$$;
revoke all on function qg_private.rider_credit_order_authorized(uuid,uuid,numeric) from public,anon,authenticated;

alter table public.orders add column credit_gp_required boolean not null default false;
create function qg_private.credit_order_event() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare u uuid;fee bigint;h qg_private.credit_holds%rowtype;
begin
 if tg_op='INSERT' then
  new.credit_gp_required:=new.shop_id is not null and new.order_type in ('shopping','DELIVERY') and public.queuego_feature_enabled('credit_merchant',now());
  return new;
 end if;
 if new.credit_gp_required is distinct from old.credit_gp_required then raise exception 'credit collection mode is immutable'; end if;
 if new.credit_gp_required and new.status not in ('cancelled','no_rider_available') and (new.status in ('preparing','ready','picked_up') or new.merchant_accepted_at is not null) then
  select user_id into u from public.shop_profiles where id=new.shop_id;
  fee:=round(coalesce(new.gp_amount,0)*100)::bigint;
  select * into h from qg_private.credit_holds where order_id=new.id and kind='merchant';
  if not found then perform qg_private.credit_hold(u,'merchant',new.id,fee);
  elsif h.state='held' and h.cash_minor+h.promo_minor<>fee then
   perform qg_private.credit_finish(new.id,'merchant',false);perform qg_private.credit_hold(u,'merchant',new.id,fee);
  elsif h.state='captured' and h.cash_minor+h.promo_minor<>fee then raise exception 'captured GP amount is immutable';
  elsif h.state='released' then perform qg_private.credit_hold(u,'merchant',new.id,fee);
  end if;
  if new.status='picked_up' then perform qg_private.credit_finish(new.id,'merchant',true); end if;
 end if;
 if new.fulfillment_vertical='ride' and coalesce(new.ride_platform_fee,0)>0 then
  if new.status='rider_assigned' and (old.rider_id is distinct from new.rider_id or old.status is distinct from new.status) then
   select user_id into u from public.rider_profiles where id=new.rider_id;
   -- A requeued assignment gets a new hold generation; all entries remain immutable.
   perform qg_private.credit_hold(u,'rider',new.id,round(new.ride_platform_fee*100)::bigint);
  elsif new.status='completed' and old.status<>'completed' then
   select user_id into u from public.rider_profiles where id=new.rider_id;
   if not exists(select 1 from qg_private.credit_holds where order_id=new.id and kind='rider' and user_id=u and state='held' and cash_minor=round(new.ride_platform_fee*100)::bigint) then raise exception 'ride credit reservation required'; end if;
   perform qg_private.credit_finish(new.id,'rider',true);
  end if;
 end if;
 if new.status in ('cancelled','no_rider_available') or (new.status='searching_rider' and old.rider_id is not null and new.rider_id is null) then
  perform qg_private.credit_finish(new.id,'rider',false);perform qg_private.credit_finish(new.id,'merchant',false);
 end if;
 return new;
end $$;
revoke all on function qg_private.credit_order_event() from public,anon,authenticated;
create trigger zzz_credit_order_event before insert or update on public.orders for each row execute function qg_private.credit_order_event();

-- Promotional credit is issued once to a genuinely NEW store after activation, never on login or topup.
create function qg_private.credit_new_shop() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if public.queuego_feature_enabled('credit_merchant_promo',now()) and public.queuego_feature_enabled('credit_merchant',now()) then
  insert into qg_private.credit_accounts(user_id,kind) values(new.user_id,'merchant') on conflict do nothing;
  perform 1 from qg_private.credit_accounts where user_id=new.user_id and kind='merchant' for update;
  insert into qg_private.credit_entries(user_id,kind,event_key,event_type,promo_delta,reason) values(new.user_id,'merchant','new-shop:'||new.user_id,'promo',100000,'New store GP trial') on conflict(event_key) do nothing;
  if found then update qg_private.credit_accounts set promo_minor=promo_minor+100000 where user_id=new.user_id and kind='merchant'; end if;
 end if;
 return new;
end $$;
revoke all on function qg_private.credit_new_shop() from public,anon,authenticated;
create trigger credit_new_shop after insert on public.shop_profiles for each row execute function qg_private.credit_new_shop();

create function public.queuego_admin_reverse_credit(p_entry uuid,p_reason text) returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare a uuid;e qg_private.credit_entries%rowtype;r uuid;
begin
 perform qg_private.assert_live_session();select id into a from public.users where auth_user_id=auth.uid() and role='admin' and status='active';
 if a is null then raise exception 'active admin required' using errcode='42501'; end if;
 if length(trim(coalesce(p_reason,''))) not between 5 and 500 then raise exception 'reversal reason required'; end if;
 select * into e from qg_private.credit_entries where id=p_entry and event_type='capture';
 if not found then raise exception 'captured credit fee required'; end if;
 perform 1 from qg_private.credit_accounts where user_id=e.user_id and kind=e.kind for update;
 select id into r from qg_private.credit_entries where reverses_id=e.id;if found then return r;end if;
 insert into qg_private.credit_entries(user_id,kind,event_key,event_type,cash_delta,promo_delta,order_id,reverses_id,actor_id,reason)
 values(e.user_id,e.kind,'reverse:'||e.id,'reversal',-e.cash_delta,-e.promo_delta,e.order_id,e.id,a,trim(p_reason)) returning id into r;
 update qg_private.credit_accounts set cash_minor=cash_minor-e.cash_delta,promo_minor=promo_minor-e.promo_delta where user_id=e.user_id and kind=e.kind;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata) values(a,'credit_reversal','credit',r,trim(p_reason),jsonb_build_object('entry_id',e.id));
 return r;
end $$;
revoke all on function public.queuego_admin_reverse_credit(uuid,text) from public,anon;
grant execute on function public.queuego_admin_reverse_credit(uuid,text) to authenticated;

create function public.queuego_admin_credit_accounts() returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
begin
 perform qg_private.assert_live_session();
 if not exists(select 1 from public.users where auth_user_id=auth.uid() and role='admin' and status='active') then raise exception 'active admin required' using errcode='42501'; end if;
 return coalesce((select jsonb_agg(to_jsonb(a)) from (select c.*,u.name,
 (select coalesce(jsonb_agg(to_jsonb(e) order by e.created_at desc),'[]'::jsonb) from (select id,event_type,cash_delta,promo_delta,order_id,created_at from qg_private.credit_entries where user_id=c.user_id and kind=c.kind order by created_at desc limit 20)e) entries
 from qg_private.credit_accounts c join public.users u on u.id=c.user_id order by u.id,c.kind limit 200)a),'[]'::jsonb);
end $$;
revoke all on function public.queuego_admin_credit_accounts() from public,anon;
grant execute on function public.queuego_admin_credit_accounts() to authenticated;

-- Keep the existing GP slip workflow for pre-activation Orders only. Never collect twice.
-- Abort deployment if the known existing routine has drifted; inspect its successor first.
do $$
declare f text;
begin
 select pg_get_functiondef(to_regprocedure('public.report_shop_gp(date,text)')) into f;
 if f is null then raise exception 'existing report_shop_gp is required'; end if;
 if position('where o.shop_id=v_shop' in f)=0 then raise exception 'GP routine drift: review before deployment'; end if;
 execute replace(f,'where o.shop_id=v_shop','where not o.credit_gp_required and o.shop_id=v_shop');
end $$;

do $$
declare f text;target text:='sum(coalesce(o.gp_amount,round(o.subtotal*coalesce(o.gp_rate,10)/100,2))) gp';
begin
 select pg_get_functiondef(to_regprocedure('public.merchant_revenue_days(date,date)')) into f;
 if f is null or position(target in f)=0 then raise exception 'merchant revenue routine drift: review before deployment'; end if;
 execute replace(f,target,'sum(coalesce(o.gp_amount,round(o.subtotal*coalesce(o.gp_rate,10)/100,2))) filter(where not o.credit_gp_required) gp');
end $$;

-- Extend the existing rule validator instead of introducing a separate configuration system.
do $migration$
declare f text;
begin
 select pg_get_functiondef(to_regprocedure('public.queuego_platform_rule_guard()')) into f;
 if f is null or position($rule$'feature.ride'$rule$ in f)=0 then raise exception 'platform rule validator drift'; end if;
 execute replace(f,$rule$'feature.ride'$rule$,$rule$'feature.ride','feature.credit_rider','feature.credit_merchant','feature.credit_merchant_promo'$rule$);
end $migration$;
