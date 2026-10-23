const assert=require('assert');
const fs=require('fs');

const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const models=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/Models.kt','utf8');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');

assert(app.includes('RiderCashPanel(')&&app.includes('title = "จ่ายร้าน"'),
  'Rider pickup verification must keep the shop cash amount inline');
assert(app.includes('RiderCustomerCashPanel(job)')&&app.includes('Text("เก็บจากลูกค้า"'),
  'Rider proof screen must retain the authoritative collection breakdown before final cash review');
assert((app.match(/fontSize = 34\.sp/g)||[]).length>=2,
  'Shop-payment and customer-collection totals must remain visually prominent');
assert(app.includes('riderMoneyBaht(item.unitPrice) + " × ${item.quantity}"')&&app.includes('riderMoneyBaht(item.totalPrice)'),
  'Rider item list must show Production unit price × quantity and the line total');
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
assert(app.includes('riderPickupCommittedStatus(mutationResult)')&&
  app.includes('riderPickupReadCanReplace(pickupCommittedStatus, job.id, candidate)')&&
  app.includes('riderApplyPickupCommit(')&&
  app.includes('snapshot = committedRead')&&
  app.includes('snapshot = localAfterSuccess')&&
  !app.includes('openNavigation(context, nextStop)'),
  'Pickup photo success must preserve the committed order through stale reads and remain in QueueGo navigation');

assert(app.includes('RiderSwipeCompleteAction(')&&
  app.includes('if (photoReady && !pickup && !marketPickupMode)')&&
  app.includes('onComplete = onConfirm'),
  'Proof-ready delivery must use the cash-gated swipe confirmation before completion');
assert(!app.includes('RiderCollectionScreen(')&&!app.includes('showCollection')&&!app.includes('verifyCollection'),
  'Delivery must not restore the retired duplicate collection screen');
assert(app.includes('RiderProofSyncRequest(')&&
  app.includes('RiderProofSyncScheduler.enqueue(context, syncRequest)')&&
  app.includes('else -> api.completeWithPhoto('),
  'Delivery confirmation must durably queue proof before reusing the existing completeWithPhoto server mutation');
assert(app.includes('tween(durationMillis = 200)')&&
  app.includes('HapticFeedbackConstants.CONFIRM')&&
  app.includes('onComplete()'),
  'Swipe confirmation must spring back in 200 ms when incomplete and haptically commit at the end');
assert(app.includes('riderCollectionReady(job.customerCash, photoReady, previewReady)'),
  'The completion swipe must require readable proof and authoritative finite cash');

console.log('Native Rider cash breakdown and one-proof one-completion parity contracts: PASS');
