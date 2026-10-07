const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const workflow=fs.readFileSync(path.join(root,'.github/workflows/build-queuego-pilot-apks.yml'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(/name: QueueGo Android Pilot Test APK/.test(workflow),'Pilot APK workflow must be clearly labeled');
ok(/npm test/.test(workflow),'Pilot APK build must run the full regression suite');
ok(/QG_FIREBASE_GOOGLE_SERVICES_JSON_B64/.test(workflow),'Pilot APK must require Firebase config');
ok(/Firebase config has no Android client/.test(workflow),'Pilot APK must verify Firebase package mapping');
for(const id of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider']){
  ok(workflow.includes('appid: '+id),'Pilot APK workflow must include '+id);
}
ok(/assembleDebug/.test(workflow),'Pilot APK workflow must build debug APKs');
ok(!/bundleRelease|assembleRelease/.test(workflow),'Pilot APK workflow must never create release AAB/APK');
ok(!/QG_ANDROID_KEYSTORE_B64|QG_ANDROID_STORE_PASSWORD|QG_ANDROID_KEY_PASSWORD/.test(workflow),'Pilot APK workflow must not consume release signing secrets');
ok(/pilot-test\.apk/.test(workflow),'Pilot artifact must be unmistakably marked as test-only');
ok(/queuego-native-push\.js/.test(workflow),'Pilot bundle must include native push client');
const pilotCopyLines=workflow.split('\n').filter(line=>line.trim().startsWith('cp '));
const pilotMerchantCopy=pilotCopyLines.find(line=>line.includes('../role-realtime.js'))||'';
const pilotRiderCopy=pilotCopyLines.find(line=>line.includes('../queuego-native-push.js')&&!line.includes('../role-realtime.js')&&!line.includes('../customer-features.js'))||'';
for(const asset of ['../queuego-password-policy.js','../account-deletion.js','../queuego-ugc.js','../queuego-native-push.js']){
  ok(pilotMerchantCopy.includes(asset),'Pilot Merchant bundle must copy '+asset);
  ok(pilotRiderCopy.includes(asset),'Pilot Rider bundle must copy '+asset);
}
ok(workflow.includes("s=s.replace('../queuego-password-policy.js','queuego-password-policy.js')")&&workflow.includes("s=s.replace('../queuego-ugc.js','queuego-ugc.js')"),'Pilot nested roles must rewrite shared runtime paths');
ok(/play-icon-512\.png/.test(workflow)&&/resize\(\(512,512\)/.test(workflow),'Pilot artifacts must include approved 512px Play Store icon');
ok(/@capacitor\/push-notifications/.test(workflow),'Pilot prerequisites must enforce Capacitor push plugin');
ok(/POST_NOTIFICATIONS/.test(workflow),'Pilot APK must verify Android notification permission');
ok(/Generated Android project is not targetSdk 36/.test(workflow)&&/Generated Android project is not compileSdk 36/.test(workflow),'Pilot APK must verify generated API 36 project');

console.log(JSON.stringify({checks,failures:0,scope:'Physical-device Pilot APK build remains debug-only, regression-gated and Firebase-aware'}));
