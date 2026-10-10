const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
for(const p of [
  'docs/play-closed-beta-readiness.md',
  'docs/play-store-listing-draft.md',
  'docs/play-app-access-content-rating.md',
  'docs/privacy.html',
  'docs/account-deletion.html',
  'docs/community-guidelines.html',
  '.github/workflows/release-secret-readiness.yml'
])ok(fs.existsSync(p),'Play readiness source missing: '+p);
const listing=read('docs/play-store-listing-draft.md');
for(const app of ['QueueGo','QueueGo Merchant','QueueGo Rider'])ok(listing.includes(app),'Store listing must cover '+app);
const access=read('docs/play-app-access-content-rating.md');
const readiness=read('docs/play-closed-beta-readiness.md');
const manifest=JSON.parse(read('docs/pilot-recovery-manifest.json'));
const releaseSecrets=read('.github/workflows/release-secret-readiness.yml');
for(const id of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider'])ok(access.includes(id),'App Access draft must cover '+id);
ok(/workflow_dispatch/.test(releaseSecrets),'Release secret preflight must be manually runnable after owner configures secrets');
for(const id of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider'])ok(releaseSecrets.includes(id),'Firebase preflight must require Android client '+id);
ok(releaseSecrets.includes('base64 --decode > /tmp/google-services.json'),'Release preflight must decode Firebase config without committing it');
ok(releaseSecrets.includes("mobilesdk_app_id"),'Firebase preflight must reject incomplete Android clients without mobilesdk_app_id');
ok(releaseSecrets.includes('len(matches) != 1'),'Firebase preflight must require exactly one client per QueueGo package');
ok(releaseSecrets.includes("project_id"),'Firebase preflight must require explicit Firebase project identity');
ok(releaseSecrets.includes('keytool -list')&&releaseSecrets.includes('keytool -importkeystore'),'Release preflight must validate keystore, alias and key password');
ok(releaseSecrets.includes('Clean temporary credentials'),'Release preflight must remove decoded credentials from the runner');
ok(/dedicated Play-review accounts/i.test(access),'Reviewer access must use dedicated reusable accounts');
ok(/Do not use an actual customer/i.test(access),'Reviewer access must forbid real-user credentials');
ok(/Contains ads.*RESOLVED FOR CURRENT BUILD/is.test(access),'Contains Ads decision must remain resolved for current build');
ok(/QueueGo Customer[\s\S]*Yes — Contains ads/i.test(access),'Customer app must declare Contains ads = Yes');
ok(/QueueGo Merchant[\s\S]*No\./i.test(access),'Merchant app must declare Contains ads = No');
ok(/QueueGo Rider[\s\S]*No\./i.test(access),'Rider app must declare Contains ads = No');
ok(manifest?.play?.contains_ads?.customer==='YES'&&manifest?.play?.contains_ads?.merchant==='NO'&&manifest?.play?.contains_ads?.rider==='NO','Manifest must lock per-app Contains Ads answers');
ok(/Target Audience decision.*CLOSED BETA/is.test(access),'Target Audience decision must remain explicit for Closed Beta');
ok(/QueueGo Customer[\s\S]*Ages 18 and over only/i.test(access)&&/QueueGo Merchant[\s\S]*Ages 18 and over only/i.test(access)&&/QueueGo Rider[\s\S]*Ages 18 and over only/i.test(access),'All three Play apps must remain 18+ only for Closed Beta');
ok(manifest?.play?.target_audience?.customer==='18_PLUS_ONLY'&&manifest?.play?.target_audience?.merchant==='18_PLUS_ONLY'&&manifest?.play?.target_audience?.rider==='18_PLUS_ONLY','Target Audience must remain 18+ only for all apps');
ok(manifest?.play?.restrict_minor_access==='ENABLE_FOR_CLOSED_BETA','Restrict Minor Access must remain enabled for Closed Beta');
ok(/Longdo[\s\S]*Shared\s*=\s*Yes for location/i.test(readiness),'Data Safety must conservatively declare Longdo location sharing');
ok(/Data sharing:\s*\*\*Yes \(conservative\)\*\*/i.test(readiness),'Play readiness must keep conservative data-sharing answer');

ok(/Native Android Customer, Merchant, Rider apps/i.test(readiness),'Play readiness must identify Native apps as the certification scope');
ok(/RECORD_AUDIO for optional order-scoped audio calls/i.test(readiness),'Native voice microphone disclosure must remain explicit');
ok(/FOREGROUND_SERVICE_MICROPHONE/i.test(readiness),'Native microphone foreground-service permission must remain documented');
ok(/Incoming FCM does not start microphone access in the background/i.test(readiness),'Play readiness must preserve visible-user-action microphone start boundary');
ok(!/Explicitly forbidden by the release workflow:[\s\S]{0,300}RECORD_AUDIO/i.test(readiness),'Legacy Capacitor RECORD_AUDIO prohibition must not return');
ok(/Native CI rejects WebView/i.test(readiness),'Data Safety draft must use Native no-WebView certification scope');
ok(!/data collected by the web application running inside Capacitor/i.test(readiness),'Legacy Capacitor Data Safety collection rule must not certify Native apps');
ok(!/android-build\/package\.json[^\n]*Capacitor Push Notifications/i.test(readiness),'Legacy Capacitor push inventory must not remain Native Play evidence');

ok(manifest?.play?.provider_sharing_classification==='RESOLVED_CONSERVATIVE_LONGDO_LOCATION_SHARED','Provider sharing classification must not regress to pending');
ok(manifest?.play?.data_safety_sharing==='YES_LONGDO_LOCATION_APP_FUNCTIONALITY','Manifest must lock Longdo location sharing for app functionality');
ok(Array.isArray(manifest?.play?.service_provider_exceptions)&&manifest.play.service_provider_exceptions.includes('Supabase')&&manifest.play.service_provider_exceptions.includes('Firebase Cloud Messaging / Google'),'Manifest must retain Supabase/Firebase service-provider treatment');
ok(manifest?.android?.native_scope==='Customer/Merchant/Rider','Recovery manifest must identify Native Android as the active release scope');
ok(manifest?.android?.legacy_capacitor_release_workflows_retired===true,'Legacy Capacitor release workflows must remain retired');
ok(!('capacitor_version' in (manifest.android||{}))&&!('push_plugin' in (manifest.android||{})),'Native recovery manifest must not treat Capacitor as the active Android runtime');
ok(Array.isArray(manifest?.android?.allowed_android_build_workflows)&&manifest.android.allowed_android_build_workflows.length===1&&manifest.android.allowed_android_build_workflows[0]==='.github/workflows/build-native-rider-pilot.yml','Only the Native Android pilot workflow may remain an active Android build workflow');
console.log(JSON.stringify({checks,failures:0,scope:'Play listing, app access, Data Safety, Contains Ads, 18+ Target Audience, privacy/account deletion and content-rating preparation'}));
