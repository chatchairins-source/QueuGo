const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
for(const p of [
  'docs/play-closed-beta-readiness.md',
  'docs/play-store-listing-draft.md',
  'docs/play-app-access-content-rating.md',
  'docs/privacy.html',
  'docs/account-deletion.html',
  'docs/community-guidelines.html'
])ok(fs.existsSync(p),'Play readiness source missing: '+p);
const listing=read('docs/play-store-listing-draft.md');
for(const app of ['QueueGo','QueueGo Merchant','QueueGo Rider'])ok(listing.includes(app),'Store listing must cover '+app);
const access=read('docs/play-app-access-content-rating.md');
const readiness=read('docs/play-closed-beta-readiness.md');
const manifest=JSON.parse(read('docs/pilot-recovery-manifest.json'));
for(const id of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider'])ok(access.includes(id),'App Access draft must cover '+id);
ok(/dedicated Play-review accounts/i.test(access),'Reviewer access must use dedicated reusable accounts');
ok(/Do not use an actual customer/i.test(access),'Reviewer access must forbid real-user credentials');
ok(/Contains ads.*DO NOT GUESS/is.test(access),'Ads declaration must stay an explicit business decision');
ok(/Longdo[\s\S]*Shared\s*=\s*Yes for location/i.test(readiness),'Data Safety must conservatively declare Longdo location sharing');
ok(/Data sharing:\s*\*\*Yes \(conservative\)\*\*/i.test(readiness),'Play readiness must keep conservative data-sharing answer');
ok(manifest?.play?.provider_sharing_classification==='RESOLVED_CONSERVATIVE_LONGDO_LOCATION_SHARED','Provider sharing classification must not regress to pending');
ok(manifest?.play?.data_safety_sharing==='YES_LONGDO_LOCATION_APP_FUNCTIONALITY','Manifest must lock Longdo location sharing for app functionality');
ok(Array.isArray(manifest?.play?.service_provider_exceptions)&&manifest.play.service_provider_exceptions.includes('Supabase')&&manifest.play.service_provider_exceptions.includes('Firebase Cloud Messaging / Google'),'Manifest must retain Supabase/Firebase service-provider treatment');
console.log(JSON.stringify({checks,failures:0,scope:'Play listing, app access, Data Safety provider classification, privacy/account deletion and content-rating preparation'}));
