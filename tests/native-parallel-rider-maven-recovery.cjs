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

// Exercise the actual parallel-job curl options, not a separately chosen retry
// command. No real repository endpoint or Gradle task is mocked as passing.
async function metadataRetryRuntime() {
  const http = require('node:http');
  const os = require('node:os');
  const execute = require('node:util').promisify(require('node:child_process').execFile);
  const directory = fs.mkdtempSync(path.join(os.tmpdir(),'qg-rider-maven-'));
  const calls = {recover:0,missing:0};
  const server = http.createServer((request,response)=>{
    const kind=request.url==='/recover'?'recover':'missing'; calls[kind]++;
    if(kind==='recover' && calls[kind]===2) {
      response.writeHead(200,{'Content-Type':'application/xml'});
      response.end('<project><artifactId>valhalla-mobile</artifactId></project>');
    } else {response.writeHead(404);response.end('metadata miss');}
  });
  try {
    await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
    const line=job.split('\n').find(value=>value.trim().startsWith('curl '));
    const flags=line.trim().split(/\s+/).slice(1);
    const args=flags.slice(0,flags.indexOf('"$artifact"'));
    assert.ok(args.includes('--retry-all-errors'),'404 must enter the bounded metadata retry');
    assert.equal(args[args.indexOf('--retry')+1],'2','never retry indefinitely');
    assert.equal(args[args.indexOf('--retry-max-time')+1],'45','retry time is bounded');
    const base='http://127.0.0.1:'+server.address().port;
    const pom=path.join(directory,'valhalla.pom');
    await execute('curl',[...args,base+'/recover','-o',pom]);
    assert.equal(calls.recover,2,'one transient 404 must recover');
    assert.ok(fs.readFileSync(pom,'utf8').includes('<artifactId>valhalla-mobile</artifactId>'));
    await assert.rejects(execute('curl',[...args,base+'/missing','-o',pom]),error=>error.code===22);
    assert.equal(calls.missing,3,'permanent 404 must stop after initial request plus two retries');
    assert.equal((job.match(/--refresh-dependencies/g)||[]).length,1,'only one full original Gradle retry');
  } finally {
    await new Promise(resolve=>server.close(resolve));
    fs.rmSync(directory,{recursive:true,force:true});
  }
  console.log('Parallel Rider pinned Maven metadata: real HTTP transient recovery and permanent exhaustion passed');
}
metadataRetryRuntime().catch(error=>{console.error(error);process.exitCode=1;});
