'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflow = fs.readFileSync(path.join(__dirname, '..', '.github', 'workflows', 'queuego-hourly-parallel-android.yml'), 'utf8');

test('hourly Android matrix retains three isolated role lanes and isolated RPC', () => {
  assert.ok(workflow.includes('max-parallel: 3'));
  assert.ok(workflow.includes('fail-fast: false'));
  assert.ok(workflow.includes('role: [customer, merchant, rider]'));
  assert.ok(workflow.includes('rpc-catalog:'));
  assert.ok(workflow.includes('node --test tests/ci-hourly-native-resolver.test.cjs tests/ci-hourly-rider-navigation-gate.test.cjs'));
  assert.ok(workflow.includes("cron: '7 * * * *'"));
});

test('Rider hourly smoke compiles navigation-map JVM tests as well as Rider APK', () => {
  assert.ok(workflow.includes('if [[ "${{ matrix.role }}" == "rider" ]]; then'));
  assert.ok(workflow.includes('tasks+=(":rider-navigation-map:testDebugUnitTest")'));
  assert.ok(workflow.includes('gradle -p native-android "${tasks[@]}" --no-daemon --stacktrace'));
  assert.ok(workflow.includes('native-android/rider-navigation-map/build/test-results/testDebugUnitTest/'));
  assert.ok(workflow.includes('native-android/rider-navigation-map/build/reports/tests/testDebugUnitTest/'));
});
