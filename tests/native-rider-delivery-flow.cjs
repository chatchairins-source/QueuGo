const assert=require('assert');
const fs=require('fs');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
assert(app.includes('Text("นำทางด้วย Google Maps"'),
  'Active Rider job card must expose Google Maps as a separate optional action');
assert(!app.includes('navigationStarted'),
  'External Google Maps must not own or advance the Rider workflow state');
assert(app.includes('pickup -> "ถึงร้านแล้ว"'));
assert(app.includes('job.status == "in_progress" -> "ถึงลูกค้าแล้ว"'));
const proof=app.slice(app.indexOf('private fun VerificationScreen('),app.indexOf('/** Final cash review'));
assert(!proof.includes('RiderSlideComplete('),'Photo/proof stage must use buttons, never a slider');
assert(proof.includes('onClick = { showCollection = true }'));
assert(proof.includes('!busy && collectionReady'));
assert(proof.includes('onClick = if (photoReady) onConfirm else onCamera'));
const final=app.slice(app.indexOf('private fun RiderCollectionScreen('),app.indexOf('private fun RiderProofCard'));
assert(final.includes('RiderSlideComplete(') && final.includes('job.customerCash'));
assert(final.includes('onComplete = onComplete') && !final.includes('api.'));
assert(final.includes('BackHandler { if (!busy) onBack() }'));
require('./native-rider-google-maps-return.cjs');
require('./native-rider-route-bundle-parity.cjs');
console.log('Rider in-app workflow / optional Google Maps / photo button / final cash slider: PASS');
