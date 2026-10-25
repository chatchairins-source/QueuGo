const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..'),read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
const sql=read('QueueGo-Pilot-Service-Area.sql'),v2=read('supabase/migrations/20261025070500_queuego_nationwide_order_service_area_v2.sql'),cutover=read('supabase/migrations/20261025073000_queuego_nationwide_service_area_cutover.sql'),legacyLaundryRetire=read('supabase/migrations/20261025094500_retire_legacy_laundry_order_writer.sql'),customer=read('index.html'),laundry=read('laundry/index.html');

ok(sql.includes('create table if not exists public.queuego_service_areas'),'service area registry must exist');
ok(sql.includes("'Buriram Pilot','บุรีรัมย์',14.99292,103.10804,20,15"),'Buriram pilot must have explicit coverage and delivery cap');
ok(sql.includes('queuego_service_area_route_status'),'public route status helper must exist');
ok(sql.includes('queuego_assert_service_route'),'authoritative checkout guard must exist');
for(const fn of ['queuego_place_cash_order_core','queuego_place_market_order','queuego_place_laundry_order_v2']){
  const start=sql.indexOf('FUNCTION public.'+fn);
  ok(start>=0,fn+' must be mirrored in the migration source');
  const end=sql.indexOf('CREATE OR REPLACE FUNCTION public.',start+20);
  const block=sql.slice(start,end<0?sql.length:end);
  ok(block.includes('queuego_assert_service_route'),fn+' must enforce service area server-side');
}
ok(/OUTSIDE_SERVICE_AREA/.test(customer),'customer must recognize outside-area server result');
ok(/DELIVERY_DISTANCE_EXCEEDED/.test(customer),'customer must recognize max-distance server result');
ok(customer.includes('ตำแหน่งรับหรือส่งอยู่นอกพื้นที่ให้บริการ QueueGo Pilot'),'customer must show a clear Thai service-area message');
ok(/OUTSIDE_SERVICE_AREA/.test(laundry)&&/DELIVERY_DISTANCE_EXCEEDED/.test(laundry),'laundry must explain service-area blocks');
ok(!/\bdrop\s+(table|schema|column)\b/i.test(sql),'service area migration must be additive');
ok(v2.includes('create table if not exists public.queuego_order_service_area_rules'),'nationwide order-area registry must exist');
ok(v2.includes('queuego_order_service_route_status_v2'),'nationwide shadow route authority must exist');
ok(v2.includes("'Thailand Nationwide Launch','TH'"),'Thailand nationwide launch rule must be data-driven');
ok(v2.includes("'natural_earth','Natural Earth','Public-Domain'"),'country boundary must retain Natural Earth provenance');
ok(v2.includes("'map_available',c.map_available")&&v2.includes("'registration_available',c.registration_available"),'map and registration capability must be independent from ordering');
ok(v2.includes("r.specificity desc,case r.mode when 'deny' then 0 else 1 end"),'more-specific rules and deny precedence must be deterministic');
ok(v2.includes('max_direct_delivery_km'),'route distance cap must be separate from polygon coverage');
ok(v2.includes("constraint queuego_order_rule_one_boundary"),'each order rule must have exactly one boundary authority');
ok(v2.includes("set search_path=''"),'new public capability functions must use an empty search path');
ok(!/\bdrop\s+(table|schema|column)\b/i.test(v2),'nationwide v2 foundation must remain additive');
ok(cutover.includes('queuego_order_service_route_status_v2'),'legacy route-status contract must delegate to nationwide v2 after cutover');
ok(cutover.includes("'area_id',v->'rule_id'")&&cutover.includes("'area_name',v->'rule_name'"),'cutover must preserve legacy area_id/area_name compatibility');
ok(!/\bdrop\s+(table|schema|column)\b/i.test(cutover),'cutover must preserve legacy rollback data and schema');
ok(legacyLaundryRetire.includes('revoke all on function public.queuego_place_laundry_order(')&&legacyLaundryRetire.includes('from public,anon,authenticated'),'legacy Laundry v1 must not remain client-executable after nationwide cutover');
ok(legacyLaundryRetire.includes('to service_role'),'legacy Laundry v1 may remain service-role only for controlled rollback/audit');
ok(/qgRpc\(['"]queuego_place_laundry_order_v2['"]/.test(laundry),'Laundry customer UI must use the v2 service-area-enforced writer');
ok(!/qgRpc\(['"]queuego_place_laundry_order['"]/.test(laundry),'Laundry customer UI must not call the legacy v1 writer');

console.log(JSON.stringify({checks,failures:0,scope:'legacy pilot guard plus additive nationwide polygon service-area authority, independent map/registration capability, route cap and shadow decision contract'}));
