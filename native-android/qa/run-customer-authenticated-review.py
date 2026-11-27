"""Real Customer UI/FCM runner. Credentials travel only on stdin to app-private storage."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shlex
import subprocess

from adb_install_recovery import install_verified_apk

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/customer-authenticated-review'
PKG = 'com.queuego.customer'
CREDENTIAL_PATH = 'files/qa-customer-credentials.json'
NAMES = ('QG_CUSTOMER_REVIEW_PHONE', 'QG_CUSTOMER_REVIEW_PASSWORD',
         'QG_FIREBASE_GOOGLE_SERVICES_JSON_B64')
CHECKS = ('login_ui', 'live_customer_session', 'profile_restore', 'push_opt_in_ui',
          'backend_created', 'real_fcm_received', 'notification_tap_return')
SAFE_FIELDS = set(CHECKS) | {'source_commit', 'physical_device_tested', 'stage',
                           'elapsed_ms', 'within_fast_window_90s', 'failure_type'}


def head():
    return subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()


def preflight():
    OUT.mkdir(parents=True, exist_ok=True)
    missing = [name for name in NAMES if not os.environ.get(name)]
    (OUT / 'credential-presence.json').write_text(json.dumps({
        'source_commit': head(), 'missing_secret_names': missing,
        'scope': 'credential presence only; no Customer login/FCM acceptance',
        'real_runtime_executed': False,
    }, indent=2))
    if missing:
        for name in missing:
            print('::error::Missing Customer runtime secret: ' + name)
        raise SystemExit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preflight-only', action='store_true')
    args = parser.parse_args()
    preflight()
    if args.preflight_only:
        print('Customer credential presence passed; real runtime remains untested')
        return
    adb = [str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'), '-s', 'emulator-5554']

    def run(command, **kwargs):
        result = subprocess.run(adb + command, capture_output=True,
                                timeout=kwargs.pop('timeout', 60), **kwargs)
        if result.returncode:
            raise RuntimeError('Customer runtime device operation failed')
        return result.stdout

    apk = ROOT / 'native-android/customer/build/outputs/apk/debug/customer-debug.apk'
    test_apk = ROOT / 'native-android/customer/build/outputs/apk/androidTest/debug/customer-debug-androidTest.apk'
    # The exact built APK bytes and actual Google services are mandatory.
    install_verified_apk(adb, apk, PKG)
    install_verified_apk(adb, test_apk, PKG + '.test')
    run(['shell', 'pm', 'clear', PKG])
    if not run(['shell', 'pm', 'path', 'com.google.android.gms']).startswith(b'package:'):
        raise RuntimeError('Actual Google Play services required for Customer FCM')
    credentials = json.dumps({
        'phone': os.environ['QG_CUSTOMER_REVIEW_PHONE'],
        'password': os.environ['QG_CUSTOMER_REVIEW_PASSWORD'],
    }).encode()
    # Static remote command; secret bytes are never command arguments or logs.
    private_command = 'run-as ' + PKG + ' sh -c ' + shlex.quote(
        'umask 077; mkdir -p files; cat > ' + CREDENTIAL_PATH + '; chmod 600 ' + CREDENTIAL_PATH)
    try:
        run(['shell', '-T', private_command], input=credentials)
        result = subprocess.run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            'com.queuego.customer.CustomerAuthenticatedRuntimeTest',
            'com.queuego.customer.test/androidx.test.runner.AndroidJUnitRunner'],
            capture_output=True, timeout=900)
        # The test intentionally suppresses exception payloads/UI trees and writes
        # only typed Boolean/timing evidence. Validate before persisting it.
        raw = run(['exec-out', 'run-as', PKG, 'cat', 'files/push-evidence/customer-authenticated.json'])
        if len(raw) > 16384:
            raise RuntimeError('Customer evidence exceeds safe size')
        evidence = json.loads(raw)
        if not isinstance(evidence, dict) or not set(evidence).issubset(SAFE_FIELDS):
            raise RuntimeError('Unexpected Customer evidence fields; refusing to publish')
        stages = {'private_credentials', 'login_ui', 'live_session', 'profile_restore',
                  'push_opt_in_ui', 'fcm_receipt', 'fcm_delivery', 'notification_tap', 'PASS'}
        if (not re.fullmatch(r'[0-9a-f]{40}', str(evidence.get('source_commit', '')))
                or evidence.get('stage') not in stages
                or any(type(evidence.get(check)) is not bool for check in CHECKS)
                or evidence.get('physical_device_tested') is not False):
            raise RuntimeError('Unsafe Customer evidence values; refusing to publish')
        if 'elapsed_ms' in evidence and (type(evidence['elapsed_ms']) is not int or not 0 <= evidence['elapsed_ms'] <= 180000):
            raise RuntimeError('Invalid Customer delivery timing')
        if 'within_fast_window_90s' in evidence and type(evidence['within_fast_window_90s']) is not bool:
            raise RuntimeError('Invalid Customer latency evidence')
        if 'failure_type' in evidence:
            safe_types = {'IllegalStateException', 'AssertionError', 'TimeoutCancellationException',
                          'NativeAuthHttpException', 'NativeSessionInvalidException', 'JSONException',
                          'IOException', 'SecurityException', 'IllegalArgumentException', 'NullPointerException'}
            if evidence['failure_type'] not in safe_types:
                evidence['failure_type'] = 'RuntimeFailure'
        (OUT / 'runtime-result.json').write_text(json.dumps(evidence, indent=2))
        text = (result.stdout + result.stderr).decode(errors='replace')
        # Only the static JUnit summary is retained, never a raw UI hierarchy.
        summary = 'OK (1 test)' if 'OK (1 test)' in text and 'FAILURES' not in text else 'FAIL'
        (OUT / 'instrumentation-summary.txt').write_text(summary + '\n')
        if result.returncode or summary != 'OK (1 test)':
            raise RuntimeError('Actual Customer Android test failed; inspect stage evidence')
        if evidence.get('source_commit') != head() or evidence.get('stage') != 'PASS':
            raise RuntimeError('Customer runtime source/stage mismatch')
        if any(evidence.get(check) is not True for check in CHECKS):
            raise RuntimeError('Real Customer login/restore/opt-in/FCM/tap evidence incomplete')
        if evidence.get('physical_device_tested') is not False:
            raise RuntimeError('Emulator evidence must not claim a physical device')
        evidence['apk_sha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
        evidence['instrumentation_apk_sha256'] = hashlib.sha256(test_apk.read_bytes()).hexdigest()
        (OUT / 'runtime-result.json').write_text(json.dumps(evidence, indent=2))
        print('QUEUEGO_CUSTOMER_REAL_LOGIN_RESTORE_FCM_TAP_PASS')
    finally:
        # A crash must not leave credentials on the emulator either.
        subprocess.run(adb + ['shell', 'run-as', PKG, 'rm', '-f', CREDENTIAL_PATH],
                       capture_output=True, timeout=30)


if __name__ == '__main__':
    main()
