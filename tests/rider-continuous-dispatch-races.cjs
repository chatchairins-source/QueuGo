const assert=require('node:assert/strict'),fs=require('node:fs'),{Client,types}=require('pg');
// Preserve PostgreSQL microseconds exactly, as PostgREST does; JS Date truncates revisions.
types.setTypeParser(1184,value=>value);
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const raw=process.env.QG_TEST_PG_URL;if(!raw)throw Error('QG_TEST_PG_URL must point to an isolated loopback test database');
 const url=new URL(raw);
 if(!['localhost','127.0.0.1','[::1]'].includes(url.hostname)||url.pathname!=='/queuego_dispatch_gate')throw Error('refusing any non-local or non-test database');
 const clients=[];const connect=async actor=>{const c=new Client({connectionString:raw,statement_timeout:8000});await c.connect();clients.push(c);if(actor)await c.query("select set_config('test.uid',$1,false)",[id(actor)]);return c;};
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++;};
 try{
  const admin=await connect(),a=await connect(101),b=await connect(101),other=await connect(102);
  const version=(await admin.query('show server_version_num')).rows[0].server_version_num;assert(Number(version)>=150000);checks++;
  if((await admin.query("select to_regclass('public.orders') existing")).rows[0].existing)throw Error('test requires a fresh empty database');
  await admin.query(require('./fixtures/rider-dispatch-schema.cjs')());
  for(const suffix of ['_rider_continuous_dispatch_phase1.sql','_rider_dynamic_stop_plan_phase1.sql']){
   const name=fs.readdirSync('supabase/migrations').find(n=>n.endsWith(suffix));await admin.query(fs.readFileSync('supabase/migrations/'+name,'utf8'));
  }
  const row=async n=>(await admin.query('select * from orders where id=$1',[id(n)])).rows[0];
  async function seed(n,status='searching_rider',rider=null){await admin.query("insert into orders(id,order_number,status,rider_id,customer_id,shop_id,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,$2,$3,$4,$5,$6,15,103,15.002,103)",[id(n),'QT-'+n,status,rider&&id(rider),id(60),id(50)]);await admin.query("insert into deliveries(order_id,rider_id,status,delivery_fee) values($1,$2,'pending',25)",[id(n),rider&&id(rider)]);}
  async function cache(primary,candidate){const x=await row(primary),y=await row(candidate);const edge={km:0.2,minutes:0.5};const edges={'AP|AD':edge,'BP|BD':edge,'RP|AD':edge,'AD|BP':edge};eq((await admin.query('select queuego_cache_route_bundle_matrix($1,$2,$3,$4,15,103,$5::jsonb) ok',[x.id,y.id,x.updated_at,y.updated_at,JSON.stringify(edges)])).rows[0].ok,true);}
  await seed(20);
  await Promise.all([a.query('select qg_dispatch_rider_offers()'),b.query('select qg_dispatch_rider_offers()')]);
  const offer=(await admin.query('select * from qg_rider_order_offers where order_id=$1',[id(20)])).rows;
  eq(offer.length,1);const actor=offer[0].rider_id===id(11)?101:102;
  await Promise.all([a,b].map(c=>c.query("select set_config('test.uid',$1,false)",[id(actor)])));
  const same=await Promise.allSettled([a.query('select rider_claim_order($1)',[id(20)]),b.query('select rider_claim_order($1)',[id(20)])]);
  eq(same.filter(r=>r.status==='fulfilled').length,2);
  eq((await admin.query('select count(*)::int n from audit_logs where entity_id=$1',[id(20)])).rows[0].n,1);
  await other.query("select set_config('test.uid',$1,false)",[id(actor===101?102:101)]);
  await assert.rejects(other.query('select rider_claim_order($1)',[id(20)]),/live targeted|no longer|finish current/);checks++;
  const owner=(await row(20)).rider_id;
  // Keep the deterministic actor11 for subsequent tests and finish the previous isolated fixture.
  await admin.query("update orders set status='completed' where id=$1",[id(20)]);await admin.query('delete from qg_rider_order_offers');
  await admin.query("update rider_profiles set metadata='{}' where id=$1",[id(12)]);
  await Promise.all([a,b].map(c=>c.query("select set_config('test.uid',$1,false)",[id(101)])));
  await a.query('select qg_rider_dispatch_preference(true,false,true)');await admin.query("insert into queuego_platform_rules values('dispatch.continuous_enabled','1',now())");
  await seed(30,'in_progress',11);await seed(31);await seed(32);await cache(30,31);await cache(30,32);
  await Promise.all([a.query('select qg_dispatch_rider_offers()'),b.query('select qg_dispatch_rider_offers()')]);
  eq((await admin.query('select count(*)::int n from orders where next_after_order_id=$1',[id(30)])).rows[0].n,1);
  eq((await admin.query("select count(*)::int n from orders where rider_id=$1 and status in ('in_progress','rider_assigned')",[id(11)])).rows[0].n,2);
  eq((await admin.query("select count(*)::int n from audit_logs where entity_id in ($1,$2)",[id(31),id(32)])).rows[0].n,1);
  await admin.query("update orders set status='completed' where rider_id is not null");await admin.query("update orders set status='cancelled' where rider_id is null");await admin.query('delete from qg_rider_order_offers');
  // Force completion to hold its row lock while NEXT assignment reaches the predecessor lock.
  for(let round=0;round<5;round++){
   const primary=100+round*2,candidate=primary+1;await seed(primary,'in_progress',11);await seed(candidate);await cache(primary,candidate);
   await a.query('select qg_rider_dispatch_preference(true,false,true)');
   await a.query('begin');await a.query("update orders set status='completed' where id=$1",[id(primary)]);
   const pending=b.query('select qg_dispatch_rider_offers()').then(value=>({value}),error=>({error}));
   let observed=false;
   for(let i=0;i<80;i++){
    const active=(await admin.query('select wait_event_type from pg_stat_activity where pid=$1',[b.processID])).rows[0];
    if(active?.wait_event_type==='Lock'){observed=true;break;}
    await new Promise(resolve=>setTimeout(resolve,10));
   }
   if(!observed){await a.query('rollback');throw Error('did not reach the intended real row-lock overlap');}
   checks++;
   await a.query('commit');const result=await pending;if(result.error)throw result.error;
   const promoted=await row(candidate);eq(promoted.rider_id,id(11));eq(promoted.next_after_order_id,null);eq(promoted.status,'rider_assigned');
   eq((await admin.query('select count(*)::int n from audit_logs where entity_id=$1',[id(candidate)])).rows[0].n,1);
   await admin.query("update orders set status='completed' where id=$1",[id(candidate)]);
  }
  // A connection disappearing before COMMIT must roll the assignment/audit back for a clean retry.
  await admin.query("update queuego_platform_rules set value='0' where rule_key='dispatch.continuous_enabled'");
  await a.query('select qg_rider_dispatch_preference(false,false,true)');await seed(200);await admin.query('select qg_dispatch_rider_offers()');
  await a.query('begin');await a.query('select rider_claim_order($1)',[id(200)]);eq((await row(200)).rider_id,null);
  await a.end();clients.splice(clients.indexOf(a),1);
  await b.query('select rider_claim_order($1)',[id(200)]);eq((await row(200)).rider_id,id(11));
  eq((await admin.query('select count(*)::int n from audit_logs where entity_id=$1',[id(200)])).rows[0].n,1);
  console.log(JSON.stringify({checks,failures:0,postgresVersion:version,scope:'real PostgreSQL independent connections: duplicate dispatcher/claim, one NEXT slot, five forced completion-reservation overlaps, pre-commit connection-loss rollback. No Production data.'}));
 }finally{await Promise.allSettled(clients.map(c=>c.end()));}
})().catch(e=>{console.error(e);process.exit(1)});
