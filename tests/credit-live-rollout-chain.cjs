const fs=require('node:fs'),assert=require('node:assert/strict'),{PGlite}=require('@electric-sql/pglite');
(async()=>{
 const db=new PGlite();
 const base=require('./fixtures/rider-dispatch-schema.cjs')();
 const continuous=fs.readFileSync('supabase/migrations/20261014234500_rider_continuous_dispatch_phase1.sql','utf8');
 const passenger=require('./fixtures/passenger-ride-schema.cjs')();
 const ride=fs.readFileSync('supabase/migrations/20261015052056_passenger_ride_shared_dispatch.sql','utf8');
 const addon=passenger.slice((base+continuous).length,passenger.indexOf(ride));
 const creditFixture=require('./fixtures/credit-schema.cjs')();
 const credit=fs.readFileSync('supabase/migrations/20261015075733_credit_rider_merchant_omise.sql','utf8');
 const creditBootstrap=creditFixture.slice(passenger.length,creditFixture.indexOf(credit));
 await db.exec(base+addon+creditBootstrap);
 const names=fs.readdirSync('supabase/migrations').filter(n=>/^2026101(4234500|5000356|5003035|5004228|5012500|5021500|5024500|5052056|5054045|5061000|5075733|5084536|5092957)_/.test(n)).sort();
 assert.equal(names.length,13);
 for(const n of names){await db.exec(fs.readFileSync('supabase/migrations/'+n,'utf8'));console.log('Applied in isolated database: '+n);}
 // The later Ride production migration replaced the earlier guard. Replay its actual
 // definition without executing that migration's service-activation statements.
 const later=fs.readFileSync('supabase/migrations/20261017113000_passenger_ride_production_enable.sql','utf8');
 const guard=later.match(/create or replace function public\.queuego_platform_rule_guard\(\)[\s\S]*?end \$\$;/)[0];
 await db.exec(guard);
 const acl=await db.query("select proacl::text acl,prosecdef from pg_proc where oid='public.queuego_platform_rule_guard()'::regprocedure");
 const rulesBefore=(await db.query('select to_jsonb(r) row from public.queuego_platform_rules r order by rule_key,effective_from')).rows;
 const fix=fs.readFileSync('supabase/migrations/20261020091222_preserve_credit_feature_rule_guard.sql','utf8');
 await db.exec(fix);await db.exec(fix);
 assert.deepEqual((await db.query("select proacl::text acl,prosecdef from pg_proc where oid='public.queuego_platform_rule_guard()'::regprocedure")).rows,acl.rows);
 await db.exec('create temporary table rule_probe(rule_key text,value jsonb,created_at timestamptz);create trigger guard_probe before insert on rule_probe for each row execute function public.queuego_platform_rule_guard()');
 for(const key of ['feature.market_multi_shop','feature.route_bundle','feature.gp','feature.laundry','feature.ride','feature.credit_rider','feature.credit_merchant','feature.credit_merchant_promo']){
  await db.query('insert into rule_probe(rule_key,value) values($1,$2::jsonb)',[key,'false']);
  await assert.rejects(db.query('insert into rule_probe(rule_key,value) values($1,$2::jsonb)',[key,'"true"']),/must be boolean/);
 }
 for(const [key,value] of [['dispatch.continuous_enabled',0],['dispatch.continuous_enabled',1],['dispatch.max_next_link_km',1.5],['ride.long_distance_threshold_km',15],['ride.long_distance_per_km',5],['ride.platform_fee_percent',99]])await db.query('insert into rule_probe(rule_key,value) values($1,$2::jsonb)',[key,String(value)]);
 for(const [key,value] of [['dispatch.continuous_enabled',2],['dispatch.continuous_enabled',0.5],['dispatch.max_next_link_km',1.51],['dispatch.max_next_link_km',-1],['ride.platform_fee_percent',100],['route_bundle.max_orders',6],['pricing.gp_default_rate',101],['feature.unknown',false]])await assert.rejects(db.query('insert into rule_probe(rule_key,value) values($1,$2::jsonb)',[key,String(value)]));
 assert.deepEqual((await db.query('select to_jsonb(r) row from public.queuego_platform_rules r order by rule_key,effective_from')).rows,rulesBefore,'guard reconciliation must not activate or seed platform rules');
 console.log('Later Ride guard reconciliation: PASS; credit/dispatch keys restored, tariff bounds/ACL retained, repeated apply safe');
 for(const n of ['credit_accounts','credit_topups','credit_entries','credit_holds'])assert.equal((await db.query("select count(*)::int n from qg_private."+n)).rows[0].n,0);
 assert.equal((await db.query("select has_function_privilege('authenticated','queuego_credit_apply_charge(uuid,text,bigint,text,boolean,text,boolean,text,timestamptz)','execute') v")).rows[0].v,false);
 assert.equal((await db.query("select has_function_privilege('anon','queuego_admin_grant_merchant_credit(uuid,uuid,bigint,text)','execute') v")).rows[0].v,false);
 console.log('Complete 13-migration live rollout chain: PASS; no balances seeded');
 await db.close();
})().catch(e=>{console.error(e.message);process.exit(1)});
