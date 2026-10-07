const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const allow=JSON.parse(read('docs/security-anon-definer-allowlist.json'));
const expected=[
  'market_public_catalog_v2()',
  'market_public_markets_v1()',
  'market_public_shops_v2()',
  'qg_public_promotions()',
  'qg_public_shop_reviews(uuid)',
  'qg_table_scan(uuid,uuid,double precision,double precision)',
  'qg_table_catalog(uuid,uuid)',
  'qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision)',
  'qg_table_session_status(uuid,uuid)'
].sort();
const actual=allow.rules.map(x=>x.signature).sort();
ok(JSON.stringify(actual)===JSON.stringify(expected),'anonymous SECURITY DEFINER allowlist must contain exactly the 9 reviewed RPCs');
ok(new Set(actual).size===9,'anonymous RPC allowlist must not contain duplicates');
for(const denied of ['market_public_catalog()','queuego_nearest_market(double precision,double precision,numeric)']){
  ok(!actual.includes(denied),'retired/non-public RPC must not return to anon allowlist: '+denied);
  ok(allow.explicitly_not_anonymous.includes(denied),'non-anonymous RPC must remain explicitly documented: '+denied);
}

const customer=read('index.html');
for(const rpc of ['market_public_catalog_v2','market_public_markets_v1','market_public_shops_v2']){
  ok(customer.includes("rpc/"+rpc),'Customer public Market flow must use '+rpc);
}
const features=read('customer-features.js');
for(const rpc of ['qg_public_promotions','qg_public_shop_reviews']){
  ok(features.includes("rpc/"+rpc),'Customer public feature flow must use '+rpc);
}
const table=read('table-order.js');
for(const rpc of ['qg_table_scan','qg_table_catalog','qg_table_checkout','qg_table_session_status']){
  ok(table.includes(rpc),'Guest QR table flow must use '+rpc);
}

const marketDir=read('supabase/migrations/20261005182500_customer_market_directory.sql');
ok(marketDir.includes("grant execute on function public.market_public_markets_v1() to anon,authenticated"),'public market selector anon grant must be explicit');
ok(marketDir.includes("grant execute on function public.market_public_shops_v2() to anon,authenticated"),'public market shops anon grant must be explicit');
ok(marketDir.includes("market_membership_status='approved'"),'public Market shops must require approved membership');

const customerRestore=read('supabase/migrations/20261005055858_customer_function_restore.sql');
ok(customerRestore.includes('qg_public_promotions'),'public promotions function must stay source-controlled');
ok(customerRestore.includes('qg_public_shop_reviews'),'public reviews function must stay source-controlled');
ok(customerRestore.includes("left(coalesce(v.customer_name,'ลูกค้า'),1)||'…'"),'public reviews must mask customer names');

const qr=read('QueueGo-Table-QR-v1-Migration.sql');
for(const guard of ['device_hash','expires_at>now()','qg_table_distance_m','jsonb_array_length','qg_table_requests']){
  ok(qr.includes(guard),'guest QR table ordering must preserve guard '+guard);
}
ok(qr.includes('>100'),'QR table ordering must retain 100m geofence');
ok(qr.includes("jsonb_array_length(p_items) not between 1 and 50"),'QR checkout must bound cart size');

const reduction=read('supabase/migrations/20261007021020_reduce_anon_security_definer_surface.sql');
ok(/revoke execute on function public\.market_public_catalog\(\) from public,anon/i.test(reduction),'legacy market catalog must stay non-anonymous');
ok(/revoke execute on function public\.queuego_nearest_market[\s\S]+from public,anon/i.test(reduction),'nearest-market helper must stay non-anonymous');

console.log(JSON.stringify({checks,failures:0,scope:'Reviewed anonymous SECURITY DEFINER allowlist for public storefront and guest QR table ordering'}));
