"""Capture the real APK's unauthenticated launch; never certify Home/parity/E2E."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import re
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

def capture_role_viewports():
    """Real Android display configurations; no authenticated state or sample orders."""
    evidence = []
    roles = ("customer", "merchant", "rider")
    for role in roles:
        role_apk = repo / f"native-android/{role}/build/outputs/apk/debug/{role}-debug.apk"
        assert role_apk.is_file(), f"Missing actual {role} APK"
        run(adb + ["install", "-r", str(role_apk)])
    for viewport, dimensions, density in (("phone", "1080x2280", "440"),
                                           ("small-phone", "720x1280", "320"),
                                           ("tablet", "1600x2560", "240")):
        run(adb + ["shell", "wm", "size", dimensions])
        run(adb + ["shell", "wm", "density", density])
        for role in roles:
            pkg = f"com.queuego.{role}"
            activity_name = pkg + "/.MainActivity"
            directory = output / "matrix" / role / viewport
            directory.mkdir(parents=True, exist_ok=True)
            run(adb + ["shell", "am", "force-stop", pkg])
            run(adb + ["logcat", "-b", "all", "-c"])
            started = run(adb + ["shell", "am", "start", "-W", "-n", activity_name], capture_output=True, text=True)
            (directory / "activity-start.txt").write_text(started.stdout + started.stderr)
            assert "Status: ok" in started.stdout, f"{role} {viewport} launch failed"
            time.sleep(5)
            run(adb + ["shell", "pidof", pkg], capture_output=True)
            activities = run(adb + ["shell", "dumpsys", "activity", "activities"], capture_output=True, text=True).stdout
            assert any("mResumedActivity" in line and activity_name in line for line in activities.splitlines()), f"{role} {viewport} not resumed"
            runtime = run(adb + ["logcat", "-d", "-s", "AndroidRuntime:E"], capture_output=True, text=True).stdout
            (directory / "android-runtime.txt").write_text(runtime)
            assert "FATAL EXCEPTION" not in runtime, f"{role} {viewport} crashed"
            run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-launch.xml"])
            ui_path = directory / "launch.xml"
            run(adb + ["pull", "/sdcard/queuego-launch.xml", str(ui_path)])
            ui_nodes = list(ET.parse(ui_path).getroot().iter("node"))
            assert any(n.get("package") == pkg and "เข้าสู่ระบบ" in (n.get("text", "") + n.get("content-desc", "")) for n in ui_nodes), f"{role} {viewport} login not rendered"
            screenshot = directory / "login.png"
            with screenshot.open("wb") as png:
                run(adb + ["exec-out", "screencap", "-p"], stdout=png)
            item = {"role": role, "viewport": viewport, "dimensions": dimensions, "density_dpi": int(density),
                    "package": pkg, "source_head": os.environ["GITHUB_SHA"], "state": "fresh-session login",
                    "apk_sha256": hashlib.sha256((repo / f"native-android/{role}/build/outputs/apk/debug/{role}-debug.apk").read_bytes()).hexdigest(),
                    "screenshot_sha256": hashlib.sha256(screenshot.read_bytes()).hexdigest(), "launch_smoke": "PASS",
                    "physical_device": False, "visual_parity_certified": False, "production_e2e_certified": False}
            if role == "rider":
                button = next((n for n in ui_nodes if n.get("text") == "สมัครเป็นไรเดอร์"), None)
                assert button is not None, f"Rider registration entry missing at {viewport}"
                numbers = [int(x) for x in re.findall(r"\d+", button.get("bounds", ""))]
                assert len(numbers) == 4
                run(adb + ["shell", "input", "tap", str((numbers[0] + numbers[2]) // 2), str((numbers[1] + numbers[3]) // 2)])
                time.sleep(3)
                run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-registration.xml"])
                registration_xml = directory / "registration.xml"
                run(adb + ["pull", "/sdcard/queuego-registration.xml", str(registration_xml)])
                actual_registration = list(ET.parse(registration_xml).getroot().iter("node"))
                assert any(n.get("package") == pkg and "ข้อมูลส่วนตัว" in n.get("text", "") for n in actual_registration), "Native registration screen not rendered"
                with (directory / "registration.png").open("wb") as png:
                    run(adb + ["exec-out", "screencap", "-p"], stdout=png)
                item["native_registration_step_one"] = "PASS; no account/data submitted"
                runtime = run(adb + ["logcat", "-d", "-s", "AndroidRuntime:E"], capture_output=True, text=True).stdout
                assert "FATAL EXCEPTION" not in runtime, "Native registration crashed"
            if role == "merchant":
                button = next((n for n in ui_nodes if n.get("text") == "สมัครร้านค้าใหม่"), None)
                assert button is not None, f"Merchant registration entry missing at {viewport}"
                bounds = [int(x) for x in re.findall(r"\d+", button.get("bounds", ""))]
                assert len(bounds) == 4
                run(adb + ["shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)])
                time.sleep(1)
                run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-merchant-register.xml"])
                form_xml = directory / "registration.xml"
                run(adb + ["pull", "/sdcard/queuego-merchant-register.xml", str(form_xml)])
                nodes = list(ET.parse(form_xml).getroot().iter("node"))
                assert any("ชื่อร้าน" in (n.get("text", "") + n.get("content-desc", "")) for n in nodes), "Merchant signup form missing"
                with (directory / "registration.png").open("wb") as png:
                    run(adb + ["exec-out", "screencap", "-p"], stdout=png)
                run(adb + ["shell", "input", "keyevent", "4"])
                time.sleep(1)
                run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-merchant-back.xml"])
                back_xml = directory / "registration-back.xml"
                run(adb + ["pull", "/sdcard/queuego-merchant-back.xml", str(back_xml)])
                nodes = list(ET.parse(back_xml).getroot().iter("node"))
                assert any(n.get("text") == "เข้าสู่ระบบร้านค้า" for n in nodes), "Merchant single Back did not restore login"
                staff = next((n for n in nodes if n.get("text") == "พนักงานหน้าร้าน: สมัคร / ใส่รหัสเชิญ"), None)
                assert staff is not None, "Merchant staff entry missing"
                bounds = [int(x) for x in re.findall(r"\d+", staff.get("bounds", ""))]
                run(adb + ["shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)])
                time.sleep(1)
                run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-merchant-staff.xml"])
                staff_xml = directory / "staff-join.xml"
                run(adb + ["pull", "/sdcard/queuego-merchant-staff.xml", str(staff_xml)])
                assert any("เข้าร่วมร้านค้า" in n.get("text", "") for n in ET.parse(staff_xml).getroot().iter("node")), "Merchant staff join not rendered"
                with (directory / "staff-join.png").open("wb") as png:
                    run(adb + ["exec-out", "screencap", "-p"], stdout=png)
                item["native_merchant_signup_navigation"] = "PASS; signup, single Back, staff invite entry; no account/data submitted"
                runtime = run(adb + ["logcat", "-d", "-s", "AndroidRuntime:E"], capture_output=True, text=True).stdout
                assert "FATAL EXCEPTION" not in runtime, "Merchant registration navigation crashed"
            if role == "customer":
                button = next((n for n in ui_nodes if n.get("text") == "สมัครสมาชิก"), None)
                assert button is not None, f"Customer registration entry missing at {viewport}"
                bounds = [int(x) for x in re.findall(r"\d+", button.get("bounds", ""))]
                assert len(bounds) == 4
                run(adb + ["shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)])
                time.sleep(2)
                run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-customer-registration.xml"])
                registration_xml = directory / "registration.xml"
                run(adb + ["pull", "/sdcard/queuego-customer-registration.xml", str(registration_xml)])
                registration_nodes = list(ET.parse(registration_xml).getroot().iter("node"))
                assert any("ชื่อ-นามสกุล" in (n.get("text", "") + n.get("content-desc", "")) for n in registration_nodes), "Customer signup form missing"
                with (directory / "registration.png").open("wb") as png:
                    run(adb + ["exec-out", "screencap", "-p"], stdout=png)
                selector = next((n for n in registration_nodes if n.get("text") == "เบอร์โทรศัพท์"), None)
                assert selector is not None, "Customer signup mode selector missing"
                bounds = [int(x) for x in re.findall(r"\d+", selector.get("bounds", ""))]
                run(adb + ["shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)])
                time.sleep(1)
                run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-customer-mode.xml"])
                mode_xml = directory / "registration-mode.xml"
                run(adb + ["pull", "/sdcard/queuego-customer-mode.xml", str(mode_xml)])
                mode_nodes = list(ET.parse(mode_xml).getroot().iter("node"))
                choice = next((n for n in mode_nodes if n.get("text") == "อีเมล"), None)
                assert choice is not None, "Customer email signup choice missing"
                bounds = [int(x) for x in re.findall(r"\d+", choice.get("bounds", ""))]
                run(adb + ["shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)])
                time.sleep(1)
                run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-customer-email.xml"])
                email_xml = directory / "registration-email.xml"
                run(adb + ["pull", "/sdcard/queuego-customer-email.xml", str(email_xml)])
                email_nodes = list(ET.parse(email_xml).getroot().iter("node"))
                assert any(n.get("content-desc") == "อีเมล" and n.get("package") == pkg for n in email_nodes), "Customer email input missing"
                with (directory / "registration-email.png").open("wb") as png:
                    run(adb + ["exec-out", "screencap", "-p"], stdout=png)
                run(adb + ["shell", "input", "keyevent", "4"])
                time.sleep(1)
                run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-customer-return.xml"])
                return_xml = directory / "registration-back.xml"
                run(adb + ["pull", "/sdcard/queuego-customer-return.xml", str(return_xml)])
                assert any(n.get("text") == "ยินดีต้อนรับสู่ QueueGo" for n in ET.parse(return_xml).getroot().iter("node")), "Customer Back did not restore login"
                item["native_customer_signup_navigation"] = "PASS; phone/email mode and back; no account/data submitted"
                runtime = run(adb + ["logcat", "-d", "-s", "AndroidRuntime:E"], capture_output=True, text=True).stdout
                assert "FATAL EXCEPTION" not in runtime, "Customer registration navigation crashed"
            evidence.append(item)
    (output / "matrix-metadata.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n")
    print("Actual three-role phone/small-phone/tablet fresh-session launch matrix PASS; authenticated E2E/visual parity remain unverified")


def verify_real_home_map_runtime():
    """A dependency/lifecycle component test, never an authenticated E2E claim."""
    baseline = output / "baseline"
    test_apk = repo / "native-android/rider/build/outputs/apk/androidTest/debug/rider-debug-androidTest.apk"
    runner = "com.queuego.rider.test/androidx.test.runner.AndroidJUnitRunner"
    instrument = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                        "com.queuego.rider.RiderMapRuntimeTest", runner]
    run(adb + ["shell", "wm", "size", "1080x2280"])
    run(adb + ["shell", "wm", "density", "440"])
    run(adb + ["shell", "am", "force-stop", package])
    run(adb + ["install", "-r", str(baseline / "rider-untransformed.apk")])
    run(adb + ["install", "-r", str(baseline / "rider-test.apk")])
    run(adb + ["logcat", "-b", "all", "-c"])
    before = subprocess.run(instrument, capture_output=True, text=True, timeout=120)
    crash = run(adb + ["logcat", "-d"], capture_output=True, text=True).stdout
    before_text = before.stdout + before.stderr + crash
    (output / "map-before-fix.txt").write_text(before_text)
    assert "NoClassDefFoundError" in before_text and "android/support/v4/view/GestureDetectorCompat" in before_text, "Baseline must reproduce the exact missing legacy Longdo class"
    run(adb + ["shell", "am", "force-stop", package])
    run(adb + ["install", "-r", str(apk)])
    run(adb + ["install", "-r", str(test_apk)])
    run(adb + ["logcat", "-b", "all", "-c"])
    after = run(instrument, capture_output=True, text=True, timeout=150)
    process_log = run(adb + ["logcat", "-d", "-s", "QueueGoMapRuntime:I"], capture_output=True, text=True).stdout
    completed_pids = re.findall(r"completed_pid=(\d+)", process_log)
    assert completed_pids, "Corrected map test must identify its completed Android process"
    corrected_pid = completed_pids[-1]
    runtime = run(adb + ["logcat", "-d", "--pid=" + corrected_pid, "-s", "AndroidRuntime:E"], capture_output=True, text=True).stdout
    (output / "map-process-log.txt").write_text(process_log)
    (output / "map-after-fix.txt").write_text(after.stdout + after.stderr + runtime)
    assert "OK (1 test)" in after.stdout, "Real map instrumentation did not pass"
    assert "FATAL EXCEPTION" not in runtime, "Corrected Home map crashed"
    metadata = {
        "source_head": os.environ["GITHUB_SHA"],
        "workflow_run": os.environ["GITHUB_RUN_ID"],
        "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "baseline": "reproduced NoClassDefFoundError android/support/v4/view/GestureDetectorCompat",
        "corrected_instrumentation_process_pid": corrected_pid,
        "actual_native_home_map_creation": "PASS",
        "map_background_foreground_and_activity_recreation": "PASS",
        "authenticated_login_e2e": False,
        "physical_device": False,
        "production_account_or_order_writes": False,
    }
    (output / "map-runtime-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print("Actual Longdo crash before fix reproduced; corrected native Home map mount/background/recreation PASS")


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
        run(adb + ["logcat", "-b", "all", "-c"])
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
        capture_role_viewports()
        verify_real_home_map_runtime()
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
