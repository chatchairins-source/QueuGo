const fs=require('fs'),path=require('path'),assert=require('assert');
let checks=0;
const ok=(v,m)=>{assert.ok(v,m);checks++};

const migration=fs.readFileSync(
  path.resolve(__dirname,'../supabase/migrations/20261022153500_rider_claim_transition_gps.sql'),
  'utf8'
);
const nativeApi=fs.readFileSync(
  path.resolve(__dirname,'../native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt'),
  'utf8'
);
const web=fs.readFileSync(path.resolve(__dirname,'../rider/index.html'),'utf8');

ok(migration.includes("p_kind in ('claim','market_claim')"),
  'claim GPS hardening must stay scoped to Rider claim actions');
ok(migration.includes("perform public.qg_require_live_rider_offer(v_order)")&&
   migration.indexOf("perform public.qg_require_live_rider_offer(v_order)")<
   migration.indexOf("update public.rider_profiles"),
  'server must validate the live targeted offer before accepting claim GPS');
ok(migration.includes("p_payload ? 'p_lat'")&&migration.includes("p_payload ? 'p_lng'"),
  'claim GPS must remain optional for cached clients but supported by current clients');
ok(migration.includes("v_lat not between -90 and 90")&&
   migration.includes("v_lng not between -180 and 180"),
  'claim GPS must reject invalid coordinates');
ok(migration.indexOf("update public.rider_profiles")<
   migration.indexOf("public.rider_claim_order(v_order)")&&
   migration.indexOf("update public.rider_profiles")<
   migration.indexOf("public.market_rider_claim_group(v_order)"),
  'fresh Rider position must be stored in the same transaction before claim changes the order');
ok(migration.includes("qg_rider_action_receipts")&&
   migration.includes("request id already in use"),
  'existing action receipt idempotency must remain intact');

const acceptStart=nativeApi.indexOf('suspend fun acceptOffer(');
const acceptEnd=nativeApi.indexOf('suspend fun liveOfferExpiry(',acceptStart);
const accept=nativeApi.slice(acceptStart,acceptEnd);
ok(acceptStart>=0&&acceptEnd>acceptStart,'Native acceptOffer source must remain discoverable');
ok(accept.includes('RiderNavigationLocationStore.fix.value')&&
   accept.includes('ageMillis in 0L..10_000L'),
  'Native claim must attach only a recent navigation fix');
ok(accept.includes('.put("p_lat", fix.point.latitude)')&&
   accept.includes('.put("p_lng", fix.point.longitude)'),
  'Native claim payload must include current Rider coordinates');
ok(!accept.includes('updateLocation(')&&accept.includes('actionOnce('),
  'Native accept must not add a second network round-trip before the idempotent claim RPC');

const webAcceptStart=web.indexOf('window.acceptJob=async function(orderId)');
const webAcceptEnd=web.indexOf('window.advanceOrder=async function(o)',webAcceptStart);
const webAccept=web.slice(webAcceptStart,webAcceptEnd);
ok(webAcceptStart>=0&&webAcceptEnd>webAcceptStart,'Web acceptJob source must remain discoverable');
ok(webAccept.includes('const claimPayload={p_order_id:orderId}')&&
   webAccept.includes('validRiderCoordinate(S.pos?.lat,S.pos?.lng)')&&
   webAccept.includes('claimPayload.p_lat=Number(S.pos.lat)')&&
   webAccept.includes('claimPayload.p_lng=Number(S.pos.lng)'),
  'Web claim payload must attach current QueueGo Rider coordinates when available');
ok(webAccept.includes('qgRiderActionOnce(kind,claimPayload,token)'),
  'Web accept must preserve the existing idempotent action receipt path');

console.log(JSON.stringify({
  checks,
  failures:0,
  scope:'Rider claim GPS payload + same-transaction audit positioning; source contract only'
}));
