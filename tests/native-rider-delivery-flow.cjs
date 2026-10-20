const assert=require('assert');
const fs=require('fs');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');

assert(app.includes('Text("นำทางด้วย QueueGo"')&&!app.includes('Text("นำทางด้วย Google Maps"'),
  'Active Rider job card must expose QueueGo as the only navigation action');
assert(!app.includes('navigationStarted'),
  'Navigation must not use the retired external-map workflow flag');
assert(app.includes('pickup -> "ถึงร้านแล้ว"'));
assert(app.includes('job.status == "in_progress" -> "ถึงลูกค้าแล้ว"'));

const proofStart=app.indexOf('private fun VerificationScreen(');
const proofEnd=app.indexOf('private fun RiderProofCard',proofStart);
assert(proofStart>=0 && proofEnd>proofStart,'Rider verification screen must exist');
const proof=app.slice(proofStart,proofEnd);

assert(!proof.includes('RiderSlideComplete('),
  'Delivery proof/completion flow must never use a slider');
assert(!proof.includes('RiderCollectionScreen('),
  'Delivery must not add a second collection/completion screen');
assert(!proof.includes('showCollection'),
  'Delivery must not restore the removed collection sub-flow');
assert(!app.includes('verifyCollection'),
  'Delivery must not persist a second collection stage');
assert(proof.includes('onClick = if (photoReady) onConfirm else onCamera'),
  'The one proof screen must submit directly after the proof photo is ready');
assert(proof.includes('else -> "จบงาน"'),
  'Delivery must expose one simple completion button after proof');
assert(proof.includes('riderCollectionReady(job.customerCash, photoReady, previewReady)'),
  'Delivery completion must remain blocked until the cash amount and proof are valid');

assert(!app.includes('private fun RiderCollectionScreen('),
  'Removed Rider collection screen must not be reintroduced');
assert(!app.includes('RiderSlideComplete('),
  'Rider delivery app must not reintroduce slide-to-complete');

require('./native-rider-google-maps-return.cjs');
require('./native-rider-route-bundle-parity.cjs');
console.log('Rider delivery: one proof photo / one completion action / no collection slider: PASS');
