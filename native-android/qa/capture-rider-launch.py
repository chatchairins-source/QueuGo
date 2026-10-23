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


# Same pinned, real Buriram vector archive as Map Engine CI. It is provisioned
# through adb into debug app-private storage, never embedded in a release APK.
CI_MAP_URL = "https://github.com/chatchairins-source/QueuGo/releases/download/rider-buriram-offline-20261016/buriram-shortbread.pmtiles"
CI_MAP_SHA256 = "30699573ed2068d3c7e4393ad477dbe8febf68f453f27ae88437f5c652a3ddfc"


def provision_real_map(pkg):
    if os.environ.get("QG_DEBUG_MAP_ARCHIVE") != "1":
        return
    import urllib.request
    archive = Path(os.environ["RUNNER_TEMP"]) / "queuego-ci-buriram.pmtiles"
    if not archive.is_file():
        with urllib.request.urlopen(CI_MAP_URL, timeout=90) as response, archive.open("wb") as target:
            import shutil
            shutil.copyfileobj(response, target)
    actual = hashlib.sha256(archive.read_bytes()).hexdigest()
    assert actual == CI_MAP_SHA256, "Real Buriram map archive checksum mismatch"
    device_archive = "/data/local/tmp/queuego-ci-buriram.pmtiles"
    run(adb + ["push", str(archive), device_archive])
    run(adb + ["shell", "run-as", pkg, "mkdir", "-p", "files"])
    run(adb + ["shell", "run-as", pkg, "cp", device_archive, "files/queuego-ci-buriram.pmtiles"])
    verified = run(adb + ["shell", "run-as", pkg, "sha256sum", "files/queuego-ci-buriram.pmtiles"],
                   capture_output=True, text=True).stdout.split()[0]
    assert verified == CI_MAP_SHA256, "App-private real map archive checksum mismatch"
    (output / (pkg + "-map-source.json")).write_text(json.dumps({
        "source_url": CI_MAP_URL, "sha256": actual, "source": "pinned real Buriram PMTiles",
        "debug_only": True, "public_runtime_certified": False,
        "routing_certified": False, "physical_gps_certified": False
    }, indent=2))


def queuego_map_runtime_configured():
    """Exercise real tiles in debug CI or use the approved HTTPS runtime."""
    return os.environ.get("QG_DEBUG_MAP_ARCHIVE") == "1" or os.environ.get("QG_MAP_SERVICE_BASE_URL", "").strip().startswith("https://")


def record_map_runtime_blocked(scope):
    """Record an external release blocker without turning it into a false runtime PASS."""
    assert os.environ.get("QG_DEBUG_MAP_ARCHIVE") != "1", "Requested real-map CI must execute, never report BLOCKED"
    directory = output / "map-runtime-blockers"
    directory.mkdir(parents=True, exist_ok=True)
    payload = {
        "source_head": os.environ["GITHUB_SHA"],
        "workflow_run": os.environ["GITHUB_RUN_ID"],
        "scope": scope,
        "status": "BLOCKED",
        "reason": "QG_MAP_SERVICE_BASE_URL is not configured with approved QueueGo Map hosting",
        "physical_device": False,
        "production_mutation": False,
    }
    (directory / (scope.replace("/", "-") + ".json")).write_text(json.dumps(payload, indent=2) + "\n")
    print("QUEUEGO_MAP_RUNTIME_BLOCKED " + json.dumps(payload))


def package_pids_optional(pkg: str):
    # pidof exits 1 when no process exists. During process-death polling that is
    # the expected success condition, so keep this non-throwing helper separate
    # from the established strict evidence resolver below.
    result = subprocess.run(
        adb + ["shell", "pidof", pkg],
        capture_output=True,
        text=True,
        timeout=15,
        check=False,
    )
    return [value for value in result.stdout.strip().split() if value.isdigit()]


def package_pids(pkg: str):
    pids = package_pids_optional(pkg)
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


class HierarchyNotReady(RuntimeError):
    pass


def read_ui(path: Path):
    # uiautomator can exit zero with a null root during a cold launch. Never read
    # the previous screen's XML as evidence of the current screen.
    run(adb + ["shell", "rm", "-f", "/sdcard/queuego-current.xml"], timeout=15)
    dumped = run(adb + ["shell", "uiautomator", "dump", "/sdcard/queuego-current.xml"],
                 timeout=15, capture_output=True, text=True)
    if "null root" in (dumped.stdout + dumped.stderr).lower():
        raise HierarchyNotReady("uiautomator returned a null root")
    run(adb + ["pull", "/sdcard/queuego-current.xml", str(path)], timeout=15)
    return list(ET.parse(path).getroot().iter("node"))


