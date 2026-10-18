const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const db=new PGlite();
 try{
  await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
  await db.exec("create function auth.role() returns text language sql as 'select coalesce(nullif(current_setting(''test.role'',true),''''),''authenticated'')';");
  await db.exec(fs.readFileSync('supabase/migrations/20261018060000_route_bundle_multi_stop_matrix_phase2.sql','utf8'));
  await db.exec(fs.readFileSync('supabase/migrations/20261018063000_route_bundle_stack_plan_readonly.sql','utf8'));
  await db.exec(fs.readFileSync('supabase/migrations/20261018074500_route_bundle_stack_financial_snapshot.sql','utf8'));
  await db.exec(fs.readFileSync('supabase/migrations/20261018083000_route_bundle_stack_quote_claim_staged.sql','utf8'));

  const rider=id(11),bundle=id(80),A=id(70),C=id(71),D=id(72),rev='2026-10-18T00:00:00Z';
  for(const [order,num,fee] of [[A,'A',50],[C,'C',30]]){
   await db.query("insert into orders(id,order_number,rider_id,customer_id,shop_id,status,updated_at,delivery_fee,rider_delivery_payout,merchant_ready_estimate_at,total_amount,route_bundle_id,route_bundle_sequence) values($1,$2,$3,$4,$5,'ready',$6,$7,$7,$6,100+$7,$8,$9)",[order,'QT-'+num,rider,id(60),id(50),rev,fee,bundle,order===A?1:2]);
   await db.query("insert into payments(order_id,status,amount,updated_at) values($1,'pending',$2,$3)",[order,100+fee,rev]);
   await db.query("insert into deliveries(order_id,rider_id,status,delivery_fee,updated_at) values($1,$2,'assigned',$3,$4)",[order,rider,fee,rev]);
  }
  await db.query("insert into orders(id,order_number,customer_id,shop_id,status,updated_at,delivery_fee,merchant_ready_estimate_at,total_amount) values($1,'QT-D',$2,$3,'searching_rider',$4,40,$4,140)",[D,id(60),id(50),rev]);
  await db.query("insert into payments(order_id,status,amount,updated_at) values($1,'pending',140,$2)",[D,rev]);
  await db.query("insert into deliveries(order_id,status,delivery_fee,updated_at) values($1,'pending',40,$2)",[D,rev]);
  const oldPlan=[{order_id:A,kind:'pickup'},{order_id:C,kind:'pickup'},{order_id:A,kind:'dropoff'},{order_id:C,kind:'dropoff'}];
  await db.query("insert into route_bundles(id,rider_id,primary_order_id,status,stop_plan,route_revision) values($1,$2,$3,'ACTIVE',$4::jsonb,7)",[bundle,rider,A,JSON.stringify(oldPlan)]);

  const orderIds=[A,C,D],pointMap={R:{kind:'rider'}};
  orderIds.forEach((o,i)=>{pointMap['O'+i+'P']={orderId:o,kind:'pickup'};pointMap['O'+i+'D']={orderId:o,kind:'dropoff'}});
  const keys=Object.keys(pointMap),edges=Object.fromEntries(keys.flatMap(a=>keys.filter(b=>a!==b).map(b=>[a+'|'+b,{km:.5,minutes:1}])));
  await db.query("select set_config('test.role','service_role',false)");
  eq((await db.query("select queuego_cache_route_bundle_stack_matrix($1,$2,$3::uuid[],$4::jsonb,$5::jsonb,15,103,$6::jsonb) ok",[A,D,orderIds,JSON.stringify(Object.fromEntries(orderIds.map(o=>[o,rev]))),JSON.stringify(pointMap),JSON.stringify(edges)])).rows[0].ok,true);
  const snapshot=(await db.query("select queuego_route_bundle_stack_financial_input($1,$2) v",[A,D])).rows[0].v;
  eq(snapshot.riderId,rider);eq(snapshot.orderRevisions[D],rev);

  const stopPlan=[{order_id:A,kind:'pickup'},{order_id:C,kind:'pickup'},{order_id:D,kind:'pickup'},{order_id:A,kind:'dropoff'},{order_id:C,kind:'dropoff'},{order_id:D,kind:'dropoff'}];
  const financial={
   policyVersion:'stack-savings-group-weighted-v1',eligible:true,reason:null,
   availableSavingSatang:2700,existingGroupSavingSatang:1350,candidateSavingSatang:1350,totalCustomerSavingSatang:2700,
   proposedCollectionsSatang:9300,riderPayoutTotalSatang:9300,fundingDeltaSatang:0,fundingGapSatang:0,platformMarginSatang:0,
   allocations:[
    {orderId:A,role:'existing',currentFeeSatang:5000,customerSavingSatang:844,proposedFeeSatang:4156,riderPayoutSatang:5000},
    {orderId:C,role:'existing',currentFeeSatang:3000,customerSavingSatang:506,proposedFeeSatang:2494,riderPayoutSatang:3000},
    {orderId:D,role:'candidate',currentFeeSatang:4000,customerSavingSatang:1350,proposedFeeSatang:2650,riderPayoutSatang:1300}
   ],
   route:{detourMeters:1000,observedDelaySeconds:240,addedMinutes:4,incrementalCompensationSatang:1300,
    pickupWaitSeconds:{[A]:0,[C]:0,[D]:0},fullKeys:['O0P','O1P','O2P','O0D','O1D','O2D']}
  };
  const quote=(await db.query("select queuego_cache_route_bundle_stack_quote($1,$2,$3::jsonb,$4::jsonb) id",[A,D,JSON.stringify(stopPlan),JSON.stringify(financial)])).rows[0].id;
  assert(quote);checks++;

  const underpay=structuredClone(financial);
  underpay.route.incrementalCompensationSatang=1200;
  underpay.allocations=underpay.allocations.map(row=>row.orderId===D?{...row,riderPayoutSatang:1200}:row);
  underpay.riderPayoutTotalSatang=9200;
  underpay.fundingDeltaSatang=-100;
  underpay.platformMarginSatang=100;
  eq((await db.query("select queuego_cache_route_bundle_stack_quote($1,$2,$3::jsonb,$4::jsonb) id",
    [A,D,JSON.stringify(stopPlan),JSON.stringify(underpay)])).rows[0].id,null);

  // Route/money binding: a valid membership plan with a different stop order
  // must not be cached against the financial route that was actually evaluated.
  const mismatched=[...stopPlan].reverse();
  eq((await db.query("select queuego_cache_route_bundle_stack_quote($1,$2,$3::jsonb,$4::jsonb) id",
    [A,D,JSON.stringify(mismatched),JSON.stringify(financial)])).rows[0].id,null);

  // Competing candidate quote at the same bundle revision. One winner must make
  // the other stale once route_revision advances.
  const E=id(73),orderIdsE=[A,C,E];
  await db.query("insert into orders(id,order_number,customer_id,shop_id,status,updated_at,delivery_fee,merchant_ready_estimate_at,total_amount) values($1,'QT-E',$2,$3,'searching_rider',$4,40,$4,140)",[E,id(60),id(50),rev]);
  const pointMapE={R:{kind:'rider'}};
  orderIdsE.forEach((o,i)=>{pointMapE['O'+i+'P']={orderId:o,kind:'pickup'};pointMapE['O'+i+'D']={orderId:o,kind:'dropoff'}});
  const keysE=Object.keys(pointMapE),edgesE=Object.fromEntries(keysE.flatMap(a=>keysE.filter(b=>a!==b).map(b=>[a+'|'+b,{km:.5,minutes:1}])));
  eq((await db.query("select queuego_cache_route_bundle_stack_matrix($1,$2,$3::uuid[],$4::jsonb,$5::jsonb,15,103,$6::jsonb) ok",
    [A,E,orderIdsE,JSON.stringify(Object.fromEntries(orderIdsE.map(o=>[o,rev]))),JSON.stringify(pointMapE),JSON.stringify(edgesE)])).rows[0].ok,true);
  const stopPlanE=[{order_id:A,kind:'pickup'},{order_id:C,kind:'pickup'},{order_id:E,kind:'pickup'},{order_id:A,kind:'dropoff'},{order_id:C,kind:'dropoff'},{order_id:E,kind:'dropoff'}];
  const financialE=structuredClone(financial);
  financialE.allocations=financial.allocations.map(row=>row.orderId===D?{...row,orderId:E}:row);
  financialE.route={...financial.route,pickupWaitSeconds:{[A]:0,[C]:0,[E]:0},fullKeys:['O0P','O1P','O2P','O0D','O1D','O2D']};
  const quoteE=(await db.query("select queuego_cache_route_bundle_stack_quote($1,$2,$3::jsonb,$4::jsonb) id",
    [A,E,JSON.stringify(stopPlanE),JSON.stringify(financialE)])).rows[0].id;
  assert(quoteE);checks++;

  await db.query("select set_config('test.uid',$1,false)",[id(101)]);
  await db.query("select set_config('test.role','authenticated',false)");
  await assert.rejects(db.query("select queuego_claim_route_bundle_stack($1)",[quote]),/route bundle order limit reached/);checks++;

  await db.exec("begin");
  await db.query("update orders set updated_at='2026-10-18T00:00:01Z' where id=$1",[D]);
  await db.query("insert into queuego_platform_rules(rule_key,value) values('route_bundle.max_orders','3'::jsonb) on conflict(rule_key) do update set value=excluded.value");
  await assert.rejects(db.query("select queuego_claim_route_bundle_stack($1)",[quote]),/stack road evidence expired|stack order revision changed/);checks++;
  await db.exec("rollback");

  await db.query("insert into queuego_platform_rules(rule_key,value) values('route_bundle.max_orders','3'::jsonb) on conflict(rule_key) do update set value=excluded.value");
  const claimed=(await db.query("select queuego_claim_route_bundle_stack($1) v",[quote])).rows[0].v;
  eq(claimed.status,'rider_assigned');eq(claimed.orders,3);eq(claimed.bundle_revision,8);

  const rows=(await db.query("select id,delivery_fee,rider_delivery_payout,status,route_bundle_id,route_bundle_sequence,total_amount from orders where id=any($1::uuid[]) order by id",[orderIds])).rows;
  const by=Object.fromEntries(rows.map(r=>[r.id,r]));
  eq(Number(by[A].delivery_fee),41.56);eq(Number(by[A].rider_delivery_payout),50);
  eq(Number(by[C].delivery_fee),24.94);eq(Number(by[C].rider_delivery_payout),30);
  eq(Number(by[D].delivery_fee),26.5);eq(Number(by[D].rider_delivery_payout),13);eq(by[D].status,'rider_assigned');eq(by[D].route_bundle_id,bundle);
  eq(Number(by[A].delivery_fee)+Number(by[C].delivery_fee)+Number(by[D].delivery_fee),93);
  eq(Number(by[A].rider_delivery_payout)+Number(by[C].rider_delivery_payout)+Number(by[D].rider_delivery_payout),93);
  const rb=(await db.query("select route_revision,stop_plan from route_bundles where id=$1",[bundle])).rows[0];
  eq(Number(rb.route_revision),8);eq(rb.stop_plan,stopPlan);
  const q=(await db.query("select claimed_at from qg_private.route_bundle_stack_quotes where id=$1",[quote])).rows[0];assert(q.claimed_at);checks++;
  await assert.rejects(db.query("select queuego_claim_route_bundle_stack($1)",[quote]),/stack quote unavailable/);checks++;
  await assert.rejects(
    db.query("select queuego_claim_route_bundle_stack($1)",[quoteE]),
    /bundle revision changed|stack active membership changed/
  );checks++;

  const sql=fs.readFileSync('supabase/migrations/20261018083000_route_bundle_stack_quote_claim_staged.sql','utf8');
  assert(sql.includes("pg_advisory_xact_lock")&&sql.includes("for update")&&sql.includes("v_count>v_max"));checks++;
  assert(sql.includes("revoke all on function public.queuego_cache_route_bundle_stack_quote")&&sql.includes("to service_role"));checks++;
  // migration anti-duplication structural guard
  eq((sql.match(/create or replace function public\.queuego_cache_route_bundle_stack_quote\(/gi)||[]).length,1);
  eq((sql.match(/create or replace function public\.queuego_claim_route_bundle_stack\(p_quote_id uuid\)/gi)||[]).length,1);
  eq((sql.match(/Safety: this migration stages 3-5 quote\/claim machinery only/g)||[]).length,1);
 }finally{await db.close();}
 console.log(JSON.stringify({checks,failures:0,scope:'staged 3-order quote cache + atomic Rider claim, exact revision/rule/membership locks, funding conservation, default max_orders=2 blocks activation'}));
})().catch(e=>{console.error(e);process.exit(1)});
