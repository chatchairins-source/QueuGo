const assert=require('node:assert/strict'),{Client,types}=require('pg');
types.setTypeParser(1184,value=>value);
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');

(async()=>{
 const raw=process.env.QG_TEST_PG_URL;
 if(!raw) throw Error('QG_TEST_PG_URL must point to an isolated loopback test database');
 const url=new URL(raw);
 if(!['localhost','127.0.0.1','[::1]'].includes(url.hostname)||url.pathname!=='/queuego_express_gate')
  throw Error('refusing any non-local or non-Express test database');

 const clients=[];
 const connect=async actor=>{
  const c=new Client({connectionString:raw,statement_timeout:8000});
  await c.connect();clients.push(c);
  if(actor) await c.query("select set_config('test.uid',$1,false)",[id(actor)]);
  return c;
 };
 let checks=0,proofSeq=0;
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++;};
 const waitForLock=async(admin,pid,label)=>{
  for(let i=0;i<120;i++){
   const row=(await admin.query('select wait_event_type from pg_stat_activity where pid=$1',[pid])).rows[0];
   if(row?.wait_event_type==='Lock'){checks++;return;}
   await new Promise(resolve=>setTimeout(resolve,10));
  }
  throw Error('did not reach intended row-lock overlap: '+label);
 };
 try{
  const admin=await connect(),riderA=await connect(101),riderB=await connect(101),other=await connect(102),review=await connect(170),customerA=await connect(160),customerB=await connect(160);
  const version=(await admin.query('show server_version_num')).rows[0].server_version_num;
  assert(Number(version)>=150000);checks++;
  if((await admin.query("select to_regclass('public.orders') existing")).rows[0].existing) throw Error('test requires a fresh empty database');
  await admin.query(require('./fixtures/express-schema.cjs')());

  for(const [k,v] of Object.entries({'express.base_fare':20,'express.included_km':0,'express.per_km':10,'express.minimum_fare':20,'express.platform_fee_percent':0}))
   await admin.query('insert into queuego_platform_rules(rule_key,value,effective_from,created_at) values($1,$2::jsonb,\'1970-01-01Z\',now())',[k,JSON.stringify(v)]);

  const seedExpress=async(n,weight=2,vehicle='motorcycle')=>{
   await admin.query(
    `insert into orders(id,order_number,customer_id,order_type,fulfillment_vertical,status,subtotal,delivery_fee,total_amount,gp_amount,
      pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,
      express_distance_km,express_duration_minutes,express_platform_fee,express_item_type,express_weight_kg,express_size,express_vehicle_type,
      express_sender_name,express_sender_phone,express_receiver_name,express_receiver_phone,express_instructions,rider_delivery_payout,rider_search_started_at)
     values($1,$2,$3,'service','express','searching_rider',0,50,50,0,'pickup',15,103,'dropoff',15.01,103,
      3,8,0,'parcel',$4,'medium',$5,'Sender','0811111111','Receiver','0822222222','race',50,now())`,
    [id(n),'QT-'+n,id(60),weight,vehicle]
   );
   await admin.query("insert into deliveries(order_id,status,delivery_fee) values($1,'pending',50)",[id(n)]);
   await admin.query("insert into payments(order_id,amount,status,payment_method) values($1,50,'pending','cash')",[id(n)]);
  };
  const seedFood=async n=>{
   await admin.query(
    `insert into orders(id,order_number,customer_id,shop_id,order_type,fulfillment_vertical,status,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude)
     values($1,$2,$3,$4,'shopping','food','searching_rider',15,103,15.002,103)`,
    [id(n),'QT-'+n,id(60),id(50)]
   );
   await admin.query("insert into deliveries(order_id,status,delivery_fee) values($1,'pending',25)",[id(n)]);
  };
  const seedRide=async n=>{
   await admin.query(
    `insert into orders(id,order_number,customer_id,order_type,fulfillment_vertical,status,subtotal,delivery_fee,total_amount,
      ride_distance_km,ride_duration_minutes,ride_platform_fee,rider_delivery_payout,pickup_address,pickup_latitude,pickup_longitude,
      delivery_address,delivery_latitude,delivery_longitude)
     values($1,$2,$3,'service','ride','searching_rider',0,50,50,3,8,0,50,'pickup',15,103,'dropoff',15.01,103)`,
    [id(n),'QT-'+n,id(60)]
   );
   await admin.query("insert into deliveries(order_id,status,delivery_fee) values($1,'pending',50)",[id(n)]);
   await admin.query("insert into payments(order_id,amount,status,payment_method) values($1,50,'pending','cash')",[id(n)]);
  };
  const order=async n=>(await admin.query('select * from orders where id=$1',[id(n)])).rows[0];
  const finishExpress=async n=>{
   await riderA.query("select qg_private.express_order_action($1,'arrive_pickup')",[id(n)]);
   const pickup=id(101)+'/express-race-pickup-'+(++proofSeq)+'.jpg';
   await admin.query("insert into storage.objects(bucket_id,name) values('qg-evidence',$1)",[pickup]);
   await riderA.query('select qg_express_pickup_with_photo($1,$2,$3,15,103)',[id(500+proofSeq),id(n),pickup]);
   await riderA.query("select qg_private.express_order_action($1,'start_transit')",[id(n)]);
   await riderA.query("select qg_private.express_order_action($1,'arrive_dropoff')",[id(n)]);
   const drop=id(101)+'/express-race-drop-'+(++proofSeq)+'.jpg';
   await admin.query("insert into storage.objects(bucket_id,name) values('qg-evidence',$1)",[drop]);
   await riderA.query('select qg_express_complete_with_photo($1,$2,$3,15.01,103)',[id(500+proofSeq),id(n),drop]);
  };
  const approveRide=async()=>{
   const path=id(101)+'/express-regression-public-license.jpg';
   await admin.query("insert into storage.objects(bucket_id,name) values('qg-evidence',$1) on conflict(name) do nothing",[path]);
   await riderA.query('select qg_rider_submit_public_license($1)',[path]);
   await review.query('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),'Express shared-dispatch regression']);
  };

  // Same Express customer + request ID is replay-safe under independent connections.
  const quote=(await admin.query(
   'select queuego_cache_express_quote($1,$2,$3,15,103,15.01,103,$4,$5,$6,$7,$8,2,$9,$10,$11,3,8) r',
   [id(160),'Pickup address','Dropoff address','Sender','0811111111','Receiver','0822222222','parcel','medium','motorcycle','race']
  )).rows[0].r;
  const replay=await Promise.all([
   customerA.query('select queuego_request_express($1,$2) r',[id(70),quote.quote_id]),
   customerB.query('select queuego_request_express($1,$2) r',[id(70),quote.quote_id])
  ]);
  eq(replay[0].rows[0].r.id,id(70));eq(replay[1].rows[0].r.id,id(70));
  eq((await admin.query("select count(*)::int n from orders where id=$1",[id(70)])).rows[0].n,1);
  await admin.query("update orders set status='cancelled',cancelled_at=now() where id=$1",[id(70)]);

  // Two dispatch workers still create one 30-second targeted offer.
  await seedExpress(80);
  await Promise.all([riderA.query('select qg_dispatch_rider_offers()'),riderB.query('select qg_dispatch_rider_offers()')]);
  const offers=(await admin.query('select * from qg_rider_order_offers where order_id=$1',[id(80)])).rows;
  eq(offers.length,1);eq(offers[0].rider_id,id(11));eq(new Date(offers[0].expires_at)>new Date(),true);

  // Same selected Rider claiming concurrently is idempotent; a non-selected Rider cannot steal it.
  const duplicate=await Promise.allSettled([riderA.query('select rider_claim_order($1)',[id(80)]),riderB.query('select rider_claim_order($1)',[id(80)])]);
  eq(duplicate.filter(x=>x.status==='fulfilled').length,2);eq((await order(80)).rider_id,id(11));
  await assert.rejects(other.query('select rider_claim_order($1)',[id(80)]),/live targeted|no longer|finish current|active online/);checks++;
  await finishExpress(80);eq((await order(80)).status,'completed');

  // Vehicle-capacity change wins the Rider-row lock; stale offer cannot assign an undersized vehicle.
  await seedExpress(81,15);
  await admin.query('select qg_dispatch_rider_offers()');
  eq((await admin.query('select rider_id from qg_rider_order_offers where order_id=$1',[id(81)])).rows[0].rider_id,id(11));
  await review.query('begin');
  await review.query('update rider_profiles set vehicle_capacity_kg=10 where id=$1',[id(11)]);
  const blockedClaim=riderA.query('select rider_claim_order($1)',[id(81)]).then(value=>({value}),error=>({error}));
  await waitForLock(admin,riderA.processID,'Express capacity vs claim');
  await review.query('commit');
  const afterCapacity=await blockedClaim;
  assert(afterCapacity.error);assert.match(String(afterCapacity.error.message),/vehicle capacity/);checks+=2;
  eq((await order(81)).rider_id,null);
  await review.query('update rider_profiles set vehicle_capacity_kg=20 where id=$1',[id(11)]);
  await riderA.query('select rider_claim_order($1)',[id(81)]);
  await finishExpress(81);

  // Express claim and ordinary Food assignment serialize on the same Rider row.
  await seedExpress(82);await seedFood(182);
  await admin.query('delete from qg_rider_order_offers');await admin.query('select qg_dispatch_rider_offers()');
  await riderA.query('begin');await riderA.query('select rider_claim_order($1)',[id(82)]);
  const blockedFood=admin.query("update orders set rider_id=$1,status='rider_assigned' where id=$2",[id(11),id(182)]).then(value=>({value}),error=>({error}));
  await waitForLock(review,admin.processID,'Express claim vs Food assignment');
  await riderA.query('commit');
  const foodAfterExpress=await blockedFood;
  assert(foodAfterExpress.error);assert.match(String(foodAfterExpress.error.message),/Express conflicts with active work/);checks+=2;
  eq((await order(182)).rider_id,null);
  await admin.query("update orders set status='cancelled' where id=$1",[id(182)]);
  await finishExpress(82);

  // Rollback cannot leave a ghost Express assignment that blocks existing delivery work.
  await seedExpress(83);await seedFood(183);
  await admin.query('delete from qg_rider_order_offers');await admin.query('select qg_dispatch_rider_offers()');
  await riderA.query('begin');await riderA.query('select rider_claim_order($1)',[id(83)]);
  const foodAfterRollback=admin.query("update orders set rider_id=$1,status='rider_assigned' where id=$2",[id(11),id(183)]).then(value=>({value}),error=>({error}));
  await waitForLock(review,admin.processID,'Express rollback vs Food assignment');
  await riderA.query('rollback');
  const rollbackResult=await foodAfterRollback;if(rollbackResult.error) throw rollbackResult.error;
  eq((await order(83)).rider_id,null);eq((await order(183)).rider_id,id(11));
  await admin.query("update orders set rider_id=null,status='cancelled' where id=$1",[id(183)]);
  await admin.query("update orders set status='cancelled',cancelled_at=now() where id=$1",[id(83)]);

  // Express migration must preserve the existing Passenger Ride path on the shared dispatcher/claim functions.
  await approveRide();await seedRide(190);await admin.query('delete from qg_rider_order_offers');await admin.query('select qg_dispatch_rider_offers()');
  eq((await admin.query('select rider_id from qg_rider_order_offers where order_id=$1',[id(190)])).rows[0].rider_id,id(11));
  await riderA.query('select rider_claim_order($1)',[id(190)]);
  await riderA.query("select qg_private.ride_order_action($1,'arrive_pickup')",[id(190)]);
  await riderA.query("select qg_private.ride_order_action($1,'start')",[id(190)]);
  await riderA.query("select qg_private.ride_order_action($1,'arrive_dropoff')",[id(190)]);
  await riderA.query("select qg_private.ride_order_action($1,'complete')",[id(190)]);
  eq((await order(190)).status,'completed');

  console.log(JSON.stringify({checks,failures:0,postgresVersion:version,scope:'real PostgreSQL independent connections: Express request replay, duplicate dispatch/claim, vehicle-capacity race, Express-vs-Food serialization, rollback recovery and Passenger Ride regression; no Production data'}));
 }finally{await Promise.allSettled(clients.map(c=>c.end()));}
})().catch(e=>{console.error(e);process.exit(1)});
