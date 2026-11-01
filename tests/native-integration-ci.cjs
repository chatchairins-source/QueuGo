const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const workflow=fs.readFileSync('.github/workflows/build-native-rider-pilot.yml','utf8');
let checks=0;
function selected(job,ref,event='push'){
 const block=workflow.split('\n  '+job+':\n')[1]?.split(/\n  [a-z][a-z-]+:\n/)[0];
 assert.ok(block,'workflow job exists: '+job);
 const condition=block.match(/^    if: \$\{\{ (.*) \}\}$/m)?.[1];
 if(!condition)return true;
 const github=event==='pull_request'
  ? {ref:'refs/pull/123/merge',head_ref:ref,event_name:event}
  : {ref:'refs/heads/'+ref,head_ref:'',event_name:event};
 return vm.runInNewContext(condition,{github,startsWith:(a,b)=>String(a??'').startsWith(String(b??''))});
}
for(const ref of ['work-native-rider-queuego-route-authority-20261017','work-native-ride-entry-recovery-20261018']){
 for(const job of ['native-pilot','continuous-dispatch-races','passenger-ride-races','credit-races','map-display-only','navigation-map-runtime','maplibre-osm-runtime']){
  assert.equal(selected(job,ref),true,ref+' must run '+job+' rather than silently skip an integrated gate');checks++;
 }
 assert.equal(selected('publish-rider-road-test',ref),false,'integration gate must not publish a release');checks++;
}
console.log(JSON.stringify({checks,failures:0,scope:'actual Native Pilot job predicates select integrated dispatch/Ride/credit/map gates without publishing releases'}));

for(const job of ['native-pilot','continuous-dispatch-races','passenger-ride-races','credit-races','express-races']){
 assert.equal(selected(job,'work-native-express-phase1-20261018'),true,'Express successor must run '+job);checks++;
}
assert.equal(selected('publish-rider-road-test','work-native-express-phase1-20261018'),false,'Express integration must not publish a release');checks++;


for(const job of ['native-pilot','continuous-dispatch-races','passenger-ride-races','credit-races','express-races','map-display-only','navigation-map-runtime','maplibre-osm-runtime']){
 assert.equal(selected(job,'work-native-release-unified-20261018'),true,'Unified release successor must run '+job+' rather than silently skip a release gate');checks++;
}
assert.equal(selected('publish-rider-road-test','work-native-release-unified-20261018'),false,'Unified release integration must not publish a road-test release');checks++;

for (const ref of ['work-native-route-bundle-matrix-phase2-20261018', 'work-native-route-bundle-matrix-sqlgate-20261018', 'work-native-route-bundle-readonly-planner-20261018', 'work-native-route-bundle-release-20261018']) {
 for (const job of ['native-pilot','continuous-dispatch-races','passenger-ride-races','credit-races','express-races','map-display-only','navigation-map-runtime','maplibre-osm-runtime']) {
  assert.equal(selected(job,ref),true,ref+' must execute '+job); checks++;
 }
 assert.equal(selected('publish-rider-road-test',ref),false,ref+' must not publish'); checks++;
}
const mapWorkflow=fs.readFileSync('.github/workflows/queuego-map-engine-buriram.yml','utf8');
assert.ok(mapWorkflow.includes("'work-native-route-bundle-*'"),'Route Bundle successors require the existing Map Engine gate'); checks++;
console.log(JSON.stringify({checks,failures:0,scope:'all integrated successor gates, publication excluded'}));

for(const ref of ['work-native-route-bundle-financial-sim-20261018','work-native-route-bundle-future']){
 assert.equal(selected('route-bundle-stack-races',ref),true); checks++;
 assert.equal(selected('publish-rider-road-test',ref),false); checks++;
}
for(const source of ['supabase/functions/queuego-route-bundle-matrix/**','supabase/migrations/*route_bundle*.sql','tests/route-bundle-*']){
 assert.ok(mapWorkflow.split('  pull_request:')[0].includes("'"+source+"'"),'Map Engine must run on exact stack-source changes: '+source); checks++;
}
assert.ok(workflow.includes('run: node tests/route-bundle-stack-races.cjs')); checks++;
console.log(JSON.stringify({checks,failures:0,scope:'generalized stack independent races and exact-head Map Engine selection'}));

// A PR merge ref is not the source branch; GitHub supplies its source as head_ref.
// Simulate both GitHub event shapes so an added head_ref gate cannot crash CI.
for (const event of ['push','pull_request']) {
 const branch='work-native-rider-navigation-map-speed-zoom-runtime-20261101';
 assert.equal(selected('navigation-map-runtime',branch,event),true,event+' must execute installed speed camera runtime'); checks++;
 assert.equal(selected('publish-rider-road-test',branch,event),false,event+' must never publish a road-test APK'); checks++;
 assert.equal(selected('navigation-map-runtime','work-native-unrelated-branch-20261101',event),false,event+' must not misselect unrelated map runtime'); checks++;
}
console.log(JSON.stringify({checks,failures:0,scope:'push/PR head_ref parity for navigation runtime, unrelated branches excluded and release gated'}));

// XML report gate must execute on its own PR merge ref and direct branch push.
for (const event of ['push','pull_request']) {
 const branch='work-native-rider-map-runtime-xml-gate-20261101';
 assert.equal(selected('navigation-map-runtime',branch,event),true,event+' must run actual MapLibre XML report gate'); checks++;
 assert.equal(selected('publish-rider-road-test',branch,event),false,event+' must not publish any APK'); checks++;
}
console.log(JSON.stringify({checks,failures:0,scope:'navigation connected XML gate push/PR selection, release remains blocked'}));
