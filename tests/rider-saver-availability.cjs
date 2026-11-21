const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite'),{JSDOM}=require('jsdom');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};const db=new PGlite();
 try{
 await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
 // Mirror the real scheduled-rule key shape for the actual availability RPC.
 await db.exec(`alter table queuego_platform_rules drop constraint queuego_platform_rules_pkey;
 alter table queuego_platform_rules add column id uuid primary key default gen_random_uuid(),add column effective_from timestamptz not null default '1970-01-01',add column note text,add column created_by uuid;
 alter table queuego_platform_rules add unique(rule_key,effective_from);
 create or replace function queuego_feature_enabled(k text,t timestamptz) returns boolean language sql as $$select coalesce((select (value#>>'{}')::boolean from queuego_platform_rules where rule_key='feature.'||k and effective_from<=t order by effective_from desc limit 1),false)$$;`);
 for(const suffix of ['rider_continuous_dispatch_phase1','rider_dynamic_stop_plan_phase1','rider_saver_availability_control']){
  const name=fs.readdirSync('supabase/migrations').find(n=>n.endsWith('_'+suffix+'.sql'));await db.exec(fs.readFileSync('supabase/migrations/'+name,'utf8'));
 }
 const q=(s,p)=>db.query(s,p),actor=n=>q("select set_config('test.uid',$1,false)",[id(n)]);
 const change=async enabled=>(await q('select queuego_admin_set_saver_enabled($1) state',[enabled])).rows[0].state;
 const quote=async()=>(await q('select queuego_route_bundle_quote_orders($1,$2,now()) q',[id(20),id(21)])).rows[0].q;
 eq((await quote()).eligible,false);eq((await quote()).reason,'route bundle is disabled');
 await actor(101);await assert.rejects(change(true),/active admin/);checks++;
 await actor(999);await assert.rejects(change(true),/active admin/);checks++;
 await q("insert into users(id,auth_user_id,role,status) values($1,$2,'admin','suspended')",[id(3),id(103)]);
 await actor(103);await assert.rejects(change(true),/active admin/);checks++;
 await q("update users set status='active' where id=$1",[id(3)]);
 await assert.rejects(change(null),/explicit saver state/);checks++;
 eq((await change(false)).changed,false);
 await q("insert into queuego_platform_rules(rule_key,value,effective_from) values('feature.route_bundle','true',now()+interval '1 day'),('pricing.gp_default_rate','10','1970-01-01')");
 const off=await change(false);eq(off.enabled,false);eq(off.cancelled_schedules,1);
 eq((await q("select count(*)::int n from queuego_platform_rules where rule_key='feature.route_bundle' and effective_from>now()")).rows[0].n,0);
 eq((await q("select value from queuego_platform_rules where rule_key='pricing.gp_default_rate'")).rows[0].value,10);
 eq((await change(true)).enabled,true);
 for(let n=20;n<=21;n++)await q("insert into orders(id,status,rider_id,customer_id,shop_id,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,$2,$3,$4,$5,15,103,15.002,103)",[id(n),n===20?'ready':'searching_rider',n===20?id(11):null,id(60),id(50)]);
 await actor(101);eq((await quote()).eligible,true);await q('select queuego_claim_route_bundle($1)',[id(21)]);
 const before=(await q('select id,status,delivery_fee,total_amount,rider_delivery_payout,route_bundle_id from orders order by id')).rows;
 await actor(103);eq((await change(false)).enabled,false);
 eq((await quote()).eligible,false);await actor(101);
 await assert.rejects(q('select queuego_claim_route_bundle($1)',[id(21)]),/disabled/);checks++;
 eq((await q('select id,status,delivery_fee,total_amount,rider_delivery_payout,route_bundle_id from orders order by id')).rows,before);
 eq((await q('select count(*)::int n from queuego_route_bundle_active_orders()')).rows[0].n,2);
 // OFF prevents new claims but never strands an accepted route or changes its cash.
 for(const [n,status] of [[20,'picked_up'],[21,'picked_up'],[20,'in_progress'],[20,'completed'],[21,'in_progress'],[21,'completed']])await q('update orders set status=$1 where id=$2',[status,id(n)]);
 eq((await q("select count(*)::int n from orders where status='completed'")).rows[0].n,2);
 eq((await q("select count(*)::int n from audit_logs where action='platform_rules_update'")).rows[0].n,3);
 eq((await q("select has_function_privilege('anon','public.queuego_admin_set_saver_enabled(boolean)','execute') ok")).rows[0].ok,false);
 }finally{await db.close();}
 // Execute the real Admin control script: one scoped RPC, no implicit price save.
 const source=fs.readFileSync('admin/index.html','utf8');const script=source.match(/<script id="qg-platform-controls-v1">([\s\S]*?)<\/script>/)[1];
 const dom=new JSDOM('<div id="qg-platform-controls"></div>',{runScripts:'outside-only',url:'https://admin.invalid'});const w=dom.window;
 const epoch='1970-01-01T00:00:00.000Z';let admin=true,fail=false,calls=[],rows=[{rule_key:'feature.route_bundle',value:false,effective_from:epoch}];
 w.qtAdminOnly=()=>admin?{id:'admin'}:null;w.qtGetAccessToken=async()=>'isolated-admin';w.qtSupabaseTable=async()=>rows;w.escapeHtml=String;w.toast=()=>{};
 w.qtSupabaseRpc=async(name,args)=>{calls.push({name,args});if(fail)throw Error('network lost');rows=[{rule_key:'feature.route_bundle',value:args.p_enabled,effective_from:epoch}];return {enabled:args.p_enabled,effective_from:epoch};};
 w.eval(script);await w.qgRenderPlatformControls(true);eq(w.document.getElementById('qg-feature-bundle').checked,false);assert(w.document.getElementById('qg-saver-status').textContent.includes('รอระบบพร้อม'));checks++;
 w.document.getElementById('qg-feature-bundle').checked=true;await w.qgSaveSaverAvailability();eq(calls.length,1);eq(calls[0].name,'queuego_admin_set_saver_enabled');eq(calls[0].args.p_enabled,true);
 await Promise.resolve();fail=true;w.document.getElementById('qg-feature-bundle').checked=false;await w.qgSaveSaverAvailability();eq(w.document.getElementById('qg-feature-bundle').checked,true);assert(w.document.getElementById('qg-saver-status').textContent.includes('บันทึกไม่สำเร็จ'));checks++;
 admin=false;const count=calls.length;await w.qgSaveSaverAvailability();eq(calls.length,count);dom.window.close();
 console.log(JSON.stringify({checks,failures:0,scope:'actual Saver Admin UI/RPC, OFF default, admin-only switch, future schedule cancellation, unchanged prices/cash and accepted route completion while OFF'}));
})().catch(error=>{console.error(error);process.exit(1)});
