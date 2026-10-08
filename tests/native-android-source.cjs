const fs=require('fs'),assert=require('assert'),path=require('path');
const root=path.resolve(__dirname,'..','native-android');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

for(const p of [
  'settings.gradle.kts',
  'build.gradle.kts',
  'rider/build.gradle.kts',
  'rider/src/main/AndroidManifest.xml',
  'rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt',
  'rider/src/main/java/com/queuego/rider/QueueGoApi.kt'
]) ok(fs.existsSync(path.join(root,p)),'missing '+p);

const gradle=read('rider/build.gradle.kts');
ok(gradle.includes('applicationId = "com.queuego.rider"'),'Rider package id');
ok(/compileSdk\s*=\s*36/.test(gradle),'compileSdk 36');
ok(/targetSdk\s*=\s*36/.test(gradle),'targetSdk 36');

const all=[];
(function walk(dir){
  for(const name of fs.readdirSync(dir)){
    const p=path.join(dir,name),st=fs.statSync(p);
    if(st.isDirectory())walk(p); else if(/\.(kt|kts|xml)$/.test(name))all.push(fs.readFileSync(p,'utf8'));
  }
})(root);
const source=all.join('\n');

ok(!/android\.webkit\.WebView|<WebView\b|loadUrl\(/.test(source),'native Rider must not use WebView UI');
ok(source.includes('claim_active_session'),'reuse active-session claim');
ok(source.includes('check_active_session')&&source.includes('touch_active_session'),'reuse session guard');
ok(source.includes('get_rider_delivery_pool')&&source.includes('qg_get_my_rider_offer'),'reuse sequential server dispatch');
ok(source.includes('status=in.(rider_assigned,preparing,ready,assigned,picked_up,in_progress)'),'reuse active order states');
ok(source.includes('AndroidKeyStore')&&source.includes('AES/GCM/NoPadding'),'encrypted session storage');
ok(source.includes('qg_rider_mark_arrival'),'native Rider must reuse arrival RPC');\nok(source.includes('qg_pickup_with_photo')&&source.includes('qg_complete_with_photo'),'native Rider must reuse photo proof RPCs');\nok(source.includes('/storage/v1/object/qg-evidence/'),'native Rider must upload proof to existing evidence bucket');\nok(source.includes('qg_rider_decline_offer'),'native Rider must preserve sequential offer decline');\nok(source.includes('FileProvider')&&source.includes('TakePicture'),'native Rider must use Android camera flow');\nok(!/cash-confirm|ยืนยันชำระเงินให้ร้าน|ยืนยันเก็บเงินจากลูกค้า/i.test(source),'native Rider must not reintroduce manual cash confirmation screens');\nok(!/service_role|sb_secret_/i.test(source),'no privileged Supabase secret');
console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Rider native Android source'}));
