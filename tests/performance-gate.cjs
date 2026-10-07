const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const rls=read('supabase/migrations/20261007003943_optimize_rls_auth_initplan.sql');
const fk=read('supabase/migrations/20261007004045_pilot_fk_index_coverage.sql');

ok((rls.match(/\(select auth\.uid\(\)\)/g)||[]).length>=13,'RLS policies must cache auth.uid() with scalar subselects');
ok(!/auth_user_id\s*=\s*auth\.uid\(\)/.test(rls),'optimized policies must not re-evaluate direct auth.uid() per row');
for(const table of ['market_orders','market_order_pickups','market_products','laundry_orders','merchant_cash_receipts','order_items']){
  ok(fk.includes('on public.'+table),'Pilot FK index migration must cover '+table);
}
ok(!/drop\s+(table|column|schema)/i.test(fk),'FK performance migration must stay additive');
ok(!/drop\s+(table|column|schema)/i.test(rls),'RLS optimization must not drop data structures');

console.log(JSON.stringify({checks,failures:0,scope:'RLS auth initplan optimization and Pilot FK index coverage'}));
