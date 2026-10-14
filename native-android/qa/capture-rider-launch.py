"""Capture the real APK's unauthenticated launch; never certify Home/parity/E2E."""
from pathlib import Path
import datetime
import hashlib
import json
import math
import os
import re
import struct
import subprocess
import time
import zlib
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


def package_pids(pkg: str):
    result = run(adb + ["shell", "pidof", pkg], capture_output=True, text=True)
    pids = [value for value in result.stdout.strip().split() if value.isdigit()]
    assert pids, f"{pkg} process is not running"
    return pids


def android_runtime_for_pids(pids):
    chunks = []
    for pid in pids:
        chunks.append(run(
            adb + ["logcat", "-d", "--pid=" + pid, "-s", "AndroidRuntime:E"],
            capture_output=True,
            text=True,
        ).stdout)
    return "\n".join(chunks)


def full_crash_buffer():
    return run(
        adb + ["logcat", "-b", "crash", "-d"],
        capture_output=True,
        text=True,
    ).stdout


def package_crash_blocks(crash_text: str, pkg: str):
    lines = crash_text.splitlines()
    blocks = []
    for index, line in enumerate(lines):
        if "FATAL EXCEPTION" not in line:
            continue
        window = "\n".join(lines[index:index + 16])
        if f"Process: {pkg}," in window:
            blocks.append(window)
    return "\n\n".join(blocks)


def assert_no_package_crash(pkg: str, evidence_path: Path):
    crash_text = full_crash_buffer()
    evidence_path.write_text(crash_text)
    relevant = package_crash_blocks(crash_text, pkg)
    assert not relevant, f"{pkg} crashed; inspect {evidence_path.name}"


def read_ui(path: Path):
    run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-current.xml"], timeout=15)
    run(adb + ["pull", "/sdcard/queuego-current.xml", str(path)], timeout=15)
    return list(ET.parse(path).getroot().iter("node"))


def wait_ui(path: Path, predicate, timeout_seconds=30):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        nodes = read_ui(path)
        if predicate(nodes):
            return nodes
        time.sleep(0.4)
    raise AssertionError(f"Native UI did not reach expected state: {path.name}")


