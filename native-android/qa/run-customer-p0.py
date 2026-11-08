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
run([manager / 'sdkmanager', 'emulator', 'platform-tools', image], input='y\n' * 100, text=True, timeout=300)
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
        if run(adb + ['shell', 'getprop', 'sys.boot_completed'], capture_output=True, text=True).stdout.strip() == '1':
            break
        time.sleep(1)
    else:
        raise AssertionError('Android boot did not complete')
    run(adb + ['shell', 'input', 'keyevent', '82'])
    for role in ('customer',):
        run(adb + ['install', '-r', root / f'native-android/{role}/build/outputs/apk/debug/{role}-debug.apk'])
    run(adb + ['install', '-r', root / 'native-android/customer/build/outputs/apk/androidTest/debug/customer-debug-androidTest.apk'])
    # Capture continuously without clearing logd: API26's system image rejects clears.
    # -T1 starts at the current tail; streaming preserves evidence beyond buffer rotation.
    log_process = subprocess.Popen([str(a) for a in adb + ['logcat', '-b', 'main', '-b', 'system', '-b', 'crash', '-v', 'threadtime', '-T', '1']], stdout=log_stream, stderr=subprocess.STDOUT)
    result = run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'com.queuego.customer.CustomerLocationP0RuntimeTest', 'com.queuego.customer.test/androidx.test.runner.AndroidJUnitRunner'], capture_output=True, text=True, timeout=600, check=False)
    (out / 'instrumentation.txt').write_text(result.stdout + result.stderr)
    # Keep the strict Android result gate, but surface the real instrumentation
    # failure in Actions logs so a failing API matrix can be fixed from evidence
    # instead of repeating four opaque "inspect artifact" failures.
    if result.returncode != 0 or not re.search(r'OK \(\d+ tests?\)', result.stdout):
        failure_lines = [line for line in result.stdout.splitlines() if (
            'FAILURES!!!' in line or 'There were ' in line or 'java.lang.' in line
            or 'INSTRUMENTATION_STATUS: stack=' in line or 'INSTRUMENTATION_CODE:' in line
            or 'Tests run:' in line or 'INSTRUMENTATION_FAILED' in line
        )]
        raise AssertionError(f'Android instrumentation failed (exit {result.returncode}); inspect instrumentation.txt/logcat. '
                             + ' | '.join(failure_lines[-24:])[:3500])
    assert log_process.poll() is None, 'Continuous Android log capture exited unexpectedly'
    log_process.terminate()
    log_process.wait(timeout=10)
    log_stream.flush()
    logs = (out / 'runtime-logcat.txt').read_text(errors='replace')
    assert logs.strip(), 'Android runtime log capture is empty'
    (out / 'logcat.txt').write_text(logs)
    assert not re.search(r'FATAL EXCEPTION|ANR in com\.queuego\.customer|signal \d+ \(SIG(?:SEGV|ABRT)\)|shared_map_\w*failed|Native map (?:operation|setup) failed', logs), 'Crash/ANR or swallowed map lifecycle failure; inspect logcat'
    # Debug run-as is scoped to this app and avoids adb shell's scoped-storage denial.
    for name in ('picker-0.png', 'picker-29.png', 'road-detail-0.png', 'road-detail-29.png',
                 'road-detail-0.txt', 'road-detail-29.txt'):
        with (out / name).open('wb') as image_out:
            run(adb + ['exec-out', 'run-as', 'com.queuego.customer', 'cat', 'files/p0-evidence/' + name], stdout=image_out)
        assert (out / name).stat().st_size > (1024 if name.endswith('.png') else 10), 'Android map evidence is missing or empty'
    # Validate and fingerprint the actual installed MapLibre screenshots on the host.
    # This does not infer road coverage from a screenshot alone: the Android test
    # independently enforces pixel diversity and geographic edge thresholds.
    evidence = {}
    for index in (0, 29):
        metrics = (out / f'road-detail-{index}.txt').read_text().strip()
        match = re.fullmatch(r'colors=(\d+) edges=(\d+) samples=(\d+)', metrics)
        assert match, f'Invalid map pixel evidence at cycle {index}: {metrics!r}'
        colors, edges, samples = map(int, match.groups())
        assert colors >= 16 and samples > 0 and edges > samples // 1000, (
            f'Insufficient geographic map detail at cycle {index}: {metrics}')
        evidence[str(index)] = {'colors': colors, 'edges': edges, 'samples': samples}
        for filename in (f'road-detail-{index}.png', f'picker-{index}.png'):
            data = (out / filename).read_bytes()
            assert data[:8] == b'\x89PNG\r\n\x1a\n', f'Invalid PNG signature: {filename}'
            evidence[str(index)][filename] = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
    summary['rendered_map_evidence'] = evidence
    summary['result'] = 'PASS'
finally:
    try:
        for buffer in ('all', 'crash'):
            result = subprocess.run([str(a) for a in adb + ['logcat', '-b', buffer, '-d']], capture_output=True, text=True, timeout=15)
            (out / (buffer + '-logcat.txt')).write_text(result.stdout + result.stderr)
        if log_process is not None and log_process.poll() is None:
            log_process.terminate()
            log_process.wait(timeout=10)
        log_stream.close()
    finally:
        (out / 'summary.json').write_text(json.dumps(summary, indent=2))
        emulator.terminate()
        try:
            emulator.wait(timeout=15)
        except subprocess.TimeoutExpired:
            emulator.kill()
        log.close()
