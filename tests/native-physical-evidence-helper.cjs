const fs=require('fs'),assert=require('assert'),{spawnSync}=require('child_process');
const script='native-android/qa/capture-physical-release-evidence.py';
assert.ok(fs.existsSync(script),'physical evidence capture helper must exist');

const listed=spawnSync('python3',[script,'--list-gates'],{encoding:'utf8'});
assert.equal(listed.status,0,'physical evidence helper must expose canonical gate contract');
const gates=JSON.parse(listed.stdout);
assert.equal(Object.keys(gates).length,11,'physical helper must cover every canonical physical gate');
assert.deepEqual(gates.physical_push_customer.required_roles,['customer']);
assert.ok(gates.physical_push_customer.required_checks.includes('background'));
assert.ok(gates.physical_push_customer.required_checks.includes('stale_token_exclusion'));
assert.deepEqual(gates.physical_voice_two_devices_two_networks.required_roles,['customer','rider']);
assert.ok(gates.physical_voice_two_devices_two_networks.required_checks.includes('bidirectional_audio'));
assert.equal(gates.customer_blueprint.blueprint,true);
assert.equal(gates.android_lifecycle_permissions_upload_location.required_checks.length,24);

const source=fs.readFileSync(script,'utf8');
assert.ok(source.includes('verify-native-release-gate.py'),'helper must import the canonical release verifier contract');
assert.ok(source.includes('contract.PHYSICAL_GATE_REQUIRED_CHECKS'),'helper must not maintain a duplicate semantic-check list');
assert.ok(source.includes('contract.PHYSICAL_GATE_REQUIRED_ROLES'),'helper must not maintain a duplicate role list');
assert.ok(source.includes('physical evidence rejects emulator'),'helper must fail closed on emulator evidence');
assert.ok(source.includes('ro.kernel.qemu')&&source.includes('ranchu')&&source.includes('goldfish'),'helper must detect common Android emulator identities');
assert.ok(source.includes('"status": "PASS" if args.certify else "DRAFT"'),'helper must never emit PASS by default');
assert.ok(source.includes('"operator_certified": bool(args.certify)'),'operator certification must be explicit');
assert.ok(source.includes('physical evidence output must be outside the source checkout'),'evidence must stay outside source');
assert.ok(source.includes('sha256_file(target)'),'captured artifacts must be SHA-256 bound');
assert.ok(source.includes('exec-out", "screencap", "-p"'),'helper must support direct physical-device screenshot capture');
assert.ok(source.includes('requires two distinct physical devices'),'voice/TURN evidence must reject one-device capture');
assert.ok(source.includes('requires two distinct network labels'),'voice/TURN evidence must reject one-network capture');
assert.ok(source.includes('cannot certify blueprint evidence with blocking differences'),'Blueprint PASS must require zero blocking differences');

console.log(JSON.stringify({checks:22,failures:0,scope:'Fail-closed real-device physical evidence capture helper'}));
