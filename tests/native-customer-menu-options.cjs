const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const migration=read('supabase/migrations/20261011003000_customer_menu_options_v1.sql');
const customerApi=read('native-android/customer/src/main/java/com/queuego/customer/CustomerApi.kt');
const customerOptions=read('native-android/customer/src/main/java/com/queuego/customer/CustomerMenuOptions.kt');
const customerShop=read('native-android/customer/src/main/java/com/queuego/customer/CustomerShopScreen.kt');
const customerCart=read('native-android/customer/src/main/java/com/queuego/customer/CustomerCartStore.kt');
const customerShell=read('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
const customerTracking=read('native-android/customer/src/main/java/com/queuego/customer/CustomerOrderTrackingScreen.kt');
const merchantModel=read('native-android/merchant/src/main/java/com/queuego/merchant/MerchantProductModel.kt');
const merchantApi=read('native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt');
const merchantApp=read('native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');

for(const column of ['selected_options jsonb','base_unit_price numeric','option_price_delta numeric']){
  ok(migration.includes(column),'menu-option migration missing additive order_items column: '+column);
}
ok(migration.includes('create or replace function public.qg_resolve_product_options'),'server option resolver must exist');
ok(migration.includes("v_group_key = 'portion'")&&migration.includes("v_option_key = 'normal'"),'legacy clients must default required portion to normal');
ok(migration.includes("raise exception 'unknown product option'"),'server must reject unknown option keys');
ok(migration.includes("raise exception 'duplicate product option'"),'server must reject duplicate option keys');
ok(migration.includes("v_unit:=round(v_base_unit+v_option_delta,2)"),'server must price order lines from base plus validated option delta');
ok(migration.includes("'selected_options',v_selected_options"),'merchant adjustment snapshot must preserve selected options');
ok(migration.includes("v_selected_options:=coalesce(v_existing.selected_options"),'merchant quantity edits must preserve existing option snapshot');
ok(migration.includes("qg_resolve_product_options(v_product.variants,'[]'::jsonb)"),'substitute product must clear old options and resolve only the new product defaults');
ok(/create or replace function public\.queuego_place_cash_order_core\(\s*p_order_id uuid,\s*p_shop_id uuid,\s*p_items jsonb,/s.test(migration),'ordinary checkout RPC core signature must remain backward-compatible');
ok(/create or replace function public\.qg_merchant_edit_order_items\(\s*p_order_id uuid,\s*p_items jsonb,\s*p_reason text default/s.test(migration),'merchant substitution RPC signature must remain backward-compatible');

ok(customerApi.includes('delivery_available,variants'),'Native Customer catalog must load product variants');
ok(customerApi.includes('.put("options", customerMenuSelectionPayload(selections))'),'Native Customer checkout must send selected option keys');
ok(customerApi.includes('customerCartLineUnitPrice(it) * it.quantity'),'Native Customer expected subtotal must include option deltas');
ok(customerApi.includes('selected_options'),'Native Customer order history must load server option snapshots');
ok(customerOptions.includes('customerDefaultMenuSelections')&&customerOptions.includes('group.key == "portion"'),'Native Customer must default required portion to normal');
ok(customerOptions.includes('customerCartLineKey')&&customerOptions.includes('product.id + "|" + canonical'),'same product with different options must have distinct cart identity');
ok(customerShop.includes('CustomerProductOptionsDialog'),'Native Customer shop must expose product option selection');
ok(customerShop.includes('RadioButton')&&customerShop.includes('Checkbox'),'single and multi option controls must both exist');
ok(customerShop.includes('"+฿"'),'option price delta must be visible before adding to cart');
ok(customerCart.includes('.put("selections"'),'Native cart persistence must retain selected option keys');
ok(customerCart.includes('.put("variants"'),'Native cart persistence must retain the option schema required to price offline/restart state');
ok(customerShell.includes('customerCartLineKey(product, selections)'),'Native cart add must be option-aware');
ok(customerShell.includes('latest.variantsJson != line.product.variantsJson'),'checkout preflight must detect changed option configuration');
ok(customerTracking.includes('item.optionSummary'),'Customer tracking must show the immutable selected option snapshot');

const approvedCategories=[
  'เมนูแนะนำ / เมนูขายดี','อาหารจานเดียว','ข้าว','เส้น / ก๋วยเตี๋ยว','ของทอด',
  'ของย่าง / ปิ้งย่าง','ต้ม / แกง / ซุป','ผัด','ส้มตำ / ยำ','กับข้าว','อาหารทะเล',
  'ของทานเล่น','ของหวาน','เครื่องดื่ม','ชุดคอมโบ / เซ็ต','เมนูเด็ก',
  'เมนูสุขภาพ / คลีน','เพิ่มเติม / ท็อปปิ้ง','อื่น ๆ'
];
for(const category of approvedCategories){
  ok(merchantModel.includes('"'+category+'"'),'approved food category missing: '+category);
}
ok(merchantModel.includes('MerchantToppingOption("ใส่ไข่", 10.0, false)'),'requested default ใส่ไข่ topping must remain available');
ok(merchantModel.includes('.put("key", "normal")')&&merchantModel.includes('.put("key", "special")'),'Merchant portion schema must publish normal/special keys');
ok(merchantApi.includes('selected_options')&&merchantApi.includes('merchantSelectedOptionsLabel'),'Merchant must load selected option snapshots from order_items');
ok(merchantApp.includes('it.optionSummary'),'Merchant order detail must display selected menu options');

console.log(JSON.stringify({checks,failures:0,scope:'Native Customer food menu options, authoritative checkout pricing and Merchant snapshot parity'}));
