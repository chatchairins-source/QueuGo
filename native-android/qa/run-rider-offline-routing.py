"""Install the actual Rider APK and exercise its bundled engine without network.

No UI, authentication, push or physical-road acceptance is inferred from this gate.
"""
from pathlib import Path
import json
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
        # Android emulators may retain host-backed Ethernet even in airplane
        # mode. Isolate the emulator network namespace rather than treating
        # the airplane-mode setting alone as proof of offline operation.
        # The default (non-Play) AVD is debuggable; if root is unavailable,
        # fail closed at the default-route assertion below.
        before = {}
        for family in ('-4', '-6'):
            before[family] = subprocess.check_output(
                [str(a) for a in adb + ['shell', 'ip', family, 'route', 'show', 'default']],
                text=True, timeout=30).strip()
        if any(before.values()):
            root = subprocess.run([str(sdk/'platform-tools/adb'), '-s', 'emulator-5554', 'root'],
                                  capture_output=True, text=True, timeout=30)
            assert root.returncode == 0, 'Cannot isolate emulator Ethernet: adb root failed: ' + root.stderr
            run(adb + ['wait-for-device'], timeout=60)
            # Bring down only interfaces explicitly carrying default routes;
            # never fabricate a route or alter the tested APK/graph.
            interfaces = set()
            for route in before.values():
                for line in route.splitlines():
                    parts = line.split()
                    if 'dev' in parts:
                        interfaces.add(parts[parts.index('dev') + 1])
            for interface in sorted(interfaces):
                assert interface not in ('lo', ''), 'Refusing to disable loopback'
                run(adb + ['shell', 'ip', 'link', 'set', 'dev', interface, 'down'])
            (output/'network-isolation.txt').write_text(
                'before=' + repr(before) + '\\ninterfaces_disabled=' + repr(sorted(interfaces)) + '\\n')
        # Fail closed if any IPv4/IPv6 default route survived isolation.
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
            graph_manifest = json.loads((Path(os.environ['QG_OFFLINE_ROUTING_ASSETS']) / 'routing/manifest.json').read_text())
            graph_hash = graph_manifest['graph']['sha256']
            assert len(graph_hash) == 64 and all(c in '0123456789abcdef' for c in graph_hash)
            partial = f'no_backup/routing/{graph_hash}/tiles.tar.part'
            if attempt == 2:
                # A real private-file interruption state; never modify the verified graph.
                run(adb + ['shell', 'run-as', 'com.queuego.rider', 'dd', 'if=/dev/zero',
                           f'of={partial}', 'bs=1024', 'count=1'])
            with (output/f'instrumentation-cold-{attempt}.log').open('w') as result:
                run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                    'com.queuego.rider.RiderOfflineRoutingRuntimeTest',
                    'com.queuego.rider.test/androidx.test.runner.AndroidJUnitRunner'],
                    stdout=result, stderr=subprocess.STDOUT, timeout=600)
            text = (output/f'instrumentation-cold-{attempt}.log').read_text()
            # adb/am can exit 0 even when instrumentation failed. Require all three real tests.
            assert 'OK (3 tests)' in text and 'FAILURES' not in text, text
            with (output/f'routing-memory-cold-{attempt}.json').open('w') as memory:
                run(adb + ['exec-out', 'run-as', 'com.queuego.rider', 'cat',
                           'files/offline-routing-memory.json'], stdout=memory)
            if attempt == 2:
                run(adb + ['shell', 'run-as', 'com.queuego.rider', 'test', '!', '-e', partial])
                (output/'interrupted-part-recovery.txt').write_text(
                    'Second cold process removed the real stale partial copy and reused the verified graph.\n')
        with (output/'connectivity.txt').open('w') as file:
            run(adb + ['shell', 'dumpsys', 'connectivity'], stdout=file)
    finally:
        run(adb + ['logcat', '-d'], stdout=(output/'logcat.txt').open('w'))
        emulator.terminate()
        try:
            emulator.wait(timeout=15)
        except subprocess.TimeoutExpired:
            emulator.kill()
