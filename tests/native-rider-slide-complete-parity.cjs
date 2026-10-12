const assert=require('assert');
const fs=require('fs');

const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const slide=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderSlideComplete.kt','utf8');

assert(app.includes('RiderCashPanel('),'Rider verification must keep cash amounts inline');
assert(app.includes('title = "จ่ายร้าน"')&&app.includes('title = "เก็บจากลูกค้า"'),
  'Rider must show shop payment and customer collection amounts without a separate confirm screen');
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

console.log('Native Rider slide-to-complete parity contracts: PASS');
