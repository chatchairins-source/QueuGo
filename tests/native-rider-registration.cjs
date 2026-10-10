const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const login=read('native-android/rider/src/main/java/com/queuego/rider/RiderLoginScreen.kt');
const app=read('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt');
const screen=read('native-android/rider/src/main/java/com/queuego/rider/RiderRegistrationScreen.kt');
const form=read('native-android/rider/src/main/java/com/queuego/rider/RiderRegistrationForm.kt');
const api=read('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt');

ok(login.includes('onRegister: () -> Unit'),'Rider login must expose the Native registration action');
ok(login.includes('Text("สมัครเป็นไรเดอร์"')&&login.includes('onClick = onRegister'),'Rider login must open Native registration from the signup button');
for(const legacy of ['Intent','Uri','ACTION_VIEW','startActivity']){
  ok(!login.includes(legacy),'Rider signup must not regress to external-browser navigation: '+legacy);
}

ok(app.includes('registrationOpen -> RiderRegistrationScreen'),'QueueGoRiderApp must render the Native Rider registration screen');
ok(app.includes('onRegister = { registrationOpen = true'),'Rider login signup action must open Native registration');
ok(app.includes('api.registerRider(form, password, documents'),'Native Rider registration must submit through QueueGoApi');
ok(app.includes('store.saveRegistrationCheckpoint')&&app.includes('store.finishRegistration'),'Native Rider registration must preserve/recover its checkpoint safely');

for(const label of ['ส่วนตัว','รถและพื้นที่','เอกสาร','ยืนยัน']){
  ok(screen.includes('"'+label+'"'),'Native Rider registration must retain four-step Production flow label: '+label);
}
ok(screen.includes('ActivityResultContracts.OpenDocument'),'Native Rider registration must use the system document picker');
ok(screen.includes('loadRegistrationDraft')&&screen.includes('saveRegistrationDraft'),'Native Rider registration must support local draft resume');
ok(screen.includes('agreement')&&screen.includes('betaAgreement'),'Native Rider registration must require final consent state');

ok(form.includes('isNativeStrongPassword(password)'),'Rider signup must use the shared Native strong-password policy');
for(const key of ['rr-photo','rr-id-front','rr-id-back','rr-license','rr-prb','rr-vehicle-photo']){
  ok(form.includes('"'+key+'"'),'Native Rider registration must retain required document key '+key);
}
ok(form.includes('RIDER_DOCUMENTS.keys.all'),'Native Rider registration must require all mandatory documents before submission');

ok(api.includes('internal suspend fun registerRider'),'QueueGoApi must retain Native Rider registration backend flow');
ok(api.includes('/auth/v1/signup'),'Native Rider registration must create Supabase Auth account when needed');
ok(api.includes('"status","pending"'),'Native Rider registration must create a pending QueueGo user');
ok(api.includes('/rest/v1/rider_profiles'),'Native Rider registration must persist rider profile data');
ok(api.includes('"applicationStatus","pending"'),'Native Rider registration metadata must remain pending for Admin review');
ok(api.includes('RIDER_DOCUMENTS.keys.forEachIndexed'),'Native Rider registration must persist all required document payloads');
ok(api.includes('before.getJSONObject(0).optString("status") == "pending"'),'Resume/update must fail closed after Admin leaves pending state');

console.log(JSON.stringify({checks,failures:0,scope:'Native Rider four-step registration, documents, resume and pending-review wiring'}));
