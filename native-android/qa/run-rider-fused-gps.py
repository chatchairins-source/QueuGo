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
        assert process.returncode == 0 and "OK (1 test)" in output, (
            "Real Fused GPS test failed: " + output[-2500:])
        adb("exec-out", "screencap", "-p", stdout=(OUT / (label + ".png")).open("wb"))
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
    call([SDKMANAGER, "system-images;android-30;google_apis;x86_64"], timeout=600,
         input="y\n" * 100, text=True)
    avd_home = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "qg-fused-avds"
    avd_home.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, ANDROID_AVD_HOME=str(avd_home))
    name = "qg_fused_" + os.environ.get("GITHUB_RUN_ID", "local")
    call([AVDMANAGER, "create", "avd", "-n", name, "-k",
          "system-images;android-30;google_apis;x86_64", "-d", "pixel_4"],
         input="no\n", text=True, env=env, timeout=60)
    with (OUT / "emulator.log").open("w") as log:
        emulator = subprocess.Popen([EMULATOR, "-avd", name, "-port", "5556",
            "-no-window", "-no-snapshot", "-no-audio", "-gpu", "swiftshader",
            "-memory", "2048", "-cores", "2", "-accel", "on"],
            stdout=log, stderr=subprocess.STDOUT, env=env)
        try:
            wait_boot(emulator)
            adb("install", "-r", str(APK), timeout=240)
            adb("install", "-r", str(TEST_APK), timeout=120)
            adb("shell", "pm", "grant", PACKAGE, "android.permission.ACCESS_FINE_LOCATION")
            adb("shell", "pm", "grant", PACKAGE, "android.permission.ACCESS_COARSE_LOCATION")
            provider = adb("shell", "pm", "list", "packages", "com.google.android.gms",
                           capture_output=True, text=True).stdout
            assert "com.google.android.gms" in provider, "Google Play services absent"
            run_case("bangkok", 100.5018, 13.7563)
            run_case("buriram", 103.1029, 14.9930)
            assert sha(APK) == expected, "APK changed during Fused GPS gate"
            (OUT / "result.json").write_text(json.dumps({
                "apk_sha256": expected, "system_image": "android-30;google_apis;x86_64",
                "debug_store_injection": False, "incomplete_tiles_allowed": False,
                "cases": ["bangkok", "buriram"], "physical_road_test": False
            }, indent=2) + "\n")
            print("QUEUEGO_REAL_FUSED_GPS_PASS")
        finally:
            subprocess.run([*ADB_TARGET, "emu", "kill"], capture_output=True, timeout=15)
            emulator.terminate()
            try:
                emulator.wait(timeout=15)
            except subprocess.TimeoutExpired:
                emulator.kill()

if __name__ == "__main__":
    main()
