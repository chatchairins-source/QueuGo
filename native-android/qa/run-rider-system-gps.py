"""Exercise the exact Rider APK through real Android/Fused emulator location.

No direct application-store injection, authentication or Production orders.
Emulator geo fixes are synthetic system input, NOT physical-road certification.
"""
from pathlib import Path
import hashlib
import json
import os
import struct
import subprocess
import time

repo = Path(__file__).resolve().parents[2]
sdk = Path(os.environ["ANDROID_HOME"])
output = repo / "artifacts/rider-system-gps"
output.mkdir(parents=True, exist_ok=True)
registry = Path(os.environ["RUNNER_TEMP"]) / "rider-system-gps-avds"
registry.mkdir(exist_ok=True)
os.environ["ANDROID_AVD_HOME"] = str(registry)
package = "com.queuego.rider"
apk = repo / "native-android/rider/build/outputs/apk/debug/rider-debug.apk"
tests = repo / "native-android/rider/build/outputs/apk/androidTest/debug/rider-debug-androidTest.apk"
image = "system-images;android-30;google_apis;x86_64"
adb = [str(sdk / "platform-tools/adb"), "-s", "emulator-5554"]


def run(args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True,
                          timeout=kwargs.pop("timeout", 180), **kwargs)


def read(args):
    return run(adb + args, capture_output=True, text=True, timeout=30).stdout.strip()


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def routes():
    return {family: read(["shell", "ip", family, "route", "show", "default"])
            for family in ("-4", "-6")}


head = run(["git", "rev-parse", "HEAD"], cwd=repo, capture_output=True, text=True).stdout.strip()
original_sha = digest(apk)
assert tests.is_file(), "Exact-source Rider instrumentation APK absent"
run([sdk / "cmdline-tools/latest/bin/sdkmanager", image], input="y\n" * 100, text=True, timeout=600)
run([sdk / "cmdline-tools/latest/bin/avdmanager", "create", "avd", "-n", "queuego_system_gps",
     "-k", image, "-d", "pixel_4"], input="no\n", text=True)
