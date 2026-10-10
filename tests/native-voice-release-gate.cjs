const fs=require('fs');
const assert=require('assert');

function read(path){return fs.readFileSync(path,'utf8')}
function has(text,re,msg){assert(re.test(text),msg)}

const migrationPath='supabase/migrations/20261010070000_native_voice_calls.sql';
const proposalPath='ops/native-voice-call-schema-proposal-20261010.sql';
const migration=read(migrationPath);
const proposal=read(proposalPath);
assert.strictEqual(migration,proposal,'Production voice migration must match reviewed proposal byte-for-byte');

const customerManifest=read('native-android/customer/src/main/AndroidManifest.xml');
const riderManifest=read('native-android/rider/src/main/AndroidManifest.xml');
const merchantManifest=read('native-android/merchant/src/main/AndroidManifest.xml');
has(customerManifest,/android\.permission\.RECORD_AUDIO/,'Customer microphone permission missing');
has(customerManifest,/android\.permission\.POST_NOTIFICATIONS/,'Customer notification permission missing');
has(customerManifest,/QueueGoCustomerMessagingService/,'Customer FCM service missing');
has(customerManifest,/queuego_orders/,'Customer notification channel id missing');
has(riderManifest,/android\.permission\.RECORD_AUDIO/,'Rider microphone permission missing');
assert(!/android\.permission\.RECORD_AUDIO/.test(merchantManifest),
  'Merchant must not request microphone until a real Merchant voice surface exists');

const customerGradle=read('native-android/customer/build.gradle.kts');
has(customerGradle,/file\("google-services\.json"\)\.exists\(\)/,'Customer Google Services must be conditional');
has(customerGradle,/firebase-messaging/,'Customer Firebase Messaging dependency missing');

