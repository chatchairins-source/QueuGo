'use strict';
const fs = require('node:fs');
const assert = require('node:assert/strict');

const source = fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/CustomerPush.kt', 'utf8');
const store = fs.readFileSync('native-android/shared/src/main/java/com/queuego/shared/SecureRoleSessionStore.kt', 'utf8');

function verify(input) {
  const syncStart = input.indexOf('internal suspend fun syncCustomerNativePush(');
  const syncEnd = input.indexOf('internal suspend fun disableCustomerNativePush(', syncStart);
  assert(syncStart >= 0 && syncEnd > syncStart, 'Customer push sync scope missing');
  const sync = input.slice(syncStart, syncEnd);
  const initialCheck = sync.indexOf('if (!sessions.isCurrent(auth)) return false');
  const storeInit = sync.indexOf('val store = NativePushDeviceStore(');
  const token = sync.indexOf('val token = customerFirebaseToken().trim()');
  const secondCheck = sync.indexOf('if (!sessions.isCurrent(auth)) return false', initialCheck + 1);
  const subscribe = sync.indexOf('api.subscribe(auth, store.deviceId(), token)');
  assert(sync.includes('val sessions = SecureRoleSessionStore(context, "customer")'));
  assert(initialCheck >= 0 && initialCheck < storeInit, 'initial session must be current');
  assert(token > storeInit && secondCheck > token && secondCheck < subscribe,
    'validate session again after asynchronous Firebase token fetch and before subscribe');

  const serviceStart = input.indexOf('override fun onNewToken(token: String)');
  const serviceEnd = input.indexOf('override fun onMessageReceived(', serviceStart);
  assert(serviceStart >= 0 && serviceEnd > serviceStart, 'FCM refresh scope missing');
  const service = input.slice(serviceStart, serviceEnd);
  assert(service.includes('sessionStore.saveIfCurrent(cached, renewed)'),
    'FCM refresh must compare-and-save rather than restoring a logged-out session');
  assert(!service.includes('sessionStore.save(it)'), 'unsafe unconditional session restore detected');
  assert(service.includes('sessionStore.isCurrent(auth)'),
    'FCM must verify the session after asynchronous validation');
  assert(service.includes('customerPushEnabled(applicationContext, auth.user.id)'),
    'FCM must recheck opt-in for refreshed user before subscribe');
}
verify(source);
assert.match(store, /fun saveIfCurrent\(expected: NativeAuth, refreshed: NativeAuth\): Boolean = synchronized\(lock\)/,
  'shared session store must compare and save under the same lock');
assert.match(store, /fun isCurrent\(auth: NativeAuth\): Boolean = synchronized\(lock\)/,
  'shared session store must compare under the same lock');

const stripped = source.replace('    if (!sessions.isCurrent(auth)) return false\n    require(token.length', '    require(token.length');
assert.throws(() => verify(stripped), /validate session again/);
assert.throws(() => verify(source.replace('sessionStore.saveIfCurrent(cached, renewed)', 'sessionStore.save(renewed)')),
  /compare-and-save/);
assert.throws(() => verify(source.replace('sessionStore.isCurrent(auth)', 'true')),
  /verify the session/);
assert.throws(() => verify(source.replace('customerPushEnabled(applicationContext, auth.user.id)', 'true')),
  /recheck opt-in/);
console.log('PASS: native Customer FCM race guards and four mutation-negative source checks; does NOT certify real FCM delivery');
