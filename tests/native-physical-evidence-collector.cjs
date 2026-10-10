const fs=require('fs'),path=require('path'),os=require('os'),assert=require('assert'),{spawnSync}=require('child_process');
const collector='native-android/qa/collect_native_physical_evidence.py';
const validator='native-android/qa/verify_native_physical_evidence.py';
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'qg-physical-collector-'));
const source='b'.repeat(40);
try{
  let r=spawnSync('python3',[collector,'init','--out',tmp,'--source-sha',source],{encoding:'utf8'});
  assert.equal(r.status,0,r.stderr);
  const manifestPath=path.join(tmp,'native-physical-evidence.json');
  const manifest=JSON.parse(fs.readFileSync(manifestPath,'utf8'));
  assert.equal(manifest.schema_version,1);
  assert.equal(manifest.source_sha,source);
  assert.equal(manifest.operator_certified,false,'collector must never auto-certify physical evidence');
  assert.equal(manifest.p0,null);
  assert.equal(manifest.p1,null);
  assert.deepEqual(manifest.devices,[]);
  for(const role of ['customer','merchant','rider']){
    assert.equal(manifest.checks.push[role].foreground,false);
    assert.equal(manifest.checks.lifecycle[role].permissions,false);
    assert.equal(manifest.checks.blueprint[role].blocking_differences,null);
  }
  assert.equal(manifest.checks.voice.forced_turn_relay,false);
  assert.equal(manifest.checks.rider_floating_q.overlay_granted_path,false);
  assert.equal(manifest.checks.order_session_block_private_topic_revocation.real_order,false);

  const reportPath=path.join(tmp,'native-release-evidence.json');
  fs.writeFileSync(reportPath,JSON.stringify({source_sha:source,p0:0,p1:0,gates:{}},null,2));
  const code=[
    "import json,os,sys",
    "from pathlib import Path",
    "sys.path.insert(0,'native-android/qa')",
    "from verify_native_physical_evidence import verify_physical_evidence",
    "p=Path(os.environ['R']).resolve()",
    "verify_physical_evidence(json.loads(p.read_text()),p,os.environ['S'])"
  ].join(';');
  r=spawnSync('python3',['-c',code],{env:{...process.env,R:reportPath,S:source},encoding:'utf8'});
  assert.equal(r.status,1,'fresh collector template must fail physical certification');
  assert.match(r.stderr,/operator-certified|operator_certified/);

  const inside=path.join(tmp,'capture.txt');
  fs.writeFileSync(inside,'evidence');
  r=spawnSync('python3',[collector,'hash-file','--out',tmp,'--file',inside],{encoding:'utf8'});
  assert.equal(r.status,0,r.stderr);
  const ref=JSON.parse(r.stdout);
  assert.equal(ref.file,'capture.txt');
  assert.match(ref.sha256,/^[0-9a-f]{64}$/);

  const outside=path.join(os.tmpdir(),'qg-outside-'+Date.now()+'.txt');
  fs.writeFileSync(outside,'outside');
  r=spawnSync('python3',[collector,'hash-file','--out',tmp,'--file',outside],{encoding:'utf8'});
  assert.equal(r.status,1,'collector must reject hashing evidence outside the bundle');
  assert.match(r.stderr,/must be inside the evidence bundle/);
  fs.rmSync(outside,{force:true});

  const sourceText=fs.readFileSync(collector,'utf8');
  assert.ok(sourceText.includes('"physical":False'));
  assert.ok(sourceText.includes('"operator_certified":False'));
  assert.ok(sourceText.includes('Raw adb serial intentionally not stored.'));

  console.log(JSON.stringify({checks:18,failures:0,scope:'Fail-closed Native physical evidence collector template'}));
}finally{
  fs.rmSync(tmp,{recursive:true,force:true});
}
