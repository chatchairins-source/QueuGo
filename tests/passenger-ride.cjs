const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;
 const q=(s,p=[])=>db.query(s,p),eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const reject=async(s,p,re)=>{await assert.rejects(q(s,p),re);checks++};
 const as=n=>q("select set_config('test.uid',$1,false)",[id(n)]);
 await db.exec(require('./fixtures/passenger-ride-schema.cjs')());
 const licenseCardSource=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderPassengerLicenseCard.kt','utf8');
 const riderApiSource=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
 const customerRideSource=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/CustomerRideScreen.kt','utf8');
 const riderPassengerCardSource=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderPassengerCard.kt','utf8');
 const riderRootSource=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
 const adminRideSource=fs.readFileSync('admin/passenger-ride.js','utf8');
 const sharedRideMigration=fs.readFileSync('supabase/migrations/20261015052056_passenger_ride_shared_dispatch.sql','utf8');
 const publicLicenseMigration=fs.readFileSync('supabase/migrations/20261015061000_passenger_public_license_evidence.sql','utf8');
 eq(licenseCardSource.includes('อัปโหลดใบขับขี่สาธารณะ'),true);
 eq(licenseCardSource.includes('api.submitPassengerLicense(context, auth, uri)'),true);
 eq(riderApiSource.includes('"qg_rider_submit_public_license"'),true);
 eq(customerRideSource.includes('var backendConfirmed by remember(auth.user.id) { mutableStateOf(false) }'),true);
 eq(customerRideSource.includes('refresh()')&&customerRideSource.includes('backendConfirmed = true')&&customerRideSource.includes('loaded = true'),true);
 eq(customerRideSource.includes('enabled = false')&&customerRideSource.includes('error = if (backendConfirmed)'),true);
 eq(riderApiSource.includes('deliveryFee = o.doubleOrNull("rider_delivery_payout") ?: o.doubleOrNull("delivery_fee")'),true);
 eq(riderApiSource.includes('result.optBoolean("requeued", false)'),true);
 eq(riderApiSource.includes('status == "searching_rider"'),true);
 eq(riderRootSource.includes('สิทธิ์หรือเครดิตรับเที่ยวไม่พร้อม · ระบบส่งเที่ยวกลับไปหาคนขับใหม่แล้ว'),true);
 eq(riderPassengerCardSource.includes('riderMoneyBaht(job.deliveryFee ?: 0.0)'),true);
 eq(riderPassengerCardSource.includes('job.customerCash?.let(::riderMoneyBaht)'),true);
 eq(riderPassengerCardSource.includes('passengerNavigationLabel(job)'),true);
 eq(adminRideSource.includes('ดูหลักฐานใบขับขี่สาธารณะ'),true);
 eq(sharedRideMigration.includes('qg_ride_profile_status_requeue'),true);
 eq(sharedRideMigration.includes('qg_ride_user_status_requeue'),true);
 eq(sharedRideMigration.includes("v_user_status<>'active' and p_kind<>'ride'"),true);
 eq(sharedRideMigration.includes('In-progress rides are intentionally untouched so the passenger is never stranded.'),true);
 eq(publicLicenseMigration.includes('pending public driving license evidence required'),true);
 eq(publicLicenseMigration.includes("bucket_id='qg-evidence'"),true);
 let evidenceSeq=0;
 const submitPublicEvidence=async(rider=11,actor=101)=>{
  const path=id(actor)+'/public-license-'+(++evidenceSeq)+'.jpg';
  await q("insert into storage.objects(bucket_id,name) values('qg-evidence',$1) on conflict(name) do nothing",[path]);
  await as(actor);
  const submitted=(await q('select qg_rider_submit_public_license($1) s',[path])).rows[0].s;
  eq(submitted.status,'pending');
  return path;
 };
 const approvePublic=async(rider=11,actor=101)=>{
  const path=await submitPublicEvidence(rider,actor);
  await as(170);
  await q('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(rider),'checked public licence']);
  return path;
 };
 // Private helpers and admin review are never anonymously callable.
 eq((await q("select has_function_privilege('anon','queuego_admin_review_public_license(uuid,date,text)','execute') v")).rows[0].v,false);
 eq((await q("select has_function_privilege('authenticated','qg_private.ride_order_action(uuid,text)','execute') v")).rows[0].v,false);
 await as(101);await reject('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),'review'],/active admin/);
 await as(170);await reject('select queuego_admin_review_public_license($1,current_date-1,$2)',[id(11),'review'],/expiry/);
 await reject('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),''],/note/);
 await reject('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),'review evidence'],/evidence/);
 const valid=async n=>(await q('select qg_private.ride_public_license_valid($1,now()) v',[id(n)])).rows[0].v;
 eq(await valid(11),false);
 // Ordinary driver licence alone never permits passenger work.
 await q("insert into qg_private.rider_document_expiries(rider_id,document_key,expires_on,recorded_by) values($1,'driver_license',current_date+30,$2)",[id(11),id(70)]);eq(await valid(11),false);
 const firstEvidence=await approvePublic();eq(await valid(11),true);
 const adminLicenses=(await q('select queuego_admin_public_licenses() l')).rows[0].l;
 eq(adminLicenses.find(x=>x.rider_id===id(11)).evidence_path,firstEvidence);
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
 await approvePublic();
 await q('select qg_dispatch_rider_offers()');eq((await q('select rider_id from qg_rider_order_offers where order_id=$1',[id(80)])).rows[0].rider_id,id(11));
 await as(101);await q('set role authenticated');eq((await q('select id from orders where id=$1',[id(80)])).rows.length,1);await q('reset role');
 await as(102);await q('set role authenticated');eq((await q('select id from orders where id=$1',[id(80)])).rows.length,0);await q('reset role');await as(170);
 // Revoke after offering: accepting the old offer must still fail.
 await q('select queuego_admin_review_public_license($1,null,$2)',[id(11),'revoked public licence']);
 await reject('select qg_private.claim_rider_order($1,$2)',[id(11),id(80)],/public driving license/);eq(await status(80),'searching_rider');
 await approvePublic();
 await q('select qg_private.claim_rider_order($1,$2)',[id(11),id(80)]);eq(await status(80),'rider_assigned');
 await reject('update orders set ride_distance_km=null where id=$1',[id(80)],/immutable/);
 await reject("insert into laundry_rider_jobs values($1,'assigned')",[id(11)],/conflicts/);
 await q(`insert into orders(id,order_number,status,customer_id,shop_id) values($1,'QT-food','searching_rider',$2,$3)`,[id(81),id(60),id(50)]);
 await reject("update orders set rider_id=$1,status='rider_assigned' where id=$2",[id(11),id(81)],/conflicts/);
 await q("update orders set status='cancelled' where id=$1",[id(81)]);
 await as(101);
 const action=async(a,n)=>q('select qg_rider_action_once($1,$2,$3::jsonb) result',[id(n),'ride',JSON.stringify({p_order_id:id(80),p_action:a})]);
 await reject("select qg_private.ride_order_action($1,'start')",[id(80)],/stage changed/);
 await action('arrive_pickup',180);
 await as(170);
 const revokedBeforeStart=(await q('select queuego_admin_review_public_license($1,null,$2) r',[id(11),'revoked before start'])).rows[0].r;
 eq(revokedBeforeStart.requeued_rides,1);
 const requeued=(await q('select status,rider_id,rider_assigned_at,rider_arrived_shop_at,rider_search_started_at from orders where id=$1',[id(80)])).rows[0];
 eq(requeued.status,'searching_rider');eq(requeued.rider_id,null);eq(requeued.rider_assigned_at,null);eq(requeued.rider_arrived_shop_at,null);eq(requeued.rider_search_started_at!==null,true);
 const resetDelivery=(await q('select rider_id,status from deliveries where order_id=$1',[id(80)])).rows[0];
 eq(resetDelivery.rider_id,null);eq(resetDelivery.status,'pending');
 eq((await q("select count(*)::int n from audit_logs where action='ride_compliance_requeue' and entity_id=$1",[id(80)])).rows[0].n,1);
 eq((await q("select count(*)::int n from qg_rider_action_receipts where kind='ride' and payload->>'p_order_id'=$1",[id(80)])).rows[0].n,0);
 eq((await q("select count(*)::int n from notifications where reference_id=$1 and title='กำลังหาคนขับใหม่'",[id(80)])).rows[0].n,1);
 await as(101);await reject("select qg_private.ride_order_action($1,'start')",[id(80)],/assigned passenger ride/);
 await approvePublic(12,102);
 await q('select qg_dispatch_rider_offers()');
 eq((await q('select rider_id from qg_rider_order_offers where order_id=$1',[id(80)])).rows[0].rider_id,id(12));
 await as(102);await q('select rider_claim_order($1)',[id(80)]);
 await action('arrive_pickup',181);
 await action('start',182);eq(await status(80),'in_progress');
 // Once the passenger is onboard, later account/profile suspension must NOT strand them.
 // The suspended Rider may only continue this already-active Ride path to a safe completion.
 await as(170);await q("update users set status='suspended' where id=$1",[id(2)]);
 await q("update rider_profiles set status='suspended' where id=$1",[id(12)]);await as(102);
 await action('arrive_dropoff',183);
 await as(170);await q('select queuego_admin_review_public_license($1,null,$2)',[id(12),'revoked after onboard']);await as(102);
 await action('complete',184);await action('complete',184);
 eq(await status(80),'completed');
 await as(170);await q("update users set status='active' where id=$1",[id(2)]);
 await q("update rider_profiles set status='active' where id=$1",[id(12)]);eq((await q("select count(*)::int n from audit_logs where action='ride_order_action' and description='complete'")).rows[0].n,1);
 eq((await q('select status from payments where order_id=$1',[id(80)])).rows[0].status,'paid');
 await as(160);const state=(await q('select queuego_ride_state($1) s',[id(80)])).rows[0].s;eq(state.rides[0].rider_latitude,null);
 await as(101);eq((await q('select queuego_ride_state($1) s',[id(80)])).rows[0].s.rides.length,0);

 // Rider-profile eligibility removal before pickup proactively requeues without waiting for the Rider to press Start.
 await as(170);await approvePublic();await seed(86);await q('delete from qg_rider_order_offers');await q('select qg_dispatch_rider_offers()');
 await as(101);await q('select rider_claim_order($1)',[id(86)]);await q("select qg_private.ride_order_action($1,'arrive_pickup')",[id(86)]);
 await as(170);await q("update rider_profiles set status='suspended' where id=$1",[id(11)]);
 eq(await status(86),'searching_rider');eq((await q('select rider_id from orders where id=$1',[id(86)])).rows[0].rider_id,null);
 eq((await q("select count(*)::int n from audit_logs where action='ride_compliance_requeue' and entity_id=$1",[id(86)])).rows[0].n,1);
 await q("update orders set status='cancelled' where id=$1",[id(86)]);await q("update rider_profiles set status='active' where id=$1",[id(11)]);

 // User-account suspension is the same eligibility loss and must also proactively release a pre-pickup Ride.
 await seed(87);await q('delete from qg_rider_order_offers');await q('select qg_dispatch_rider_offers()');
 await as(101);await q('select rider_claim_order($1)',[id(87)]);await q("select qg_private.ride_order_action($1,'arrive_pickup')",[id(87)]);
 await as(170);await q("update users set status='suspended' where id=$1",[id(1)]);
 eq(await status(87),'searching_rider');eq((await q('select rider_id from orders where id=$1',[id(87)])).rows[0].rider_id,null);
 eq((await q("select count(*)::int n from audit_logs where action='ride_compliance_requeue' and entity_id=$1",[id(87)])).rows[0].n,1);
 await q("update orders set status='cancelled' where id=$1",[id(87)]);await q("update users set status='active' where id=$1",[id(1)]);

 // Expiry between claim and passenger pickup must also requeue instead of stranding the customer.
 await as(170);await approvePublic();await seed(84);await q('delete from qg_rider_order_offers');await q('select qg_dispatch_rider_offers()');
 eq((await q('select rider_id from qg_rider_order_offers where order_id=$1',[id(84)])).rows[0].rider_id,id(11));
 await as(101);await q('select rider_claim_order($1)',[id(84)]);await q("select qg_private.ride_order_action($1,'arrive_pickup')",[id(84)]);
 await as(170);await q("update qg_private.rider_document_expiries set expires_on=current_date-1 where rider_id=$1 and document_key='public_driver_license'",[id(11)]);await as(101);
 const expiredStart=(await q("select qg_private.ride_order_action($1,'start') r",[id(84)])).rows[0].r;
 eq(expiredStart.status,'searching_rider');eq(expiredStart.requeued,true);
 eq((await q('select rider_id from orders where id=$1',[id(84)])).rows[0].rider_id,null);
 await as(170);await q("update orders set status='cancelled' where id=$1",[id(84)]);
 // Credit integration is fail closed; never manufacture balance or assume a QR was paid.
 eq((await q('select qg_private.ride_credit_authorized($1,$2,5) v',[id(11),id(80)])).rows[0].v,false);
 await as(170);await approvePublic();
 await seed(82,5);await q('delete from qg_rider_order_offers');await q('select qg_dispatch_rider_offers()');
 eq((await q('select count(*)::int n from qg_rider_order_offers where order_id=$1',[id(82)])).rows[0].n,0);
 // Pricing and durable request creation execute the actual staged RPCs.
 await q("update orders set status='cancelled' where id=$1",[id(82)]);
 for(const [k,v] of Object.entries({'ride.base_fare':20,'ride.included_km':0,'ride.per_km':10,'ride.minimum_fare':20,'ride.platform_fee_percent':0})) await q('insert into queuego_platform_rules(rule_key,value,created_at) values($1,$2::jsonb,now())',[k,JSON.stringify(v)]);
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
 await as(101);await reject('select queuego_admin_configure_ride(false,$1::jsonb)',[JSON.stringify({base_fare:20,included_km:0,per_km:10,minimum_fare:20,platform_fee_percent:0})],/active admin/);
 await as(170);await reject('select queuego_admin_configure_ride(true,$1::jsonb)',[JSON.stringify({base_fare:20,included_km:0,per_km:10,minimum_fare:20,platform_fee_percent:5})],/credit integration/);
 await q('select queuego_admin_configure_ride(false,$1::jsonb)',[JSON.stringify({base_fare:20,included_km:0,per_km:10,minimum_fare:20,platform_fee_percent:0})]);
 eq((await q("select value from queuego_platform_rules where rule_key='feature.ride' order by effective_from desc limit 1")).rows[0].value,false);
 await as(102);const ledger=(await q("select qg_rider_cash_ledger((now() at time zone 'Asia/Bangkok')::date,(now() at time zone 'Asia/Bangkok')::date) l")).rows[0].l;
 eq(ledger.find(o=>o.order_id===id(80)).cash_collected_customer,50);eq(ledger.find(o=>o.order_id===id(80)).cash_paid_merchant,0);
 await db.close();console.log('Passenger Ride: '+checks+' checks PASS');
})().catch(e=>{console.error(e.message,e.detail||'');process.exitCode=1});