def capture_customer_market(directory: Path, dimensions: str):
    """Public Production reads and actual UI taps; no account/cart/order inserted."""
    width, height = map(int, dimensions.split("x"))
    path = directory / "market-home-navigation.xml"
    nodes = read_ui(path)
    target = None
    for _ in range(6):
        target = next((n for n in nodes if n.get("text") == "ตลาดสด"), None)
        if target is not None:
            break
        # The current Home category row scrolls horizontally. Derive its y from
        # a rendered category label instead of using guessed screen coordinates.
        anchor = next((n for n in nodes if n.get("text") in ("อาหาร", "เครื่องดื่ม", "ของใช้", "ฝากซัก")), None)
        assert anchor is not None, "Customer category row missing"
        bounds = [int(x) for x in re.findall(r"\d+", anchor.get("bounds", ""))]
        assert len(bounds) == 4 and 0 <= bounds[1] < bounds[3] <= height
        y = (bounds[1] + bounds[3]) // 2
        run(adb + ["shell", "input", "swipe", str(width - 40), str(y), "40", str(y), "350"])
        nodes = read_ui(path)
    assert target is not None, "Market category did not become visible after bounded swipes"
    bounds = [int(x) for x in re.findall(r"\d+", target.get("bounds", ""))]
    assert len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]
    run(adb + ["shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)])
    nodes = wait_ui(directory / "market-browse.xml", lambda ns:
        any(n.get("text") == "เลือกตลาด" for n in ns) and
        any(n.get("clickable") == "true" and n.get("enabled") == "true" and
            any("⌄" in (child.get("text", "") + child.get("content-desc", "")) for child in n.iter("node"))
            for n in ns))
    assert not any(n.get("text") in ("ที่อยู่ / จุดสังเกต", "ยืนยันสั่งซื้อ") for n in nodes), "Market browse leaked checkout form"
    assert not any(n.get("class") == "android.widget.EditText" for n in nodes), "Market browse added a search/checkout field"
    with (directory / "market-browse.png").open("wb") as png:
        run(adb + ["exec-out", "screencap", "-p"], stdout=png)
    run(adb + ["shell", "input", "keyevent", "4"])
    wait_ui(directory / "market-back.xml", lambda ns: any(n.get("text") == "ค้นหาร้านค้าจากทุกหมวด" for n in ns))
    return {"state": "guest Market browse", "single_back_to_home": "PASS",
            "data_source": "existing Production public RPCs; no rows submitted",
            "screenshot_sha256": hashlib.sha256((directory / "market-browse.png").read_bytes()).hexdigest(),
            "populated_cart_e2e_certified": False, "visual_parity_certified": False}


def capture_customer_location():
    """Tap the real Customer guest Home location entry; no coordinate or account fixtures."""
    pkg = "com.queuego.customer"
    customer_apk = repo / "native-android/customer/build/outputs/apk/debug/customer-debug.apk"
    run(adb + ["install", "-r", str(customer_apk)])
    for permission_state in ("denied", "allowed"):
        directory = output / "customer-location" / permission_state
        directory.mkdir(parents=True, exist_ok=True)
        run(adb + ["shell", "pm", "clear", pkg])
        if permission_state == "allowed":
            for permission in ("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"):
                run(adb + ["shell", "pm", "grant", pkg, "android.permission." + permission])
        run(adb + ["logcat", "-b", "all", "-c"])
        run(adb + ["shell", "am", "start", "-W", "-n", pkg + "/.MainActivity"], timeout=40)
        nodes = wait_ui(directory / "home.xml", lambda ns: any(n.get("text") == "เลือกที่อยู่จัดส่ง" for n in ns))
        target = next(n for n in nodes if n.get("text") == "เลือกที่อยู่จัดส่ง")
        bounds = [int(x) for x in re.findall(r"\d+", target.get("bounds", ""))]
        run(adb + ["shell", "input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2)])
        time.sleep(3)
        # Keep the original crash evidence even when UI dumping or process checks fail.
        crash = full_crash_buffer()
        (directory / "after-open-crash.txt").write_text(crash)
        relevant = package_crash_blocks(crash, pkg)
        if relevant:
            print("QUEUEGO_CUSTOMER_LOCATION_CRASH " + permission_state + "\n" + relevant)
            raise AssertionError("Actual Customer location picker crashed on open")
        nodes = read_ui(directory / "picker.xml")
        if permission_state == "denied":
            nodes = wait_ui(directory / "permission.xml", lambda ns: any(
                n.get("resource-id", "").endswith("permission_deny_button") for n in ns))
            deny = next((n for n in nodes if n.get("resource-id", "").endswith("permission_deny_button")), None)
            assert deny is not None, "Expected actual Android location permission request"
            b = [int(x) for x in re.findall(r"\d+", deny.get("bounds", ""))]
            run(adb + ["shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2)])
        time.sleep(20)
        (directory / "all-logcat.txt").write_text(run(adb + ["logcat", "-d"], capture_output=True, text=True).stdout)
        assert_no_package_crash(pkg, directory / "settled-crash.txt")
        package_pids(pkg)
        nodes = read_ui(directory / "settled.xml")
        assert any(n.get("text") == "เลือกที่อยู่จัดส่ง" for n in nodes), "Customer location screen disappeared"
        assert any(n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน" for n in nodes), "GPS action missing"
        with (directory / "picker.png").open("wb") as png:
            run(adb + ["exec-out", "screencap", "-p"], stdout=png)
        from PIL import Image
        frame = Image.open(directory / "picker.png").convert("RGB")
        gray_fraction = sum(1 for pixel in frame.getdata() if pixel == (179, 179, 179)) / (frame.width * frame.height)
        (directory / "render-metrics.json").write_text(json.dumps({"sdk_blank_gray_fraction": gray_fraction}))
        assert gray_fraction < 0.01, "Actual Customer map remains blank SDK gray"
        # Pan the actual rendered map, without saving an address or injecting GPS.
        gps = next(n for n in nodes if n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน")
        gb = [int(x) for x in re.findall(r"\d+", gps.get("bounds", ""))]
        description = next(n for n in nodes if n.get("text", "").startswith("ปักหมุดตำแหน่งรับสินค้า"))
        db = [int(x) for x in re.findall(r"\d+", description.get("bounds", ""))]
        map_y = (db[3] + gb[1]) // 2
        run(adb + ["shell", "input", "swipe", str(frame.width // 2), str(map_y), str(frame.width // 2 + 100), str(map_y), "400"])
        wait_ui(directory / "panned.xml", lambda ns: any(n.get("text", "").startswith("Lat: ") and "--" not in n.get("text", "") for n in ns))
        if permission_state == "allowed":
            run(adb + ["shell", "input", "tap", str((gb[0]+gb[2])//2), str((gb[1]+gb[3])//2)])
            time.sleep(1)
            assert_no_package_crash(pkg, directory / "gps-tap-crash.txt")
        run(adb + ["shell", "input", "keyevent", "3"])
        time.sleep(1)
        # A hot Activity may not notify am start -W's launch wait. Check the real
        # foreground UI and a subsequent tap rather than that CLI wait result.
        run(adb + ["shell", "am", "start", "-n", pkg + "/.MainActivity"], timeout=15)
        nodes = wait_ui(directory / "resumed.xml", lambda ns: any(n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน" for n in ns))
        activities = run(adb + ["shell", "dumpsys", "activity", "activities"], capture_output=True, text=True).stdout
        assert any("mResumedActivity" in line and pkg + "/.MainActivity" in line for line in activities.splitlines()), "Customer Activity is not resumed"
        time.sleep(3)
        assert_no_package_crash(pkg, directory / "resume-crash.txt")
        package_pids(pkg)
        nodes = read_ui(directory / "resumed.xml")
        assert any(n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน" for n in nodes), "Picker did not resume"
        with (directory / "resumed.png").open("wb") as png:
            run(adb + ["exec-out", "screencap", "-p"], stdout=png)
        back = next(n for n in nodes if n.get("content-desc") == "ย้อนกลับ")
        b = [int(x) for x in re.findall(r"\d+", back.get("bounds", ""))]
        run(adb + ["shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2)])
        wait_ui(directory / "back-home.xml", lambda ns: any(n.get("text") == "เลือกที่อยู่จัดส่ง" for n in ns) and not any(n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน" for n in ns))
        assert_no_package_crash(pkg, directory / "back-crash.txt")
        print("QUEUEGO_CUSTOMER_LOCATION_PASS " + permission_state)


def capture_role_viewports(roles=("customer", "merchant", "rider")):
    """Real Android display configurations; no authenticated state or sample orders."""
    evidence = []
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
            role_pids = package_pids(pkg)
            activities = run(adb + ["shell", "dumpsys", "activity", "activities"], capture_output=True, text=True).stdout
            assert any("mResumedActivity" in line and activity_name in line for line in activities.splitlines()), f"{role} {viewport} not resumed"
            runtime = android_runtime_for_pids(role_pids)
            (directory / "android-runtime.txt").write_text(runtime)
            assert "FATAL EXCEPTION" not in runtime, f"{role} {viewport} crashed"
            run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-launch.xml"])
            ui_path = directory / "launch.xml"
            run(adb + ["pull", "/sdcard/queuego-launch.xml", str(ui_path)])
            ui_nodes = list(ET.parse(ui_path).getroot().iter("node"))
            if role == "customer":
                # The actual fresh Customer entry is public Home; authentication is explicit.
                assert any(n.get("text") == "ตะกร้า" for n in ui_nodes), "Customer guest Home navigation missing"
                assert not any(n.get("text") == "ยินดีต้อนรับสู่ QueueGo" for n in ui_nodes), "Customer still forces login at launch"
                with (directory / "guest-home.png").open("wb") as png:
                    run(adb + ["exec-out", "screencap", "-p"], stdout=png)
                login = next((n for n in ui_nodes if n.get("text") == "เข้าสู่ระบบ" and n.get("package") == pkg), None)
                assert login is not None, "Customer guest login entry missing"
                bounds = [int(x) for x in re.findall(r"\d+", login.get("bounds", ""))]
                assert len(bounds) == 4
                run(adb + ["shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)])
                time.sleep(1)
                run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-customer-login.xml"])
                run(adb + ["pull", "/sdcard/queuego-customer-login.xml", str(directory / "login.xml")])
                ui_nodes = list(ET.parse(directory / "login.xml").getroot().iter("node"))
                assert any(n.get("text") == "ยินดีต้อนรับสู่ QueueGo" for n in ui_nodes), "Customer guest action did not open real login"
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
                runtime = android_runtime_for_pids(role_pids)
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
                runtime = android_runtime_for_pids(role_pids)
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
                run(adb + ["shell", "input", "keyevent", "4"])
                time.sleep(1)
                run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-customer-guest-back.xml"])
                run(adb + ["pull", "/sdcard/queuego-customer-guest-back.xml", str(directory / "guest-back.xml")])
                returned = list(ET.parse(directory / "guest-back.xml").getroot().iter("node"))
                assert any(n.get("text") == "ตะกร้า" for n in returned), "Customer login Back did not return to guest Home"
                assert not any(n.get("text") == "ยินดีต้อนรับสู่ QueueGo" for n in returned), "Customer login Back stayed on auth"
                item["native_customer_market_browse"] = capture_customer_market(directory, dimensions)
                item["native_customer_guest_navigation"] = "PASS; public Home, explicit login, single Back to Home; no account/data submitted"
                item["native_customer_signup_navigation"] = "PASS; phone/email mode and back; no account/data submitted"
                runtime = android_runtime_for_pids(role_pids)
                assert "FATAL EXCEPTION" not in runtime, "Customer registration navigation crashed"
            assert_no_package_crash(pkg, directory / "crash-buffer.txt")
            if os.environ.get("QG_MAP_DISPLAY_ONLY") == "1" and role == "rider":
                import base64
                from PIL import Image
                for state in ("login", "registration"):
                    preview = directory / (state + ".jpg")
                    with Image.open(directory / (state + ".png")) as bitmap:
                        bitmap.convert("RGB").resize((720, round(bitmap.height * 720 / bitmap.width))).save(preview, quality=85)
                    print("QUEUEGO_RIDER_FRAME " + viewport + "/" + state + " " + base64.b64encode(preview.read_bytes()).decode())
            evidence.append(item)
    (output / "matrix-metadata.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n")
    print("Actual " + ",".join(roles) + " phone/small-phone/large-screen fresh-session launch matrix PASS; authenticated E2E/visual parity remain unverified")


def valid_real_map_evidence(filename, payload):
    # ADB exec-out can report success while returning a remote cat error on stdout.
    if filename.endswith(".png"):
        if not payload.startswith(b"\x89PNG\r\n\x1a\n"):
            return False
        offset = 8
        header = False
        pixels = False
        while offset + 12 <= len(payload):
            length = struct.unpack(">I", payload[offset:offset + 4])[0]
            end = offset + 12 + length
            if end > len(payload):
                return False
            kind = payload[offset + 4:offset + 8]
            data = payload[offset + 8:offset + 8 + length]
            crc = struct.unpack(">I", payload[offset + 8 + length:end])[0]
            if zlib.crc32(kind + data) & 0xffffffff != crc:
                return False
            if offset == 8:
                if kind != b"IHDR" or length != 13:
                    return False
                width, height = struct.unpack(">II", data[:8])
                header = width > 0 and height > 0
            if kind == b"IDAT":
                pixels = True
            if kind == b"IEND":
                return header and pixels and length == 0 and end == len(payload)
            offset = end
        return False
    try:
        row = json.loads(payload)
        names = ("longitude", "latitude", "zoom", "first_frame_gray_fraction",
                 "final_gray_fraction", "capture_after_ms")
        if not all(type(row[name]) in (int, float) and math.isfinite(row[name]) for name in names):
            return False
        return (-180 <= row["longitude"] <= 180 and -90 <= row["latitude"] <= 90
                and row["zoom"] >= 0 and row["capture_after_ms"] >= 0
                and 0 <= row["first_frame_gray_fraction"] <= 1
                and 0 <= row["final_gray_fraction"] <= 1)
    except (ValueError, TypeError, KeyError, UnicodeError):
        return False


def retain_real_map_frames(directory):
    """Keep available original frames even when instrumentation fails its gates."""
    directory.mkdir(parents=True, exist_ok=True)
    copies = []
    unavailable = []
    for state in ("initial", "resumed", "recreated"):
        for suffix in ("-first-frame.png", ".png", "-camera.json"):
            filename = state + suffix
            try:
                copied = subprocess.run(
                    adb + ["exec-out", "run-as", package, "cat", "files/map-runtime/" + filename],
                    capture_output=True, timeout=10, check=False
                )
            except subprocess.TimeoutExpired:
                copied = None
            # Later lifecycle states may be absent after an early failure.
            if copied is not None and copied.returncode == 0 and valid_real_map_evidence(filename, copied.stdout):
                (directory / filename).write_bytes(copied.stdout)
                copies.append({"file": filename, "sha256": hashlib.sha256(copied.stdout).hexdigest()})
            else:
                unavailable.append(filename)
                (directory / filename).unlink(missing_ok=True)
    manifest = {
        "source_head": os.environ["GITHUB_SHA"],
        "workflow_run": os.environ["GITHUB_RUN_ID"],
        "physical_device": False,
        "visual_parity_certified": False,
        "files": copies,
        "unavailable_files": unavailable,
    }
    (directory / "capture-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


def verify_real_home_map_runtime():
    """A dependency/lifecycle component test, never an authenticated E2E claim."""
    baseline = output / "baseline"
    test_apk = repo / "native-android/rider/build/outputs/apk/androidTest/debug/rider-debug-androidTest.apk"
    runner = "com.queuego.rider.test/androidx.test.runner.AndroidJUnitRunner"
    instrument = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                        "com.queuego.rider.RiderMapRuntimeTest",
                        "-e", "allowIncompleteTiles", "true", runner]
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
    # Clear only generated evidence so a partial run cannot retain stale frames.
    run(adb + ["shell", "run-as", package, "rm", "-rf", "files/map-runtime"])
    run(adb + ["logcat", "-b", "all", "-c"])
    after_process = subprocess.Popen(
        instrument,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    running_pids = []
    instrument_deadline = time.time() + 150
    while after_process.poll() is None:
        if time.time() >= instrument_deadline:
            after_process.kill()
            after_process.communicate()
            raise subprocess.TimeoutExpired(instrument, 150)
        pid_probe = subprocess.run(
            adb + ["shell", "pidof", package],
            capture_output=True,
            text=True,
            timeout=5,
            check=False
        )
        running_pids.extend(re.findall(r"\b\d+\b", pid_probe.stdout))
        time.sleep(0.25)
    after_stdout, after_stderr = after_process.communicate(timeout=10)
    after = subprocess.CompletedProcess(
        instrument,
        after_process.returncode,
        after_stdout,
        after_stderr
    )
    process_log = run(
        adb + ["logcat", "-d", "-v", "threadtime", "-s", "QueueGoMapRuntime:I"],
        capture_output=True,
        text=True
    ).stdout
    completed_pids = re.findall(r"completed_pid=(\d+)", process_log)
    captured_pids = re.findall(
        r"\s(\d+)\s+\d+\s+I\s+QueueGoMapRuntime:\s+captured=(?:initial|resumed|recreated)\b",
        process_log
    )
    unique_running_pids = list(dict.fromkeys(running_pids))
    assert unique_running_pids, "Corrected map test must expose its Android process while instrumentation is running"
    assert len(unique_running_pids) == 1, "Corrected map test must not crash/restart into multiple Android processes"
    observed_pids = completed_pids or captured_pids or unique_running_pids
    corrected_pid = observed_pids[-1]
    assert corrected_pid == unique_running_pids[-1], "Corrected map log PID must match the process observed during instrumentation"
    assert all(pid == corrected_pid for pid in captured_pids), "Corrected map frames must remain inside one Android process"
    runtime = run(adb + ["logcat", "-d", "--pid=" + corrected_pid, "-s", "AndroidRuntime:E"], capture_output=True, text=True).stdout
    (output / "map-process-log.txt").write_text(process_log)
    (output / "map-after-fix.txt").write_text(after.stdout + after.stderr + runtime)
    retained_frames = retain_real_map_frames(output / "map-frames")
    assert after.returncode == 0, "Real map instrumentation returned a non-zero exit code"
    assert "OK (1 test)" in (after.stdout + after.stderr), "Real map instrumentation did not pass"
    assert "FATAL EXCEPTION" not in runtime, "Corrected Home map crashed"
    assert len(retained_frames["files"]) == 9, "Successful map runtime must retain all original PNG/camera evidence"
    metadata = {
        "source_head": os.environ["GITHUB_SHA"],
        "workflow_run": os.environ["GITHUB_RUN_ID"],
        "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "baseline": "reproduced NoClassDefFoundError android/support/v4/view/GestureDetectorCompat",
        "corrected_instrumentation_process_pid": corrected_pid,
        "corrected_pid_source": (
            "completed_pid" if completed_pids
            else "captured_frame_logcat" if captured_pids
            else "instrumentation_pid_poll"
        ),
        "actual_native_home_map_creation": "PASS",
        "map_background_foreground_and_activity_recreation": "PASS",
        "authenticated_login_e2e": False,
        "physical_device": False,
        "production_account_or_order_writes": False,
    }
    (output / "map-runtime-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print("Actual Longdo crash before fix reproduced; corrected native Home map mount/background/recreation PASS")


def verify_voice_foreground_runtime():
    runner = "com.queuego.rider.test/androidx.test.runner.AndroidJUnitRunner"
    result = run(adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                       "com.queuego.rider.NativeVoiceForegroundRuntimeTest", runner],
                 capture_output=True, text=True, timeout=120)
    (output / "voice-foreground-runtime.txt").write_text(result.stdout + result.stderr)
    assert "OK (1 test)" in result.stdout, "Microphone foreground service lifecycle failed"
    (output / "voice-foreground-metadata.json").write_text(json.dumps({
        "source_head": os.environ["GITHUB_SHA"],
        "workflow_run": os.environ["GITHUB_RUN_ID"],
        "microphone_service_home_background_and_owned_cleanup": "PASS",
        "physical_device": False, "voice_audio_certified": False,
        "production_account_or_order_writes": False,
    }, indent=2) + "\n")
    print("Microphone foreground service background/owned cleanup PASS; real voice audio remains unverified")


def verify_navigation_location_foreground_runtime():
    runner = "com.queuego.rider.test/androidx.test.runner.AndroidJUnitRunner"
    result = run(adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                       "com.queuego.rider.RiderNavigationLocationServiceRuntimeTest", runner],
                 capture_output=True, text=True, timeout=120)
    (output / "navigation-location-foreground-runtime.txt").write_text(result.stdout + result.stderr)
    assert "OK (1 test)" in result.stdout, "Navigation location foreground service lifecycle failed"
    logcat = run(adb + ["logcat", "-d", "-s", "QueueGoNavLocation:I"],
                 capture_output=True, text=True).stdout
    (output / "navigation-location-logcat.txt").write_text(logcat)
    assert "foreground_updates_started" in logcat, "Location runtime did not start Fused updates"
    assert "foreground_updates_stopped" in logcat, "Location runtime did not stop Fused updates"
    (output / "navigation-location-metadata.json").write_text(json.dumps({
        "source_head": os.environ["GITHUB_SHA"],
        "workflow_run": os.environ["GITHUB_RUN_ID"],
        "location_service_visible_start_background_survival_and_cleanup": "PASS",
        "physical_device": False,
        "physical_gps_certified": False,
        "production_account_or_order_writes": False,
    }, indent=2) + "\n")
    print("Navigation location foreground service visible-start/background-survival/cleanup PASS")



def verify_merchant_shop_map_runtime():
    """Exercise the real Merchant Longdo picker lifecycle without session or business fixtures."""
    pkg = "com.queuego.merchant"
    apk = repo / "native-android/merchant/build/outputs/apk/debug/merchant-debug.apk"
    test_apk = repo / "native-android/merchant/build/outputs/apk/androidTest/debug/merchant-debug-androidTest.apk"
    runner = "com.queuego.merchant.test/androidx.test.runner.AndroidJUnitRunner"
    run(adb + ["install", "-r", str(apk)])
    run(adb + ["install", "-r", str(test_apk)])
    directory = output / "merchant-shop-map"
    directory.mkdir(parents=True, exist_ok=True)
    for state in ("denied", "allowed"):
        run(adb + ["shell", "am", "force-stop", pkg])
        for permission in ("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"):
            command = "grant" if state == "allowed" else "revoke"
            subprocess.run(
                adb + ["shell", "pm", command, pkg, "android.permission." + permission],
                capture_output=True, text=True, timeout=15
            )
        run(adb + ["logcat", "-b", "all", "-c"])
        result = run(
            adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                   "com.queuego.merchant.MerchantShopMapRuntimeTest", runner],
            capture_output=True, text=True, timeout=150
        )
        (directory / (state + "-instrumentation.txt")).write_text(result.stdout + result.stderr)
        full_log = run(adb + ["logcat", "-d", "-v", "threadtime"], capture_output=True, text=True).stdout
        (directory / (state + "-logcat.txt")).write_text(full_log)
        assert "OK (1 test)" in result.stdout, "Merchant edit-shop map lifecycle failed: " + state
        assert_no_package_crash(pkg, directory / (state + "-crash.txt"))
        print("QUEUEGO_MERCHANT_SHOP_MAP_PASS " + state)


def capture_map_display_only():
    """Actual Longdo component frames; allowed cases inject emulator GPS only, never Production data."""
    import base64
    from PIL import Image
    test_apk = repo / "native-android/rider/build/outputs/apk/androidTest/debug/rider-debug-androidTest.apk"
    run(adb + ["install", "-r", str(test_apk)])
    # Public UI is reviewed independently before map/network checks; no form data is submitted.
    capture_role_viewports(("rider",))
    evidence = []
    for viewport, dimensions, density, granted, gps_fix in (
        ("phone-denied", "1080x2280", "440", False, None),
        ("phone-allowed", "1080x2280", "440", True, (100.612345, 13.712345)),
        ("large-allowed", "1600x2560", "240", True, (100.632345, 13.732345)),
    ):
        directory = output / viewport
        directory.mkdir(exist_ok=True)
        run(adb + ["shell", "am", "force-stop", package])
        run(adb + ["shell", "wm", "size", dimensions])
        run(adb + ["shell", "wm", "density", density])
        for permission in ("ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION"):
            command = "grant" if granted else "revoke"
            subprocess.run(
                adb + ["shell", "pm", command, package, "android.permission." + permission],
                capture_output=True, text=True, timeout=15, check=False
            )
        if gps_fix is not None:
            run(adb + ["emu", "geo", "fix", str(gps_fix[0]), str(gps_fix[1])])
            time.sleep(1)
        run(adb + ["logcat", "-b", "all", "-c"])
        instrument = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                            "com.queuego.rider.RiderMapRuntimeTest",
                            "-e", "allowIncompleteTiles", "true"]
        if gps_fix is not None:
            instrument += ["-e", "expectedLongitude", str(gps_fix[0]),
                           "-e", "expectedLatitude", str(gps_fix[1])]
        instrument += ["com.queuego.rider.test/androidx.test.runner.AndroidJUnitRunner"]
        result = run(instrument, capture_output=True, text=True, timeout=210)
        (directory / "instrumentation.txt").write_text(result.stdout + result.stderr)
        print("QUEUEGO_MAP_INSTRUMENTATION " + viewport + "\n" + result.stdout + result.stderr)
        full_log = run(adb + ["logcat", "-d", "-v", "threadtime"], capture_output=True, text=True).stdout
        (directory / "full-logcat.txt").write_text(full_log)
        if "OK (1 test)" not in result.stdout:
            print("QUEUEGO_MAP_FAILURE_LOG\n" + full_log[-24000:])
        assert "OK (1 test)" in result.stdout, "Actual map mount/lifecycle test failed: " + viewport
        starts = full_log.count("foreground_updates_started")
        stops = full_log.count("foreground_updates_stopped")
        if granted:
            assert starts >= 3 and stops >= 3, "Foreground location lifecycle was not exercised: " + viewport
        else:
            assert starts == 0, "Location updates started without permission"
        runtime = run(adb + ["logcat", "-d", "-s", "AndroidRuntime:E"], capture_output=True, text=True).stdout
        (directory / "android-runtime.txt").write_text(runtime)
        assert_no_package_crash(package, directory / "crash-buffer.txt")
        screenshots = []
        for state in ("initial", "resumed", "recreated"):
            image = directory / (state + ".png")
            with image.open("wb") as png:
                run(adb + ["exec-out", "run-as", package, "cat", "files/map-runtime/" + state + ".png"], stdout=png)
            raw = image.read_bytes()
            assert raw[:8] == b"\x89PNG\r\n\x1a\n", "Invalid real Android screenshot"
            first = directory / (state + "-first-frame.png")
            with first.open("wb") as png:
                run(adb + ["exec-out", "run-as", package, "cat", "files/map-runtime/" + state + "-first-frame.png"], stdout=png)
            camera = run(adb + ["exec-out", "run-as", package, "cat", "files/map-runtime/" + state + "-camera.json"], capture_output=True, text=True)
            (directory / (state + "-camera.json")).write_text(camera.stdout)
            camera_evidence = json.loads(camera.stdout)
            preview = directory / (state + ".jpg")
            with Image.open(image) as bitmap:
                assert bitmap.size == tuple(map(int, dimensions.split("x"))), "Unexpected actual viewport"
                bitmap.convert("RGB").resize((540, round(bitmap.height * 540 / bitmap.width))).save(preview, quality=80)
            # Read-only review transport, alongside original lossless evidence artifacts.
            print("QUEUEGO_MAP_FRAME " + viewport + "/" + state + " " + base64.b64encode(preview.read_bytes()).decode())
            screenshots.append({"state": state, "sha256": hashlib.sha256(raw).hexdigest(), "file": str(image.relative_to(output)), "actual_sdk_camera": camera_evidence})
        for state in ("initial", "resumed", "recreated"):
            with Image.open(directory / (state + ".png")) as bitmap:
                # Ignore system bars and the SDK center cross; inspect actual map coverage.
                interior = bitmap.convert("RGB").crop((0, bitmap.height // 10, bitmap.width, bitmap.height * 9 // 10))
                colors = interior.resize((180, 240)).quantize(colors=64).getcolors()
                dominant_fraction = max(count for count, _ in colors) / (180 * 240)
                assert dominant_fraction < 0.95, "Blank native map pixels: " + viewport + "/" + state
        from PIL import ImageChops
        def map_interior(state):
            with Image.open(directory / (state + ".png")) as bitmap:
                return bitmap.convert("RGB").crop((0, bitmap.height // 10, bitmap.width, bitmap.height * 9 // 10)).resize((180, 240))
        initial = map_interior("initial")
        for state in ("resumed", "recreated"):
            difference = ImageChops.difference(initial, map_interior(state))
            changed = sum(1 for pixel in difference.getdata() if max(pixel) > 60) / (180 * 240)
            # Network tile completion can change pixels while the camera stays fixed.
            # Camera continuity is asserted against the actual SDK by instrumentation.
            print("QUEUEGO_MAP_PIXEL_CHANGE " + viewport + "/" + state + " " + str(changed))
        evidence.append({"viewport": viewport, "dimensions": dimensions, "density": density,
                         "location_permission_granted": granted,
                         "emulator_gps_fix": (
                             {"longitude": gps_fix[0], "latitude": gps_fix[1]} if gps_fix is not None else None
                         ),
                         "map_mount_background_recreation": "PASS",
                         "screenshots": screenshots})
    metadata = {"source_head": os.environ["GITHUB_SHA"], "workflow_run": os.environ["GITHUB_RUN_ID"],
                "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "cases": evidence,
                "physical_device": False, "gps_injected_for_allowed_emulator_cases": True,
                "sample_jobs_created": False,
                "production_account_or_order_writes": False, "authenticated_home_parity_certified": False,
                "physical_gps_positioning_certified": False, "visible_tiles_review_required": True}
    (output / "map-display-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print("QUEUEGO_MAP_METADATA " + json.dumps(metadata))


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
        if os.environ.get("QG_CUSTOMER_LOCATION_ONLY") == "1":
            capture_customer_location()
            verify_merchant_shop_map_runtime()
            capture_map_display_only()
        elif os.environ.get("QG_MAP_DISPLAY_ONLY") == "1":
            capture_map_display_only()
        else:
            start = run(adb + ["shell", "am", "start", "-W", "-n", component], capture_output=True, text=True)
            (output / "activity-start.txt").write_text(start.stdout + start.stderr)
            assert "Status: ok" in start.stdout, "Activity launch did not succeed"
            time.sleep(10)
            rider_pids = package_pids(package)
            activity = run(adb + ["shell", "dumpsys", "activity", "activities"], capture_output=True, text=True).stdout
            assert any("mResumedActivity" in line and component in line for line in activity.splitlines()), "Rider is not the resumed activity"
            runtime = android_runtime_for_pids(rider_pids)
            (output / "android-runtime.txt").write_text(runtime)
            assert "FATAL EXCEPTION" not in runtime, "QueueGo Rider runtime crash; inspect the evidence"
            assert_no_package_crash(package, output / "rider-crash-buffer.txt")
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
            verify_voice_foreground_runtime()
            verify_navigation_location_foreground_runtime()
    finally:
        for arguments, filename in [(["logcat", "-d", "-s", "AndroidRuntime:E"], "android-runtime.txt"), (["logcat", "-b", "crash", "-d"], "crash-buffer.txt"), (["logcat", "-d"], "all-logcat-final.txt"), (["shell", "dumpsys", "activity", "lastanr"], "last-anr.txt")]:
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
