const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const web = fs.readFileSync(path.join(root, 'merchant/index.html'), 'utf8');
const nativeApi = fs.readFileSync(path.join(root, 'native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt'), 'utf8');
const nativeUi = fs.readFileSync(path.join(root, 'native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt'), 'utf8');
let checks = 0;
function ok(condition, message) {
  checks++;
  if (!condition) throw new Error(`FAIL: ${message}`);
}

const orderMapper = web.slice(web.indexOf('function qtOrderFromRow('), web.indexOf('\n}', web.indexOf('function qtOrderFromRow(')));
const detailStart = web.indexOf('window.renderShopOrderModern=function(id){');
const detailEnd = web.indexOf('\n};', detailStart);
const detail = web.slice(detailStart, detailEnd);
const stateDeclaration = detail.indexOf("const orderState=String(o.status||'').toLowerCase();");
const controlsUse = detail.indexOf("['searching_rider','rider_to_shop','preparing'].includes(orderState)");
ok(orderMapper.includes('readyEstimateAt:r.merchant_ready_estimate_at||null') && orderMapper.includes('readyRevision:Number(r.merchant_ready_revision||0)'), 'Web order mapper must read the authoritative ready estimate and revision');
ok(stateDeclaration >= 0 && controlsUse > stateDeclaration, 'Web order state must be declared before ready estimate controls use it');
ok(detail.includes("qgmAdjustReadyEstimate('${esc(o.id)}',-5,this)") && detail.includes("qgmAdjustReadyEstimate('${esc(o.id)}',5,this)") && detail.includes("qgmAdjustReadyEstimate('${esc(o.id)}',10,this)"), 'Web order detail must expose the same -5, +5 and +10 minute adjustments');
ok(web.includes('.qgm-ready-estimate-controls button{flex:1;min-width:0;min-height:36px;') && web.includes('border-radius:10px;background:#fff;color:#e6002d'), 'Web estimate buttons must have tappable QueueGo-styled controls rather than browser-default styling');
const adjust = web.slice(web.indexOf('window.qgmAdjustReadyEstimate=async function('), web.indexOf('\nwindow.renderShopOrderModern=', web.indexOf('window.qgmAdjustReadyEstimate=async function(')));
ok(adjust.includes("['searching_rider','rider_to_shop','preparing'].includes(String(order.status||'').toLowerCase())"), 'Web estimate edits must be limited to the same active order states');
ok(adjust.includes("'rpc/qg_merchant_set_ready_estimate'") && adjust.includes('p_expected_revision:Number(order.readyRevision||0)') && adjust.includes('p_request_id:uid()'), 'Web must use the existing authenticated, idempotent, revision-guarded estimate RPC');
ok(adjust.includes('Math.max(now,Math.min(now+2*60*60000,current+minutes*60000))'), 'Web must apply the native 0..120 minute estimate bounds');
ok(nativeApi.includes('"qg_merchant_set_ready_estimate"') && nativeApi.includes('p_expected_revision'), 'Android must continue using the same server-owned estimate RPC');
ok(['onAdjustReady(-5)', 'onAdjustReady(5)', 'onAdjustReady(10)'].every(step => nativeUi.includes(step)) && nativeUi.includes('order.readyEstimateAt'), 'Android must retain the same adjustment steps and authoritative estimate field');
console.log(`PASS ${checks} merchant ready estimate Web parity checks`);
