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
assert.ok(step.includes('mapfile -t missing < <(grep -Eo'), 'retry must derive the exact missing pinned artifacts from Gradle log');
assert.ok(step.includes('for coordinate in "${missing[@]}"; do'), 'every recognized missing artifact must be verified');
assert.ok(step.includes('org.hamcrest:hamcrest-library:1.3'), 'Hamcrest 1.3 failure must be handled by its own artifact metadata');
assert.ok(step.includes('group_path="$(printf'), 'group path must be derived without unknown coordinates');
assert.ok(step.includes('metadata="https://repo.maven.apache.org/maven2/${group_path}/${artifact}/${version}/${artifact}-${version}.pom"'), 'download only pinned missing module metadata');
assert.ok(step.includes('grep -Fq "<artifactId>${artifact}</artifactId>" "$pom"'), 'POM must match requested artifactId');
assert.ok(step.includes('test -s "$pom"'), 'empty remote metadata cannot authorize recovery');
assert.ok(!step.includes('customer-coroutines-test-1.10.2.pom'), 'do not hardcode Kotlinx recovery for unrelated Hamcrest failure');
const sdkRunner = fs.readFileSync(path.resolve(__dirname, '../native-android/qa/run-customer-p0.py'), 'utf8');
assert.ok(sdkRunner.includes('for attempt in (1, 2):'), 'Android SDK mirror retry is bounded to 2 attempts');
assert.ok(sdkRunner.includes('if attempt == 2:') && sdkRunner.includes('raise'), 'SDK installation must fail closed after second attempt');
assert.ok(sdkRunner.includes("'system-images;android-'" ) || sdkRunner.includes("f'system-images;android-'") || sdkRunner.includes("image = f'system-images;android-"), 'API-specific system image must remain');
assert.equal((step.match(/--refresh-dependencies/g)||[]).length, 1, 'at most one fresh-dependency retry');
assert.ok(step.includes('"${command[@]}" --refresh-dependencies'), 'retry must run exact same real Gradle tasks');
assert.ok(step.includes('non-retryable reason') && step.includes('exit 1'), 'real source/test errors must fail closed');
assert.ok(!step.includes('--offline') && !step.includes('--continue'), 'do not bypass missing dependencies or test failures');
assert.ok(workflow.includes('python3 native-android/qa/run-customer-p0.py'), 'real picker/GL Android instrumentation must remain');
// Exercise the actual curl flags against a disposable HTTP server. Default
// curl --retry does not retry 404, which reproduced API35's metadata failure.
async function verifyMetadataRetrieval() {
  const http = require('node:http');
  const os = require('node:os');
  const { execFile } = require('node:child_process');
  const execute = require('node:util').promisify(execFile);
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'qg-maven-retry-'));
  const calls = {recover: 0, missing: 0};
  const server = http.createServer((req, res) => {
    const kind = req.url === '/recover' ? 'recover' : 'missing';
    calls[kind]++;
    if (kind === 'recover' && calls[kind] > 1) {
      res.writeHead(200, {'Content-Type': 'application/xml'});
      res.end('<project><artifactId>jsr305</artifactId></project>');
    } else {
      res.writeHead(404); res.end('temporary metadata miss');
    }
  });
  try {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const line = step.split('\n').find(line => line.trim().startsWith('curl '));
    const flags = line.trim().split(/\s+/).slice(1);
    const args = flags.slice(0, flags.indexOf('"$metadata"'));
    assert.ok(args.includes('--retry-all-errors'), 'transient 404 metadata must be retried');
    assert.ok(args.includes('--retry-max-time'), 'remote retrieval retries need a total time bound');
    const url = `http://127.0.0.1:${server.address().port}`;
    const pom = path.join(directory, 'module.pom');
    await execute('curl', [...args, url + '/recover', '-o', pom]);
    assert.equal(calls.recover, 2, 'existing pinned module recovers from one 404');
    assert.ok(fs.readFileSync(pom, 'utf8').includes('<artifactId>jsr305</artifactId>'));
    await assert.rejects(execute('curl', [...args, url + '/missing', '-o', pom]),
      error => error.code === 22, 'persistent 404 remains a hard build failure');
    assert.equal(calls.missing, 3, 'persistent metadata failure stops after initial request plus two retries');
  } finally {
    await new Promise(resolve => server.close(resolve));
    fs.rmSync(directory, {recursive: true, force: true});
  }
  console.log('Customer P0 bounded Maven dependency recovery regression passed (real HTTP 404 recovery and exhaustion)');
}
verifyMetadataRetrieval().catch(error => { console.error(error); process.exitCode = 1; });
