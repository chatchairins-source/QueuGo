const fs=require('fs'),assert=require('assert'),path=require('path');
let checks=0;
const ok=(v,m)=>{assert.ok(v,m);checks++};
const eq=(a,b,m)=>{assert.deepStrictEqual(a,b,m);checks++};
const read=p=>fs.readFileSync(path.resolve(__dirname,'..',p),'utf8');

const customer=read('index.html');
const customerLaundry=read('customer-laundry.js');
const laundry=read('laundry/index.html');
const merchant=read('merchant/index.html');
const merchantLaundry=read('merchant/laundry.js');
const rider=read('rider/index.html');
const riderLaundry=read('rider/laundry.js');
const admin=read('admin/index.html');
const platform=read('QueueGo-Platform-Feature-Pricing-Migration.sql');
const market=read('QueueGo-Market-Registration-Migration.sql');
const marketAdd=read('QueueGo-Market-Trip-Add-Shops.sql');
const bundleBase=fs.existsSync(path.resolve(__dirname,'../QueueGo-Route-Bundle-v2.sql'))?read('QueueGo-Route-Bundle-v2.sql'):read('QueueGo-Route-Bundle-v1.sql');
const bundleFair=fs.existsSync(path.resolve(__dirname,'../QueueGo-Route-Bundle-Fair-Savings.sql'))?read('QueueGo-Route-Bundle-Fair-Savings.sql'):'';
const bundle=bundleBase+'\n'+bundleFair;
const laundryV2=read('QueueGo-Laundry-v2.sql');

// Customer top-level service separation.
for(const label of ['อาหาร','เครื่องดื่ม','ร้านขายของชำ','ฝากซัก','ตลาดสด'])ok(customer.includes('<b>'+label+'</b>'),'missing service '+label);
ok(customer.includes("go('market')"),'market must be a dedicated route');
ok(customer.includes("location.href='laundry/'"),'laundry must use dedicated module');
ok(customer.includes("dedicatedMarketCats"),'normal search must exclude dedicated market shop categories');
ok(customer.includes("queuego_place_market_order"),'market checkout RPC missing');
ok(customer.includes("queuego_add_market_order_shops"),'market add-shop RPC missing');
ok(customer.includes("ตะกร้าตลาดสดซื้อข้ามตลาดไม่ได้"),'same-market cart guard missing');

// Market pricing formula and explicit feature gate.
ok(platform.includes("'pricing.market_second_shop_fee'"),'second-shop rule missing');
ok(platform.includes("'pricing.market_additional_shop_fee'"),'additional-shop rule missing');
ok(platform.includes("when p_shop_count=2"),'second-shop fee branch missing');
ok(platform.includes("(p_shop_count-2)*"),'third-plus fee formula missing');
ok(platform.includes("'feature.market_multi_shop'"),'multi-shop feature switch missing');
ok(marketAdd.includes("queuego_feature_enabled('market_multi_shop'"),'add-shop must honor feature switch');
ok(marketAdd.includes("market_id"),'add-shop must enforce market identity');
ok(marketAdd.includes("locked"),'Market Trip add-shop must enforce lock state');

// Market registration must suggest and require explicit merchant confirmation/admin review.
ok(market.includes('market_suggested_id'),'nearest market suggestion field missing');
ok(market.includes("market_membership_status='pending'"),'merchant membership must be pending before approval');
ok(market.includes('merchant confirmation required'),'merchant confirmation guard missing');
ok(market.includes('queuego_admin_review_market_membership'),'admin review RPC missing');
ok(market.includes('ตลาดสดสวายจีก'),'Sawai Chik launch market seed missing');

// Route bundle: server-side feature gate + customer savings + rider compensation.
ok(bundle.includes("queuego_feature_enabled('route_bundle'"),'route bundle feature gate missing');
ok(bundle.includes('bundle_customer_savings'),'bundle customer savings missing');
ok(bundle.includes('bundle_rider_extra_fee'),'bundle rider compensation missing');
ok(bundle.includes('primary_customer_savings')||bundle.includes('primary_savings'),'primary customer bundle saving missing');
ok(bundle.includes('candidate_customer_savings')||bundle.includes('customer_savings'),'candidate customer bundle saving missing');
ok(admin.includes('route_bundle.primary_savings_percent'),'Admin fair-savings split control missing');
ok(bundle.includes('route_bundle_max_detour_km')||bundle.includes('max_detour'),'bundle detour rule missing');
ok(bundle.includes('added_minutes')||bundle.includes('max_delay'),'bundle delay rule missing');
ok(rider.includes('queuego_claim_route_bundle'),'Rider bundle acceptance missing');
ok(rider.includes('ลูกค้าประหยัด')||rider.includes('ประหยัด'),'Rider bundle customer saving display missing');

