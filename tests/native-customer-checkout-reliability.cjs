const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const app=read('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
const store=read('native-android/customer/src/main/java/com/queuego/customer/CustomerCartStore.kt');
const recovery=read('native-android/customer/src/main/java/com/queuego/customer/CustomerCheckoutRecovery.kt');

const start=app.indexOf('fun showPlacedOrder(order: CustomerOrder)');
const end=app.indexOf('\n    LaunchedEffect(',start);
ok(start>=0&&end>start,'Native Customer confirmed-order handler must exist');
const placed=app.slice(start,end);
ok(placed.includes('lastCheckoutReceipt = order.id'),'confirmed-order handler must bind the server receipt');
ok(placed.includes('cart = emptyList()'),'server-confirmed order must clear the visible cart immediately');
ok(placed.includes('runCatching { cartStore.save(emptyList()) }'),'confirmed-order handler must best-effort persist the empty cart without turning server success into UI failure');
ok(!placed.includes('cart = cartStore.load()'),'confirmed-order handler must never reload a stale submitted cart');
ok(placed.includes('selectedOrder = order')&&placed.includes('screen = "order"'),'confirmed order must open tracking directly');
ok(placed.includes('pendingCheckoutRecord = null')&&placed.includes('checkoutPending = false'),'confirmed-order UI must release checkout pending state');

ok(store.includes('if (prefs.getString("cart", "[]") == pending.cartSnapshot) edit.putString("cart", "[]")'),'durable cleanup must clear only the cart snapshot that produced the confirmed order');
ok(recovery.includes('runCatching { journal.complete(pending) }'),'post-commit local cleanup must stay non-fatal');
ok(recovery.includes('val pending = journal.read() ?: return null')&&recovery.includes('val receipt = recover(pending) ?: return null'),'pending journal must remain recoverable after local cleanup failure');

console.log(JSON.stringify({checks,failures:0,scope:'Native Customer confirmed-order cart clearing and durable checkout recovery'}));
