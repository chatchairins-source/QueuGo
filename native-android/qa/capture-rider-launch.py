"""Capture the real APK's unauthenticated launch; never certify Home/parity/E2E."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import struct
import subprocess
import time
import xml.etree.ElementTree as ET


def run(args, **kwargs):
    return subprocess.run(args, check=True, timeout=kwargs.pop("timeout", 120), **kwargs)


repo = Path(__file__).resolve().parents[2]
sdk = Path(os.environ["ANDROID_HOME"])
output = repo / "artifacts/native-rider-launch"
output.mkdir(parents=True, exist_ok=True)
apk = repo / "native-android/rider/build/outputs/apk/debug/rider-debug.apk"
assert apk.is_file(), "Build the actual Rider APK first"
assert os.access("/dev/kvm", os.R_OK | os.W_OK), "An accelerated Android runner is required"
manager = sdk / "cmdline-tools/latest/bin"
# Recent command-line tools and the emulator can resolve different default
# Android user homes on hosted runners. Share an explicit, run-scoped registry.
avd_registry = Path(os.environ["RUNNER_TEMP"]) / "queuego-avd-registry"
avd_registry.mkdir(parents=True, exist_ok=True)
os.environ["ANDROID_AVD_HOME"] = str(avd_registry)
run([str(manager / "sdkmanager"), "emulator", "platform-tools", "system-images;android-30;default;x86_64"], input="y\n" * 100, text=True, timeout=300)
name = "queuego_rider_capture_" + os.environ["GITHUB_RUN_ID"] + "_" + os.environ.get("GITHUB_RUN_ATTEMPT", "1")
avd_path = Path(os.environ["RUNNER_TEMP"]) / name
run([str(manager / "avdmanager"), "create", "avd", "-n", name, "-k", "system-images;android-30;default;x86_64", "-p", str(avd_path), "-d", "pixel_4"], input="no\n", text=True)
assert (avd_registry / (name + ".ini")).is_file(), "AVD registry was not created in the shared emulator search path"
emulator_bin = str(sdk / "emulator/emulator")
run([emulator_bin, "-accel-check"])
adb_bin = str(sdk / "platform-tools/adb")
run([adb_bin, "start-server"])
adb = [adb_bin, "-s", "emulator-5554"]
package = "com.queuego.rider"
component = package + "/.MainActivity"

with (output / "emulator.log").open("w") as log:
    emulator = subprocess.Popen([emulator_bin, "-avd", name, "-port", "5554", "-accel", "on", "-no-window", "-no-snapshot", "-no-audio", "-gpu", "swiftshader", "-memory", "2048", "-cores", "2"], stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.monotonic() + 240
        while True:
            assert emulator.poll() is None, "Emulator exited before Android booted"
            boot = subprocess.run(adb + ["shell", "getprop", "sys.boot_completed"], capture_output=True, text=True, timeout=15)
            if boot.returncode == 0 and boot.stdout.strip() == "1":
                break
            assert time.monotonic() < deadline, "Android boot timed out; no Native screenshot certified"
            time.sleep(5)
        run(adb + ["install", "-r", str(apk)])
        run(adb + ["logcat", "-c"])
        start = run(adb + ["shell", "am", "start", "-W", "-n", component], capture_output=True, text=True)
        (output / "activity-start.txt").write_text(start.stdout + start.stderr)
        assert "Status: ok" in start.stdout, "Activity launch did not succeed"
        time.sleep(10)
        run(adb + ["shell", "pidof", package], capture_output=True)
        activity = run(adb + ["shell", "dumpsys", "activity", "activities"], capture_output=True, text=True).stdout
        assert any("mResumedActivity" in line and component in line for line in activity.splitlines()), "Rider is not the resumed activity"
        runtime = run(adb + ["logcat", "-d", "-s", "AndroidRuntime:E"], capture_output=True, text=True).stdout
        (output / "android-runtime.txt").write_text(runtime)
        assert "FATAL EXCEPTION" not in runtime, "Android runtime crash; inspect the evidence"
        run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-rider-launch.xml"])
        ui = output / "rider-launch.xml"
        run(adb + ["pull", "/sdcard/queuego-rider-launch.xml", str(ui)])
        nodes = list(ET.parse(ui).getroot().iter("node"))
        assert any(n.get("package") == package for n in nodes), "No Rider UI in the actual accessibility hierarchy"
        assert any("เข้าสู่ระบบ" in (n.get("text", "") + n.get("content-desc", "")) for n in nodes), "Expected the real fresh-session login screen, not an empty page"
        image = output / "rider-native-login.png"
        with image.open("wb") as png:
            run(adb + ["exec-out", "screencap", "-p"], stdout=png)
        raw = image.read_bytes()
        assert raw[:8] == b"\x89PNG\r\n\x1a\n", "Invalid device screenshot"
        width, height = struct.unpack(">II", raw[16:24])
        assert (width, height) == (1080, 2280), "Unexpected capture dimensions"
        metrics = run(adb + ["shell", "wm", "size"], capture_output=True, text=True).stdout
        metrics += run(adb + ["shell", "wm", "density"], capture_output=True, text=True).stdout
        (output / "device-metrics.txt").write_text(metrics)
        api = run(adb + ["shell", "getprop", "ro.build.version.sdk"], capture_output=True, text=True).stdout.strip()
        metadata = {
            "source_head": os.environ["GITHUB_SHA"],
            "workflow_run": os.environ["GITHUB_RUN_ID"],
            "captured_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "package": package,
            "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "screenshot_sha256": hashlib.sha256(raw).hexdigest(),
            "width_px": width, "height_px": height, "android_api": api,
            "hardware_accelerated_emulator": True,
            "physical_device": False,
            "state": "fresh-session login; no credentials entered",
            "launch_smoke": "PASS",
            "home_map_captured": False,
            "visual_parity_certified": False,
            "production_e2e_certified": False,
            "physical_fcm_certified": False,
        }
        (output / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
        print("Actual Rider unauthenticated launch PASS; Home visual / E2E / physical FCM remain unverified")
    finally:
        for arguments, filename in [(["logcat", "-d", "-s", "AndroidRuntime:E"], "android-runtime.txt"), (["logcat", "-b", "crash", "-d"], "crash-buffer.txt")]:
            try:
                result = subprocess.run(adb + arguments, capture_output=True, text=True, timeout=15)
                (output / filename).write_text(result.stdout + result.stderr)
            except subprocess.TimeoutExpired:
                pass
        try:
            subprocess.run(adb + ["emu", "kill"], capture_output=True, timeout=15)
        except subprocess.TimeoutExpired:
            pass
        finally:
            emulator.terminate()
            try:
                emulator.wait(timeout=15)
            except subprocess.TimeoutExpired:
                emulator.kill()