const push=read('native-android/customer/src/main/java/com/queuego/customer/CustomerPush.kt');
has(push,/UUID\.randomUUID\(\)\.toString\(\)/,'Customer push device id must be a stable UUID');
has(push,/subscribe-native/,'Customer native push subscription missing');
has(push,/platform", "android"/,'Customer native push platform must be Android');
has(push,/auth\.user\.status != "active"/,'Customer push must require active account state');
has(push,/CATEGORY_CALL/,'Incoming voice push must use call notification category');
has(push,/setTimeoutAfter\(50_000L\)/,'Incoming voice notification must expire with ringing window');
assert(!/setFullScreenIntent/.test(push),'Voice push must not use full-screen intent');
assert(!/ACTION_CALL|CALL_PHONE/.test(push),'Voice push must not invoke carrier calling');

const app=read('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
has(app,/syncCustomerNativePush\(context, auth, customerPushStore\)/,'Customer login push sync missing');
has(app,/Manifest\.permission\.POST_NOTIFICATIONS/,'Customer runtime notification permission flow missing');
has(app,/CustomerNativePushApi\(\)\.unsubscribe\(auth, customerPushStore\.deviceId\(\)\)/,
  'Customer logout must unsubscribe native push before logout');
has(app,/voiceController\.refreshIncoming\(auth\)/,'Customer incoming voice discovery loop missing');
has(app,/QueueGoVoiceCallOverlay/,'Customer voice overlay missing');

const tracking=read('native-android/customer/src/main/java/com/queuego/customer/CustomerOrderTrackingScreen.kt');
has(tracking,/onCallShop: \(\) -> Unit/,'Customer shop voice callback missing');
has(tracking,/onCallRider: \(\) -> Unit/,'Customer Rider voice callback missing');
assert(!/ACTION_DIAL|Uri\.fromParts\("tel"/.test(tracking),'Customer tracking must not expose carrier dialer');

const rider=read('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt');
has(rider,/voiceController\.refreshIncoming\(nativeVoiceAuth\)/,'Rider incoming voice discovery loop missing');
has(rider,/startOutgoing\(nativeVoiceAuth, orderId, "customer"\)/,'Rider customer voice action missing');
has(rider,/QueueGoVoiceCallOverlay/,'Rider voice overlay missing');

const overlay=read('native-android/shared/src/main/java/com/queuego/shared/QueueGoVoiceCallOverlay.kt');
for(const token of ['INCOMING','RINGING_OUT','CONNECTING','CONNECTED','รับสาย','ปฏิเสธ','วางสาย','ปิดไมค์','ลำโพง']){
  assert(overlay.includes(token),'Voice overlay missing '+token);
}

const controller=read('native-android/shared/src/main/java/com/queuego/shared/NativeVoiceCallController.kt');
has(controller,/refreshIncoming\(nextAuth: NativeAuth\)/,'Global incoming discovery missing');
has(controller,/delay\(10_000L\)/,'Active-call authorization heartbeat missing');
has(controller,/connection\.send\("ready"/,'Ready handshake missing');
has(controller,/dismissTerminal\(\)/,'Terminal call dismissal missing');
has(controller,/session\.sessionId != next\.session\.sessionId/,'Session replacement must terminate local call');

const realtime=read('native-android/shared/src/main/java/com/queuego/shared/NativeVoiceRealtime.kt');
has(realtime,/Channel<NativeVoiceSignal>\(Channel\.BUFFERED\)/,'Voice signals must buffer before collector starts');
has(realtime,/parseNativeVoiceBroadcast/,'Deterministic voice broadcast parser missing');
has(realtime,/put\("private", true\)/,'Voice Realtime channel must be private');
has(realtime,/ALLOWED_EVENTS = setOf\("ready", "offer", "answer", "ice", "hangup"\)/,
  'Voice signal event allowlist changed unexpectedly');

const voiceApi=read('native-android/shared/src/main/java/com/queuego/shared/NativeVoiceCallApi.kt');
has(voiceApi,/functionPost\(\s*"queuego-turn"/s,'Native client must request ICE through TURN broker');
has(voiceApi,/turnReady", false/,'TURN readiness check missing');
has(voiceApi,/startsWith\("turn:"\).*startsWith\("turns:"\)/s,'Native client must require relay URL');
has(voiceApi,/qg_call_incoming/,'Incoming call RPC client missing');

const peer=read('native-android/shared/src/main/java/com/queuego/shared/NativeVoicePeer.kt');
has(peer,/createAudioTrack/,'WebRTC audio track missing');
assert(!/createVideoTrack|VideoSource|CameraVideoCapturer/.test(peer),'Voice call must remain audio-only');

const turn=read('supabase/functions/queuego-turn/index.ts');
has(turn,/CLOUDFLARE_TURN_KEY_ID/,'TURN key id secret missing');
has(turn,/CLOUDFLARE_TURN_KEY_API_TOKEN/,'TURN API token secret missing');
has(turn,/qg_call_ice_config/,'TURN broker authorization RPC missing');
assert(!/SUPABASE_SERVICE_ROLE_KEY/.test(turn),'TURN broker must not bypass user authorization with service role');
has(turn,/generate-ice-servers/,'Cloudflare short-lived TURN endpoint missing');
has(turn,/ttl:7200/,'TURN TTL must stay bounded');
has(turn,/Cache-Control['"]?:['"]no-store|['"]Cache-Control['"]\s*:\s*['"]no-store/i,'TURN responses must not be cached');

const lower=migration.toLowerCase();
has(migration,/create table if not exists public\.qg_call_sessions/i,'Call session table missing');
has(migration,/alter table public\.qg_call_sessions enable row level security/i,'Call RLS missing');
has(migration,/qg_call_sessions_no_client_access[\s\S]*using\(false\)[\s\S]*with check\(false\)/i,
  'Direct client call table access must be denied');
has(migration,/qg_call_incoming/i,'Incoming call RPC missing');
has(migration,/qg_voice_realtime_allowed/i,'Realtime authorization helper missing');
has(migration,/realtime\.messages/i,'Private Realtime policy missing');
assert(!/\b(sdp|ice_candidate|media_blob|recording)\b/i.test(
  migration.match(/create table if not exists public\.qg_call_sessions \(([\s\S]*?)\n\);/i)?.[1]||''
),'Call table must not persist SDP, ICE, recordings or media blobs');
assert((lower.match(/check_active_session\(p_session_id\)/g)||[]).length>=7,
  'Every voice RPC path must enforce active app session');
assert((lower.match(/lower\(o\.status\) not in \('cancelled','completed','no_rider_available'\)/g)||[]).length>=4,
  'Voice authorization must end with terminal orders');

const workflow=read('.github/workflows/build-native-rider-pilot.yml');
has(workflow,/com\.queuego\.customer/,'CI must inspect optional Customer Firebase package');
has(workflow,/Customer background-push physical certification remains open/,
  'CI must keep Customer background-push certification explicit when Firebase client is absent');

console.log('Native voice release gate: PASS');