def wait_ui(path: Path, predicate, timeout_seconds=30):
    deadline = time.monotonic() + timeout_seconds
    last_error = None
    while time.monotonic() < deadline:
        try:
            nodes = read_ui(path)
            if predicate(nodes):
                return nodes
        except (HierarchyNotReady, subprocess.CalledProcessError, subprocess.TimeoutExpired,
                ET.ParseError, FileNotFoundError) as error:
            last_error = error
            with path.with_suffix(".poll-errors.txt").open("a") as evidence:
                evidence.write(type(error).__name__ + ": " + str(error) + "\n")
        time.sleep(0.4)
    raise AssertionError(f"Native UI did not reach expected state: {path.name}; last error: {last_error}")


def capture_customer_market(directory: Path, dimensions: str):
    """Public Production reads and actual UI taps; no account/cart/order inserted."""
    width, height = map(int, dimensions.split("x"))
    path = directory / "market-home-navigation.xml"
    nodes = read_ui(path)
    target = None
    category_labels = {"อาหาร", "เครื่องดื่ม", "ร้านขายของชำ", "ฝากซัก", "ตลาดสด", "ช้อปปิ้ง"}

    # Ride adds a real Home action above the category row, so on shorter viewports
    # the row can legitimately start below the visible LazyColumn window. Reveal
    # the row first by swiping inside the actual vertically-scrollable semantics
    # bounds; only then perform the category row's horizontal swipes.
    for _ in range(5):
        target = next((n for n in nodes if n.get("text") == "ตลาดสด"), None)
        anchor = next((n for n in nodes if n.get("text") in category_labels), None)
        if target is not None or anchor is not None:
            break
        scrollables = []
        for node in nodes:
            if node.get("scrollable") != "true":
                continue
            bounds = [int(x) for x in re.findall(r"\d+", node.get("bounds", ""))]
            if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                scrollables.append((bounds, node))
        assert scrollables, "Customer Home vertical scroll container missing"
        bounds, _ = max(scrollables, key=lambda item: (item[0][3] - item[0][1]) * (item[0][2] - item[0][0]))
        x = (bounds[0] + bounds[2]) // 2
        start_y = bounds[3] - max(24, (bounds[3] - bounds[1]) // 10)
        end_y = bounds[1] + max(24, (bounds[3] - bounds[1]) // 3)
        assert bounds[0] <= x <= bounds[2] and bounds[1] < end_y < start_y < bounds[3]
        run(adb + ["shell", "input", "swipe", str(x), str(start_y), str(x), str(end_y), "350"])
        nodes = read_ui(path)

    for _ in range(6):
        target = next((n for n in nodes if n.get("text") == "ตลาดสด"), None)
        if target is not None:
            break
        # The current Home category row scrolls horizontally. Derive its y from
        # a rendered category label instead of using guessed screen coordinates.
        anchor = next((n for n in nodes if n.get("text") in category_labels), None)
        assert anchor is not None, "Customer category row missing after bounded vertical reveal"
        bounds = [int(x) for x in re.findall(r"\d+", anchor.get("bounds", ""))]
        assert len(bounds) == 4 and 0 <= bounds[1] < bounds[3] <= height
        y = (bounds[1] + bounds[3]) // 2
        run(adb + ["shell", "input", "swipe", str(width - 40), str(y), "40", str(y), "350"])
        nodes = read_ui(path)
    assert target is not None, "Market category did not become visible after bounded vertical/horizontal swipes"
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


def customer_location_permission_nodes(nodes, directory):
    # CustomerLocationPicker automatically requests GPS after mapReady. Without
    # a map source that callback never runs, so exercise its explicit GPS action.
    if not queuego_map_runtime_configured():
        gps_action = next((n for n in nodes if n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน"), None)
        assert gps_action is not None, "GPS action missing before permission request"
        gb = [int(x) for x in re.findall(r"\d+", gps_action.get("bounds", ""))]
        run(adb + ["shell", "input", "tap", str((gb[0]+gb[2])//2), str((gb[1]+gb[3])//2)])
    return wait_ui(directory / "permission.xml", lambda ns: any(
        n.get("resource-id", "").endswith("permission_deny_button") for n in ns))


def capture_customer_location():
    """Tap the real Customer guest Home location entry; no coordinate or account fixtures."""
    pkg = "com.queuego.customer"
    customer_apk = repo / "native-android/customer/build/outputs/apk/debug/customer-debug.apk"
    run(adb + ["install", "-r", str(customer_apk)])
    for permission_state in ("denied", "allowed"):
        directory = output / "customer-location" / permission_state
        directory.mkdir(parents=True, exist_ok=True)
        run(adb + ["shell", "pm", "clear", pkg])
        provision_real_map(pkg)
        if os.environ.get("QG_DEBUG_MAP_ARCHIVE") == "1":
            # Emulator GPS stays within the real archive; never changes a user address.
            run(adb + ["emu", "geo", "fix", "103.0994795", "14.9918756"])
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
            nodes = customer_location_permission_nodes(nodes, directory)
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
        if not queuego_map_runtime_configured():
            # The picker itself must remain stable even while public QueueGo Map hosting
            # is intentionally unconfigured. Tile/pan/GPS-camera proof stays BLOCKED.
            with (directory / "picker-blocked-runtime.png").open("wb") as png:
                run(adb + ["exec-out", "screencap", "-p"], stdout=png)
            run(adb + ["shell", "input", "keyevent", "3"])
            time.sleep(1)
            run(adb + ["shell", "am", "start", "-n", pkg + "/.MainActivity"], timeout=15)
            nodes = wait_ui(directory / "resumed-blocked-runtime.xml", lambda ns: any(
                n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน" for n in ns))
            assert_no_package_crash(pkg, directory / "blocked-runtime-crash.txt")
            back = next(n for n in nodes if n.get("content-desc") == "ย้อนกลับ")
            bb = [int(x) for x in re.findall(r"\d+", back.get("bounds", ""))]
            run(adb + ["shell", "input", "tap", str((bb[0]+bb[2])//2), str((bb[1]+bb[3])//2)])
            wait_ui(directory / "back-home-blocked-runtime.xml", lambda ns: any(
                n.get("text") == "เลือกที่อยู่จัดส่ง" for n in ns) and not any(
                n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน" for n in ns))
            record_map_runtime_blocked("customer-location-" + permission_state)
            continue
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
    if os.environ.get("QG_CUSTOMER_LOCATION_STRESS") == "1":
        stress_customer_location()
    if os.environ.get("QG_CUSTOMER_DRAFT_RESTORE") == "1":
        if queuego_map_runtime_configured():
            probe_customer_draft_restore()
        else:
            record_map_runtime_blocked("customer-location-unsaved-draft-restore")


def probe_customer_draft_restore():
    """Kill the real Guest process before Save; navigation and draft must restore."""
    pkg = "com.queuego.customer"
    directory = output / "customer-location" / "unsaved-draft-restore"
    directory.mkdir(parents=True, exist_ok=True)
    result = {"source_head": os.environ["GITHUB_SHA"], "workflow_run": os.environ["GITHUB_RUN_ID"],
              "authenticated": False, "production_writes": False, "result": "FAIL"}

    def tap(node):
        b = [int(x) for x in re.findall(r"\d+", node.get("bounds", ""))]
        assert len(b) == 4 and b[2] > b[0] and b[3] > b[1]
        run(adb + ["shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2)])

    def selected_point(ns):
        text = next(n.get("text") for n in ns if n.get("text", "").startswith("Lat: "))
        values = [float(x) for x in re.findall(r"-?\d+\.\d+", text)]
        assert len(values) == 2, "Real selected map point missing"
        return values

    try:
        nodes = wait_ui(directory / "home.xml", lambda ns: any(n.get("text") == "ส่งไปที่" for n in ns))
        tap(next(n for n in nodes if n.get("text") == "ส่งไปที่"))
        nodes = wait_ui(directory / "picker.xml", lambda ns: any(n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน" for n in ns))
        # Reuse the real pan geometry from the public permission flow.
        description = next(n for n in nodes if n.get("text", "").startswith("ปักหมุดตำแหน่งรับสินค้า"))
        gps = next(n for n in nodes if n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน")
        db = [int(x) for x in re.findall(r"\d+", description.get("bounds", ""))]
        gb = [int(x) for x in re.findall(r"\d+", gps.get("bounds", ""))]
        width = int(run(adb + ["shell", "wm", "size"], capture_output=True, text=True).stdout.strip().split()[-1].split("x")[0])
        y = (db[3] + gb[1]) // 2
        run(adb + ["shell", "input", "swipe", str(width//2), str(y), str(width//2+120), str(y), "400"])
        nodes = wait_ui(directory / "draft.xml", lambda ns: any(n.get("class") == "android.widget.EditText" and n.get("text", "").strip() for n in ns), timeout_seconds=40)
        point = selected_point(nodes)
        address = next(n.get("text") for n in nodes if n.get("class") == "android.widget.EditText")
        old_pids = package_pids(pkg)
        run(adb + ["shell", "input", "keyevent", "3"])
        time.sleep(1)
        run(adb + ["shell", "am", "kill", pkg])
        deadline = time.monotonic() + 10
        while subprocess.run(adb + ["shell", "pidof", pkg], capture_output=True).returncode == 0:
            assert time.monotonic() < deadline, "Customer process did not end"
            time.sleep(0.4)
        run(adb + ["shell", "am", "start", "-n", pkg + "/.MainActivity"])
        nodes = wait_ui(directory / "restored.xml", lambda ns: any(n.get("text") in ("ส่งไปที่", "ใช้ตำแหน่ง GPS ปัจจุบัน") for n in ns))
        assert package_pids(pkg) != old_pids, "New process was not created"
        result["restored_screen"] = "picker" if any(n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน" for n in nodes) else "home"
        assert result["restored_screen"] == "picker", "Process recreation discarded the unsaved address screen"
        restored = selected_point(nodes)
        assert all(abs(a-b) < 0.000002 for a,b in zip(point, restored)), "Process recreation changed draft coordinates"
        assert any(n.get("class") == "android.widget.EditText" and n.get("text") == address for n in nodes), "Process recreation lost draft address"
        assert_no_package_crash(pkg, directory / "crash.txt")
        result["result"] = "PASS"
    finally:
        (directory / "results.json").write_text(json.dumps(result, indent=2))
        (directory / "all-logcat.txt").write_text(run(adb + ["logcat", "-d"], capture_output=True, text=True).stdout)
        with (directory / "restored.png").open("wb") as png:
            run(adb + ["exec-out", "screencap", "-p"], stdout=png)


def stress_customer_location():
    """Real guest Save→Home and durable restoration; only device-local writes."""
    pkg = "com.queuego.customer"
    directory = output / "customer-location" / "save-stress"
    directory.mkdir(parents=True, exist_ok=True)
    cases = []

    def tap(node):
        b = [int(x) for x in re.findall(r"\d+", node.get("bounds", ""))]
        assert len(b) == 4 and b[2] > b[0] and b[3] > b[1], "Missing visible touch target"
        run(adb + ["shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2)])

    def saved_location():
        raw = run(adb + ["exec-out", "run-as", pkg, "cat",
                        "shared_prefs/queuego_customer_delivery_location.xml"],
                  capture_output=True, text=True).stdout
        root = ET.fromstring(raw)
        return json.loads(next(n.text for n in root if n.get("name") == "location"))

    log_path = directory / "continuous-logcat.txt"
    with log_path.open("w") as log:
        capture = subprocess.Popen(adb + ["logcat", "-T", "1", "-b", "main", "-b", "system", "-b", "crash"],
                                   stdout=log, stderr=subprocess.STDOUT)
        try:
            previous = None
            for index in range(30):
                case = directory / ("round-%02d" % index)
                case.mkdir(exist_ok=True)
                nodes = wait_ui(case / "home.xml", lambda ns: any(n.get("text") == "ส่งไปที่" for n in ns))
                tap(next(n for n in nodes if n.get("text") == "ส่งไปที่"))
                nodes = wait_ui(case / "picker.xml", lambda ns: any(n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน" for n in ns))
                if index % 3 == 0:
                    tap(next(n for n in nodes if n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน"))
                    nodes = wait_ui(case / "gps.xml", lambda ns: any(n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน" for n in ns))
                description = next(n for n in nodes if n.get("text", "").startswith("ปักหมุดตำแหน่งรับสินค้า"))
                gps = next(n for n in nodes if n.get("text") == "ใช้ตำแหน่ง GPS ปัจจุบัน")
                db = [int(x) for x in re.findall(r"\d+", description.get("bounds", ""))]
                gb = [int(x) for x in re.findall(r"\d+", gps.get("bounds", ""))]
                width = int(run(adb + ["shell", "wm", "size"], capture_output=True, text=True).stdout.strip().split()[-1].split("x")[0])
                y = (db[3] + gb[1]) // 2
                run(adb + ["shell", "input", "swipe", str(width//2), str(y),
                           str(width//2 + (90 if index % 2 == 0 else -110)), str(y), "350"])
                if index % 5 == 0:
                    run(adb + ["shell", "input", "keyevent", "3"])
                    run(adb + ["shell", "am", "start", "-n", pkg + "/.MainActivity"])
                # Let the real reverse geocoder settle; no fixture address/GPS is injected.
                def save_ready(ns):
                    return any(n.get("text") == "บันทึกที่อยู่นี้" and n.get("enabled") == "true" for n in ns)
                nodes = wait_ui(case / "before-save.xml", save_ready, timeout_seconds=40)
                time.sleep(1)
                nodes = read_ui(case / "before-save.xml")
                coordinate = next(n.get("text") for n in nodes if n.get("text", "").startswith("Lat: "))
                point = [float(v) for v in re.findall(r"-?\d+\.\d+", coordinate)]
                assert len(point) == 2, "Selected real map point is missing"
                tap(next(n for n in nodes if n.get("text") == "บันทึกที่อยู่นี้"))
                nodes = wait_ui(case / "saved-home.xml", lambda ns: any(n.get("text") == "ส่งไปที่" for n in ns))
                saved = saved_location()
                assert abs(saved["lat"] - point[0]) < 0.000002 and abs(saved["lng"] - point[1]) < 0.000002, "Saved coordinates differ from selected map point"
                assert saved["address"].strip(), "Empty saved address"
                assert any(n.get("text") == saved["address"] for n in nodes), "Home displays a stale delivery address"
                assert previous is None or (saved["lat"], saved["lng"]) != previous, "Map pan did not change location"
                previous = saved["lat"], saved["lng"]
                if index % 10 == 9:
                    old_pids = package_pids(pkg)
                    run(adb + ["shell", "input", "keyevent", "3"])
                    time.sleep(1)
                    run(adb + ["shell", "am", "kill", pkg])
                    deadline = time.monotonic() + 10
                    while subprocess.run(adb + ["shell", "pidof", pkg], capture_output=True).returncode == 0:
                        assert time.monotonic() < deadline, "System kill did not end Customer process"
                        time.sleep(0.4)
                    run(adb + ["shell", "am", "start", "-n", pkg + "/.MainActivity"])
                    nodes = wait_ui(case / "restored-home.xml", lambda ns: any(n.get("text") == saved["address"] for n in ns))
                    assert saved_location() == saved, "Process recreation lost selected delivery location"
                    assert package_pids(pkg) != old_pids, "Process was not recreated"
                if index in (0, 29):
                    with (case / "saved-home.png").open("wb") as png:
                        run(adb + ["exec-out", "screencap", "-p"], stdout=png)
                assert_no_package_crash(pkg, case / "crash.txt")
                cases.append({"round": index + 1, "save_home": "PASS", "background": index % 5 == 0,
                              "process_recreation": index % 10 == 9})
                print("QUEUEGO_CUSTOMER_SAVE_STRESS_PASS " + str(index + 1), flush=True)
        finally:
            capture.terminate()
            capture.wait(timeout=15)
            (directory / "results.json").write_text(json.dumps({"source_head": os.environ["GITHUB_SHA"],
                "workflow_run": os.environ["GITHUB_RUN_ID"], "cases": cases,
                "authenticated": False, "production_writes": False, "gps_injected": False}, indent=2))
    failures = re.findall(r"FATAL EXCEPTION|ANR in com\.queuego\.customer|SIGSEGV|SIGABRT|shared_map_\w*failed|Native map (?:setup|operation) failed", log_path.read_text())
    assert not failures, "Crash/ANR/native-map failure in Customer save stress log: " + str(failures)


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



def verify_three_role_system_kill_recovery():
    """Exercise Android-style background process death and cold recreation for all three apps."""
    evidence = []
    run(adb + ["shell", "wm", "size", "1080x2280"])
    run(adb + ["shell", "wm", "density", "440"])
    for role in ("customer", "merchant", "rider"):
        pkg = "com.queuego." + role
        component_name = pkg + "/.MainActivity"
        directory = output / "system-kill-recovery" / role
        directory.mkdir(parents=True, exist_ok=True)

        # Emulator-only clean state. No Production account/order/data mutation.
        run(adb + ["shell", "pm", "clear", pkg])
        provision_real_map(pkg)
        run(adb + ["logcat", "-b", "all", "-c"])
        started = run(
            adb + ["shell", "am", "start", "-W", "-n", component_name],
            capture_output=True,
            text=True,
        )
        (directory / "initial-start.txt").write_text(started.stdout + started.stderr)
        assert "Status: ok" in started.stdout, f"{role} initial launch failed before process-kill test"
        time.sleep(2)
        before_pids = package_pids(pkg)
        (directory / "before-kill-pids.json").write_text(json.dumps({"package": pkg, "pids": before_pids}))
        kill_attempts = []
        pid_observations = []

        trim_running = subprocess.run(
            adb + ["shell", "am", "send-trim-memory", pkg, "RUNNING_CRITICAL"],
            capture_output=True,
            text=True,
            timeout=20,
        )
        (directory / "trim-running-critical.txt").write_text(trim_running.stdout + trim_running.stderr)

        # A low-memory kill only applies to background/cached apps. HOME first, then
        # request COMPLETE trim and kill the process without force-stop semantics.
        run(adb + ["shell", "input", "keyevent", "3"])
        time.sleep(1)
        trim_complete = subprocess.run(
            adb + ["shell", "am", "send-trim-memory", pkg, "COMPLETE"],
            capture_output=True,
            text=True,
            timeout=20,
        )
        (directory / "trim-complete.txt").write_text(trim_complete.stdout + trim_complete.stderr)
        am_kill = subprocess.run(adb + ["shell", "am", "kill", pkg], capture_output=True, text=True, timeout=20)
        kill_attempts.append({"mode": "am-kill", "returncode": am_kill.returncode, "stdout": am_kill.stdout, "stderr": am_kill.stderr})

        # ActivityManager kill is asynchronous and on some AOSP emulator builds a
        # WebView/renderer binding can keep the debuggable app process alive even
        # after HOME + COMPLETE trim. Poll first. If it is still alive, use the
        # emulator's adb shell domain to deliver SIGKILL. `run-as` cannot signal
        # another app process under Android SELinux (`runas_app` -> `sigkill`
        # is denied), so it is not a valid fallback for process-death testing.
        # This remains an abrupt process death without force-stop semantics.
        kill_mode = "am-kill"
        kill_deadline = time.time() + 5
        remaining = package_pids_optional(pkg)
        while remaining and time.time() < kill_deadline:
            time.sleep(0.25)
            remaining = package_pids_optional(pkg)
        if remaining:
            kill_mode = "adb-shell-sigkill"
            for pid in remaining:
                killed = subprocess.run(
                    adb + ["shell", "kill", "-9", str(pid)],
                    capture_output=True,
                    text=True,
                    timeout=15,
                )
                kill_attempts.append({"mode": "adb-shell-sigkill", "pid": pid, "returncode": killed.returncode,
                                      "stdout": killed.stdout, "stderr": killed.stderr})
            kill_deadline = time.time() + 5
            observed = package_pids_optional(pkg)
            while observed and time.time() < kill_deadline:
                pid_observations.append({"time": time.time(), "pids": observed})
                time.sleep(0.25)
                observed = package_pids_optional(pkg)

        (directory / "kill-attempts.json").write_text(json.dumps(kill_attempts, indent=2))
        (directory / "kill-pid-observations.json").write_text(json.dumps(pid_observations, indent=2))
        dead = subprocess.run(
            adb + ["shell", "pidof", pkg],
            capture_output=True,
            text=True,
            timeout=15,
        )
        (directory / "after-kill-pid.txt").write_text(
            "mode=" + kill_mode + "\n" + dead.stdout + dead.stderr
        )
        if dead.stdout.strip():
            for label, command in [
                ("kill-failure-logcat.txt", ["logcat", "-d", "-v", "threadtime"]),
                ("kill-failure-processes.txt", ["shell", "ps", "-A", "-o", "USER,PID,PPID,NAME,ARGS"]),
                ("kill-failure-services.txt", ["shell", "dumpsys", "activity", "services", pkg]),
            ]:
                evidence = subprocess.run(adb + command, capture_output=True, text=True, timeout=20)
                (directory / label).write_text(evidence.stdout + evidence.stderr)
        assert not dead.stdout.strip(), f"{role} process did not die after system-style abrupt kill"

        relaunched = run(
            adb + ["shell", "am", "start", "-W", "-n", component_name],
            capture_output=True,
            text=True,
        )
        (directory / "relaunch.txt").write_text(relaunched.stdout + relaunched.stderr)
        assert "Status: ok" in relaunched.stdout, f"{role} did not relaunch after system-style process death"
        time.sleep(2)

        after_pids = package_pids(pkg)
        activities = run(
            adb + ["shell", "dumpsys", "activity", "activities"],
            capture_output=True,
            text=True,
        ).stdout
        assert any(
            "mResumedActivity" in line and component_name in line
            for line in activities.splitlines()
        ), f"{role} Activity was not resumed after process recreation"

        assert_no_package_crash(pkg, directory / "crash-buffer.txt")
        nodes = read_ui(directory / "relaunch-ui.xml")
        assert any(n.get("package") == pkg for n in nodes), f"{role} UI missing after process recreation"
        combined = "\n".join(
            (n.get("text", "") + " " + n.get("content-desc", "")).strip()
            for n in nodes
        )
        if role == "customer":
            assert "ตะกร้า" in combined and "เข้าสู่ระบบ" in combined, "Customer guest Home did not recover after system kill"
        elif role == "merchant":
            assert "เข้าสู่ระบบร้านค้า" in combined, "Merchant login did not recover after system kill"
        else:
            assert "เข้าสู่ระบบ" in combined and "สมัครเป็นไรเดอร์" in combined, "Rider login did not recover after system kill"

        runtime = android_runtime_for_pids(after_pids)
        (directory / "android-runtime.txt").write_text(runtime)
        assert "FATAL EXCEPTION" not in runtime, f"{role} crashed after process recreation"

        with (directory / "relaunch.png").open("wb") as png:
            run(adb + ["exec-out", "screencap", "-p"], stdout=png)

        evidence.append({
            "role": role,
            "package": pkg,
            "before_pids": before_pids,
            "after_pids": after_pids,
            "running_critical_trim_exit": trim_running.returncode,
            "complete_trim_exit": trim_complete.returncode,
            "background_am_kill": "PASS",
            "kill_mode": kill_mode,
            "cold_relaunch": "PASS",
            "fatal_exception": False,
            "physical_device": False,
            "production_account_or_order_writes": False,
        })

    (output / "three-role-system-kill-recovery.json").write_text(
        json.dumps({
            "source_head": os.environ["GITHUB_SHA"],
            "workflow_run": os.environ["GITHUB_RUN_ID"],
            "cases": evidence,
            "physical_device": False,
            "production_account_or_order_writes": False,
        }, ensure_ascii=False, indent=2) + "\n"
    )
    print("Customer/Merchant/Rider background system-kill and cold recreation PASS")


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
    if not queuego_map_runtime_configured():
        record_map_runtime_blocked("rider-home-map-runtime")
        return
    test_apk = repo / "native-android/rider/build/outputs/apk/androidTest/debug/rider-debug-androidTest.apk"
    runner = "com.queuego.rider.test/androidx.test.runner.AndroidJUnitRunner"
    instrument = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                        "com.queuego.rider.RiderMapRuntimeTest",
                        "-e", "allowIncompleteTiles", "true", runner]
    run(adb + ["shell", "wm", "size", "1080x2280"])
    run(adb + ["shell", "wm", "density", "440"])
    run(adb + ["shell", "am", "force-stop", package])
    run(adb + ["install", "-r", str(apk)])
    provision_real_map(package)
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
        "renderer": "QueueGo MapLibre",
        "baseline": "not applicable to current renderer; actual lifecycle test required",
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
    print("Actual legacy map crash before fix reproduced; corrected native Home map mount/background/recreation PASS")


def verify_proof_process_recovery():
    # Proof/process-death QA is independent of QueueGo Map hosting.
    test_apk = repo / "native-android/rider/build/outputs/apk/androidTest/debug/rider-debug-androidTest.apk"
    run(adb + ["install", "-r", str(test_apk)])
    runner = "com.queuego.rider.test/androidx.test.runner.AndroidJUnitRunner"
    stages = ["navigating_merchant", "arrived_merchant", "pickup_verification", "picked_up",
              "navigating_customer", "arrived_customer", "photo_captured"]
    for stage in stages:
        for method in ["preparePendingProofForProcessKill", "recoverPendingProofAfterProcessKill"]:
            result = run(adb + ["shell", "am", "instrument", "-w", "-r", "-e", "stage", stage, "-e", "class",
                               "com.queuego.rider.RiderProofPersistenceTest#" + method, runner],
                         capture_output=True, text=True, timeout=120)
            (output / ("proof-" + stage + "-" + method + ".txt")).write_text(result.stdout + result.stderr)
            assert "OK (1 test)" in result.stdout, "Durable Rider process recovery failed: " + stage + "/" + method
            if method == "preparePendingProofForProcessKill":
                run(adb + ["shell", "am", "force-stop", "com.queuego.rider"])
    print("Seven-stage Rider snapshot/navigation/workflow/photo recovery across real process death PASS")


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
    """Exercise the real Merchant QueueGo map picker with a real map source."""
    if not queuego_map_runtime_configured():
        record_map_runtime_blocked("merchant-shop-map")
        return
    pkg = "com.queuego.merchant"
    apk = repo / "native-android/merchant/build/outputs/apk/debug/merchant-debug.apk"
    test_apk = repo / "native-android/merchant/build/outputs/apk/androidTest/debug/merchant-debug-androidTest.apk"
    runner = "com.queuego.merchant.test/androidx.test.runner.AndroidJUnitRunner"
    run(adb + ["install", "-r", str(apk)])
    provision_real_map(pkg)
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



def assert_navigation_location_lifecycle(full_log, granted, viewport):
    """Validate real foreground-location ownership without counting stale prior-scope teardown."""
    cycles = full_log.count("lifecycle_stress_cycle=")
    assert cycles >= 8, "Activity lifecycle stress was not exercised: " + viewport
    if granted:
        # A force-stop from the prior viewport can emit its final onDestroy/stop
        # after logcat has been cleared. Fence ownership at this scope's first
        # foreground start; then require exact start/stop balance from there.
        first_start = full_log.find("foreground_updates_started")
        assert first_start >= 0, "Foreground location service never started: " + viewport
        owned_log = full_log[first_start:]
        starts = owned_log.count("foreground_updates_started")
        stops = owned_log.count("foreground_updates_stopped")
        # The foreground service intentionally survives CREATED/RESUMED transitions.
        # It restarts for ActivityScenario.recreate(), then must clean up at close.
        assert starts >= 2, "Foreground location service did not restart after recreate: " + viewport
        assert stops == starts, "Foreground location service cleanup imbalance: " + viewport
    else:
        starts = full_log.count("foreground_updates_started")
        stops = full_log.count("foreground_updates_stopped")
        assert starts == 0 and stops == 0, "Location service ran without permission: " + viewport
    return starts, stops, cycles

def run_map_instrumentation_with_live_gps(instrument, gps_fix):
    """Run Rider instrumentation and inject emulator GPS only after location subscription starts."""
    if gps_fix is None:
        return run(instrument, capture_output=True, text=True, timeout=210)
    process = subprocess.Popen(instrument, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        deadline = time.monotonic() + 45
        started = False
        while time.monotonic() < deadline:
            if process.poll() is not None:
                break
            probe = subprocess.run(
                adb + ["logcat", "-d", "-s", "QueueGoNavLocation:I"],
                capture_output=True, text=True, timeout=15, check=False
            )
            if "foreground_updates_started" in probe.stdout:
                started = True
                break
            time.sleep(0.25)
        assert started, "Rider foreground location subscription did not start before GPS injection"
        # Repeat the emulator fix after subscription so FusedLocationProvider receives
        # a genuinely fresh measurement rather than relying on a pre-start lastLocation.
        for _ in range(3):
            run(adb + ["emu", "geo", "fix", str(gps_fix[0]), str(gps_fix[1])])
            time.sleep(0.35)
        stdout, stderr = process.communicate(timeout=210)
        return subprocess.CompletedProcess(instrument, process.returncode, stdout, stderr)
    except Exception:
        process.kill()
        process.wait(timeout=15)
        raise


def capture_map_display_only():
    """Capture actual QueueGo map frames from real debug tiles or approved HTTPS hosting."""
    if not queuego_map_runtime_configured():
        record_map_runtime_blocked("rider-map-display")
        return
    import base64
    from PIL import Image
    test_apk = repo / "native-android/rider/build/outputs/apk/androidTest/debug/rider-debug-androidTest.apk"
    run(adb + ["install", "-r", str(test_apk)])
    # Public UI is reviewed independently before map/network checks; no form data is submitted.
    capture_role_viewports(("rider",))
    evidence = []
    for viewport, dimensions, density, granted, gps_fix in (
        ("phone-denied", "1080x2280", "440", False, None),
        ("phone-allowed", "1080x2280", "440", True, (103.0994795, 14.9918756)),
        ("large-allowed", "1600x2560", "240", True, (103.1300059, 15.0088319)),
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
        run(adb + ["logcat", "-b", "all", "-c"])
        instrument = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                            "com.queuego.rider.RiderMapRuntimeTest",
                            "-e", "allowIncompleteTiles", "true"]
        if gps_fix is not None:
            instrument += ["-e", "expectedLongitude", str(gps_fix[0]),
                           "-e", "expectedLatitude", str(gps_fix[1]),
                           "-e", "allowDebugStoreInjection", "true"]
        instrument += ["com.queuego.rider.test/androidx.test.runner.AndroidJUnitRunner"]
        result = run_map_instrumentation_with_live_gps(instrument, gps_fix)
        (directory / "instrumentation.txt").write_text(result.stdout + result.stderr)
        print("QUEUEGO_MAP_INSTRUMENTATION " + viewport + "\n" + result.stdout + result.stderr)
        full_log = run(adb + ["logcat", "-d", "-v", "threadtime"], capture_output=True, text=True).stdout
        (directory / "full-logcat.txt").write_text(full_log)
        if "OK (1 test)" not in result.stdout:
            print("QUEUEGO_MAP_FAILURE_LOG\n" + full_log[-24000:])
        assert "OK (1 test)" in result.stdout, "Actual map mount/lifecycle test failed: " + viewport
        starts, stops, cycles = assert_navigation_location_lifecycle(full_log, granted, viewport)
        print("QUEUEGO_NAV_LOCATION_LIFECYCLE " + viewport +
              " starts=" + str(starts) + " stops=" + str(stops) + " cycles=" + str(cycles))
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
        provision_real_map(package)
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
            verify_three_role_system_kill_recovery()
            verify_real_home_map_runtime()
            verify_proof_process_recovery()
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
