const assert=require('node:assert/strict'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;
 const q=(s,p=[])=>db.query(s,p),eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const reject=async(s,p,re)=>{await assert.rejects(q(s,p),re);checks++};
 const as=n=>q("select set_config('test.uid',$1,false)",[id(n)]);
 await db.exec(require('./fixtures/express-schema.cjs')());
 for(const [k,v] of Object.entries({'express.base_fare':20,'express.included_km':0,'express.per_km':10,'express.minimum_fare':20,'express.platform_fee_percent':0}))
  await q('insert into queuego_platform_rules(rule_key,value,effective_from,created_at) values($1,$2::jsonb,\'1970-01-01Z\',now())',[k,JSON.stringify(v)]);
 eq((await q("select has_function_privilege('authenticated','queuego_cache_express_quote(uuid,text,text,double precision,double precision,double precision,double precision,text,text,text,text,text,numeric,text,text,text,numeric,numeric)','execute') v")).rows[0].v,false);
 const quote=async(weight=2,vehicle='motorcycle')=>(await q(
  'select queuego_cache_express_quote($1,$2,$3,15,103,15.01,103,$4,$5,$6,$7,$8,$9,$10,$11,$12,3,8) r',
  [id(160),'Pickup address','Dropoff address','Sender','0811111111','Receiver','0822222222','parcel',weight,'medium',vehicle,'handle carefully']
 )).rows[0].r;
 const quoted=await quote();eq(quoted.fare,50);eq(quoted.payment_method,'cash');
 await as(160);
 const requested=(await q('select queuego_request_express($1,$2) r',[id(90),quoted.quote_id])).rows[0].r;
 eq(requested.status,'searching_rider');eq(requested.fare,50);
 eq((await q('select queuego_request_express($1,$2) r',[id(90),quoted.quote_id])).rows[0].r.id,id(90));
 const shape=(await q('select order_type,fulfillment_vertical,express_weight_kg,express_vehicle_type,shop_id,market_order_id from orders where id=$1',[id(90)])).rows[0];
 eq(shape.order_type,'service');eq(shape.fulfillment_vertical,'express');eq(shape.express_weight_kg,2);eq(shape.express_vehicle_type,'motorcycle');eq(shape.shop_id,null);eq(shape.market_order_id,null);
 eq((await q("select status from payments where order_id=$1",[id(90)])).rows[0].status,'pending');
 await q('select qg_dispatch_rider_offers()');
 const offer=(await q('select rider_id,expires_at>now() live from qg_rider_order_offers where order_id=$1',[id(90)])).rows[0];
 eq(offer.rider_id,id(11));eq(offer.live,true);
 await as(101);await q('set role authenticated');eq((await q('select id from orders where id=$1',[id(90)])).rows.length,1);await q('reset role');
 await as(102);await q('set role authenticated');eq((await q('select id from orders where id=$1',[id(90)])).rows.length,0);await q('reset role');
 await as(101);eq((await q('select rider_claim_order($1) s',[id(90)])).rows[0].s,'rider_assigned');
 const action=async(a,n)=>q('select qg_rider_action_once($1,\'express\',$2::jsonb) r',[id(n),JSON.stringify({p_order_id:id(90),p_action:a})]);
 await action('arrive_pickup',180);
 await reject('select qg_rider_action_once($1,\'express\',$2::jsonb)',[id(181),JSON.stringify({p_order_id:id(90),p_action:'pickup'})],/pickup photo/);
 const pickup=id(101)+'/express-pickup.jpg';await q("insert into storage.objects(bucket_id,name) values('qg-evidence',$1)",[pickup]);
 eq((await q('select qg_express_pickup_with_photo($1,$2,$3,15,103) r',[id(182),id(90),pickup])).rows[0].r.status,'picked_up');
 eq((await action('start_transit',183)).rows[0].r.status,'in_progress');
 await action('arrive_dropoff',184);
 await reject('select qg_rider_action_once($1,\'express\',$2::jsonb)',[id(185),JSON.stringify({p_order_id:id(90),p_action:'complete'})],/delivery photo/);
 const drop=id(101)+'/express-drop.jpg';await q("insert into storage.objects(bucket_id,name) values('qg-evidence',$1)",[drop]);
 eq((await q('select qg_express_complete_with_photo($1,$2,$3,15.01,103) r',[id(186),id(90),drop])).rows[0].r.status,'completed');
 eq((await q('select status from payments where order_id=$1',[id(90)])).rows[0].status,'paid');
 eq((await q('select status from deliveries where order_id=$1',[id(90)])).rows[0].status,'delivered');
 eq((await q('select count(*)::int n from qg_pickup_proofs where order_id=$1',[id(90)])).rows[0].n,1);
 eq((await q('select count(*)::int n from qg_delivery_proofs where order_id=$1',[id(90)])).rows[0].n,1);
 const heavy=await quote(25);await as(160);await q('select queuego_request_express($1,$2)',[id(91),heavy.quote_id]);await q('select qg_dispatch_rider_offers()');
 eq((await q('select count(*)::int n from qg_rider_order_offers where order_id=$1',[id(91)])).rows[0].n,0);
 await q("update orders set status='cancelled' where id=$1",[id(91)]);
 const car=await quote(2,'car');await q('select queuego_request_express($1,$2)',[id(92),car.quote_id]);await q('select qg_dispatch_rider_offers()');
 eq((await q('select count(*)::int n from qg_rider_order_offers where order_id=$1',[id(92)])).rows[0].n,0);
 await q("update orders set status='cancelled' where id=$1",[id(92)]);
 eq((await q("select count(*)::int n from orders where fulfillment_vertical='express'")).rows[0].n,3);
 await db.close();console.log('QueueGo Express Phase 1: '+checks+' checks PASS');
})().catch(e=>{console.error(e.message,e.detail||'');process.exitCode=1});
