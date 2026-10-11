const fs=require('fs'),os=require('os'),path=require('path'),assert=require('assert'),{spawnSync}=require('child_process');
const script='native-android/qa/finalize-native-release-evidence.py';
assert.ok(fs.existsSync(script),'release evidence bundler must exist');

const listed=spawnSync('python3',[script,'--list-gates'],{encoding:'utf8'});
assert.equal(listed.status,0,'bundler must expose the canonical gate list');
const gates=JSON.parse(listed.stdout);
for(const gate of ['full_native_ci','backup_restore','physical_push_customer','physical_voice_two_devices_two_networks','customer_blueprint','android_lifecycle_permissions_upload_location','play_store_preflight','release_signing']){
  assert.ok(gates.includes(gate),'canonical gate missing from bundler: '+gate);
}
assert.ok(gates.length>=22,'bundler must cover the complete Native release gate set');

const root=fs.mkdtempSync(path.join(os.tmpdir(),'qg-evidence-bundler-'));
const bundle=path.join(root,'bundle');
const zip=path.join(root,'evidence.zip');
fs.mkdirSync(bundle);
try{
  const p0=spawnSync('python3',[script,'--bundle-dir',bundle,'--zip-output',zip,'--p0','1','--p1','0'],{encoding:'utf8'});
  assert.equal(p0.status,1,'P0/P1 non-zero must block bundle finalization');
  assert.match(p0.stderr,/P0 or P1 is non-zero/);

  const missing=spawnSync('python3',[script,'--bundle-dir',bundle,'--zip-output',zip,'--p0','0','--p1','0'],{encoding:'utf8'});
  assert.equal(missing.status,1,'missing gate envelopes must block bundle finalization');
  assert.match(missing.stderr,/missing gate envelope:/);

  const outside=path.join(root,'outside-cwd');
  fs.mkdirSync(outside);
  const outsideBundle=path.join(root,'outside-bundle');
  fs.mkdirSync(outsideBundle);
  const outsideRun=spawnSync('python3',[path.resolve(script),'--bundle-dir',outsideBundle,'--zip-output',path.join(root,'outside.zip'),'--p0','0','--p1','0'],{cwd:outside,encoding:'utf8'});
  assert.equal(outsideRun.status,1,'alternate working directory must still validate QueueGo source provenance');
  assert.match(outsideRun.stderr,/missing gate envelope:/,'alternate working directory must not switch git provenance');
}finally{
  fs.rmSync(root,{recursive:true,force:true});
}

const source=fs.readFileSync(script,'utf8');
assert.ok(source.includes('contract.verify_gate_envelope'),'bundler must use the canonical gate envelope validator');
assert.ok(source.includes('for gate in contract.GATES'),'bundler must derive required gates from the canonical verifier');
assert.ok(source.includes('release evidence finalization requires a clean source checkout'),'bundler must require clean exact-source provenance');
assert.ok(source.includes('["git", "-C", str(ROOT), "rev-parse", "HEAD"]'),'bundler must bind source SHA to the QueueGo checkout regardless of caller cwd');
assert.ok(source.includes('["git", "-C", str(ROOT), "status", "--porcelain", "--untracked-files=normal"]'),'bundler must check cleanliness on the QueueGo checkout regardless of caller cwd');
assert.ok(source.includes('must be outside the source checkout'),'bundle and ZIP output must stay outside source');
assert.ok(source.includes('cannot finalize release evidence while P0 or P1 is non-zero'),'bundler must hard-block known P0/P1 defects');
assert.ok(source.includes('evidence bundle may not contain symlinks'),'bundler must reject symlink evidence');
assert.ok(source.includes('evidence bundle exceeds 1000 files'),'bundler must mirror certification file-count bounds');
assert.ok(source.includes('evidence bundle exceeds 4 GiB uncompressed limit'),'bundler must mirror certification size bounds');
assert.ok(source.includes('certified-release-metadata.json'),'bundler must reserve certification-generated metadata');
assert.ok(source.includes('READY_FOR_PRIVATE_STORAGE_UPLOAD'),'bundler must identify output as upload-ready, not release-certified');
assert.ok(source.includes('zip_sha256'),'bundler must surface the ZIP digest required by certification');

console.log(JSON.stringify({checks:28,failures:0,scope:'Fail-closed Native release evidence bundle finalization'}));
