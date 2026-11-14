const assert=require('assert');
const fs=require('fs');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const passenger=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderPassengerCard.kt','utf8');
const passengerTest=fs.readFileSync('native-android/rider/src/test/java/com/queuego/rider/RiderPassengerTest.kt','utf8');
const webRide=fs.readFileSync('rider/rider-ride.js','utf8');

assert(app.includes('fun startQueueGoNavigation(')&&
  app.includes('// Accept is the start of work. Enter QueueGo follow/navigation mode')&&
  app.includes('routeOverviewActive = false')&&
  app.includes('recenterSignal += 1')&&
  !app.includes('Text("นำทางด้วย QueueGo"')&&
  !app.includes('Text("นำทางด้วย Google Maps"')&&
  !app.includes('"google.navigation:q="')&&
  !app.includes('GOOGLE_MAPS_PACKAGE'),
  'Rider accept must auto-start QueueGo navigation with no redundant navigation button or external-map handoff');
assert(!app.includes('navigationStarted'),
  'Navigation must not use the retired external-map workflow flag');
assert(app.includes('pickup -> "ถึงร้านแล้ว"'));
assert(app.includes('job.status == "in_progress" -> "ถึงลูกค้าแล้ว"'));

const proofStart=app.indexOf('private fun VerificationScreen(');
const proofEnd=app.indexOf('private fun RiderProofCard',proofStart);
assert(proofStart>=0 && proofEnd>proofStart,'Rider verification screen must exist');
const proof=app.slice(proofStart,proofEnd);

assert(proof.includes('RiderSwipeCompleteAction(')&&
  proof.includes('if (photoReady && !pickup && !marketPickupMode)')&&
  proof.includes('onComplete = onConfirm'),
  'Delivery proof/cash flow must use the cash-gated swipe completion action');
assert(!proof.includes('RiderCollectionScreen('),
  'Delivery must not add the retired duplicate collection screen');
assert(!proof.includes('showCollection'),
  'Delivery must not restore the removed collection sub-flow');
assert(!app.includes('verifyCollection'),
  'Delivery must not persist a second collection stage');
assert(proof.includes('riderCollectionReady(job.customerCash, photoReady, previewReady)'),
  'Delivery completion must remain blocked until the authoritative cash amount and readable proof are valid');
assert(proof.includes('"สไลด์เพื่อยืนยันรับเงินและจบงาน"'),
  'Delivery must tell the rider that the final swipe confirms cash collection and completion');

assert(!app.includes('private fun RiderCollectionScreen('),
  'Removed Rider collection screen must not be reintroduced');
assert(!app.includes('RiderSlideComplete(')&&app.includes('internal fun RiderSwipeCompleteAction('),
  'Rider delivery must use the current cash-gated swipe implementation, not the retired slider');
assert(passenger.includes('showCashConfirm = true')&&passenger.includes('AlertDialog(')&&
  passenger.includes('riderPassengerCashConfirmReady(job.customerCash)'),
  'Passenger Ride completion must match Web with a separate valid-cash confirmation step');
assert(passenger.includes('RiderSwipeCompleteAction(')&&
  passenger.includes('onComplete = { onAction(key) }')&&
  passengerTest.includes('passengerCompletionRequiresAValidCashAmountBeforeTheConfirmationSwipe'),
  'Passenger Ride must require cash confirmation swipe and cover invalid cash boundaries');
assert(webRide.includes("mode:'customer',amount:cash")&&webRide.includes('จบเที่ยว · เก็บเงินสด ฿'),
  'Passenger Ride completion must match the Web-confirmed cash amount and action wording');

require('./native-rider-google-maps-return.cjs');
require('./native-rider-route-bundle-parity.cjs');
console.log('Rider delivery: auto-start QueueGo navigation / one proof / cash-gated swipe completion: PASS');