cases = []
with (output / "emulator.log").open("w") as log:
    emulator = subprocess.Popen([str(sdk / "emulator/emulator"), "-avd", "queuego_system_gps",
        "-port", "5554", "-no-window", "-no-audio", "-no-snapshot", "-no-boot-anim",
        "-gpu", "swiftshader", "-memory", "2048", "-partition-size", "4096", "-accel", "on"],
        stdout=log, stderr=log)
    try:
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            assert emulator.poll() is None, "Google APIs emulator exited before boot"
            boot = subprocess.run(adb + ["shell", "getprop", "sys.boot_completed"],
                                  capture_output=True, text=True, timeout=15)
            if boot.returncode == 0 and boot.stdout.strip() == "1":
                break
            time.sleep(2)
        else:
            raise AssertionError("Google APIs Android boot timed out")
        assert read(["shell", "pm", "path", "com.google.android.gms"]).startswith("package:"), (
            "Real Fused Location gate requires installed Google Play services")
        run(adb + ["install", "-r", apk], timeout=300)
        run(adb + ["install", "-r", tests])
        # Fresh app has no login/session and cannot publish any dispatch/order data.
        assert read(["shell", "pm", "clear", package]) == "Success"
        for permission in ("ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION"):
            run(adb + ["shell", "pm", "grant", package, "android.permission." + permission])
        run(adb + ["shell", "cmd", "location", "set-location-enabled", "true"])
        run(adb + ["shell", "cmd", "connectivity", "airplane-mode", "enable"])
        run(adb + ["shell", "svc", "wifi", "disable"])
        run(adb + ["shell", "svc", "data", "disable"])
        before = routes()
        if any(before.values()):
            run(adb + ["root"])
            run(adb + ["wait-for-device"], timeout=60)
            interfaces = set()
            for route in before.values():
                for line in route.splitlines():
                    words = line.split()
                    if "dev" in words:
                        interfaces.add(words[words.index("dev") + 1])
            for interface in sorted(interfaces):
                assert interface and interface != "lo"
                run(adb + ["shell", "ip", "link", "set", "dev", interface, "down"])
        after = routes()
        assert not any(after.values()), f"GPS gate still has a default network route: {after}"
        assert read(["shell", "settings", "get", "global", "airplane_mode_on"]) == "1"
        assert read(["shell", "settings", "get", "global", "wifi_on"]) == "0"
        (output / "network-isolation.json").write_text(json.dumps({
            "before": before, "after": after, "airplane": True}, indent=2) + "\n")
        for attempt in (1, 2):
            directory = output / f"cold-{attempt}"
            directory.mkdir()
            run(adb + ["shell", "am", "force-stop", package])
            run(adb + ["logcat", "-b", "all", "-c"])
            command = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                "com.queuego.rider.RiderMapRuntimeTest",
                "-e", "expectedLongitude", "103.0994795",
                "-e", "expectedLatitude", "14.9918756",
                "-e", "allowDebugStoreInjection", "false",
                "-e", "allowIncompleteTiles", "false",
                package + ".test/androidx.test.runner.AndroidJUnitRunner"]
            # Keep fresh GPS flowing after each actual foreground-service recreation.
            # Small real system-coordinate movements exceed its 0.5m update threshold.
            with (directory / "instrumentation.txt").open("w") as result:
                instrument = subprocess.Popen(command, stdout=result, stderr=subprocess.STDOUT)
                try:
                    deadline = time.monotonic() + 240
                    fixes = 0
                    while instrument.poll() is None:
                        assert time.monotonic() < deadline, "Real system GPS instrumentation timed out"
                        offset = (-1, 0, 1, 0)[fixes % 4] * 0.00001
                        run(adb + ["emu", "geo", "fix", "103.0994795",
                                   str(14.9918756 + offset)], timeout=15,
                            stdout=subprocess.DEVNULL)
                        fixes += 1
                        time.sleep(1)
                finally:
                    if instrument.poll() is None:
                        instrument.kill()
                    instrument.wait(timeout=15)
            transcript = (directory / "instrumentation.txt").read_text()
            full_log = read(["logcat", "-d", "-v", "threadtime"])
            (directory / "logcat.txt").write_text(full_log + "\n")
            assert instrument.returncode == 0 and "OK (1 test)" in transcript and "FAILURES" not in transcript, transcript
            assert "debug_store_fix_injected_no_play_services" not in full_log, (
                "Direct app-store location injection must never certify system GPS")
            assert full_log.count("injected_gps_store_verified") == 2, "Initial and recreated real GPS not verified"
            assert full_log.count("injected_gps_camera_verified") == 3, "Actual camera did not follow real system fixes"
            assert full_log.count("lifecycle_stress_cycle=") == 8, "Missing real lifecycle stress"
            first_start = full_log.find("foreground_updates_started")
            assert first_start >= 0, "Real location foreground service did not start"
            owned_log = full_log[first_start:]
            starts = owned_log.count("foreground_updates_started")
            stops = owned_log.count("foreground_updates_stopped")
            assert starts >= 2 and starts == stops, f"Location service leaked: starts={starts}, stops={stops}"
            assert not any(routes().values()), "Network route reappeared during GPS instrumentation"
            screenshots = {}
            for state in ("initial", "resumed", "recreated"):
                for suffix in (".png", "-first-frame.png"):
                    target = directory / (state + suffix)
                    with target.open("wb") as stream:
                        run(adb + ["exec-out", "run-as", package, "cat",
                                   "files/map-runtime/" + state + suffix], stdout=stream)
                    raw = target.read_bytes()
                    assert raw[:8] == b"\x89PNG\r\n\x1a\n", "Actual GPS screenshot missing"
                    width, height = struct.unpack(">II", raw[16:24])
                    assert width > 100 and height > 100
                    screenshots[target.name] = digest(target)
                camera = json.loads(read(["exec-out", "run-as", package, "cat",
                                         f"files/map-runtime/{state}-camera.json"]))
                assert camera["strict_tile_completeness"] is True
                assert abs(camera["longitude"] - 103.0994795) <= 0.002
                assert abs(camera["latitude"] - 14.9918756) <= 0.002
                (directory / (state + "-camera.json")).write_text(json.dumps(camera, indent=2) + "\n")
            cases.append({"cold_process": attempt, "system_geo_commands": fixes,
                          "service_starts": starts, "service_stops": stops,
                          "direct_store_injection": False, "screenshots": screenshots})
        assert digest(apk) == original_sha, "GPS gate changed the offline-tested APK"
        (output / "metadata.json").write_text(json.dumps({
            "source_head": head, "workflow_run": os.environ.get("GITHUB_RUN_ID"),
            "apk_sha256": original_sha, "instrumentation_apk_sha256": digest(tests),
            "system_image": image, "cases": cases, "airplane_mode": True,
            "physical_road_test": False, "authenticated": False,
            "production_orders_created": False}, indent=2) + "\n")
        print("QUEUEGO_RIDER_REAL_FUSED_EMULATOR_GPS_PASS")
    finally:
        try:
            run(adb + ["logcat", "-d"], stdout=(output / "final-logcat.txt").open("w"), timeout=30)
        finally:
            subprocess.run(adb + ["emu", "kill"], capture_output=True, timeout=15)
            emulator.terminate()
            try:
                emulator.wait(timeout=15)
            except subprocess.TimeoutExpired:
                emulator.kill()
                emulator.wait(timeout=15)
