const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const script='native-android/qa/verify-native-release-gate.py';
const env={...process.env};
delete env.QG_NATIVE_RELEASE_EVIDENCE;
const absent=spawnSync('python3',[script],{env,encoding:'utf8'});
assert.equal(absent.status,1,'release must fail without physical evidence');
assert.match(absent.stderr,/physical and Play gates remain OPEN/);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'qg-release-negative-'));
try{
  const stale=path.join(dir,'stale.json');
  fs.writeFileSync(stale,JSON.stringify({source_sha:'invalid'}));
  const result=spawnSync('python3',[script],{env:{...env,QG_NATIVE_RELEASE_EVIDENCE:stale},encoding:'utf8'});
  assert.equal(result.status,1,'stale certification must fail');
  assert.match(result.stderr,/exact release HEAD/);
}finally{fs.rmSync(dir,{recursive:true,force:true});}
const gradle=fs.readFileSync('native-android/build.gradle.kts','utf8');
assert.match(gradle,/isDebuggable = false/);
assert.match(gradle,/signingConfig = nativeReleaseSigning/);
assert.match(gradle,/name\.contains\("Release"\)/,'direct internal release tasks must also require the gate');
assert.match(gradle,/dependsOn\(verifyNativeReleaseGate\)/);
assert(!/signingConfigs.getByName\("debug"\)/.test(gradle),'release must not use debug signing');
console.log('Native release packaging guard: PASS (negative authorization checks; physical/signing certification remains OPEN)');
