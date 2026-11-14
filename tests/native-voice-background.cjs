const assert = require('node:assert/strict');
const fs = require('node:fs');
const root = 'native-android/shared/src/main/';
const controller = fs.readFileSync(root + 'java/com/queuego/shared/NativeVoiceCallController.kt', 'utf8');
const service = fs.readFileSync(root + 'java/com/queuego/shared/NativeVoiceForegroundService.kt', 'utf8');
const manifest = fs.readFileSync(root + 'AndroidManifest.xml', 'utf8');
assert.match(manifest, /FOREGROUND_SERVICE_MICROPHONE/);
assert.match(manifest, /NativeVoiceForegroundService[\s\S]*android:exported="false"[\s\S]*foregroundServiceType="microphone"/);
assert.match(service, /FOREGROUND_SERVICE_TYPE_MICROPHONE/);
assert.match(service, /START_NOT_STICKY/);
assert.match(service, /IMPORTANCE_FOREGROUND/);
assert.match(service, /lease\?\.owner != owner/);
assert.match(service, /onTaskRemoved/);
assert.match(service, /onDestroy/);
assert.doesNotMatch(service, /NativeVoicePeer\(|api\.start\(|AudioRecord\(|MediaRecorder\(/);
for (const method of ['startOutgoing', 'answerIncoming']) {
  const body = controller.slice(controller.indexOf('fun ' + method));
  assert.ok(body.indexOf('prepareForeground()') < body.indexOf('operationJob = scope.launch'));
}
const connect = controller.slice(controller.indexOf('private suspend fun connect'));
assert.ok(connect.indexOf('prepareForeground().await()') < connect.indexOf('NativeVoicePeer('));
assert.match(connect, /closeTransport\(releaseForeground = false\)/);
assert.match(controller, /if \(releaseForeground\)[\s\S]*NativeVoiceForegroundService\.stop/);
assert.match(controller, /if \(foregroundOwner == owner\)[\s\S]*hangUp\(\)/);
console.log('Native microphone foreground lifecycle source contracts PASS (physical audio remains OPEN)');
