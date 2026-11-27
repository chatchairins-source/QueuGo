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
    for apk in ('customer-debug.apk', 'customer-debug-androidTest.apk'):
        run(adb + ['install', '-r', root / 'pair' / apk])
    stem = 'texture-' + os.environ['QG_ROUND']
    run(adb + ['logcat', '-c'])
    log_process = subprocess.Popen([str(x) for x in adb + ['logcat', '-v', 'threadtime']], stdout=log_stream)
    case = 'com.queuego.customer.CustomerLocationP0RuntimeTest#appCameraMovesDoNotBecomeUserDeliverySelections'
    result = run(adb + ['shell','am','instrument','-w','-r','-e','mapSurface','texture','-e','class',case,'com.queuego.customer.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,text=True,timeout=180,check=False)
    evidence = result.stdout + result.stderr
    (out / (stem + '-instrumentation.txt')).write_text(evidence)
    print(evidence, flush=True)
    assert result.returncode == 0 and re.search(r'OK \(1 test\)', evidence)
    assert case.split('#')[1] in evidence
finally:
    if log_process is not None:
        log_process.terminate()
        log_process.wait(timeout=10)
    log_stream.close()
    rows = (out / 'runtime-logcat.txt').read_text(errors='replace').splitlines()
    (out / 'MAPDIAG.log').write_text('\n'.join(row for row in rows if 'MAPDIAG' in row))
    emulator.terminate()
    log.close()
assert any('event=waitAfterPan mode=texture' in row for row in rows)
