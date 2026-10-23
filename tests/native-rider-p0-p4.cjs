const fs = require('fs');
const assert = require('assert');
const root = 'native-android/rider/src/main/java/com/queuego/rider/';
const api = fs.readFileSync(root + 'QueueGoApi.kt', 'utf8');
const app = fs.readFileSync(root + 'QueueGoRiderApp.kt', 'utf8');
const store = fs.readFileSync(root + 'RiderPendingProofStore.kt', 'utf8');
const preview = fs.readFileSync(root + 'RiderEvidencePreview.kt', 'utf8');
const issue = fs.readFileSync(root + 'RiderPickupIssueDialog.kt', 'utf8');
const runtime = fs.readFileSync('native-android/qa/capture-rider-launch.py', 'utf8');
const report = api.slice(api.indexOf('suspend fun reportPickupIssue('), api.indexOf('suspend fun reportDeliveryIssue('));
assert(report.includes('qg_create_ticket') && report.includes('.put("p_order_id", job.id)'));
assert(!/cancel|rider_order_action|qg_rider_action_once|update.*payment/i.test(report), 'Pickup reports must never mutate operational/payment state');
assert(issue.includes('api.pickupIssueOrderId(auth, pickupId)'), 'Market support and contact must resolve exact child order');
assert(issue.includes('if (busy) return@TextButton'));
assert(app.includes('voiceController.startOutgoing(nativeVoiceAuth, orderId, "shop")'));
assert(app.includes('File(context.filesDir, "queuego-evidence")'), 'Pending evidence must survive Android cache eviction');
assert(store.includes('.commit()') && store.includes('fun saveVerification'));
assert(preview.includes('ContentScale.Fit'), 'Proof review must show full frame');
assert(preview.includes('evidenceExposure(oriented)') && preview.includes('withContext(Dispatchers.IO)'));
assert(api.includes('MessageDigest.getInstance("SHA-256")') && api.includes('uploadedEvidenceMatches(auth, path, bytes)'));
assert(api.includes('qg_rider_action_receipts?select=result,kind,payload&request_id=eq.'));
assert(api.includes('mutationProofExists(auth, "qg_delivery_proofs", job.id, path)'));
assert(api.includes('mutationProofExists(auth, "qg_pickup_proofs", orderId, path)'));
assert(
  app.includes('if (actionBusy) return@VerificationScreen') &&
  app.includes('RiderProofSyncScheduler.enqueue(context, syncRequest)') &&
  app.includes('if (!networkAvailable) {') &&
  app.includes('if (!requireServerNetwork()) return@VerificationScreen') &&
  app.indexOf('RiderProofSyncScheduler.enqueue(context, syncRequest)') <
    app.indexOf('if (!requireServerNetwork()) return@VerificationScreen'),
  'Verification must durably queue proof before the network gate so offline retry survives'
);
assert(!app.includes('showCollection')&&!app.includes('verifyCollection')&&
  app.includes('riderCollectionReady(job.customerCash, photoReady, previewReady)'),
  'Restored delivery proof must return to the single proof screen and revalidate proof/cash before completion');
assert(runtime.includes('RiderProofPersistenceTest#') && runtime.includes('"force-stop", "com.queuego.rider"'));
assert(runtime.includes('verify_proof_process_recovery()'));
assert(app.includes('val step = if (pickup) 1 else 2') &&
  app.includes('val stepTitle = if (pickup) "ไปรับสินค้าที่ร้าน" else "ส่งสินค้าให้ลูกค้า"'),
  'Active job must expose explicit two-stage pickup/delivery control center');
assert(app.includes('riderActiveJobEtaLabel(etaSeconds)') &&
  app.includes('internal fun riderActiveJobEtaSeconds(guidance: NavigationGuidanceState): Double?') &&
  app.includes('if (guidance.weakGps || guidance.offRoute) null else guidance.etaSeconds') &&
  app.includes('etaSeconds = riderActiveJobEtaSeconds(navigationGuidance)'),
  'Active job must surface live QueueGo navigation ETA and suppress unreliable guidance');
assert(app.includes('var homeItems by remember') &&
  app.includes('api.orderItems(auth, job.id)') &&
  app.includes('Text("รายการสินค้า"'),
  'Active job must surface a compact real order-item summary');
assert(app.includes('val cashLabel = if (pickup) "ยอดที่ต้องชำระร้าน" else "ยอดที่ต้องเก็บลูกค้า"'),
  'Active job must surface stage-specific cash responsibility');
assert(app.includes('RiderJobTool("jobs", processLabel') &&
  app.includes('RiderJobTool("phone", riderActiveJobContactLabel(pickup)') &&
  app.includes('RiderJobTool("support", "แชต"') &&
  app.includes('"phone" -> R.drawable.qg_rider_flow_phone') &&
  app.includes('api.pickupIssueOrderId(auth, pickupId)') &&
  app.includes('.onSuccess(::startShopVoice)') &&
  app.includes('startShopVoice(current.activeJob.id)') &&
  app.includes('startCustomerVoice(current.activeJob.id)'),
  'Active job must keep details, exact stage-aware contact and chat immediately reachable');
assert(
  app.includes('fun startQueueGoNavigation(') &&
  app.includes('// Accept is the start of work. Enter QueueGo follow/navigation mode') &&
  app.includes('routeOverviewActive = false') &&
  app.includes('recenterSignal += 1') &&
  !app.includes('Text("นำทางด้วย QueueGo"') &&
  !app.includes('นำทางด้วย Google Maps') &&
  !app.includes('"google.navigation:q="') &&
  !app.includes('GOOGLE_MAPS_PACKAGE'),
  'Active food delivery must auto-start inside QueueGo without a redundant navigation button or external-map handoff'
);
assert(app.includes('pushStore.saveHomeSnapshot(auth.user.id, value, durable = true)') &&
  app.includes('kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)'),
  'Acknowledged mutations must persist encrypted snapshots off the main thread before navigation');
for (const stage of ['navigating_merchant','arrived_merchant','pickup_verification','picked_up',
  'navigating_customer','arrived_customer','photo_captured']) assert(runtime.includes('"'+stage+'"'));
assert(!runtime.includes('"cash_collection"'),'Removed cash_collection process stage must never return');
console.log('Rider P0–P4 integration contracts: PASS (authenticated operational E2E remains separate)');
