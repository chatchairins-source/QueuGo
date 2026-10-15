const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;
 const q=(s,p=[])=>db.query(s,p),eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const reject=async(s,p,re)=>{await assert.rejects(q(s,p),re);checks++};
 const as=n=>q("select set_config('test.uid',$1,false)",[id(n)]);
 await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
 await db.exec(fs.readFileSync('supabase/migrations/20261014234500_rider_continuous_dispatch_phase1.sql','utf8'));
 await db.exec(`
 create function qg_private.assert_live_session() returns void language plpgsql as $$begin if auth.uid() is null then raise exception 'live session required'; end if; end$$;
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
 `);
 await db.exec(fs.readFileSync('supabase/migrations/20261015052056_passenger_ride_shared_dispatch.sql','utf8'));
 // Private helpers and admin review are never anonymously callable.
 eq((await q("select has_function_privilege('anon','queuego_admin_review_public_license(uuid,date,text)','execute') v")).rows[0].v,false);
 eq((await q("select has_function_privilege('authenticated','qg_private.ride_order_action(uuid,text)','execute') v")).rows[0].v,false);
 await as(101);await reject('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),'review'],/active admin/);
 await as(170);await reject('select queuego_admin_review_public_license($1,current_date-1,$2)',[id(11),'review'],/expiry/);
 await reject('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),''],/note/);
 const valid=async n=>(await q('select qg_private.ride_public_license_valid($1,now()) v',[id(n)])).rows[0].v;
 eq(await valid(11),false);
 // Ordinary driver licence alone never permits passenger work.
 await q("insert into qg_private.rider_document_expiries(rider_id,document_key,expires_on,recorded_by) values($1,'driver_license',current_date+30,$2)",[id(11),id(70)]);eq(await valid(11),false);
 await q('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),'checked public licence']);eq(await valid(11),true);
 eq((await q("select qg_private.ride_public_license_valid($1,'2030-01-01T00:00:00Z') v",[id(11)])).rows[0].v,false);
 await q("update users set status='suspended' where id=$1",[id(1)]);eq(await valid(11),false);await q("update users set status='active' where id=$1",[id(1)]);
 await q('select queuego_admin_review_public_license($1,null,$2)',[id(11),'revoked public licence']);eq(await valid(11),false);
 const seed=async(n,fee=0)=>{
 await q(`insert into orders(id,order_number,customer_id,order_type,fulfillment_vertical,status,subtotal,delivery_fee,total_amount,ride_distance_km,ride_duration_minutes,ride_platform_fee,rider_delivery_payout,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,$2,$3,'service','ride','searching_rider',0,50,50,3,8,$4::numeric,50-$4::numeric,15,103,15.01,103)`,[id(n),'QT-'+n,id(60),fee]);
 await q("insert into deliveries(order_id,status,delivery_fee) values($1,'pending',50)",[id(n)]);
 await q("insert into payments(order_id,amount,status,payment_method) values($1,50,'pending','cash')",[id(n)]);
 };
 const status=async n=>(await q('select status from orders where id=$1',[id(n)])).rows[0].status;
 await seed(80);await q('select qg_dispatch_rider_offers()');eq((await q('select count(*)::int n from qg_rider_order_offers')).rows[0].n,0);
 await q('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),'checked public licence']);
 await q('select qg_dispatch_rider_offers()');eq((await q('select rider_id from qg_rider_order_offers where order_id=$1',[id(80)])).rows[0].rider_id,id(11));
 // Revoke after offering: accepting the old offer must still fail.
 await q('select queuego_admin_review_public_license($1,null,$2)',[id(11),'revoked public licence']);
 await reject('select qg_private.claim_rider_order($1,$2)',[id(11),id(80)],/public driving license/);eq(await status(80),'searching_rider');
 await q('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),'checked public licence']);
 await q('select qg_private.claim_rider_order($1,$2)',[id(11),id(80)]);eq(await status(80),'rider_assigned');
 await reject('update orders set ride_distance_km=null where id=$1',[id(80)],/immutable/);
 await reject("insert into laundry_rider_jobs values($1,'assigned')",[id(11)],/conflicts/);
 await q(`insert into orders(id,order_number,status,customer_id,shop_id) values($1,'QT-food','searching_rider',$2,$3)`,[id(81),id(60),id(50)]);
 await reject("update orders set rider_id=$1,status='rider_assigned' where id=$2",[id(11),id(81)],/conflicts/);
 await as(101);
 const action=async(a,n)=>q('select qg_rider_action_once($1,$2,$3::jsonb) result',[id(n),'ride',JSON.stringify({p_order_id:id(80),p_action:a})]);
 await reject("select qg_private.ride_order_action($1,'start')",[id(80)],/stage changed/);
 await action('arrive_pickup',180);
 await as(170);await q('select queuego_admin_review_public_license($1,null,$2)',[id(11),'revoked before start']);await as(101);
 await reject("select qg_private.ride_order_action($1,'start')",[id(80)],/public driving license/);
 await as(170);await q('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),'checked public licence']);await as(101);
 await action('start',181);eq(await status(80),'in_progress');
 await action('arrive_dropoff',182);
 await as(170);await q('select queuego_admin_review_public_license($1,null,$2)',[id(11),'revoked after onboard']);await as(101);
 await action('complete',183);await action('complete',183);
 eq(await status(80),'completed');eq((await q("select count(*)::int n from audit_logs where action='ride_order_action' and description='complete'")).rows[0].n,1);
 eq((await q('select status from payments where order_id=$1',[id(80)])).rows[0].status,'paid');
 await as(160);const state=(await q('select queuego_ride_state($1) s',[id(80)])).rows[0].s;eq(state.rides[0].rider_latitude,null);
 await as(102);eq((await q('select queuego_ride_state($1) s',[id(80)])).rows[0].s.rides.length,0);
 // Credit integration is fail closed; never manufacture balance or assume a QR was paid.
 eq((await q('select qg_private.ride_credit_authorized($1,$2,5) v',[id(11),id(80)])).rows[0].v,false);
 await as(170);await q('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),'checked public licence']);
 await seed(82,5);await q('delete from qg_rider_order_offers');await q('select qg_dispatch_rider_offers()');
 eq((await q('select count(*)::int n from qg_rider_order_offers where order_id=$1',[id(82)])).rows[0].n,0);
 // Pricing and durable request creation execute the actual staged RPCs.
 await q("update orders set status='cancelled' where id=$1",[id(82)]);
 for(const [k,v] of Object.entries({'ride.base_fare':20,'ride.included_km':0,'ride.per_km':10,'ride.minimum_fare':20,'ride.platform_fee_percent':0})) await q('insert into queuego_platform_rules values($1,$2::jsonb,now())',[k,JSON.stringify(v)]);
 eq((await q("select has_function_privilege('authenticated','queuego_cache_ride_quote(uuid,text,text,double precision,double precision,double precision,double precision,numeric,numeric)','execute') v")).rows[0].v,false);
 const quote=async()=> (await q('select queuego_cache_ride_quote($1,$2,$3,15,103,15.01,103,3,8) r',[id(160),'pickup address','destination address'])).rows[0].r;
 const quoted=await quote();eq(quoted.fare,50);await as(160);
 const requested=(await q('select queuego_request_ride($1,$2) r',[id(90),quoted.quote_id])).rows[0].r;eq(requested.fare,50);
 eq((await q('select queuego_request_ride($1,$2) r',[id(90),quoted.quote_id])).rows[0].r.id,id(90));
 await reject('select queuego_request_ride($1,$2)',[id(91),quoted.quote_id],/already used/);
 await as(102);await reject('select queuego_request_ride($1,$2)',[id(90),quoted.quote_id],/active customer/);
 await as(160);const another=await quote();await reject('select queuego_request_ride($1,$2)',[id(92),another.quote_id],/finish current ride/);
 await q("update orders set status='cancelled' where id=$1",[id(90)]);
 await q("update qg_private.ride_quotes set expires_at=now()-interval '1 second' where id=$1",[another.quote_id]);
 await reject('select queuego_request_ride($1,$2)',[id(92),another.quote_id],/expired/);
 await db.exec("create or replace function queuego_feature_enabled(k text,t timestamptz) returns boolean language sql as $$select false$$;");
 await reject('select queuego_cache_ride_quote($1,$2,$3,15,103,15.01,103,3,8)',[id(160),'pickup address','destination address'],/not configured or enabled/);
 await db.close();console.log('Passenger Ride: '+checks+' checks PASS');
})().catch(e=>{console.error(e.message,e.detail||'');process.exitCode=1});
