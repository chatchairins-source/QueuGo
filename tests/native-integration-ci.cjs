const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const workflow=fs.readFileSync('.github/workflows/build-native-rider-pilot.yml','utf8');
let checks=0;
function selected(job,ref){
 const block=workflow.split('\n  '+job+':\n')[1]?.split(/\n  [a-z][a-z-]+:\n/)[0];
 assert.ok(block,'workflow job exists: '+job);
 const condition=block.match(/^    if: \$\{\{ (.*) \}\}$/m)?.[1];
 if(!condition)return true;
 return vm.runInNewContext(condition,{github:{ref:'refs/heads/'+ref,event_name:'push'},startsWith:(a,b)=>a.startsWith(b)});
}
for(const ref of ['work-native-rider-queuego-route-authority-20261017','work-native-ride-entry-recovery-20261018']){
 for(const job of ['native-pilot','continuous-dispatch-races','passenger-ride-races','credit-races','map-display-only','navigation-map-runtime','maplibre-osm-runtime']){
  assert.equal(selected(job,ref),true,ref+' must run '+job+' rather than silently skip an integrated gate');checks++;
 }
 assert.equal(selected('publish-rider-road-test',ref),false,'integration gate must not publish a release');checks++;
}
console.log(JSON.stringify({checks,failures:0,scope:'actual Native Pilot job predicates select integrated dispatch/Ride/credit/map gates without publishing releases'}));
