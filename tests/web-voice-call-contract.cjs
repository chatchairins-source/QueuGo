'use strict';

const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const acorn=require('acorn');

const root=path.resolve(__dirname,'..');
const voice=fs.readFileSync(path.join(root,'queuego-voice-web.js'),'utf8');
const customer=fs.readFileSync(path.join(root,'customer-features.js'),'utf8');
const index=fs.readFileSync(path.join(root,'index.html'),'utf8');
const rider=fs.readFileSync(path.join(root,'rider/index.html'),'utf8');
const merchant=fs.readFileSync(path.join(root,'merchant/index.html'),'utf8');

assert.doesNotThrow(()=>acorn.parse(voice,{ecmaVersion:'latest',sourceType:'script'}),'shared web voice client must parse');

for(const rpc of ['qg_call_start','qg_call_incoming','qg_call_answer','qg_call_decline','qg_call_end','qg_call_active']){
  assert.match(voice,new RegExp(rpc),'web voice must reuse Production '+rpc);
}
assert.match(voice,/functions\/v1\/queuego-turn/,'web voice must reuse Production TURN edge function');
assert.match(voice,/new RTCPeerConnection\(/,'web voice must use browser WebRTC');
assert.match(voice,/getUserMedia\(\{audio:/,'web voice must request audio only');
assert.match(voice,/video:false/,'web voice must not request video');
assert.match(voice,/private:true/,'voice signaling must use a private Realtime channel');
assert.match(voice,/heartbeatTimer/,'web Realtime signaling must keep the private channel alive');
assert.match(voice,/topic:'phoenix'|envelope\('phoenix','heartbeat'/,'web Realtime signaling must send Phoenix heartbeats');
for(const event of ['ready','offer','answer','ice','hangup'])assert.match(voice,new RegExp("'"+event+"'"),'missing signaling event '+event);
assert.match(voice,/check_active_session/,'web voice must validate active QueueGo session');
assert.match(voice,/claim_active_session/,'legacy web sessions must be upgraded through the existing active-session RPC');
assert.match(voice,/startRpcWithRecovery/,'web voice must recover a server-side stale call before redial');
assert.match(voice,/call already active/,'web voice recovery must handle the Production stale-call error');
assert.match(voice,/qg_call_active/,'web voice stale-call recovery must inspect the active call before ending it');
assert.match(voice,/keepalive:true/,'web voice must end an active call during page unload when possible');
assert.match(voice,/addEventListener\('pagehide'/,'web voice must install page-unload cleanup');
assert.doesNotMatch(voice,/MediaRecorder/,'voice media must not be recorded');
assert.doesNotMatch(voice,/storage\/v1/,'voice media must not be uploaded to Storage');
assert.doesNotMatch(voice,/service[_-]?role/i,'browser voice client must never contain service-role access');

assert.match(index,/queuego-voice-web\.js\?v=20261012-voice2/,'Customer web must load the shared voice client');
assert.match(customer,/QueueGoVoiceWeb\.create/,'Customer must initialize shared voice controller');
assert.match(customer,/p_target|data-qg-voice-target|startVoiceCall/,'Customer order UI must expose QueueGo voice actions');
assert.match(customer,/startVoiceCall\(id,'rider'/,'Customer chat must call the assigned Rider through QueueGo');

assert.match(rider,/queuego-voice-web\.js\?v=20261012-voice2/,'Rider web must load the shared voice client');
assert.match(rider,/QueueGoVoiceWeb\.create/,'Rider must initialize shared voice controller');
assert.match(rider,/controller\.start\(order\.id,'customer'/,'Rider must call only the order customer');
assert.match(rider,/id="chat-call"/,'Rider chat must expose a QueueGo call action');
assert.match(rider,/id="qg-delivery-call"/,'Rider delivery flow must expose a QueueGo call action');

assert.match(merchant,/\.\.\/queuego-voice-web\.js\?v=20261012-voice2/,'Merchant web must load the shared voice client');
assert.match(merchant,/QueueGoVoiceWeb\.create/,'Merchant must initialize shared voice controller');
assert.match(merchant,/controller\.start\(order\.id,'customer'/,'Merchant must call only the order customer');
assert.match(merchant,/id="qgm-order-call-customer"/,'Merchant active order must expose a QueueGo customer call action');
assert.match(merchant,/QGM_VOICE_CONTROLLER\.destroy/,'Merchant logout must close voice transport');

console.log('web voice call contract: OK');
