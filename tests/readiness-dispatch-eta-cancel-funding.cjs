const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;
 await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
 for(const suffix of [
  '_rider_continuous_dispatch_phase1.sql',
  '_rider_dynamic_stop_plan_phase1.sql',
  '_merchant_readiness_dispatch_eta_funding.sql'
 ]){
  const name=fs.readdirSync('supabase/migrations').find(n=>n.endsWith(suffix));
  if(!name)throw Error('missing migration '+suffix);
  await db.exec(fs.readFileSync('supabase/migrations/'+name,'utf8'));
 }
 const q=(s,p)=>db.query(s,p),as=uid=>q("select set_config('test.uid',$1,false)",[id(uid)]);
 const row=async n=>(await q('select * from orders where id=$1',[id(n)])).rows[0];
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 async function seed(n,status='searching_rider',rider=null){
  await q("insert into orders(id,order_number,status,rider_id,customer_id,shop_id,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,$2,$3,$4,$5,$6,15,103,15.002,103)",[id(n),'QT-'+n,status,rider&&id(rider),id(60),id(50)]);
  await q("insert into deliveries(order_id,rider_id,status,delivery_fee) values($1,$2,$3,25)",[id(n),rider&&id(rider),rider?'assigned':'pending']);
 }
 async function cacheRoute(a,b,link=.3){
  const primary=await row(a),candidate=await row(b),rider=(await q('select * from rider_profiles where id=$1',[primary.rider_id])).rows[0];
  const points={AP:[primary.pickup_latitude,primary.pickup_longitude],AD:[primary.delivery_latitude,primary.delivery_longitude],BP:[candidate.pickup_latitude,candidate.pickup_longitude],BD:[candidate.delivery_latitude,candidate.delivery_longitude],RP:[rider.latitude,rider.longitude]};
  const edges=Object.fromEntries(Object.entries(points).flatMap(([a,p])=>Object.entries(points).filter(([b])=>a!==b).map(([b,z])=>{const km=Math.hypot(p[0]-z[0],p[1]-z[1])*111;return[a+'|'+b,{km,minutes:km/25*60}]})));
  edges['AD|BP']={km:link,minutes:link*2.4};
  edges['AP|BP']={km:link,minutes:link*2.4};
  return (await q('select queuego_cache_route_bundle_matrix($1,$2,$3,$4,$5,$6,$7::jsonb) ok',[id(a),id(b),primary.updated_at,candidate.updated_at,rider.latitude,rider.longitude,JSON.stringify(edges)])).rows[0].ok;
 }
 async function setReady(n,minutes){
  await as(151);const o=await row(n);
  const target=(await q("select now()+($1||' minutes')::interval t",[String(minutes)])).rows[0].t;
  const req=id(800000+n*10+minutes);
  return (await q('select qg_merchant_set_ready_estimate($1,$2,$3,$4) r',[req,id(n),target,o.merchant_ready_revision])).rows[0].r;
 }

 await seed(300);
 let o=await row(300);assert(o.merchant_ready_estimate_at);checks++;eq(Number(o.merchant_ready_revision),1);
 let ready=await setReady(300,45);eq(Number(ready.ready_revision),2);
 let replay=(await q('select qg_merchant_set_ready_estimate($1,$2,$3,$4) r',[id(803045),id(300),ready.ready_estimate_at,1])).rows[0].r;
 // Different request id with stale revision is rejected; exact idempotency is tested next.
 // The previous call used deterministic id 803045.
 await assert.rejects(
   q('select qg_merchant_set_ready_estimate($1,$2,$3,$4)',[id(999001),id(300),ready.ready_estimate_at,1]),
   /readiness revision changed/
 );checks++;
 const exact=(await q('select qg_merchant_set_ready_estimate($1,$2,$3,$4) r',[id(803045),id(300),ready.ready_estimate_at,1])).rows[0].r;
 eq(exact.replayed,true);

 // Dynamic NEXT: late merchant blocks the SAME dispatcher; earlier readiness unlocks it.
 await as(101);await q('select qg_rider_dispatch_preference(true,false,true)');
 await q("insert into queuego_platform_rules values('dispatch.continuous_enabled','1',now())");
 await q("update rider_profiles set metadata='{}' where id=$1",[id(12)]);
 await seed(301,'in_progress',11);await seed(302);
 await cacheRoute(301,302);await q('select qg_dispatch_rider_offers()');eq((await row(302)).rider_id,null);
 await setReady(302,1);await as(101);await cacheRoute(301,302);await q('select qg_dispatch_rider_offers()');
 eq((await row(302)).rider_id,id(11));eq((await row(302)).next_after_order_id,id(301));
 await q("update orders set status='completed' where id in ($1,$2)",[id(301),id(302)]);
 await q('delete from qg_rider_order_offers');

 // Route bundle quote uses the same readiness facts.
 await seed(310,'ready',11);await seed(311);
 await as(101);await cacheRoute(310,311);
 let quote=(await q('select queuego_route_bundle_quote_orders($1,$2,now()) q',[id(310),id(311)])).rows[0].q;
 eq(quote.eligible,false);eq(quote.readiness_verified,true);
 await setReady(311,1);await as(101);await cacheRoute(310,311);
 quote=(await q('select queuego_route_bundle_quote_orders($1,$2,now()) q',[id(310),id(311)])).rows[0].q;
 eq(quote.eligible,true);eq(quote.readiness_verified,true);
 await q('select queuego_claim_route_bundle($1)',[id(311)]);
 const primaryBefore=await row(310);assert(Number(primaryBefore.delivery_fee)<25);checks++;

 // A later merchant readiness update re-plans only the uncommitted suffix.
 // Primary AP remains committed rank1 while its customer can move ahead of a late BP.
 await setReady(311,45);await as(101);
 let active=(await q('select * from queuego_route_bundle_active_orders()')).rows;
 let primaryPlan=JSON.parse(active.find(x=>x.order_id===id(310)).route_sequence);
 let candidatePlan=JSON.parse(active.find(x=>x.order_id===id(311)).route_sequence);
 eq(primaryPlan.pickup_rank,1);eq(primaryPlan.dropoff_rank,2);eq(candidatePlan.pickup_rank,3);

 // Merchant-side cancellation leaves the remaining customer's saving untouched,
 // restores standard Rider pay, dissolves the two-order stack and records QueueGo funding.
 await q("update orders set status='cancelled',cancelled_at=now() where id=$1",[id(311)]);
 const remain=await row(310);eq(remain.route_bundle_id,null);eq(Number(remain.rider_delivery_payout),25);eq(Number(remain.delivery_fee),Number(primaryBefore.delivery_fee));
 const funding=(await q('select amount,replacement_target_amount,protected_route_payout,replacement_order_id,status from qg_private.rider_cancellation_funding where order_id=$1',[id(310)])).rows[0];
 eq(Number(funding.amount),Number((25-Number(remain.delivery_fee)).toFixed(2)));eq(funding.status,'reserved');
 eq(Number(funding.replacement_target_amount),Number(quote.rider_extra_fee));
 eq(Number(funding.protected_route_payout),Number((25+Number(quote.rider_extra_fee)).toFixed(2)));
 eq(funding.replacement_order_id,null);
 const bundle=(await q('select status from route_bundles where id=$1',[primaryBefore.route_bundle_id])).rows[0];eq(bundle.status,'CANCELLED');

 // Customer ETA reads the same merchant readiness / Rider state and stays approximate.
 await as(160);const eta=(await q('select qg_customer_order_eta($1) e',[id(310)])).rows[0].e;
 assert(eta.eta_at);checks++;eq(eta.road_eta_verified,false);

 // After the surviving order becomes in-progress, live road guidance replaces
 // approximate ETA through the SAME customer read model.
 await q("update orders set status='in_progress' where id=$1",[id(310)]);
 await as(101);
 const liveEta=(await q('select qg_rider_publish_customer_eta($1,$2) e',[id(310),420])).rows[0].e;
 eq(liveEta.source,'rider_navigation_road');eq(Number(liveEta.eta_seconds),420);
 await as(160);
 const roadEta=(await q('select qg_customer_order_eta($1) e',[id(310)])).rows[0].e;
 eq(roadEta.source,'rider_navigation_road');eq(roadEta.road_eta_verified,true);
 await as(101);
 await seed(312);await setReady(312,1);await as(101);await q('select qg_rider_dispatch_preference(true,false,true)');
 await cacheRoute(310,312);await q('select qg_dispatch_rider_offers()');
 eq((await row(312)).rider_id,id(11));eq((await row(312)).next_after_order_id,id(310));
 const linked=(await q('select replacement_order_id from qg_private.rider_cancellation_funding where order_id=$1',[id(310)])).rows[0];
 eq(linked.replacement_order_id,id(312));

 const ledger=(await q('select qg_rider_cash_ledger(current_date,current_date) l')).rows[0].l;
 const line=ledger.find(x=>x.order_id===id(310));eq(Number(line.delivery_fee),25);eq(Number(line.platform_cancellation_funding),Number(funding.amount));
 eq(Number(line.platform_cancellation_replacement_target),Number(funding.replacement_target_amount));
 eq(line.platform_replacement_order_id,id(312));
 console.log(JSON.stringify({checks,failures:0,scope:'merchant readiness -> same dynamic dispatcher -> rider stack -> approximate customer ETA -> cancellation funding; isolated database only, no Production data or payout claim'}));
 await db.close();
})().catch(e=>{console.error(e);process.exit(1)});