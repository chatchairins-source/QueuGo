'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const text=fs.readFileSync('native-android/shared/src/main/java/com/queuego/shared/QueueGoMap.kt','utf8');
const checks=[
  ['real pixel diversity','return colors.size >= 16 && samples > 0 && edges > samples / 1000'],
  ['asynchronous map snapshot','active.snapshot { bitmap ->'],
  ['generation fence','requestedGeneration == snapshotGeneration'],
  ['tile-completion observer','addOnDidFinishLoadingMapListener'],
  ['map idle observer','addOnDidBecomeIdleListener'],
  ['recenter invalidates prior snapshots','snapshotGeneration++'],
  ['ready only after actual detail','!readyReported && hasMapDetail'],
];
for(const [name,needle] of checks)assert.ok(text.includes(needle),name+' missing');
assert.ok(!text.includes('holder.readyReported = true\n                                currentReady.value(true)'), 'Do not accept an idle blank frame as delivery map ready');
console.log('QueueGo Customer map real-GL-pixel readiness source gate PASS; Android API26/30/33/35 runtime remains mandatory');