// Admin master switches and scheduled pricing.
for(const key of ['feature.market_multi_shop','feature.route_bundle','feature.gp','feature.laundry'])ok(admin.includes(key),'Admin switch missing '+key);
ok(admin.includes('effective_from'),'scheduled effective time missing');

// Laundry v2 client + merchant + rider.
ok(laundry.includes('queuego_place_laundry_order_v2'),'Laundry customer must use idempotent v2 checkout');
ok(laundry.includes('p_estimated_quantity'),'Laundry estimated quantity missing');
ok(laundry.includes('request_id'),'Laundry idempotency request key missing');
for(const unit of ['per_kg','per_item','per_set','fixed'])ok(merchantLaundry.includes(unit),'Merchant Laundry pricing unit missing '+unit);
ok(merchantLaundry.includes('queuego_laundry_save_settings'),'Merchant Laundry settings RPC missing');
ok(merchantLaundry.includes('queuego_laundry_save_service'),'Merchant Laundry service editor missing');
ok(merchantLaundry.includes('queuego_laundry_invite_rider'),'Merchant Laundry Rider invite missing');
ok(merchant.includes("case'shop-laundry'"),'Merchant Laundry route missing');
ok(riderLaundry.includes('queuego_laundry_rider_state'),'Rider Laundry state RPC missing');
ok(riderLaundry.includes('queuego_claim_laundry_job'),'Rider Laundry claim missing');
ok(riderLaundry.includes('queuego_laundry_rider_action'),'Rider Laundry lifecycle action missing');
ok(customerLaundry.includes("V['laundry-order']"),'Customer Laundry status page missing');
ok(customerLaundry.includes('laundry_order_events'),'Customer Laundry timeline missing');

// Server v2 must snapshot prices and use customer->shop for pickup, shop->customer for return.
for(const col of ['unit_price_snapshot','pickup_fee_snapshot','return_fee_snapshot','delivery_fee_total_snapshot','request_key'])ok(laundryV2.includes(col),'Laundry snapshot field missing '+col);
ok(laundryV2.includes("case when j.leg='pickup' then o.pickup_address else s.address end"),'Laundry pickup route origin must be customer for pickup');
ok(laundryV2.includes("case when j.leg='pickup' then s.address else o.pickup_address end"),'Laundry pickup route destination must be shop for pickup');


/* Laundry RPC security and no-direct-write contract. */
const checkoutStart=laundryV2.indexOf('create or replace function public.queuego_place_laundry_order_v2');
const checkoutEnd=laundryV2.indexOf('create or replace function',checkoutStart+1);
const checkoutFn=laundryV2.slice(checkoutStart,checkoutEnd<0?undefined:checkoutEnd);
ok(checkoutStart>=0,'Laundry checkout RPC definition missing');
ok(checkoutFn.includes('auth.uid() is null'),'Laundry checkout must require an authenticated session');
ok(checkoutFn.includes("auth_user_id=auth.uid() and role='customer' and status='active'"),'Laundry checkout must resolve the active customer from auth');
ok(checkoutFn.includes('where id=p_hub_id and active=true'),'Laundry checkout must reject inactive hubs');
ok(checkoutFn.includes('where id=p_service_id and hub_id=v_hub.id and active=true'),'Laundry service must be active and belong to the selected hub');
ok(checkoutFn.includes("perform pg_advisory_xact_lock")&&checkoutFn.includes('where request_key=p_request_id'),'Laundry retry must serialize and replay by request key');
ok(checkoutFn.includes('p_request_id,')&&checkoutFn.includes('where request_key=p_request_id'),'Laundry request key must be written and replayed by the server');
for(const snapshot of ['service_name_snapshot','pricing_type_snapshot','unit_price_snapshot','pickup_fee_snapshot','return_fee_snapshot','round_trip_fee_snapshot'])ok(checkoutFn.includes(snapshot),'Laundry checkout snapshot missing '+snapshot);
ok(checkoutFn.includes('insert into public.laundry_order_events')&&checkoutFn.includes('insert into public.notifications'),'Laundry order, event, and notification must share the RPC transaction');
ok(!/\\.from\\(['"]laundry_orders['"]\\)\\s*\\.insert\\s*\\(/i.test(laundry),'Laundry client must not insert orders directly');
ok(!/p_(?:unit_)?price\\s*:/i.test(laundry),'Laundry client must not submit service price to checkout');

console.log(JSON.stringify({checks,failures:0,scope:'static integration contracts for QueueGo Market, Route Bundle and Laundry; no production order mutation'}));