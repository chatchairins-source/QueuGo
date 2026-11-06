"""Install the actual Rider APK and exercise its bundled engine without network.

No UI, authentication, push or physical-road acceptance is inferred from this gate.
"""
from pathlib import Path
import os
import subprocess
import time

repo = Path(__file__).resolve().parents[2]
sdk = Path(os.environ['ANDROID_HOME'])
output = repo / 'artifacts/rider-offline-routing'
output.mkdir(parents=True, exist_ok=True)
registry = Path(os.environ['RUNNER_TEMP']) / 'routing-avd'
registry.mkdir(exist_ok=True)
os.environ['ANDROID_AVD_HOME'] = str(registry)


def run(args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True, timeout=kwargs.pop('timeout', 180), **kwargs)


name = 'queuego_routing'
run([sdk/'cmdline-tools/latest/bin/avdmanager', 'create', 'avd', '-n', name,
     '-k', 'system-images;android-30;default;x86_64', '-d', 'pixel_4'], input='no\n', text=True)
adb = [sdk/'platform-tools/adb', '-s', 'emulator-5554']
with (output/'emulator.log').open('w') as log:
    emulator = subprocess.Popen([str(sdk/'emulator/emulator'), '-avd', name, '-port', '5554',
        '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'swiftshader_indirect',
        '-memory', '2048', '-partition-size', '4096', '-accel', 'on'], stdout=log, stderr=log)
    try:
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            if emulator.poll() is not None:
                raise RuntimeError('Emulator exited before boot')
            status = subprocess.run([str(a) for a in adb + ['shell', 'getprop', 'sys.boot_completed']],
                capture_output=True, text=True, timeout=15)
            if status.returncode == 0 and status.stdout.strip() == '1':
                break
            time.sleep(2)
        else:
            raise RuntimeError('Emulator failed to boot')
        run(adb + ['install', '-r', repo/'native-android/rider/build/outputs/apk/debug/rider-debug.apk'], timeout=300)
        tests = list((repo/'native-android/rider/build/outputs/apk/androidTest/debug').glob('*.apk'))
        assert len(tests) == 1, 'Expected one Rider instrumentation APK'
        run(adb + ['install', '-r', tests[0]])
        # The emulator must be offline BEFORE the first routing request. Merely
        # describing this as an airplane-mode test does not disable its network.
        # ConnectivityService toggles the radios as the shell user; broadcasting
        # AIRPLANE_MODE directly is protected and fails on the Android 30 image.
        run(adb + ['shell', 'cmd', 'connectivity', 'airplane-mode', 'enable'])
        run(adb + ['shell', 'svc', 'wifi', 'disable'])
        run(adb + ['shell', 'svc', 'data', 'disable'])
        airplane = subprocess.check_output(
            [str(a) for a in adb + ['shell', 'settings', 'get', 'global', 'airplane_mode_on']],
            text=True, timeout=30).strip()
        wifi = subprocess.check_output(
            [str(a) for a in adb + ['shell', 'settings', 'get', 'global', 'wifi_on']],
            text=True, timeout=30).strip()
        assert airplane == '1' and wifi == '0', (
            f'Offline test setup failed: airplane_mode_on={airplane}, wifi_on={wifi}')
        # Airplane mode does not necessarily disable the emulator's virtual
        # Ethernet interface. A surviving default route makes an alleged
        # offline test capable of reaching the network through eth0.
        # Fail closed instead of reporting a false offline acceptance.
        routes = {}
        for family in ('-4', '-6'):
            route = subprocess.check_output(
                [str(a) for a in adb + ['shell', 'ip', family, 'route', 'show', 'default']],
                text=True, timeout=30).strip()
            routes[family] = route
        (output/'network-default-routes.txt').write_text(
            ''.join(f'{family}: {route or "(none)"}\\n' for family, route in routes.items()))
        assert not any(routes.values()), (
            'Airplane mode left a default network route active; refusing to certify '
            f'offline navigation: {routes}')
        (output/'airplane-mode.txt').write_text(
            f'airplane_mode_on={airplane}\nwifi_on={wifi}\n')
        for attempt in (1, 2):
            # Each invocation starts a new app process; the second must recover the
            # already-installed, checksummed graph and build a fresh native actor.
            run(adb + ['shell', 'am', 'force-stop', 'com.queuego.rider'])
            with (output/f'instrumentation-cold-{attempt}.log').open('w') as result:
                run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                    'com.queuego.rider.RiderOfflineRoutingRuntimeTest',
                    'com.queuego.rider.test/androidx.test.runner.AndroidJUnitRunner'],
                    stdout=result, stderr=subprocess.STDOUT, timeout=600)
            text = (output/f'instrumentation-cold-{attempt}.log').read_text()
            # adb/am can exit 0 even when instrumentation failed. Require all three real tests.
            assert 'OK (3 tests)' in text and 'FAILURES' not in text, text
        with (output/'connectivity.txt').open('w') as file:
            run(adb + ['shell', 'dumpsys', 'connectivity'], stdout=file)
    finally:
        run(adb + ['logcat', '-d'], stdout=(output/'logcat.txt').open('w'))
        emulator.terminate()
        try:
            emulator.wait(timeout=15)
        except subprocess.TimeoutExpired:
            emulator.kill()
