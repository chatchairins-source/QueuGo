const fs=require('node:fs');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
module.exports=()=>require('./rider-dispatch-schema.cjs')()+fs.readFileSync('supabase/migrations/20261014234500_rider_continuous_dispatch_phase1.sql','utf8')+`
 create function qg_private.assert_live_session() returns void language plpgsql as $qg$begin if auth.uid() is null then raise exception 'live session required'; end if; end$qg$;
 create schema storage;
 create table storage.objects(bucket_id text not null,name text primary key);
 alter table orders add column completed_at timestamptz,add column rider_arrived_customer_at timestamptz,add column gp_rate numeric default 0,add column created_by uuid,add column rider_search_started_at timestamptz,add column picked_up_at timestamptz,add column delivering_at timestamptz,add column cancelled_reason text;
 alter table deliveries add column pickup_address text,add column delivery_address text,add column pickup_latitude float8,add column pickup_longitude float8,add column delivery_latitude float8,add column delivery_longitude float8;
 create table queuego_cash_order_locks(order_id uuid primary key);
 create table qg_rider_action_receipts(request_id uuid primary key,user_id uuid,kind text,payload jsonb,result jsonb);
 create table qg_private.rider_document_expiries(id uuid primary key default gen_random_uuid(),rider_id uuid references rider_profiles,vehicle_id uuid,document_key text,expires_on date,recorded_by uuid,
 constraint rider_document_expiries_key_check check(document_key in ('national_id','driver_license','vehicle_prb')),
 constraint rider_document_expiries_subject_check check((document_key in ('national_id','driver_license') and vehicle_id is null) or (document_key='vehicle_prb' and vehicle_id is not null)));
 create unique index rider_doc_key on qg_private.rider_document_expiries(rider_id,document_key) where vehicle_id is null;
 create function queuego_service_area_route_status(float8,float8,float8,float8,text) returns jsonb language sql as $$select '{"available":true}'::jsonb$$;
 create sequence test_order_number;
 create function qg_next_order_number() returns text language sql as $$select 'QT-'||nextval('test_order_number')$$;
 insert into users values('${id(70)}','${id(170)}','admin','active','Admin');

 alter table queuego_platform_rules drop constraint queuego_platform_rules_pkey;
 alter table queuego_platform_rules add column effective_from timestamptz not null default '1970-01-01Z',add column note text,add column created_by uuid;
 alter table queuego_platform_rules add primary key(rule_key,effective_from);
 create or replace function queuego_rule_numeric(k text,d numeric,t timestamptz) returns numeric language sql as $$select coalesce((select (value#>>'{}')::numeric from queuego_platform_rules where rule_key=k and effective_from<=t order by effective_from desc,created_at desc limit 1),d)$$;
 create function get_my_user_id() returns uuid language sql security definer as $$select id from users where auth_user_id=auth.uid() and status='active'$$;
 create function get_my_role() returns text language sql security definer as $$select role from users where auth_user_id=auth.uid() and status='active'$$;
 create function is_active_admin() returns boolean language sql security definer as $$select coalesce(get_my_role()='admin',false)$$;
 create function qg_has_live_rider_offer(p uuid) returns boolean language sql security definer as $$select exists(select 1 from qg_rider_order_offers f join rider_profiles r on r.id=f.rider_id where f.order_id=p and r.user_id=get_my_user_id() and f.expires_at>now())$$;
 alter table orders enable row level security;
 create policy rider_orders_offered_select on orders for select to authenticated using(false);
 create policy orders_rider_select on orders for select to authenticated using(rider_id in(select id from rider_profiles where user_id=get_my_user_id()));
 create policy orders_customer_select on orders for select to authenticated using(customer_id=get_my_user_id());
 grant usage on schema auth to authenticated;
 grant select on orders,rider_profiles to authenticated;
`+fs.readFileSync('supabase/migrations/20261015052056_passenger_ride_shared_dispatch.sql','utf8')+fs.readFileSync('supabase/migrations/20261015054045_passenger_ride_flow_controls.sql','utf8')+fs.readFileSync('supabase/migrations/20261015061000_passenger_public_license_evidence.sql','utf8');
