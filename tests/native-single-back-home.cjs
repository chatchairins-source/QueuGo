const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const customer=read('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
const merchant=read('native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
const rider=read('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt');

function backBlock(source, marker){
  const start=source.indexOf(marker);
  ok(start>=0,'BackHandler missing: '+marker);
  const tail=source.slice(start,start+900);
  return tail;
}

const customerBack=backBlock(customer,'BackHandler(enabled = screen != "home")');
ok(customerBack.includes('screen = "home"'),'Customer hardware back must return Home in one press');
ok(customerBack.includes('shoppingMode = "all"'),'Customer hardware back must reset nested shopping mode before Home');
ok(!customerBack.includes('screen = "cart"'),'Customer hardware back must not require a second press through Cart');
ok(!customerBack.includes('if (screen == "checkout")'),'Customer hardware back must not branch through nested screen history');
ok(customerBack.includes('selectedOrder = null')&&customerBack.includes('selectedShop = null'),'Customer Home return must clear nested selections');

const merchantBack=backBlock(merchant,'BackHandler(enabled = screen != "home")');
ok(merchantBack.includes('screen = "home"'),'Merchant hardware back must return Home in one press');
ok(!merchantBack.includes('screen = when (screen)'),'Merchant hardware back must not walk nested route history');
ok(!merchantBack.includes('"order" -> "orders"'),'Merchant hardware back must not require a second press through Orders');
ok(merchantBack.includes('selectedOrder = null'),'Merchant Home return must clear selected order state');

const riderBack=backBlock(rider,'BackHandler(enabled = activeTab != "home"');
ok(riderBack.includes('activeTab = "home"'),'Rider hardware back must return Home in one press');

ok(customer.includes('onBack = { screen = "cart" }'),'Customer explicit in-UI checkout back control must still return to Cart');
ok(merchant.includes('onBack = { screen = "orders" }'),'Merchant explicit in-UI order back control must still return to Orders');

console.log(JSON.stringify({checks,failures:0,scope:'Native Android single hardware-back-to-Home contract while preserving explicit in-UI back navigation'}));
