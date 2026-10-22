const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;
 await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
 const migration=fs.readdirSync('supabase/migrations').find(n=>n.endsWith('_rider_continuous_dispatch_phase1.sql'));
 await db.exec(fs.readFileSync('supabase/migrations/'+migration,'utf8'));
 const stopMigration=fs.readdirSync('supabase/migrations').find(n=>n.endsWith('_rider_dynamic_stop_plan_phase1.sql'));await db.exec(fs.readFileSync('supabase/migrations/'+stopMigration,'utf8'));
 const query=(s,p)=>db.query(s,p),as=async n=>query("select set_config('test.uid',$1,false)",[id(n)]);
 const row=async n=>(await query('select * from orders where id=$1',[id(n)])).rows[0];
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const reject=async(s,p,re)=>{await assert.rejects(query(s,p),re);checks++};
 async function seed(n,status='searching_rider',rider=null){await query(`insert into orders(id,order_number,status,rider_id,customer_id,shop_id,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,$2,$3,$4,$5,$6,15,103,15.002,103)`,[id(n),'QT-'+n,status,rider&&id(rider),id(60),id(50)]);await query('insert into deliveries(order_id,rider_id,status,delivery_fee) values($1,$2,$3,25)',[id(n),rider&&id(rider),rider?'assigned':'pending'])}
 async function cacheRoute(a,b,link=0.3){
  const primary=await row(a),candidate=await row(b),rider=(await query('select * from rider_profiles where id=$1',[primary.rider_id])).rows[0];
  const points={AP:[primary.pickup_latitude,primary.pickup_longitude],AD:[primary.delivery_latitude,primary.delivery_longitude],BP:[candidate.pickup_latitude,candidate.pickup_longitude],BD:[candidate.delivery_latitude,candidate.delivery_longitude],RP:[rider.latitude,rider.longitude]};
  const edges=Object.fromEntries(Object.entries(points).flatMap(([a,p])=>Object.entries(points).filter(([b])=>a!==b).map(([b,q])=>{const km=Math.hypot(p[0]-q[0],p[1]-q[1])*111;return [a+'|'+b,{km,minutes:km/25*60}]})));
  edges['AD|BP']={km:link,minutes:link*2.4};
  return (await query('select queuego_cache_route_bundle_matrix($1,$2,$3,$4,$5,$6,$7::jsonb) ok',[id(a),id(b),primary.updated_at,candidate.updated_at,rider.latitude,rider.longitude,JSON.stringify(edges)])).rows[0].ok;
 }
 await as(101);await query('select qg_rider_dispatch_preference(true,false,true)');
 await seed(20);eq((await query('select qg_dispatch_rider_offers() n')).rows[0].n,1);eq((await row(20)).rider_id,null); // rollout OFF preserves manual lease
 await query('select rider_claim_order($1)',[id(20)]);eq((await row(20)).rider_id,id(11));
 await query('delete from qg_rider_order_offers');await query("update orders set status='in_progress' where id=$1",[id(20)]);
 await query("insert into queuego_platform_rules values('dispatch.continuous_enabled','1',now())");
 // Only one Rider available for deterministic continuation tests.
 await query("update rider_profiles set metadata='{}' where id=$1",[id(12)]);
 await seed(21);await query('select qg_dispatch_rider_offers()');eq((await row(21)).rider_id,null);eq(await cacheRoute(20,21),true);
 await query("update qg_private.route_bundle_road_matrices set expires_at=now()-interval '1 second'");await query('select qg_dispatch_rider_offers()');eq((await row(21)).rider_id,null);
 await cacheRoute(20,21);await query("update orders set updated_at=now()+interval '1 second' where id=$1",[id(21)]);await query('select qg_dispatch_rider_offers()');eq((await row(21)).rider_id,null);
 await cacheRoute(20,21);await query('select qg_dispatch_rider_offers()');eq((await row(21)).next_after_order_id,id(20));eq((await row(21)).rider_id,id(11));eq((await row(20)).status,'in_progress');
 eq((await query('select count(*)::int n from qg_rider_offer_history where order_id=$1 and outcome=$2',[id(21),'accepted'])).rows[0].n,1);
 await reject("update orders set status='picked_up' where id=$1",[id(21)],/finish current delivery/);
 await reject("update orders set rider_arrived_shop_at=now() where id=$1",[id(21)],/finish current delivery/);
 await query("update orders set status='preparing' where id=$1",[id(21)]);eq((await row(21)).status,'preparing');
 await seed(22);await cacheRoute(21,22);await query('select qg_dispatch_rider_offers()');eq((await row(22)).rider_id,null); // third order denied
 await query("update orders set status='completed' where id=$1",[id(20)]);eq((await row(21)).next_after_order_id,null);
 await query("update orders set status='in_progress' where id=$1",[id(21)]);
 await query('select qg_rider_dispatch_preference(true,true,true)');await query('select qg_dispatch_rider_offers()');eq((await row(22)).rider_id,null); // stop wins
 await query('select qg_rider_dispatch_preference(true,false,false)');await query('select qg_dispatch_rider_offers()');eq((await row(22)).rider_id,null); // unsafe/stale lease
 await query('select qg_rider_dispatch_preference(false,false,true)');await query('select qg_dispatch_rider_offers()');eq((await row(22)).rider_id,null); // manual active Rider has no implicit assignment
 await query('select qg_rider_dispatch_preference(true,false,true)');
 await query("insert into laundry_rider_jobs values($1,'assigned')",[id(11)]);await query('select qg_dispatch_rider_offers()');eq((await row(22)).rider_id,null);await query('delete from laundry_rider_jobs');
 await query("update orders set pickup_latitude=16 where id=$1",[id(22)]);await query('select qg_dispatch_rider_offers()');eq((await row(22)).rider_id,null);
 await query("update orders set pickup_latitude=15 where id=$1",[id(22)]);await cacheRoute(21,22);await query('select qg_dispatch_rider_offers()');eq((await row(22)).next_after_order_id,id(21));
 await query('select qg_dispatch_rider_offers()');eq((await query('select count(*)::int n from audit_logs where entity_id=$1',[id(22)])).rows[0].n,1); // duplicate retry no assignment side effects
 await query("update orders set status='cancelled' where id=$1",[id(21)]);eq((await row(22)).next_after_order_id,null);
 await query("update orders set status='completed' where id=$1",[id(22)]);
 // Quote tests execute the NEW Production function body, not a test reimplementation.
 await seed(30,'ready',11);await seed(31);
 let q=(await query('select queuego_route_bundle_quote_orders($1,$2,now()) q',[id(30),id(31)])).rows[0].q;
 eq(q.eligible,true);eq(q.min_rider_extra_fee,0);eq(q.road_eta_verified,false);
 assert(q.primary_bundled_delivery_fee<25&&q.bundled_delivery_fee<25);checks++;
 eq(q.primary_bundled_delivery_fee+q.bundled_delivery_fee,q.rider_route_payout);
 assert(q.rider_extra_fee===q.incremental_distance_cost+q.incremental_pickup_cost+q.incremental_dropoff_cost+q.incremental_time_cost);checks++;
 await reject('select queuego_claim_route_bundle($1)',[id(31)],/fresh road route/);eq(await cacheRoute(30,31),true);q=(await query('select queuego_route_bundle_quote_orders($1,$2,now()) q',[id(30),id(31)])).rows[0].q;eq(q.road_eta_verified,true);await query('select queuego_claim_route_bundle($1)',[id(31)]);eq(Number((await row(30)).rider_delivery_payout),25);eq(Number((await row(31)).rider_delivery_payout),q.rider_extra_fee);
 const routeRows=(await query('select * from queuego_route_bundle_active_orders()')).rows;const planA=JSON.parse(routeRows.find(r=>r.order_id===id(30)).route_sequence);eq(planA.kind,'queuego_stop_plan_v1');eq(planA.pickup_rank,1);eq(planA.dropoff_rank,3);
 eq(Number((await row(30)).subtotal),100);eq(Number((await row(31)).subtotal),100);
 eq(Number((await row(30)).total_amount),100+q.primary_bundled_delivery_fee);eq(Number((await row(31)).total_amount),100+q.bundled_delivery_fee);
 await reject('select queuego_claim_route_bundle($1)',[id(31)],/no longer available/);
 await query("insert into queuego_platform_rules values('route_bundle.max_orders','5',now())");await seed(32);
 await reject('select queuego_claim_route_bundle($1)',[id(32)],/order limit/);eq((await row(32)).rider_id,null);
 await query('insert into rider_cash_advances select id,rider_id,shop_id,subtotal,now() from orders where id in ($1,$2)',[id(30),id(31)]);
 await query('insert into merchant_cash_receipts select id,shop_id,subtotal,now() from orders where id in ($1,$2)',[id(30),id(31)]);
 await query("insert into payments(order_id,status,amount,paid_at) select id,'paid',total_amount,now() from orders where id in ($1,$2)",[id(30),id(31)]);
 // The ledger groups by Bangkok business date. Use that same date in the test
 // range so a UTC runner after 17:00 does not query yesterday's empty ledger.
 const businessDate=(await query("select (now() at time zone 'Asia/Bangkok')::date day")).rows[0].day;
 const ledger=(await query('select qg_rider_cash_ledger($1::date,$1::date) rows',[businessDate])).rows[0].rows;
 const aCash=ledger.find(r=>r.order_id===id(30)),bCash=ledger.find(r=>r.order_id===id(31));
 eq(aCash.cash_paid_merchant,100);eq(bCash.cash_paid_merchant,100);eq(aCash.delivery_fee,25);eq(bCash.delivery_fee,q.rider_extra_fee);
 eq(aCash.cash_collected_customer,100+q.primary_bundled_delivery_fee);eq(bCash.cash_collected_customer,100+q.bundled_delivery_fee);
 eq(aCash.delivery_fee+bCash.delivery_fee,aCash.customer_delivery_fee+bCash.customer_delivery_fee);

 await query("update orders set status='ready' where id=$1",[id(31)]);
 await reject("update orders set rider_arrived_shop_at=now() where id=$1",[id(31)],/authoritative pickup/);
 await reject("update orders set status='picked_up' where id=$1",[id(31)],/authoritative pickup/);
 await query("update orders set status='picked_up' where id=$1",[id(30)]);
 await reject("update orders set status='in_progress' where id=$1",[id(30)],/authoritative delivery/);
 await query("update orders set status='picked_up' where id=$1",[id(31)]);
 await query("update orders set status='in_progress' where id=$1",[id(30)]);await query("update orders set status='completed' where id=$1",[id(30)]);
 await query("update orders set status='in_progress' where id=$1",[id(31)]);await query("update orders set status='completed' where id=$1",[id(31)]);
 await query('update deliveries set delivered_at=now() where order_id in ($1,$2)',[id(30),id(31)]);
 const summary=(await query('select qg_rider_period_summary(1) s')).rows[0].s;eq(summary.jobs,2);eq(summary.income,25+q.rider_extra_fee);

 await seed(40,'ready',11);await seed(41);await query('update orders set pickup_latitude=null where id=$1',[id(41)]);
 q=(await query('select queuego_route_bundle_quote_orders($1,$2,now()) q',[id(40),id(41)])).rows[0].q;eq(q.eligible,false);
 await query('update orders set pickup_latitude=15,delivery_fee=1 where id=$1',[id(41)]);
 q=(await query('select queuego_route_bundle_quote_orders($1,$2,now()) q',[id(40),id(41)])).rows[0].q;eq(q.eligible,false);assert(q.rider_extra_fee>1);checks++; // fair cost NEVER capped to the fee to manufacture eligibility
 // Exposed RPCs have explicit ownership and no anonymous execution.
 await as(999);await reject('select qg_rider_dispatch_preference(true,false,true)',[],/active rider/);
 eq((await query("select has_function_privilege('anon','public.qg_rider_dispatch_preference(boolean,boolean,boolean)','execute') b")).rows[0].b,false);
 eq((await query("select has_function_privilege('authenticated','qg_private.claim_rider_order(uuid,uuid)','execute') b")).rows[0].b,false);
 await query('grant select,update on orders to authenticated');await query('set role authenticated');await reject('update orders set rider_delivery_payout=1 where id=$1',[id(40)],/dispatch snapshots require/);await query('reset role');
 // The same quote simulator supports 1/2/3/5-order architecture without enabling >2 in dispatch.
 async function simulate(stops,positions,{locked=null,pickupsFirst=false,edges=null}={}){
  const matrix=edges??Object.fromEntries(['START',...stops.map(s=>s.key)].flatMap(a=>stops.filter(b=>b.key!==a).map(b=>[a+'|'+b.key,{km:Math.abs((positions[a]??0)-positions[b.key])+0.001,minutes:Math.abs((positions[a]??0)-positions[b.key])+0.001}])));
  return (await query('select qg_private.route_bundle_simulate($1::jsonb,$2::jsonb,$3,$4,$5) q',[JSON.stringify(stops),JSON.stringify(matrix),'START',locked,pickupsFirst])).rows[0].q;
 }
 const makeStops=n=>Array.from({length:n},(_,i)=>[{key:`${i}:P`,order_id:String(i),kind:'pickup'},{key:`${i}:D`,order_id:String(i),kind:'dropoff'}]).flat();
 for(const n of [1,2,3,5]){
  const stops=makeStops(n),positions=Object.fromEntries(stops.map((s,i)=>[s.key,i]));
  const route=await simulate(stops,positions,{locked:'0:P'});eq(route.eligible,true);eq(route.orders,n);eq(route.stop_keys.length,n*2);
  for(let k=0;k<n;k++){assert(route.stop_keys.indexOf(`${k}:P`)<route.stop_keys.indexOf(`${k}:D`));checks++;}
 }
 const stops=makeStops(3),positions={'0:P':0,'1:P':1,'0:D':2,'2:P':3,'1:D':4,'2:D':5};
 let route=await simulate(stops,positions,{locked:'0:P'});eq(route.stop_keys,['0:P','1:P','0:D','2:P','1:D','2:D']);
 route=await simulate(stops,positions,{locked:'1:P'});eq(route.stop_keys[0],'1:P'); // committed NEXT never reordered
 eq((await simulate(stops,positions,{locked:'0:D'})).eligible,false); // no drop before pickup
 eq((await simulate(makeStops(6),{})).eligible,false); // architecture hard cap5
 eq((await simulate(stops,positions,{edges:{}})).eligible,false); // no fake distance/ETA fallback
 const cancelled=stops.filter(s=>s.order_id!=='1');eq((await simulate(cancelled,positions)).stop_keys.length,4);
 const late=stops.map(s=>s.key==='0:D'?{...s,deadline_minutes:0.1}:s);eq((await simulate(late,positions)).eligible,false);
 const delayed=stops.map(s=>s.key==='0:D'?{...s,baseline_minutes:0,max_delay_minutes:0.1}:s);eq((await simulate(delayed,positions)).eligible,false);
 const cold=stops.map(s=>s.key==='0:D'?{...s,max_ride_minutes:0.1}:s);eq((await simulate(cold,positions)).eligible,false);
 const carried=[{key:'0:D',order_id:'0',kind:'dropoff',picked_up:true,already_carried_minutes:9,max_ride_minutes:10}];
 eq((await simulate(carried,{'0:D':2})).eligible,false);
 const slow=stops.map(s=>s.key==='0:P'?{...s,ready_minutes:25}:s.key==='0:D'?{...s,deadline_minutes:10}:s);eq((await simulate(slow,positions)).eligible,false);
 const missingPickup=[{key:'0:D',order_id:'0',kind:'dropoff'}];eq((await simulate(missingPickup,{'0:D':1})).eligible,false);
 eq((await simulate([{key:'0:P',order_id:'0',kind:'pickup'}],{'0:P':0})).eligible,false);
 const duplicate=[...makeStops(1),makeStops(1)[0]];eq((await simulate(duplicate,{'0:P':0,'0:D':1})).eligible,false);

 // Three-order dynamic transition architecture, isolated owner fixture only; dispatch cap2 was tested above.
 await seed(50,'ready',11);await seed(51,'ready',11);await seed(52,'ready',11);
 await query("insert into route_bundles(id,rider_id,primary_order_id,status) values($1,$2,$3,'ACTIVE')",[id(500),id(11),id(50)]);
 await query('update orders set route_bundle_id=$1 where id in ($2,$3,$4)',[id(500),id(50),id(51),id(52)]);
 const dynamicPlan=[[50,'pickup'],[51,'pickup'],[50,'dropoff'],[52,'pickup'],[51,'dropoff'],[52,'dropoff']].map(([n,kind])=>({order_id:id(n),kind}));
 await query('update route_bundles set stop_plan=$1::jsonb,route_revision=1 where id=$2',[JSON.stringify(dynamicPlan),id(500)]);
 await reject('update route_bundles set stop_plan=$1::jsonb,route_revision=2 where id=$2',[JSON.stringify([dynamicPlan[1],dynamicPlan[0],...dynamicPlan.slice(2)]),id(500)],/committed stops/);
 await reject("update route_bundles set stop_plan='[]'::jsonb,route_revision=2 where id=$1",[id(500)],/clear committed/);
 await reject('update route_bundles set stop_plan=$1::jsonb,route_revision=2 where id=$2',[JSON.stringify(dynamicPlan.filter(p=>p.order_id!==id(52))),id(500)],/preserve every separate order/);
 await query("update orders set status='picked_up' where id=$1",[id(50)]);await query("update orders set status='picked_up' where id=$1",[id(51)]);
 await reject("update orders set status='picked_up' where id=$1",[id(52)],/authoritative pickup/);
 await query("update orders set status='in_progress' where id=$1",[id(50)]);await query("update orders set status='completed' where id=$1",[id(50)]);
 await query("update orders set status='picked_up' where id=$1",[id(52)]);
 await query("update orders set status='in_progress' where id=$1",[id(51)]);await query("update orders set status='completed' where id=$1",[id(51)]);
 await query("update orders set status='in_progress' where id=$1",[id(52)]);await query("update orders set status='completed' where id=$1",[id(52)]);
 eq((await row(50)).status,'completed');eq((await row(51)).status,'completed');eq((await row(52)).status,'completed');
 console.log(JSON.stringify({checks,failures:0,scope:'isolated PostgreSQL phase1: next assignment/promotion, rollout, stop/readiness/conflict, cash and payout. Independent connection races, road ETA and physical recovery are NOT certified.'}));await db.close();
})().catch(e=>{console.error(e);process.exit(1)});
