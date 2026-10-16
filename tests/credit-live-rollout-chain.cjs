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
 for(const n of ['credit_accounts','credit_topups','credit_entries','credit_holds'])assert.equal((await db.query("select count(*)::int n from qg_private."+n)).rows[0].n,0);
 assert.equal((await db.query("select has_function_privilege('authenticated','queuego_credit_apply_charge(uuid,text,bigint,text,boolean,text,boolean,text,timestamptz)','execute') v")).rows[0].v,false);
 assert.equal((await db.query("select has_function_privilege('anon','queuego_admin_grant_merchant_credit(uuid,uuid,bigint,text)','execute') v")).rows[0].v,false);
 console.log('Complete 13-migration live rollout chain: PASS; no balances seeded');
 await db.close();
})().catch(e=>{console.error(e.message);process.exit(1)});
