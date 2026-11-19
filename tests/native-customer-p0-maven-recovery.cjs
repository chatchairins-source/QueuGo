const assert = require('assert/strict');
const fs = require('fs');
const path = require('path');

const workflow = fs.readFileSync(
  path.resolve(__dirname, '../.github/workflows/native-customer-p0.yml'), 'utf8'
);
assert.ok(workflow.includes('api: [26, 30, 33, 35]'), 'full customer API matrix must run');
assert.ok(workflow.includes('node tests/native-customer-p0-maven-recovery.cjs'), 'regression must run on exact workflow head');
assert.ok(workflow.includes('set -euo pipefail'), 'propagate actual build and pipe failures');
const start = workflow.indexOf('      - name: Customer JVM and real UI harness builds');
const end = workflow.indexOf('      - name: Android acceleration', start);
assert.ok(start >= 0 && end > start, 'actual customer build step must be preserved');
const step = workflow.slice(start, end);
for (const task of [':customer:testDebugUnitTest', ':customer:assembleDebug', ':customer:assembleDebugAndroidTest']) {
  assert.ok(step.includes(task), 'must run actual Gradle task ' + task);
}
assert.ok(step.includes('if "${command[@]}" 2>&1 | tee "$first_log"; then'), 'original build must really run before recovery');
assert.ok(step.includes('Could not find (org\\.jetbrains\\.kotlinx:kotlinx-coroutines-test:1\\.10\\.2'), 'only known version-pinned Maven metadata misses are retryable');
assert.ok(step.includes('curl --fail --show-error --silent --location --retry 2'), 'Maven Central metadata must actually be retrieved');
assert.ok(step.includes("grep -Fq '<artifactId>kotlinx-coroutines-test</artifactId>'"), 'metadata must identify expected artifact');
assert.ok(step.includes('test -s "$RUNNER_TEMP/customer-coroutines-test-1.10.2.pom"'), 'empty remote metadata cannot authorize recovery');
assert.equal((step.match(/--refresh-dependencies/g)||[]).length, 1, 'at most one fresh-dependency retry');
assert.ok(step.includes('"${command[@]}" --refresh-dependencies'), 'retry must run exact same real Gradle tasks');
assert.ok(step.includes('non-retryable reason') && step.includes('exit 1'), 'real source/test errors must fail closed');
assert.ok(!step.includes('--offline') && !step.includes('--continue'), 'do not bypass missing dependencies or test failures');
assert.ok(workflow.includes('python3 native-android/qa/run-customer-p0.py'), 'real picker/GL Android instrumentation must remain');
console.log('Customer P0 bounded Maven dependency recovery regression passed');
