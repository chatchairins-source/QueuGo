const fs=require('fs'),path=require('path'),assert=require('node:assert/strict');
const {PGlite}=require('@electric-sql/pglite');
const root=path.resolve(__dirname,'..'),read=p=>fs.readFileSync(path.join(root,p),'utf8');
const pos=read('merchant/pos-table-v2.js'),merchant=read('merchant/index.html'),pricing=read('supabase/migrations/20261022084500_separate_pos_delivery_markup_from_gp.sql');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++},eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++};
ok(!/deliveryOrders|deliveryItems|qgPosDeliveryAction|pos_delivery_kitchen_action|pos_enable_delivery/.test(pos),'POS has no Delivery state or action endpoints');
ok(!/QUEUEGO_DELIVERY|GP จาก|r\.GP|r\.DELIVERY/.test(pos),'POS report does not render Delivery or GP');
ok(pos.includes('&sales_channel=eq.POS&created_at=gte.'),'POS history requests only POS orders');
ok(!pos.includes('value="DELIVERY"'),'POS history has no Delivery filter');
ok(merchant.includes('id="qgm-delivery-markup"'),'merchant product editor displays the shop Delivery markup');
ok(!merchant.includes('delivery_markup_percent:deliveryMarkup'),'merchant product save does not create per-product Delivery pricing policy');
ok(!merchant.includes("บันทึกสินค้าแล้ว · Delivery + GP"),'merchant save confirmation does not describe GP as price markup');
ok(pricing.includes("new.delivery_price:=round(v_store_price*(1+v_markup/100),2)"),'Delivery price derives from storefront price plus Delivery markup');
ok(!pricing.includes("effective_gp_rate_at"),'product price trigger no longer reads GP');
ok(pricing.includes("drop trigger if exists queuego_reprice_products_after_shop_gp_change_trg"),'shop GP changes no longer reprice products');
const match=merchant.match(/function qgmCanCancelDelivery\(o\)\{[\s\S]*?\n\}/);assert.ok(match);
const canCancel=new Function(`${match[0]};return qgmCanCancelDelivery`)();
for(const status of ['pending_shop','shop_accepted','searching_rider','rider_to_shop','rider_assigned','assigned'])eq(canCancel({status}),true,status+' can cancel before preparation');
for(const status of ['preparing','ready','waiting_rider','picked_up','in_progress','completed','cancelled'])eq(canCancel({status}),false,status+' cannot cancel');
for(const field of ['preparingAt','preparing_at','readyAt','ready_at'])eq(canCancel({status:'rider_to_shop',[field]:'2026-10-22T01:00:00Z'}),false,'timestamps override stale status');
const shopPricing=read('supabase/migrations/20261022093000_restore_shop_delivery_markup_default_10.sql');
ok(shopPricing.includes("'delivery_markup','{\"default_rate\":10}'::jsonb"),'Delivery markup defaults to 10 percent');
ok(shopPricing.includes('shop_delivery_markup_rates'),'Delivery markup supports shop-level overrides');
ok(shopPricing.includes('queuego_effective_delivery_markup'),'products use effective shop Delivery markup');

(async()=>{const db=new PGlite();
await db.exec(`create role anon;create role authenticated;
 create function pos_is_owner() returns boolean language sql as $$select current_setting('test.owner',true)='true'$$;
 create function pos_my_shop() returns uuid language sql as $$select '00000000-0000-4000-8000-000000000001'::uuid$$;
 create table orders(shop_id uuid,sales_channel text,order_type text,payment_status text,status text,total_amount numeric,created_at timestamptz);
 create function pos_owner_dashboard(integer default 1) returns jsonb language sql as $$select '{}'::jsonb$$;
 revoke execute on function pos_owner_dashboard(integer) from public,anon;grant execute on function pos_owner_dashboard(integer) to authenticated;
 select set_config('test.owner','true',false);
 insert into orders values
 ('00000000-0000-4000-8000-000000000001','POS','DINE_IN','PAID','completed',100,now()),
 ('00000000-0000-4000-8000-000000000001','POS','TAKEAWAY','PAID','completed',50,now()),
 ('00000000-0000-4000-8000-000000000001','QUEUEGO_DELIVERY','shopping','PAID','completed',900,now()),
 ('00000000-0000-4000-8000-000000000001',null,'shopping','PAID','completed',800,now()),
 ('00000000-0000-4000-8000-000000000001','POS','TAKEAWAY','UNPAID','pending',700,now()),
 ('00000000-0000-4000-8000-000000000002','POS','TAKEAWAY','PAID','completed',600,now());`);
await db.exec(read('supabase/migrations/20261022073650_pos_report_context_separation.sql'));
eq((await db.query('select pos_owner_dashboard(1) result')).rows[0].result,{DINE_IN:100,TAKEAWAY:50,orders:2},'Delivery, unpaid and foreign-shop orders excluded from totals and bill count');
eq((await db.query("select has_function_privilege('anon','pos_owner_dashboard(integer)','execute') allowed")).rows[0].allowed,false,'anonymous access remains denied');
eq((await db.query("select has_function_privilege('authenticated','pos_owner_dashboard(integer)','execute') allowed")).rows[0].allowed,true,'cashier API ACL preserved');
await db.exec("select set_config('test.owner','false',false)");await assert.rejects(()=>db.query('select pos_owner_dashboard(1)'),/owner only/);checks++;
await db.close();console.log(JSON.stringify({checks,failures:0,scope:'POS-only totals/history/actions and Delivery pre-preparation cancellation boundaries'}));
})().catch(e=>{console.error(e);process.exit(1)});
