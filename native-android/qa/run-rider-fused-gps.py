"""Real Google APIs emulator FusedLocationProvider gate for the exact Rider APK.

No debug store injection. The same installed Rider binary must already have passed
country offline routing and renderer gates. This is NOT a physical road test.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
SDK = Path(os.environ["ANDROID_HOME"])
ADB = str(SDK / "platform-tools/adb")
EMULATOR = str(SDK / "emulator/emulator")
AVDMANAGER = str(SDK / "cmdline-tools/latest/bin/avdmanager")
SDKMANAGER = str(SDK / "cmdline-tools/latest/bin/sdkmanager")
APK = ROOT / "native-android/rider/build/outputs/apk/debug/rider-debug.apk"
TEST_APK = ROOT / "native-android/rider/build/outputs/apk/androidTest/debug/rider-debug-androidTest.apk"
OUT = ROOT / "artifacts/rider-fused-gps"
OUT.mkdir(parents=True, exist_ok=True)
SERIAL = "emulator-5556"
ADB_TARGET = [ADB, "-s", SERIAL]
PACKAGE = "com.queuego.rider"
TEST_PACKAGE = PACKAGE + ".test"
TEST_CLASS = "com.queuego.rider.RiderMapRuntimeTest#homeMapMountsBackgroundsAndRecreatesWithoutMissingSdkClasses"
# API30 goldfish GNSS reports wall time as elapsed time; never weaken app guards.
FUSED_SYSTEM_IMAGE = "system-images;android-33;google_apis;x86_64"

def call(command, timeout=180, **kwargs):
    return subprocess.run(command, check=True, timeout=timeout, **kwargs)

def adb(*args, **kwargs):
    return call([*ADB_TARGET, *args], **kwargs)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def wait_boot(proc):
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        assert proc.poll() is None, "Google APIs emulator exited during boot"
        result = subprocess.run([*ADB_TARGET, "shell", "getprop", "sys.boot_completed"],
                                capture_output=True, text=True, timeout=15)
        if result.returncode == 0 and result.stdout.strip() == "1":
            return
        time.sleep(3)
    raise RuntimeError("Google APIs emulator boot timeout")

def ensure_emulator_location_enabled():
    # Permissions alone do not turn on Android's global Location switch.
    # The pinned API33 image supports `cmd location`; fail closed if unavailable.
    adb("shell", "cmd", "location", "set-location-enabled", "true", timeout=15)
    # Google APIs emulators can temporarily drop ADB transport during
    # location-provider startup. Retry only ADB transport exit 255, never
    # a disabled switch, unknown command, or a GPS instrumentation failure.
    for attempt in range(3):
        try:
            state = adb("shell", "cmd", "location", "is-location-enabled",
                        capture_output=True, text=True, timeout=15).stdout.strip()
        except subprocess.CalledProcessError as error:
            if error.returncode != 255:
                raise
            if attempt < 2:
                time.sleep(2)
                continue
            raise RuntimeError("Google APIs master Location switch not verified after transport recovery") from error
        if state != "true":
            raise RuntimeError("Google APIs emulator master Location switch is disabled: " + repr(state))
        return
    raise RuntimeError("Unable to verify Google APIs emulator master Location switch")

def capture_fused_failure_evidence(label):
    # Keep genuine platform/provider evidence when GPS never arrives. No fake
    # locations, relaxation of assertions, or secrets from entire device logs.
    diagnostics = {
        "location-provider": ["shell", "dumpsys", "location"],
        "navigation-service": ["shell", "dumpsys", "activity", "services", PACKAGE],
        "fused-logcat": ["logcat", "-d", "-t", "300", "-s",
                         "QueueGoNavLocation:D", "QueueGoMapRuntime:I", "*:S"],
    }
    for name, arguments in diagnostics.items():
        try:
            result = subprocess.run([*ADB_TARGET, *arguments], capture_output=True,
                                    text=True, timeout=25)
            detail = "exit=" + str(result.returncode) + "\\n" + result.stdout + result.stderr
        except (subprocess.SubprocessError, OSError) as error:
            detail = "diagnostic_unavailable: " + type(error).__name__ + "\\n"
        (OUT / (label + "-" + name + ".txt")).write_text(detail)


def run_case(label, longitude, latitude):
    # Run the identical instrumented test twice in separate target processes.
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "force-stop", TEST_PACKAGE)
    adb("emu", "geo", "fix", str(longitude), str(latitude))
    command = [*ADB_TARGET, "shell", "am", "instrument", "-w", "-r",
               "-e", "class", TEST_CLASS,
               "-e", "expectedLongitude", str(longitude),
               "-e", "expectedLatitude", str(latitude),
               "-e", "allowDebugStoreInjection", "false",
               "-e", "allowIncompleteTiles", "false",
               TEST_PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"]
    # Continue feeding genuine emulator GPS through lifecycle recreation.
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True)
    try:
        deadline = time.monotonic() + 210
        while process.poll() is None and time.monotonic() < deadline:
            adb("emu", "geo", "fix", str(longitude), str(latitude), timeout=15)
            time.sleep(2)
        if process.poll() is None:
            process.kill()
            raise RuntimeError("Fused GPS instrumentation timed out")
        output = process.communicate(timeout=15)[0]
        (OUT / (label + "-instrumentation.txt")).write_text(output)
        if process.returncode != 0 or "OK (1 test)" not in output:
            capture_fused_failure_evidence(label)
            raise AssertionError("Real Fused GPS test failed: " + output[-2500:])
        # ADB screenshot transport can close transiently after instrumented
        # lifecycle recreation. Retry transport only; never retry a failed GPS
        # assertion or manufacture a screenshot.
        screenshot = OUT / (label + ".png")
        screenshot_ok = False
        for attempt in range(3):
            with screenshot.open("wb") as stream:
                capture = subprocess.run([*ADB_TARGET, "exec-out", "screencap", "-p"],
                                         stdout=stream, stderr=subprocess.PIPE,
                                         timeout=30)
            if capture.returncode == 0 and screenshot.read_bytes().startswith(b"\x89PNG\r\n\x1a\n") and screenshot.stat().st_size > 1024:
                screenshot_ok = True
                break
            (OUT / (label + "-screencap-error.txt")).write_text(
                "attempt=" + str(attempt + 1) + " returncode=" + str(capture.returncode) +
                " stderr=" + capture.stderr.decode(errors="replace") + "\n")
            screenshot.unlink(missing_ok=True)
            time.sleep(3)
        assert screenshot_ok, "ADB screenshot transport failed after 3 attempts; GPS test result retained"
        dump = adb("shell", "dumpsys", "activity", "services", PACKAGE,
                   capture_output=True, text=True).stdout
        (OUT / (label + "-services.txt")).write_text(dump)
        # The test explicitly stops its foreground service.
        assert "isForeground=true" not in dump, "Rider location service leaked after test"
    finally:
        if process.poll() is None:
            process.kill()
            process.wait(timeout=10)

def main():
    assert APK.is_file() and TEST_APK.is_file(), "Exact Rider and androidTest APK required"
    expected = (ROOT / "artifacts/rider-offline-routing/apk.sha256").read_text().split()[0]
    assert sha(APK) == expected, "Rider APK differs from offline-tested candidate"
    call([SDKMANAGER, FUSED_SYSTEM_IMAGE], timeout=600,
         input="y\n" * 100, text=True)
    avd_home = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "qg-fused-avds"
    avd_home.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, ANDROID_AVD_HOME=str(avd_home))
    name = "qg_fused_" + os.environ.get("GITHUB_RUN_ID", "local")
    call([AVDMANAGER, "create", "avd", "-n", name, "-k",
          FUSED_SYSTEM_IMAGE, "-d", "pixel_4"],
         input="no\n", text=True, env=env, timeout=60)
    # A second region in the same emulator can inherit a cached Fused fix from
    # the first region. The Rider correctly rejects an implausible 300+ km
    # instantaneous jump; it must not be weakened to accommodate a test fixture.
    # Boot a fresh, wiped Google APIs device for each regional scenario instead.
    # This also proves cold-start behavior with a new Play Services location cache.
    for label, longitude, latitude in (
        ("bangkok", 100.5018, 13.7563),
        ("buriram", 103.1029, 14.9930),
    ):
        with (OUT / (label + "-emulator.log")).open("w") as log:
            emulator = subprocess.Popen([EMULATOR, "-avd", name, "-port", "5556",
                "-wipe-data", "-no-window", "-no-snapshot", "-no-audio",
                "-gpu", "swiftshader", "-memory", "2048", "-cores", "2",
                "-accel", "on"], stdout=log, stderr=subprocess.STDOUT, env=env)
            try:
                wait_boot(emulator)
                adb("install", "-r", str(APK), timeout=240)
                adb("install", "-r", str(TEST_APK), timeout=120)
                adb("shell", "pm", "grant", PACKAGE, "android.permission.ACCESS_FINE_LOCATION")
                adb("shell", "pm", "grant", PACKAGE, "android.permission.ACCESS_COARSE_LOCATION")
                provider = adb("shell", "pm", "list", "packages", "com.google.android.gms",
                               capture_output=True, text=True).stdout
                assert "com.google.android.gms" in provider, "Google Play services absent"
                ensure_emulator_location_enabled()
                run_case(label, longitude, latitude)
            finally:
                subprocess.run([*ADB_TARGET, "emu", "kill"], capture_output=True, timeout=15)
                emulator.terminate()
                try:
                    emulator.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    emulator.kill()
    assert sha(APK) == expected, "APK changed during Fused GPS gate"
    (OUT / "result.json").write_text(json.dumps({
        "apk_sha256": expected, "system_image": FUSED_SYSTEM_IMAGE.removeprefix("system-images;"),
        "debug_store_injection": False, "incomplete_tiles_allowed": False,
        "fresh_wiped_emulator_per_region": True,
        "gnss_transport": "platform_default",
        "cases": ["bangkok", "buriram"], "physical_road_test": False
    }, indent=2) + "\n")
    print("QUEUEGO_REAL_FUSED_GPS_PASS")

if __name__ == "__main__":
    main()
