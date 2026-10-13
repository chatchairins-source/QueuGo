const assert=require('assert');
const fs=require('fs');

const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const models=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/Models.kt','utf8');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
const slide=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderSlideComplete.kt','utf8');

assert(app.includes('RiderCashPanel(')&&app.includes('title = "จ่ายร้าน"'),
  'Rider pickup verification must keep the shop cash amount inline');
assert(app.includes('RiderCustomerCashPanel(job)')&&app.includes('Text("เก็บจากลูกค้า"'),
  'Rider delivery verification must keep customer collection inline without another confirmation screen');
for(const label of ['"ที่อยู่ร้าน"','"ยอดสินค้า"','"ค่าส่ง"','"ยอดที่ต้องเก็บทั้งหมด"','"เก็บเงินตามยอดนี้จากลูกค้า"']){
  assert(app.includes(label),'Rider cash workflow detail missing: '+label);
}
assert(models.includes('customerSubtotal: Double? = null')&&models.includes('customerDeliveryFee: Double? = null')&&
  models.includes('customerServiceFee: Double? = null'),
  'Rider job must preserve customer-facing cash breakdown separately from Rider income');
assert(api.includes('customerSubtotal = o.doubleOrNull("subtotal")')&&api.includes('customerDeliveryFee = o.doubleOrNull("delivery_fee")'),
  'Normal Rider orders must map customer subtotal and delivery fee from Production');
assert(api.includes('select=subtotal,delivery_fee,multi_shop_service_fee,rider_bonus,total_amount')&&
  api.includes('customerServiceFee = parent.doubleOrNull("multi_shop_service_fee")'),
  'Market Rider cash breakdown must use Production parent totals without treating Rider bonus as customer delivery fee');

assert(app.includes('RiderSlideComplete(')&&app.includes('label = "สไลด์เพื่อส่งสำเร็จ"'),
  'Delivery verification must use slide-to-complete');
assert(app.includes('if (photoReady && !pickup && !marketPickupMode)'),
  'Slide-to-complete must only replace the final delivery action after proof is captured');
assert(app.includes('onComplete = onConfirm'),
  'Slide completion must reuse the existing completeWithPhoto path');
assert(!/CashConfirm|cashConfirm|qgOpenCashConfirmPage/.test(app),
  'Native Rider must not introduce a separate cash-confirm screen');

assert(slide.includes('detectHorizontalDragGestures('),'Slide control must require a horizontal gesture');
assert(slide.includes('if (progress >= 0.92f)'),'Slide control must require near-complete travel');
assert(slide.includes('onDragCancel = { progress = 0f }')&&slide.includes('progress = 0f'),
  'Incomplete or cancelled drags must reset');
assert(slide.includes('completing = true')&&slide.includes('onComplete()'),
  'Slide must fire completion once at the end of a successful drag');

console.log('Native Rider cash breakdown and slide-to-complete parity contracts: PASS');
