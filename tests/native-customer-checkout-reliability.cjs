const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const app=read('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
const store=read('native-android/customer/src/main/java/com/queuego/customer/CustomerCartStore.kt');
const recovery=read('native-android/customer/src/main/java/com/queuego/customer/CustomerCheckoutRecovery.kt');
const checkout=read('native-android/customer/src/main/java/com/queuego/customer/CustomerCartCheckout.kt');
const web=read('index.html');

const start=app.indexOf('fun showPlacedOrder(order: CustomerOrder)');
const end=app.indexOf('\n    LaunchedEffect(',start);
ok(start>=0&&end>start,'Native Customer confirmed-order handler must exist');
const placed=app.slice(start,end);
ok(placed.includes('lastCheckoutReceipt = order.id'),'confirmed-order handler must bind the server receipt');
ok(placed.includes('cart = emptyList()'),'server-confirmed order must clear the visible cart immediately');
ok(
  placed.includes('scope.launch(Dispatchers.IO) {') &&
  placed.includes('runCatching { cartStore.save(emptyList()) }'),
  'confirmed-order durable cart cleanup must be best-effort and explicitly off Main'
);
const persistenceStart=app.indexOf('LaunchedEffect(cart, localStateLoaded)');
const persistenceEnd=app.indexOf('LaunchedEffect(auth.session.accessToken, screen',persistenceStart);
const persistence=app.slice(persistenceStart,persistenceEnd);
ok(persistence.includes('withContext(Dispatchers.IO) { cartStore.save(cart) }')&&persistence.includes('catch (_: Exception)'),'reactive cart persistence must be off Main and non-fatal after confirmation');
ok(persistence.includes('catch (e: CancellationException) { throw e }'),'cart persistence must propagate lifecycle cancellation');
ok(!placed.includes('cart = cartStore.load()'),'confirmed-order handler must never reload a stale submitted cart');
ok(placed.includes('selectedOrder = order')&&placed.includes('screen = "order"'),'confirmed order must open tracking directly');
ok(placed.includes('pendingCheckoutRecord = null')&&placed.includes('checkoutPending = false'),'confirmed-order UI must release checkout pending state');

ok(store.includes('if ((boundedPref("cart", CUSTOMER_CART_MAX_JSON_CHARS) ?: "[]") == pending.cartSnapshot) edit.putString("cart", "[]")'),'durable cleanup must clear only the cart snapshot that produced the confirmed order');
ok(recovery.includes('runCatching { withContext(Dispatchers.IO) { journal.complete(pending) } }'),'post-commit local cleanup must stay non-fatal and off Main');
ok(recovery.includes('val pending = withContext(Dispatchers.IO) { journal.read() } ?: return null')&&recovery.includes('val receipt = recover(pending) ?: return null'),'pending journal must remain recoverable after local cleanup failure without reading durable state on Main');

ok(checkout.includes('val checkoutMapHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.28f)') &&
   checkout.includes('.coerceIn(200.dp, 300.dp)') &&
   checkout.includes('.height(checkoutMapHeight)') &&
   !checkout.includes('.height(115.dp)'),
   'Native Customer checkout map must be touch-usable and responsive on mobile/tablet');
ok(web.includes('id="checkout-pin-map" class="mapbox" style="height:clamp(200px,28dvh,300px)') &&
   !web.includes('id="checkout-pin-map" class="mapbox" style="height:115px'),
   'Web checkout must share the same touch-usable responsive map height');
console.log(JSON.stringify({checks,failures:0,scope:'Native Customer confirmed-order cart clearing and durable checkout recovery'}));
