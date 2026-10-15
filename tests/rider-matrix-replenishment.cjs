const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 try{
 await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
 for(const suffix of ['rider_continuous_dispatch_phase1','rider_dynamic_stop_plan_phase1','rider_route_matrix_replenishment_phase1']){
  const name=fs.readdirSync('supabase/migrations').find(n=>n.endsWith('_'+suffix+'.sql'));await db.exec(fs.readFileSync('supabase/migrations/'+name,'utf8'));
 }
 const q=(s,p)=>db.query(s,p);const claim=async (limit=2)=>(await q('select queuego_route_matrix_refresh_claim($1) work',[limit])).rows[0].work;
 await q("select set_config('test.uid',$1,false)",[id(101)]);await q('select qg_rider_dispatch_preference(true,false,true)');
 for(let n=20;n<=23;n++)await q("insert into orders(id,status,rider_id,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,$2,$3,15,103,15.002,103)",[id(n),n===20?'in_progress':'searching_rider',n===20?id(11):null]);
 eq(await claim(),{}); // Default OFF must do no work.
 await q("insert into queuego_platform_rules values('dispatch.continuous_enabled','1',now())");
 await assert.rejects(claim(3),/limit must/);checks++;
 const first=await claim();eq(first.pairs.length,2);eq(await claim(),{});
 eq((await q('select queuego_route_matrix_refresh_release($1) ok',[id(999)])).rows[0].ok,false);
 eq(await claim(),{});
 eq((await q('select queuego_route_matrix_refresh_release($1) ok',[first.leaseId])).rows[0].ok,true);
 eq((await q('select queuego_route_matrix_refresh_release($1) ok',[first.leaseId])).rows[0].ok,false);
 const second=await claim(1);eq(second.pairs.length,1);
 await q("update qg_private.route_matrix_refresh_lease set expires_at=now()-interval '1 second'");
 const restarted=await claim();assert.notEqual(restarted.leaseId,second.leaseId);checks++;
 eq((await q('select queuego_route_matrix_refresh_release($1) ok',[second.leaseId])).rows[0].ok,false);eq(await claim(),{});
 await q('select queuego_route_matrix_refresh_release($1)',[restarted.leaseId]);
 // Fresh exact-revision cache skips provider work; stale/changed orders are refreshed.
 const edges=JSON.stringify(Object.fromEntries(['AP|AD','BP|BD','RP|AD','AD|BP'].map(k=>[k,{km:0.2,minutes:1}])));
 for(let n=21;n<=23;n++)eq((await q('select queuego_cache_route_bundle_matrix(a.id,b.id,a.updated_at,b.updated_at,15,103,$1::jsonb) ok from orders a cross join orders b where a.id=$2 and b.id=$3',[edges,id(20),id(n)])).rows[0].ok,true);
 eq(await claim(),{});
 await q("update orders set updated_at=updated_at+interval '1 second' where id=$1",[id(21)]);
 const revised=await claim();eq(revised.pairs.map(p=>p.candidateOrderId),[id(21)]);await q('select queuego_route_matrix_refresh_release($1)',[revised.leaseId]);
 await q('select qg_rider_dispatch_preference(true,true,true)');eq(await claim(),{});
 await q('select qg_rider_dispatch_preference(true,false,false)');eq(await claim(),{});
 await q('select qg_rider_dispatch_preference(false,false,true)');eq(await claim(),{}); // Manual NEXT never implicitly enabled.
 await q('select qg_rider_dispatch_preference(true,false,true)');
 await q("insert into laundry_rider_jobs values($1,'accepted')",[id(11)]);eq(await claim(),{});await q('delete from laundry_rider_jobs');
 await q("insert into qg_rider_delivery_returns values($1,'pending')",[id(11)]);eq(await claim(),{});await q('delete from qg_rider_delivery_returns');
 await q('update orders set pickup_latitude=16 where id=$1',[id(21)]);eq(await claim(),{});
 eq((await q('select count(*)::int n from audit_logs')).rows[0].n,0); // Evidence work never assigned/paid.
 eq((await q("select count(*)::int n from orders where status='searching_rider' and rider_id is null")).rows[0].n,3);
 for(const role of ['anon','authenticated'])for(const signature of ['queuego_route_matrix_refresh_claim(integer)','queuego_route_matrix_refresh_release(uuid)'])
  eq((await q('select has_function_privilege($1,$2,$3) ok',[role,'public.'+signature,'execute'])).rows[0].ok,false);
 await q('select qg_private.wake_route_matrix_refresh()'); // No vault config: no outbound call.
 console.log(JSON.stringify({checks,failures:0,scope:'isolated evidence lease expiry/fencing, bounded work, revision freshness, conflict/Stop/manual guards; no assignment or provider calls'}));
 }finally{await db.close();}
})().catch(error=>{console.error(error);process.exit(1)});
