const assert=require('node:assert/strict'),{Client,types}=require('pg');
types.setTypeParser(1184,value=>value);
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');

(async()=>{
 const raw=process.env.QG_TEST_PG_URL;
 if(!raw) throw Error('QG_TEST_PG_URL must point to an isolated loopback test database');
 const url=new URL(raw);
 if(!['localhost','127.0.0.1','[::1]'].includes(url.hostname)||url.pathname!=='/queuego_passenger_gate')
  throw Error('refusing any non-local or non-passenger test database');

 const clients=[];
 const connect=async actor=>{
  const c=new Client({connectionString:raw,statement_timeout:8000});
  await c.connect(); clients.push(c);
  if(actor) await c.query("select set_config('test.uid',$1,false)",[id(actor)]);
  return c;
 };
 let checks=0;
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
  const admin=await connect(),riderA=await connect(101),riderB=await connect(101),other=await connect(102),review=await connect(170);
  const version=(await admin.query('show server_version_num')).rows[0].server_version_num;
  assert(Number(version)>=150000);checks++;
  if((await admin.query("select to_regclass('public.orders') existing")).rows[0].existing)
   throw Error('test requires a fresh empty database');

  await admin.query(require('./fixtures/passenger-ride-schema.cjs')());

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
  const seedFood=async n=>{
   await admin.query(
    `insert into orders(id,order_number,customer_id,shop_id,order_type,fulfillment_vertical,status,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude)
     values($1,$2,$3,$4,'shopping','food','searching_rider',15,103,15.002,103)`,
    [id(n),'QT-'+n,id(60),id(50)]
   );
   await admin.query("insert into deliveries(order_id,status,delivery_fee) values($1,'pending',25)",[id(n)]);
  };
  const order=async n=>(await admin.query('select * from orders where id=$1',[id(n)])).rows[0];
  const approve=()=>review.query('select queuego_admin_review_public_license($1,current_date+30,$2)',[id(11),'race gate approved']);
  const revoke=()=>review.query('select queuego_admin_review_public_license($1,null,$2)',[id(11),'race gate revoked']);
  const finishRide=async n=>{
   await riderA.query("select qg_private.ride_order_action($1,'arrive_pickup')",[id(n)]);
   await riderA.query("select qg_private.ride_order_action($1,'start')",[id(n)]);
   await riderA.query("select qg_private.ride_order_action($1,'arrive_dropoff')",[id(n)]);
   await riderA.query("select qg_private.ride_order_action($1,'complete')",[id(n)]);
  };

  await approve();

  // Two independent dispatch workers must still produce one targeted offer for the only eligible public-licence Rider.
  await seedRide(80);
  await Promise.all([riderA.query('select qg_dispatch_rider_offers()'),riderB.query('select qg_dispatch_rider_offers()')]);
  const offers=(await admin.query('select * from qg_rider_order_offers where order_id=$1',[id(80)])).rows;
  eq(offers.length,1);eq(offers[0].rider_id,id(11));

  // Duplicate claim calls from the same Rider session identity are idempotent and audit once.
  const duplicate=await Promise.allSettled([
   riderA.query('select rider_claim_order($1)',[id(80)]),
   riderB.query('select rider_claim_order($1)',[id(80)])
  ]);
  eq(duplicate.filter(x=>x.status==='fulfilled').length,2);
  eq((await order(80)).rider_id,id(11));
  eq((await admin.query("select count(*)::int n from audit_logs where entity_id=$1",[id(80)])).rows[0].n,1);
  await assert.rejects(other.query('select rider_claim_order($1)',[id(80)]),/live targeted|no longer|finish current|active online/);checks++;
  await finishRide(80);

  // Revocation races with claim: both paths lock the Rider row, so a stale offer cannot be claimed after revocation commits.
  await seedRide(81);
  await admin.query('delete from qg_rider_order_offers');
  await admin.query('select qg_dispatch_rider_offers()');
  eq((await admin.query('select rider_id from qg_rider_order_offers where order_id=$1',[id(81)])).rows[0].rider_id,id(11));
  await review.query('begin');await revoke();
  const blockedClaim=riderA.query('select rider_claim_order($1)',[id(81)]).then(value=>({value}),error=>({error}));
  await waitForLock(admin,riderA.processID,'licence revoke vs claim');
  await review.query('commit');
  const claimAfterRevoke=await blockedClaim;
  assert(claimAfterRevoke.error);assert.match(String(claimAfterRevoke.error.message),/public driving license|verified current/);checks+=2;
  eq((await order(81)).rider_id,null);
  await approve();
  await riderA.query('select rider_claim_order($1)',[id(81)]);
  await riderA.query("select qg_private.ride_order_action($1,'arrive_pickup')",[id(81)]);

  // Revocation races with trip start: an already assigned Rider cannot put the passenger onboard with a revoked licence.
  await review.query('begin');await revoke();
  const blockedStart=riderA.query("select qg_private.ride_order_action($1,'start')",[id(81)]).then(value=>({value}),error=>({error}));
  await waitForLock(admin,riderA.processID,'licence revoke vs trip start');
  await review.query('commit');
  const startAfterRevoke=await blockedStart;
  assert(startAfterRevoke.error);assert.match(String(startAfterRevoke.error.message),/public driving license|verified current/);checks+=2;
  eq((await order(81)).status,'rider_assigned');
  await approve();
  await riderA.query("select qg_private.ride_order_action($1,'start')",[id(81)]);
  await riderA.query("select qg_private.ride_order_action($1,'arrive_dropoff')",[id(81)]);
  await riderA.query("select qg_private.ride_order_action($1,'complete')",[id(81)]);

  // A Ride claim and Food assignment cannot both commit for the same Rider even when they overlap before either transaction finishes.
  await seedRide(82);await seedFood(182);
  await admin.query('delete from qg_rider_order_offers');await admin.query('select qg_dispatch_rider_offers()');
  await riderA.query('begin');await riderA.query('select rider_claim_order($1)',[id(82)]);
  const blockedFood=admin.query("update orders set rider_id=$1,status='rider_assigned' where id=$2",[id(11),id(182)])
    .then(value=>({value}),error=>({error}));
  await waitForLock(review,admin.processID,'Ride claim vs Food assignment');
  await riderA.query('commit');
  const foodAfterRide=await blockedFood;
  assert(foodAfterRide.error);assert.match(String(foodAfterRide.error.message),/passenger ride conflicts with active work/);checks+=2;
  eq((await order(182)).rider_id,null);
  await finishRide(82);

  // Connection/transaction rollback must not leave a ghost passenger assignment that blocks ordinary work.
  await seedRide(83);await seedFood(183);
  await admin.query('delete from qg_rider_order_offers');await admin.query('select qg_dispatch_rider_offers()');
  await riderA.query('begin');await riderA.query('select rider_claim_order($1)',[id(83)]);
  const foodAfterRollback=admin.query("update orders set rider_id=$1,status='rider_assigned' where id=$2",[id(11),id(183)])
    .then(value=>({value}),error=>({error}));
  await waitForLock(review,admin.processID,'Ride rollback vs Food assignment');
  await riderA.query('rollback');
  const rollbackResult=await foodAfterRollback;
  if(rollbackResult.error) throw rollbackResult.error;
  eq((await order(83)).rider_id,null);eq((await order(183)).rider_id,id(11));

  console.log(JSON.stringify({
   checks,failures:0,postgresVersion:version,
   scope:'real PostgreSQL independent connections: duplicate Ride dispatch/claim, licence revoke claim/start races, Ride-vs-Food serialization, rollback recovery. No Production data.'
  }));
 }finally{
  await Promise.allSettled(clients.map(c=>c.end()));
 }
})().catch(e=>{console.error(e);process.exit(1)});
