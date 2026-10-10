const fs=require('fs'),assert=require('assert');
const policy=require('../queuego-password-policy.js');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
ok(policy.MIN_LENGTH===12,'Password minimum must be 12');
ok(policy.validate('QueueGo#2026Secure'),'Strong password must pass');
for(const weak of ['12345678','queuegoqueuego1!','QUEUEGOQUEUEGO1!','QueueGoQueueGo!!','QueueGoQueueGo12']){
  ok(!policy.validate(weak),'Weak password must fail: '+weak);
}
for(const p of ['index.html','merchant/index.html','rider/index.html']){
  const s=fs.readFileSync(p,'utf8');
  ok(s.includes('queuego-password-policy.js'),'Shared password policy must be loaded by '+p);
  ok(s.includes('QueueGoPasswordPolicy'),'Signup flow must use shared password policy in '+p);
}
const customer=fs.readFileSync('index.html','utf8');
const merchant=fs.readFileSync('merchant/index.html','utf8');
const rider=fs.readFileSync('rider/index.html','utf8');
ok(/id="rw"[^>]*minlength="12"/.test(customer),'Customer registration must require 12 chars');
ok(/id="reg-password"[^>]*minlength="12"/.test(merchant),'Merchant registration must require 12 chars');
ok(/id="rr-password"[^>]*minlength="12"/.test(rider),'Rider registration must require 12 chars');
const manifest=JSON.parse(fs.readFileSync('docs/pilot-recovery-manifest.json','utf8'));
ok(manifest?.release_gates?.security_platform_auth==='PASS_FREE_PLAN_CONTROLS','Free-plan Pilot Auth gate must be explicit');
ok(manifest?.security?.leaked_password_protection_enabled===false,'Manifest must not claim leaked-password protection is enabled');
ok(manifest?.security?.leaked_password_protection_plan_requirement==='PRO_OR_ABOVE','Manifest must record the Pro-only leaked-password requirement');
ok(manifest?.security?.leaked_password_protection_release_blocker===false,'Pro-only leaked-password protection must be classified as post-Beta hardening on the Free-plan baseline');
ok(manifest?.android?.auth_gate_accepts_free_plan_compensating_controls===true,'Android release gate must record the certified Free-plan Auth path');
const nativePolicy=fs.readFileSync('native-android/shared/src/main/java/com/queuego/shared/NativeCustomerRegistration.kt','utf8');
const nativeAuth=fs.readFileSync('native-android/shared/src/main/java/com/queuego/shared/NativeAuthApi.kt','utf8');
const nativeCustomer=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/CustomerAuthenticationScreen.kt','utf8');
const nativeRider=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderRegistrationForm.kt','utf8');
ok(/fun isNativeStrongPassword/.test(nativePolicy)&&/password\.length >= 12/.test(nativePolicy),'Native shared password policy must require at least 12 characters');
for(const token of ["password.any { it in 'a'..'z' }","password.any { it in 'A'..'Z' }","password.any { it in '0'..'9' }"]){
  ok(nativePolicy.includes(token),'Native shared password policy missing complexity check: '+token);
}
ok(nativePolicy.includes("it !in 'a'..'z'")&&nativePolicy.includes("it !in 'A'..'Z'")&&nativePolicy.includes("it !in '0'..'9'"),'Native shared password policy must require a symbol');
ok(nativeCustomer.includes('requireNativeStrongPassword(password)'),'Native Customer registration must use the shared password policy');
ok(nativePolicy.includes('NativeMerchantRegistration')&&nativePolicy.includes('requireNativeStrongPassword(password)'),'Native Merchant registration must use the shared password policy');
ok(nativeRider.includes('import com.queuego.shared.isNativeStrongPassword')&&nativeRider.includes('isNativeStrongPassword(password)'),'Native Rider registration must use the shared password policy');
ok(!nativeRider.includes('strongRiderPassword'),'Rider must not keep a duplicate password-policy implementation');
ok(nativeAuth.includes('fun joinPosStaff')&&nativeAuth.includes('requireNativeStrongPassword(password)'),'POS staff signup must use the shared Native password policy');
for(const legacy of ['.github/workflows/build-queuego-pilot-apks.yml','.github/workflows/build-queuego-apks.yml']){
  ok(!fs.existsSync(legacy),'Retired Capacitor workflow must stay absent: '+legacy);
}
console.log(JSON.stringify({checks,failures:0,scope:'shared 12-character password policy across Web and Native Customer, Merchant, Rider and POS staff signup'}));
