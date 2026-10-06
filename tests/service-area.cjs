const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..'),read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
const sql=read('QueueGo-Pilot-Service-Area.sql'),customer=read('index.html'),laundry=read('laundry/index.html');

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
console.log(JSON.stringify({checks,failures:0,scope:'QueueGo pilot service-area registry, 15km route cap and Customer/Market/Laundry server guards'}));
