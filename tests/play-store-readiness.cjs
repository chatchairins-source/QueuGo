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
ok(manifest?.play?.provider_sharing_classification==='RESOLVED_CONSERVATIVE_LONGDO_LOCATION_SHARED','Provider sharing classification must not regress to pending');
ok(manifest?.play?.data_safety_sharing==='YES_LONGDO_LOCATION_APP_FUNCTIONALITY','Manifest must lock Longdo location sharing for app functionality');
ok(Array.isArray(manifest?.play?.service_provider_exceptions)&&manifest.play.service_provider_exceptions.includes('Supabase')&&manifest.play.service_provider_exceptions.includes('Firebase Cloud Messaging / Google'),'Manifest must retain Supabase/Firebase service-provider treatment');
console.log(JSON.stringify({checks,failures:0,scope:'Play listing, app access, Data Safety, Contains Ads, 18+ Target Audience, privacy/account deletion and content-rating preparation'}));
