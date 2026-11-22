'use strict';
const fs=require('node:fs');
const assert=require('node:assert/strict');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const workflow=fs.readFileSync(path.join(root,'.github/workflows/rider-offline-routing.yml'),'utf8');
const build=workflow.split('      - name: Exact-source JVM tests and Rider Debug APK\n')[1]?.split('\n      - ')[0];
let checks=0;
assert(build,'the actual offline candidate build must exist');checks++;
assert(build.includes('python3 native-android/qa/gradle_maven_recovery.py'));checks++;
assert(build.includes('--log artifacts/rider-offline-routing/gradle-first-attempt.log --'));checks++;
for(const task of [':rider-navigation-map:testDebugUnitTest',':rider-maplibre-osm:testDebugUnitTest',
 ':rider:testDebugUnitTest',':rider:assembleDebug',':rider:assembleDebugAndroidTest']){
 assert(build.includes(task),'recovery must retain '+task);checks++;
}
assert(!/continue-on-error|--continue|--offline|\|\| true/.test(build));checks++;
assert(build.includes('git rev-parse HEAD')&&build.includes('apk.sha256')&&build.includes('apksigner'));checks++;
for(const trigger of ['native-android/qa/gradle_maven_recovery.py','native-android/rider-maplibre-osm/**']){
 assert(workflow.includes("'"+trigger+"'"),'actual routing acceptance must run on '+trigger);checks++;
}
assert(workflow.includes('path: |\n            artifacts/rider-offline-routing/'),'first/retry logs stay in retained failure evidence');checks++;
assert(fs.readFileSync(path.join(root,'.github/workflows/native-country-offline-runtime.yml'),'utf8')
 .includes('native-android/qa/gradle_maven_recovery.py'),'installed-country acceptance retriggers on shared recovery changes');checks++;
console.log(JSON.stringify({checks,failures:0,scope:'offline build retains exact Gradle tasks, source/APK fingerprints and failed logs under shared bounded Maven recovery; actual emulator acceptance separate'}));
