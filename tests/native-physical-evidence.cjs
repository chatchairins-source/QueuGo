const fs=require('fs'),path=require('path'),os=require('os'),crypto=require('crypto'),assert=require('assert'),{spawnSync}=require('child_process');

const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const source='a'.repeat(40);
const physicalGates=[
  'physical_push_customer','physical_push_merchant','physical_push_rider',
  'physical_voice_two_devices_two_networks','turn_relay',
  'voice_session_order_block_authorization','rider_floating_q',
  'customer_blueprint','merchant_blueprint','rider_blueprint',
  'android_lifecycle_permissions_upload_location'
];
const run=(reportPath)=>{
  const code=[
    "import json,os,sys",
    "from pathlib import Path",
    "sys.path.insert(0, 'native-android/qa')",
    "from verify_native_physical_evidence import verify_physical_evidence",
    "p=Path(os.environ['QG_TEST_REPORT']).resolve()",
    "verify_physical_evidence(json.loads(p.read_text()),p,os.environ['QG_TEST_SOURCE'])"
  ].join(';');
  return spawnSync('python3',['-c',code],{
    cwd:process.cwd(),
    env:{...process.env,QG_TEST_REPORT:reportPath,QG_TEST_SOURCE:source},
    encoding:'utf8'
  });
};

const dir=fs.mkdtempSync(path.join(os.tmpdir(),'qg-physical-evidence-'));
try{
  const evidence=[];
  for(let i=0;i<8;i++){
    const name=`capture-${i}.txt`, body=`QueueGo physical evidence ${i}\n`;
    fs.writeFileSync(path.join(dir,name),body);
    evidence.push({file:name,sha256:sha(body)});
  }
  const ev=(...indexes)=>indexes.map(i=>evidence[i]);
  const trueMap=keys=>Object.fromEntries(keys.map(k=>[k,true]));
  const pushKeys=['foreground','background','killed','refresh_login','logout_revocation','stale_token_exclusion','order_notification','call_notification'];
  const lifecycleKeys=['permissions','single_back_to_home','upload','location','session_persistence','offline_timeout','reconnect','background_foreground'];
  const deviceId=name=>sha(name);
  const d1=deviceId('device-1'),d2=deviceId('device-2');

  const physical={
    schema_version:1,
    source_sha:source,
    captured_at:'2026-10-10T21:00:00+07:00',
    operator_certified:true,
    p0:0,p1:0,
    devices:[
      {device_id_sha256:d1,physical:true,emulator:false,android_api:36,model:'Android Phone A',roles:['customer','rider'],evidence:ev(0)},
      {device_id_sha256:d2,physical:true,emulator:false,android_api:35,model:'Android Phone B',roles:['merchant'],evidence:ev(1)}
    ],
    checks:{
      push:{
        customer:{...trueMap(pushKeys),evidence:ev(0,2)},
        merchant:{...trueMap(pushKeys),evidence:ev(1,3)},
        rider:{...trueMap(pushKeys),evidence:ev(2,4)}
      },
      voice:{
        ...trueMap(['two_devices','two_networks','bidirectional_audio','forced_turn_relay','foreground_background','controls_complete','hangup_cleanup','session_order_block_authorization']),
        device_ids_sha256:[d1,d2],
        evidence:ev(2,3,4)
      },
      rider_floating_q:{
        ...trueMap(['overlay_granted_path','overlay_denied_path','tap_returns_to_active_job','persistent_notification_return','stops_outside_active_work']),
        evidence:ev(4,5)
      },
      blueprint:{
        customer:{...trueMap(['all_required_screens_observed','all_required_states_observed','pixel_diff_reviewed']),blocking_differences:0,evidence:ev(0,5)},
        merchant:{...trueMap(['all_required_screens_observed','all_required_states_observed','pixel_diff_reviewed']),blocking_differences:0,evidence:ev(1,6)},
        rider:{...trueMap(['all_required_screens_observed','all_required_states_observed','pixel_diff_reviewed']),blocking_differences:0,evidence:ev(2,7)}
      },
      lifecycle:{
        customer:{...trueMap(lifecycleKeys),evidence:ev(0,6)},
        merchant:{...trueMap(lifecycleKeys),evidence:ev(1,7)},
        rider:{...trueMap(lifecycleKeys),evidence:ev(2,5)}
      },
      order_session_block_private_topic_revocation:{
        ...trueMap(['real_order','session_revocation','block_revocation','private_topic_revocation']),
        evidence:ev(6,7)
      }
    }
  };

  const physicalPath=path.join(dir,'native-physical-evidence.json');
  const reportPath=path.join(dir,'native-release-evidence.json');
  const writeBundle=()=>{
    fs.writeFileSync(physicalPath,JSON.stringify(physical,null,2)+'\n');
    const digest=sha(fs.readFileSync(physicalPath));
    const gates={};
    for(const gate of physicalGates)gates[gate]={status:'PASS',evidence_file:'native-physical-evidence.json',sha256:digest};
    fs.writeFileSync(reportPath,JSON.stringify({source_sha:source,p0:0,p1:0,gates},null,2)+'\n');
  };

  writeBundle();
  let result=run(reportPath);
  assert.equal(result.status,0,result.stderr||result.stdout);

  physical.checks.voice.forced_turn_relay=false;
  writeBundle();
  result=run(reportPath);
  assert.equal(result.status,1,'forced TURN evidence must fail when not observed');
  assert.match(result.stderr,/checks\.voice\.forced_turn_relay must be observed true/);

  physical.checks.voice.forced_turn_relay=true;
  physical.devices[0].emulator=true;
  writeBundle();
  result=run(reportPath);
  assert.equal(result.status,1,'emulator evidence must never certify physical device coverage');
  assert.match(result.stderr,/real non-emulator device/);

  physical.devices[0].emulator=false;
  physical.checks.push.customer.evidence=[{file:'../outside.txt',sha256:'0'.repeat(64)},evidence[2]];
  writeBundle();
  result=run(reportPath);
  assert.equal(result.status,1,'physical evidence traversal must fail closed');
  assert.match(result.stderr,/path traversal/);

  const gateSource=fs.readFileSync('native-android/qa/verify-native-release-gate.py','utf8');
  assert.ok(gateSource.includes('from verify_native_physical_evidence import verify_physical_evidence'));
  assert.ok(gateSource.includes('verify_physical_evidence(report, report_file, head)'));

  console.log(JSON.stringify({checks:8,failures:0,scope:'Semantic Native physical evidence certification'}));
}finally{
  fs.rmSync(dir,{recursive:true,force:true});
}
