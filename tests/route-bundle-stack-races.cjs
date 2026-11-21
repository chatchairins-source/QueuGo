const assert=require('node:assert/strict'),fs=require('node:fs'),{Client,types}=require('pg');
types.setTypeParser(1184,value=>value);
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const raw=process.env.QG_TEST_PG_URL;
 if(!raw)throw Error('QG_TEST_PG_URL must point to an isolated loopback test database');
 const url=new URL(raw);
 if(!['localhost','127.0.0.1','[::1]'].includes(url.hostname)||url.pathname!=='/queuego_bundle_gate')throw Error('refusing any non-local or non-test database');
 const clients=[];let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++;};
 const connect=async()=>{const c=new Client({connectionString:raw,statement_timeout:8000});await c.connect();clients.push(c);await c.query("select set_config('test.uid',$1,false),set_config('test.role','authenticated',false)",[id(101)]);return c;};
 try{
  const admin=await connect(),a=await connect(),b=await connect();
  const version=(await admin.query('show server_version_num')).rows[0].server_version_num;assert(Number(version)>=150000);checks++;
  if((await admin.query("select to_regclass('public.orders') v")).rows[0].v)throw Error('test requires a fresh empty database');
  await admin.query(require('./fixtures/rider-dispatch-schema.cjs')());
  await admin.query("create function auth.role() returns text language sql as 'select coalesce(nullif(current_setting(''test.role'',true),''''),''authenticated'')'");
  for(const name of ['20261018060000_route_bundle_multi_stop_matrix_phase2.sql','20261018063000_route_bundle_stack_plan_readonly.sql','20261018074500_route_bundle_stack_financial_snapshot.sql','20261018083000_route_bundle_stack_quote_claim_staged.sql'])await admin.query(fs.readFileSync('supabase/migrations/'+name,'utf8'));
  await admin.query("update shop_profiles set public_category='grocery'");
  const {planStackRoute}=await import('../supabase/functions/queuego-route-bundle-matrix/stack-planner.mjs');
  const {simulateStackFinancialPlan}=await import('../supabase/functions/queuego-route-bundle-matrix/stack-financial-plan.mjs');
  async function seed(count,round){
   await admin.query("update orders set status='completed' where rider_id is not null");
   await admin.query('delete from qg_rider_order_offers');
   await admin.query("insert into queuego_platform_rules(rule_key,value) values('route_bundle.max_orders',$1::jsonb) on conflict(rule_key) do update set value=excluded.value",[String(count)]);
   const bundle=id(1000+round),existing=Array.from({length:count-1},(_,i)=>id(10000+round*10+i));
   const candidates=[id(10000+round*10+7),id(10000+round*10+8)];
   for(const o of [...existing,...candidates]){
    const assigned=existing.includes(o);
    await admin.query("insert into orders(id,order_number,rider_id,customer_id,shop_id,status,delivery_fee,rider_delivery_payout,merchant_ready_estimate_at,total_amount,route_bundle_id,route_bundle_sequence) values($1,$2,$3,$4,$5,$6,50,$7,now(),150,$8,$9)",[o,'QT-'+o,id(11),id(60),id(50),assigned?'ready':'searching_rider',assigned?50:null,assigned?bundle:null,assigned?existing.indexOf(o)+1:null].map((v,i)=>i===2&&!assigned?null:v));
    await admin.query("insert into payments(order_id,status,amount) values($1,'pending',150)",[o]);
    await admin.query("insert into deliveries(order_id,rider_id,status,delivery_fee) values($1,$2,$3,50)",[o,assigned?id(11):null,assigned?'assigned':'pending']);
   }
   const oldPlan=['pickup','dropoff'].flatMap(kind=>existing.map(order_id=>({order_id,kind})));
   await admin.query("insert into route_bundles(id,rider_id,primary_order_id,status,stop_plan,route_revision) values($1,$2,$3,'ACTIVE',$4::jsonb,7)",[bundle,id(11),existing[0],JSON.stringify(oldPlan)]);
   await admin.query("select set_config('test.role','service_role',false)");
   const quotes=[];
   for(const candidate of candidates){
    const ids=[...existing,candidate],pointMap={R:{kind:'rider'}};
    ids.forEach((o,i)=>{pointMap['O'+i+'P']={orderId:o,kind:'pickup'};pointMap['O'+i+'D']={orderId:o,kind:'dropoff'};});
    const keys=Object.keys(pointMap),edges=Object.fromEntries(keys.flatMap(x=>keys.filter(y=>x!==y).map(y=>[x+'|'+y,{km:.1,minutes:.1}])));
    const revisions=Object.fromEntries((await admin.query('select id,updated_at from orders where id=any($1::uuid[])',[ids])).rows.map(o=>[o.id,o.updated_at]));
    eq((await admin.query('select queuego_cache_route_bundle_stack_matrix($1,$2,$3::uuid[],$4::jsonb,$5::jsonb,15,103,$6::jsonb) v',[existing[0],candidate,ids,JSON.stringify(revisions),JSON.stringify(pointMap),JSON.stringify(edges)])).rows[0].v,true);
    const evidence=(await admin.query('select queuego_route_bundle_stack_financial_input($1,$2) v',[existing[0],candidate])).rows[0].v;
    const plan=planStackRoute(evidence),financial=simulateStackFinancialPlan(evidence,plan);eq(financial.eligible,true);
    const stops=[...evidence.committedPrefix,...plan.stops.map(s=>({order_id:pointMap[s.key].orderId,kind:pointMap[s.key].kind}))];
    const quote=(await admin.query('select queuego_cache_route_bundle_stack_quote($1,$2,$3::jsonb,$4::jsonb) v',[existing[0],candidate,JSON.stringify(stops),JSON.stringify(financial)])).rows[0].v;
    assert(quote);checks++;quotes.push(quote);
   }
   return {bundle,existing,candidates,quotes};
  }
  async function blocked(client){
   for(let i=0;i<150;i++){
    if((await admin.query('select wait_event_type from pg_stat_activity where pid=$1',[client.processID])).rows[0]?.wait_event_type==='Lock'){checks++;return;}
    await new Promise(resolve=>setTimeout(resolve,10));
   }
   throw Error('did not observe intended independent-connection lock overlap');
  }
  async function invariant(s,winner){
   const rows=(await admin.query('select id,delivery_fee,rider_delivery_payout from orders where rider_id=$1 and status<>\'completed\'',[id(11)])).rows;
   eq(rows.length,s.existing.length+1);
   eq(rows.filter(o=>s.existing.includes(o.id)).map(o=>Number(o.rider_delivery_payout)),s.existing.map(()=>50));
   eq(rows.reduce((sum,o)=>sum+Math.round(Number(o.delivery_fee)*100),0),rows.reduce((sum,o)=>sum+Math.round(Number(o.rider_delivery_payout)*100),0));
   eq(Number((await admin.query('select route_revision from route_bundles where id=$1',[s.bundle])).rows[0].route_revision),8);
   eq((await admin.query("select count(*)::int n from audit_logs where action='route_bundle_stack_claim' and entity_id=$1",[s.candidates[winner]])).rows[0].n,1);
   eq((await admin.query("select count(*)::int n from qg_rider_offer_history where order_id=$1 and outcome='accepted'",[s.candidates[winner]])).rows[0].n,1);
  }
  for(const count of [3,4,5]){
   // Two service connections publish competing candidates behind the same real Rider row lock.
   const s=await seed(count,count*10);
   await Promise.all([a,b].map(c=>c.query("select set_config('test.role','service_role',false)")));
   await admin.query('begin');await admin.query('select id from rider_profiles where id=$1 for update',[id(11)]);
   const publications=[a,b].map((c,i)=>c.query('select queuego_offer_route_bundle_stack_quote($1) v',[s.quotes[i]]).then(r=>r.rows[0].v));
   await blocked(a);await blocked(b);await admin.query('commit');
   const offers=await Promise.all(publications);eq(offers.filter(Boolean).length,1);const winner=offers.indexOf(true);
   eq((await admin.query('select count(*)::int n from qg_rider_order_offers where rider_id=$1',[id(11)])).rows[0].n,1);
   await Promise.all([a,b].map(c=>c.query("select set_config('test.role','authenticated',false)")));
   // One connection has performed the whole claim but cannot commit; contender demonstrably waits.
   await a.query('begin');await a.query('select queuego_claim_route_bundle_stack($1)',[s.quotes[winner]]);
   const replay=b.query('select queuego_claim_route_bundle_stack($1)',[s.quotes[winner]]).then(()=>({ok:true}),error=>({error}));
   await blocked(b);await a.query('commit');const rejected=await replay;
   assert.match(rejected.error?.message||'',/stack quote unavailable/);checks++;
   await assert.rejects(b.query('select queuego_claim_route_bundle_stack($1)',[s.quotes[1-winner]]),/live targeted offer required|bundle revision changed|stack active membership changed/);checks++;
   await invariant(s,winner);
   // A fully executed transaction that disconnects before commit must leave no repricing/audit.
   const r=await seed(count,count*10+1);
   eq((await admin.query('select queuego_offer_route_bundle_stack_quote($1) v',[r.quotes[0]])).rows[0].v,true);
   const lost=await connect();await lost.query('begin');await lost.query('select queuego_claim_route_bundle_stack($1)',[r.quotes[0]]);
   eq(Number((await admin.query('select route_revision from route_bundles where id=$1',[r.bundle])).rows[0].route_revision),7);
   await lost.end();clients.splice(clients.indexOf(lost),1);
   await b.query('select queuego_claim_route_bundle_stack($1)',[r.quotes[0]]);await invariant(r,0);
  }
  console.log(JSON.stringify({checks,failures:0,postgresVersion:version,scope:'3/4/5 orders: forced independent PostgreSQL offer contention, claim/replay lock overlap, stale competing quote rejection, exact satang conservation, locked payouts, precommit disconnect rollback'}));
 }finally{await Promise.allSettled(clients.map(async c=>{await c.query('rollback').catch(()=>{});await c.end();}));}
})().catch(e=>{console.error(e);process.exit(1)});
