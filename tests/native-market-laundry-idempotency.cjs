const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const marketApi=read('native-android/customer/src/main/java/com/queuego/customer/CustomerMarketApi.kt');
const marketStore=read('native-android/customer/src/main/java/com/queuego/customer/MarketCartStore.kt');
const marketUi=read('native-android/customer/src/main/java/com/queuego/customer/QueueGoMarketScreen.kt');
const laundryApi=read('native-android/customer/src/main/java/com/queuego/customer/CustomerLaundryApi.kt');
const laundryStore=read('native-android/customer/src/main/java/com/queuego/customer/CustomerLaundryRequestStore.kt');
const laundryUi=read('native-android/customer/src/main/java/com/queuego/customer/QueueGoLaundryScreen.kt');
const recovery=read('native-android/customer/src/main/java/com/queuego/customer/CustomerCheckoutRecovery.kt');

function block(source,startToken,endToken){
  const start=source.indexOf(startToken); ok(start>=0,'missing '+startToken);
  const end=source.indexOf(endToken,start+startToken.length);
  ok(end>start,'missing end token '+endToken);
  return source.slice(start,end);
}

ok(marketApi.includes('data class PreparedMarketCheckout'),'Market must prepare an exact request before sending');
ok(marketApi.includes('"queuego_place_market_order"')&&marketApi.includes('.put("p_market_order_id", UUID.randomUUID().toString())'),'new Market Trip must use one client-owned idempotency UUID');
ok(marketApi.includes('"queuego_add_market_order_shops"')&&marketApi.includes('.put("p_request_id", UUID.randomUUID().toString())'),'Market add-shop must use one client-owned request UUID');
const marketSend=block(marketApi,'suspend fun sendPrepared','suspend fun place');
ok(!marketSend.includes('UUID.randomUUID'),'Market retry transport must never generate a new request id');
ok(marketSend.includes('http.rpc(pending.rpc'),'Market retry must resend the prepared RPC/body');

ok(marketStore.includes('CheckoutJournal<PendingMarketCheckout>'),'Market exact request must use the durable checkout journal');
ok(marketStore.includes('.put("rpc", pending.rpc)')&&marketStore.includes('.put("body", pending.body)'),'Market journal must persist exact RPC and body');
ok(marketStore.includes('if (prefs.getString("cart", "[]") == pending.cartSnapshot)'),'Market success must clear only the cart snapshot that produced the request');
ok(marketUi.includes('CustomerCheckoutRecovery<PendingMarketCheckout'),'Market UI must reuse generic durable checkout recovery');
ok(marketUi.includes('checkoutRecovery.submit(')&&marketUi.includes('checkoutRecovery.reconcile'),'Market UI must support both retry and restart reconciliation');
ok(marketUi.includes('api.preparePlace(currentAuth, submittedCart, exactLocation, note)'),'Market request must snapshot current cart/location once');
ok((marketUi.match(/api\.sendPrepared\(currentAuth, it\.prepared\(\)\)/g)||[]).length>=2,'Market send and recover must use the same prepared request');
ok(!marketUi.includes('api.place(auth, cart, loc, note)'),'Market UI must not call the one-shot random-id wrapper');
ok(marketUi.includes('checkoutPending -> "ตรวจผล Market Trip เดิม"'),'Market ambiguous result must expose retry of the existing request, not a new order');
ok(marketUi.includes('enabled = !checkoutPending'),'Market mutable form/cart controls must freeze while an ambiguous request exists');

ok(laundryApi.includes('data class PreparedLaundryCheckout'),'Laundry must prepare an exact request before sending');
ok(laundryApi.includes('.put("p_request_id", UUID.randomUUID().toString())'),'Laundry must use one client-owned idempotency UUID');
const laundrySend=block(laundryApi,'suspend fun sendPrepared','suspend fun place');
ok(!laundrySend.includes('UUID.randomUUID'),'Laundry retry transport must never generate a new request id');
ok(laundrySend.includes('queuego_place_laundry_order_v2'),'Laundry retry must use the idempotent Production RPC');
ok(laundryStore.includes('CheckoutJournal<PendingLaundryCheckout>'),'Laundry exact request must use the durable checkout journal');
ok(laundryStore.includes('pending.body.toString()'),'Laundry journal must persist the exact RPC body');
ok(laundryUi.includes('CustomerCheckoutRecovery<PendingLaundryCheckout'),'Laundry UI must reuse generic durable checkout recovery');
ok(laundryUi.includes('checkoutRecovery.submit(')&&laundryUi.includes('checkoutRecovery.reconcile'),'Laundry UI must support retry and restart reconciliation');
ok((laundryUi.match(/api\.sendPrepared\(auth, it\.prepared\(\)\)/g)||[]).length>=2,'Laundry send and recover must use the same prepared request');
ok(!laundryUi.includes('api.place(auth, hub, service, loc, qty, note)'),'Laundry UI must not call the one-shot random-id wrapper');
ok(laundryUi.includes('ระบบจะใช้ request เดิมเท่านั้น เพื่อไม่สร้างคำขอซ้ำ'),'Laundry ambiguous result must explain exact-request recovery');
ok(laundryUi.includes('enabled = !busy && !checkoutPending'),'Laundry submit must not create another request while the prior result is ambiguous');

ok(recovery.includes('journal.read() ?: create().also(journal::write)'),'shared recovery must durably write before first send and reuse pending before create');
ok(recovery.includes('val found = try { recover(pending) }'),'shared recovery must reconcile an ambiguous send using the same pending request');
ok(recovery.includes('runCatching { journal.complete(pending) }'),'post-commit local cleanup must remain non-fatal');

console.log(JSON.stringify({checks,failures:0,scope:'Native Market/Laundry exact-request idempotency across duplicate tap, timeout and process restart'}));
