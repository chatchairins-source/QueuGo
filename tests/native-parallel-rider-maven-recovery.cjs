const assert = require('assert/strict');
const fs = require('fs');
const path = require('path');

const workflow = fs.readFileSync(
  path.resolve(__dirname, '../.github/workflows/build-native-rider-pilot.yml'), 'utf8'
);
const jobStart = workflow.indexOf('  parallel-role-smoke:');
const jobEnd = workflow.indexOf('\n  native-pilot:', jobStart);
assert.ok(jobStart >= 0 && jobEnd > jobStart, 'real independent role job must exist');
const job = workflow.slice(jobStart, jobEnd);
assert.ok(job.includes('role: [customer, merchant, rider]'), 'smoke matrix must still build all three roles');
assert.ok(job.includes('fail-fast: false'), 'other role builds must not be cancelled by Rider failure');
assert.ok(job.includes('node tests/native-parallel-rider-maven-recovery.cjs'), 'guard must run in actual job');
assert.ok(job.includes('if "${command[@]}" 2>&1 | tee "$log"; then'), 'first Gradle build must really run');
assert.ok(job.includes('if [[ "$role" != rider ]] || ! grep -Fq'), 'only Rider missing pinned Maven dependency may retry');
assert.ok(job.includes('Could not find io.github.rallista:valhalla-mobile:0.6.4'), 'exact missing dependency must identify retry');
assert.ok(job.includes('https://repo.maven.apache.org/maven2/io/github/rallista/valhalla-mobile/0.6.4/valhalla-mobile-0.6.4.pom'), 'use exact official Maven Central metadata');
assert.ok(job.includes("grep -Fq '<artifactId>valhalla-mobile</artifactId>'"), 'metadata must identify real artifact');
assert.ok(job.includes('--refresh-dependencies'), 'second Gradle attempt must avoid cached missing dependency');
assert.ok(job.includes('"${command[@]}" --refresh-dependencies'), 'still run unmodified real JVM tests and APK build');
assert.ok(!job.includes('--offline') && !job.includes('--continue'), 'do not bypass missing binary or failing test');
assert.ok(job.includes('test -s "$RUNNER_TEMP/valhalla-mobile-0.6.4.pom"'), 'missing metadata must fail closed');
console.log('Native Rider parallel Maven dependency recovery guard passed');
