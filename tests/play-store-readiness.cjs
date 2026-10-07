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
for(const id of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider'])ok(access.includes(id),'App Access draft must cover '+id);
ok(/dedicated Play-review accounts/i.test(access),'Reviewer access must use dedicated reusable accounts');
ok(/Do not use an actual customer/i.test(access),'Reviewer access must forbid real-user credentials');
ok(/Contains ads.*DO NOT GUESS/is.test(access),'Ads declaration must stay an explicit business decision');
console.log(JSON.stringify({checks,failures:0,scope:'Play listing, app access, privacy/account deletion and content-rating preparation'}));
