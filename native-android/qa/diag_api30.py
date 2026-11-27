"""Real Android picker harness; isolated fixtures, no authenticated/backend writes."""
from pathlib import Path
import json
import hashlib
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
    return subprocess.run([str(a) for a in args], check=kw.pop('check', True), timeout=kw.pop('timeout', 120), **kw)

registry = Path(os.environ['RUNNER_TEMP']) / ('p0-avd-' + api)
registry.mkdir(parents=True, exist_ok=True)
os.environ['ANDROID_AVD_HOME'] = str(registry)
manager = sdk / 'cmdline-tools/latest/bin'
image = f'system-images;android-{api};default;x86_64'
def install_emulator_sdk():
    # Android artifact mirrors can fail during unzip/download on one API image.
    # Retry only the SDK installer once; real picker tests and assertions remain mandatory.
    command = [manager / 'sdkmanager', 'emulator', 'platform-tools', image]
    for attempt in (1, 2):
        try:
            return run(command, input='y\n' * 100, text=True, timeout=300)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            if attempt == 2:
                raise
            print('::warning::Customer Android SDK setup failed; retrying once', flush=True)
            time.sleep(5)

install_emulator_sdk()
name = 'queuego_p0_' + api
run([manager / 'avdmanager', 'create', 'avd', '-n', name, '-k', image, '-p', registry / name, '-d', 'pixel'], input='no\n', text=True)
adb = [sdk / 'platform-tools/adb', '-s', 'emulator-5554']
log = (out / 'emulator.log').open('w')
emulator = subprocess.Popen([str(sdk / 'emulator/emulator'), '-avd', name, '-port', '5554', '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'swiftshader_indirect', '-accel', 'on'], stdout=log, stderr=subprocess.STDOUT)
log_process = None
log_stream = (out / 'runtime-logcat.txt').open('w')
source_commit = run(['git', 'rev-parse', 'HEAD'], cwd=root, capture_output=True, text=True).stdout.strip()
summary = {'source_commit': source_commit, 'api': int(api), 'result': 'FAIL', 'authenticated_online_tested': False, 'physical_device_tested': False, 'release_gate': 'BLOCKED'}
try:
    run(adb + ['wait-for-device'], timeout=180)
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        if emulator.poll() is not None:
            raise AssertionError('Android emulator exited during boot; inspect emulator.log')
        try:
            boot = run(adb + ['shell', 'getprop', 'sys.boot_completed'],
                       capture_output=True, text=True, check=False, timeout=15)
        except subprocess.TimeoutExpired:
            time.sleep(1)
            continue
        if boot.returncode == 0 and boot.stdout.strip() == '1':
            break
        time.sleep(1)
    else:
        raise AssertionError('Android boot did not complete')
    run(adb + ['shell', 'input', 'keyevent', '82'])
    for role in ('customer',):
        run(adb + ['install', '-r', root / f'native-android/{role}/build/outputs/apk/debug/{role}-debug.apk'])
    run(adb + ['install', '-r', root / 'native-android/customer/build/outputs/apk/androidTest/debug/customer-debug-androidTest.apk'])
    outcomes = []
    for n in range(1, 4):
        for mode in ('surface', 'texture'):
            stem = f'{mode}-{n}'
            run(adb + ['logcat', '-c'])
            result = run(adb + ['shell','am','instrument','-w','-r','-e','mapSurface',mode,'-e','class','com.queuego.customer.CustomerLocationP0RuntimeTest#appCameraMovesDoNotBecomeUserDeliverySelections','com.queuego.customer.test/androidx.test.runner.AndroidJUnitRunner'], capture_output=True,text=True,timeout=180,check=False)
            evidence = result.stdout + result.stderr
            (out / (stem + '-instrumentation.txt')).write_text(evidence)
            logs = run(adb + ['logcat','-d','-v','threadtime'],capture_output=True,text=True).stdout
            rows = [line for line in logs.splitlines() if 'MAPDIAG' in line or re.search(r'\s[EF]\s.*(?:GL|Surface)',line,re.I)]
            (out / (stem + '.log')).write_text('\n'.join(rows) + '\n')
            passed = result.returncode == 0 and bool(re.search(r'OK \(1 test\)', evidence)) and any('event=waitAfterPan mode=' + mode in line for line in rows)
            outcomes.append(passed)
            print(stem, 'PASS' if passed else 'FAIL', flush=True)
    assert all(outcomes), 'See individual case logs'
finally:
    emulator.terminate()
    log_stream.close()
    log.close()
