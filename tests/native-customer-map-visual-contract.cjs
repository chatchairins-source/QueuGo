'use strict';
// This is source-level defense in depth ONLY. Real Android API26/30/33/35
// instrumented pixel assertions still must pass, and cannot be skipped.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const kotlin=fs.readFileSync('native-android/customer/src/androidTest/java/com/queuego/customer/CustomerLocationP0RuntimeTest.kt','utf8');
const workflow=fs.readFileSync('.github/workflows/native-customer-p0.yml','utf8');
const picker=kotlin.slice(kotlin.indexOf('private fun assertRenderedRoadDetail(index: Int)'));
assert.ok(picker.includes('repeat(8) { attempt ->'),'API30 must probe real GL after a bounded settle window');
assert.ok(picker.includes('map.snapshot { bitmap -> rendered.set(bitmap); completed.countDown() }'),
  'The test must consume a real native MapLibre GL snapshot, never a fake bitmap');
assert.ok(picker.includes('colors.size >= 16')&&picker.includes('edges > samples / 1000'),
  'Real geographic detail thresholds cannot be reduced');
assert.ok(picker.includes('if (attempt < 7) Thread.sleep(700)'),'Snapshot settle must be bounded');
assert.ok(picker.includes('File(directory, "road-detail-$index.png")'),'Save the real last map snapshot');
assert.ok(workflow.includes('matrix:\n        api: [26, 30, 33, 35]'),
  'All four Android API targets must remain instrumented');
assert.ok(workflow.includes('run: python3 native-android/qa/run-customer-p0.py'),
  'Real device/emulator picker instrumentation cannot be bypassed');
console.log('QueueGo Customer Android GL rendered-map source contract PASS; physical pixel CI still REQUIRED');
