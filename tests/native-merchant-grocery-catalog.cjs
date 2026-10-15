'use strict';
const fs=require('fs'),assert=require('assert');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const api=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt','utf8');
const app=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt','utf8');
const products=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantProductsScreen.kt','utf8');
const screen=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantGroceryCatalogScreen.kt','utf8');

ok(products.includes('shopCategory == "grocery"'),'Grocery Merchant must branch to Grocery-specific add choices');
ok(products.includes('Text("เพิ่มสินค้าเอง"')&&products.includes('Text("เพิ่มสินค้าจากแคตตาล็อก"'),'Grocery Merchant must expose both manual and master-catalog add actions');
ok(app.includes('"catalog", "grocery-catalog" -> "products"'),'Grocery catalog back navigation must return to Products');
ok(app.includes('screen = "grocery-catalog"')&&app.includes('MerchantGroceryCatalogScreen('),'Merchant app must route Grocery catalog to its Native screen');
ok(app.includes('api.saveGroceryCatalogItem(')&&app.includes('api.loadProducts(auth, activeShop.id)'),'Catalog save must refresh the real Merchant product list after server confirmation');

for(const rpc of ['qg_grocery_categories','qg_grocery_merchant_page','qg_grocery_store_save']){
  ok(api.includes('"'+rpc+'"'),'Native Grocery catalog must call existing Production RPC '+rpc);
}
ok(api.includes('.put("p_request_id", draft.requestId)'),'Grocery catalog save must use a stable request ID for repeat-safe retries');
ok(api.includes('.put("expected_updated_at", item.productUpdatedAt ?: JSONObject.NULL)'),'Grocery catalog save must preserve optimistic concurrency');
ok(api.includes('.put("pos_available", draft.posAvailable)')&&api.includes('.put("delivery_available", draft.deliveryAvailable)'),'Grocery catalog must persist POS and customer Delivery channels');
ok(api.includes('rows.length() > 50')&&api.includes('minOf(rows.length(), 50)'),'Grocery Native paging must preserve the backend 51-row sentinel contract');

ok(screen.includes('Text("เพิ่มสินค้าจากแคตตาล็อก"'),'Native screen title must match the owner-approved wording');
ok(screen.includes('Text("ขายหน้าร้าน"')&&screen.includes('Text("เดลิเวอรี่ลูกค้า"'),'Native catalog cards must expose explicit POS and customer Delivery choices');
ok(screen.includes('GP " + trimCatalogNumber(gpRate)')&&screen.includes('merchantDeliveryPriceFromStore(storePrice, gpRate)'),'Native catalog must preview Delivery price from the existing GP rule');
ok(screen.includes('QgRemoteImage('),'Native catalog must render centrally supplied product imagery with the shared image component');
ok(screen.includes('remember(item.id, item.productUpdatedAt) { UUID.randomUUID().toString() }'),'Retry-safe request ID must remain stable until the server version changes');
ok(!screen.includes('ราคาโปร')&&!screen.includes('promo_price'),'Retired Grocery promo-price UI must not return in Native catalog');
ok(!screen.includes('mock')&&!screen.includes('demo')&&!screen.includes('fake'),'Native Grocery catalog must not ship mock/demo/fake catalog data');

const pageContract=api.slice(api.indexOf('suspend fun groceryCatalogPage'),api.indexOf('suspend fun setProductAvailable'));
ok(pageContract.length>500,'Grocery API contract must be present');
ok(!pageContract.includes('promo_price'),'Native Grocery catalog RPC contract must not revive promo-price payloads');

console.log(JSON.stringify({checks,failures:0,scope:'Native Merchant Grocery Master Catalog source contract; physical-device visual and authenticated save evidence remain release-gated'}));
