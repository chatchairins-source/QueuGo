"""Real Android picker harness; isolated fixtures, no authenticated/backend writes."""
from pathlib import Path
import json
import os
import re
import subprocess
import time

root = Path(__file__).resolve().parents[2]
sdk = Path(os.environ['ANDROID_HOME'])
api = os.environ['QG_P0_API']
out = root / 'artifacts/customer-p0' / api
out.mkdir(parents=True, exist_ok=True)

def run(args, **kw):
    return subprocess.run([str(a) for a in args], check=True, timeout=kw.pop('timeout', 120), **kw)

registry = Path(os.environ['RUNNER_TEMP']) / ('p0-avd-' + api)
registry.mkdir(parents=True, exist_ok=True)
os.environ['ANDROID_AVD_HOME'] = str(registry)
manager = sdk / 'cmdline-tools/latest/bin'
image = f'system-images;android-{api};default;x86_64'
run([manager / 'sdkmanager', 'emulator', 'platform-tools', image], input='y\n' * 100, text=True, timeout=300)
name = 'queuego_p0_' + api
run([manager / 'avdmanager', 'create', 'avd', '-n', name, '-k', image, '-p', registry / name, '-d', 'pixel'], input='no\n', text=True)
adb = [sdk / 'platform-tools/adb', '-s', 'emulator-5554']
log = (out / 'emulator.log').open('w')
emulator = subprocess.Popen([str(sdk / 'emulator/emulator'), '-avd', name, '-port', '5554', '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'swiftshader_indirect', '-accel', 'on'], stdout=log, stderr=subprocess.STDOUT)
summary = {'source_commit': os.environ['GITHUB_SHA'], 'api': int(api), 'result': 'FAIL', 'authenticated_online_tested': False, 'physical_device_tested': False, 'release_gate': 'BLOCKED'}
try:
    run(adb + ['wait-for-device'], timeout=180)
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        if run(adb + ['shell', 'getprop', 'sys.boot_completed'], capture_output=True, text=True).stdout.strip() == '1':
            break
        time.sleep(1)
    else:
        raise AssertionError('Android boot did not complete')
    run(adb + ['shell', 'input', 'keyevent', '82'])
    for role in ('customer',):
        run(adb + ['install', '-r', root / f'native-android/{role}/build/outputs/apk/debug/{role}-debug.apk'])
    run(adb + ['install', '-r', root / 'native-android/customer/build/outputs/apk/androidTest/debug/customer-debug-androidTest.apk'])
    run(adb + ['logcat', '-b', 'all', '-c'])
    result = run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'com.queuego.customer.CustomerLocationP0RuntimeTest', 'com.queuego.customer.test/androidx.test.runner.AndroidJUnitRunner'], capture_output=True, text=True, timeout=600)
    (out / 'instrumentation.txt').write_text(result.stdout + result.stderr)
    assert re.search(r'OK \(\d+ tests?\)', result.stdout), 'Android instrumentation failed; inspect instrumentation.txt/logcat'
    logs = run(adb + ['logcat', '-b', 'all', '-d'], capture_output=True, text=True).stdout
    (out / 'logcat.txt').write_text(logs)
    assert not re.search(r'FATAL EXCEPTION|ANR in com\.queuego\.customer|signal \d+ \(SIG(?:SEGV|ABRT)\)|shared_map_\w*failed|Native map (?:operation|setup) failed', logs), 'Crash/ANR or swallowed map lifecycle failure; inspect logcat'
    summary['result'] = 'PASS'
finally:
    try:
        for buffer in ('all', 'crash'):
            result = subprocess.run([str(a) for a in adb + ['logcat', '-b', buffer, '-d']], capture_output=True, text=True, timeout=15)
            (out / (buffer + '-logcat.txt')).write_text(result.stdout + result.stderr)
        subprocess.run([str(a) for a in adb + ['pull', '/sdcard/Android/data/com.queuego.customer/files/p0-evidence', out]], timeout=15, check=False)
    finally:
        (out / 'summary.json').write_text(json.dumps(summary, indent=2))
        emulator.terminate()
        try:
            emulator.wait(timeout=15)
        except subprocess.TimeoutExpired:
            emulator.kill()
        log.close()
