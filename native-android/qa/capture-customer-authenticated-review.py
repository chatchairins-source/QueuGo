"""Capture actual authenticated Customer APK screens without changing orders or settings.

Screens are evidence only when Android UIAutomator confirms navigation. Secrets,
hierarchy dumps and account details are never written to CI artifacts.
"""
from pathlib import Path
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from adb_install_recovery import install_verified_apk

ADB = [str(Path(os.environ.get("ANDROID_HOME", "/opt/android-sdk")) / "platform-tools/adb"), "-s", "emulator-5554"]
PKG = "com.queuego.customer"
OUT = Path("artifacts/customer-authenticated-review")
EXPECTED = ("login", "home", "search", "cart", "notifications", "profile",
            "orders", "favorites", "promotions", "support", "location")


def adb(args, **kwargs):
    return subprocess.run(ADB + args, check=True, timeout=kwargs.pop("timeout", 30), **kwargs)


def rect(node):
    bounds = [int(value) for value in re.findall(r"\d+", node.get("bounds", ""))]
    return bounds if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1] else None


def labels(node):
    return {value.strip() for field in ("text", "content-desc")
            for value in node.get(field, "").splitlines() if value.strip()}


def visible(nodes):
    return {label for node in nodes if rect(node) for label in labels(node)}


def nodes():
    last_error = None
    for _ in range(5):
        try:
            adb(["shell", "rm", "-f", "/sdcard/qg-customer-review.xml"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            dump = subprocess.run(ADB + ["shell", "uiautomator", "dump",
                                  "/sdcard/qg-customer-review.xml"],
                                  capture_output=True, text=True, timeout=30)
            if dump.returncode != 0 or "null root" in (dump.stdout + dump.stderr).lower():
                raise RuntimeError("Android UI hierarchy unavailable")
            raw = adb(["exec-out", "cat", "/sdcard/qg-customer-review.xml"],
                      capture_output=True).stdout
            root = ET.fromstring(raw)
            if root.tag != "hierarchy":
                raise RuntimeError("Android UI hierarchy missing root")
            return list(root.iter("node"))
        except (RuntimeError, ET.ParseError, subprocess.CalledProcessError,
                subprocess.TimeoutExpired, OSError) as error:
            last_error = error
            time.sleep(0.7)
    raise RuntimeError("Fresh Customer Android UI hierarchy unavailable") from last_error


def wait_for(predicate, seconds=75):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        current = nodes()
        if predicate(current):
            return current
        time.sleep(1)
    raise AssertionError("Customer screen did not reach expected Android UI state")


def tap_node(node):
    bounds = rect(node)
    assert bounds, "Customer Android control has no visible bounds"
    adb(["shell", "input", "tap", str((bounds[0] + bounds[2]) // 2),
         str((bounds[1] + bounds[3]) // 2)])


def find_label(nodeset, label):
    candidates = [n for n in nodeset if label in labels(n) and rect(n)]
    if not candidates:
        raise AssertionError("Customer Android navigation control missing: " + label)
    clickable = [n for n in candidates if n.get("clickable") == "true"]
    return clickable[-1] if clickable else candidates[-1]


def changed(previous, current):
    return visible(previous) != visible(current)


def home_ready(current):
    return "ค้นหาร้านค้าจากทุกหมวด" in visible(current) and "ร้านใกล้คุณ" in visible(current)


def page_ready(current, page):
    texts = visible(current)
    if "เข้าสู่ระบบ" in texts and not home_ready(current):
        return False
    if page == "search":
        return any(n.get("class") == "android.widget.EditText" and rect(n) for n in current)
    # Markers from actual Compose Customer pages; navigation changes must also
    # be verified, so a persistent toolbar/bottom-tab label is insufficient.
    markers = {
        "cart": {"ตะกร้า", "ตะกร้าสินค้า", "ตะกร้าของฉัน"},
        "notifications": {"การแจ้งเตือน", "แจ้งเตือน"},
        "profile": {"บัญชีและการใช้งาน"},
        "orders": {"ออเดอร์ของฉัน"},
        "favorites": {"รายการโปรด"},
        "promotions": {"โปรโมชั่น", "โปรโมชั่นจากร้าน"},
        "support": {"ติดต่อฝ่ายช่วยเหลือ", "ติดต่อฝ่ายดูแล"},
        "location": {"ที่อยู่จัดส่ง", "เลือกตำแหน่ง", "ตำแหน่งจัดส่ง"},
    }
    return bool(texts & markers[page])


def shot(name):
    target = OUT / (name + ".png")
    with target.open("wb") as handle:
        adb(["exec-out", "screencap", "-p"], stdout=handle)
    data = target.read_bytes()
    assert data.startswith(b"\x89PNG\r\n\x1a\n"), "Invalid actual Android screenshot"
    return {"screen": name, "file": target.name,
            "sha256": hashlib.sha256(data).hexdigest(), "read_only": True}


def navigate(screens, source, control, page, filename):
    before = nodes()
    tap_node(find_label(before, control))
    wait_for(lambda current: changed(before, current) and page_ready(current, page), 50)
    time.sleep(1)
    screens.append(shot(filename))


def back_to_home():
    adb(["shell", "input", "keyevent", "4"])
    wait_for(home_ready, 45)


def input_secret(value):
    # Do not print adb arguments or persist login hierarchy/screenshot with
    # credentials typed. Test secrets must be ADB-input-compatible.
    subprocess.run(ADB + ["shell", "input", "text", value],
                   check=True, timeout=30, stdout=subprocess.DEVNULL,
                   stderr=subprocess.DEVNULL)


def self_test():
    def n(text="", kind="android.widget.TextView", bounds="[0,0][100,100]"):
        return ET.Element("node", {"text": text, "class": kind, "bounds": bounds})
    home = [n("ค้นหาร้านค้าจากทุกหมวด"), n("ร้านใกล้คุณ")]
    profile = [n("บัญชีและการใช้งาน")]
    assert home_ready(home)
    assert not home_ready(profile)
    assert page_ready(profile, "profile")
    assert not page_ready(home, "profile")
    assert changed(home, profile)
    assert not changed(home, list(home))
    assert page_ready([n("ค้นหา", kind="android.widget.EditText")], "search")
    assert not page_ready([n("ค้นหา")], "search")
    assert find_label([n("ตะกร้า")], "ตะกร้า") is not None
    try:
        find_label(home, "ลบบัญชีถาวร")
    except AssertionError:
        pass
    else:
        raise AssertionError("Missing navigation label must fail closed")
    assert rect(n(bounds="[0,0][0,0]")) is None
    print("QUEUEGO_CUSTOMER_VISUAL_GATE_SELFTEST_PASS 11")


def main():
    phone = os.environ.get("QG_CUSTOMER_REVIEW_PHONE", "")
    password = os.environ.get("QG_CUSTOMER_REVIEW_PASSWORD", "")
    if not phone or not password:
        raise SystemExit("Customer review secrets absent; no authenticated screenshot claim")
    OUT.mkdir(parents=True, exist_ok=True)
    apk = Path("native-android/customer/build/outputs/apk/debug/customer-debug.apk")
    assert apk.is_file(), "Build exact-source Customer debug APK first"
    screens = []
    failure = None
    adb(["shell", "wm", "size", "1080x2280"])
    adb(["shell", "wm", "density", "440"])
    try:
        install_verified_apk(ADB, apk, PKG)
        adb(["shell", "pm", "clear", PKG], timeout=60)
        adb(["shell", "am", "start", "-W", "-n", PKG + "/.MainActivity"],
            capture_output=True, timeout=60)
        initial = wait_for(lambda current: "เข้าสู่ระบบ" in visible(current) or home_ready(current))
        # Customer deliberately starts as a guest. Reach the actual native
        # authentication screen before entering credentials or claiming Auth.
        if home_ready(initial):
            if "เข้าสู่ระบบ" not in visible(initial):
                tap_node(find_label(initial, "โปรไฟล์"))
                initial = wait_for(lambda current: "เข้าสู่ระบบ" in visible(current), 45)
            tap_node(find_label(initial, "เข้าสู่ระบบ"))
            initial = wait_for(lambda current: len([n for n in current
                if n.get("class") == "android.widget.EditText" and rect(n)]) >= 2, 45)
        screens.append(shot("login"))
        fields = sorted((n for n in initial if n.get("class") == "android.widget.EditText"
                         and rect(n)), key=lambda n: rect(n)[1])
        if len(fields) < 2:
            raise AssertionError("Customer login fields not accessible in actual APK")
        for field, value in zip(fields[:2], (phone, password)):
            tap_node(field)
            input_secret(value)
        adb(["shell", "input", "keyevent", "4"])
        tap_node(find_label(nodes(), "เข้าสู่ระบบ"))
        wait_for(home_ready, 90)
        screens.append(shot("home"))
        navigate(screens, "home", "ค้นหาร้านค้าจากทุกหมวด", "search", "search")
        back_to_home()
        navigate(screens, "home", "ตะกร้า", "cart", "cart")
        back_to_home()
        navigate(screens, "home", "แจ้งเตือน", "notifications", "notifications")
        back_to_home()
        navigate(screens, "home", "โปรไฟล์", "profile", "profile")
        for control, page in (
            ("ออเดอร์ของฉัน", "orders"),
            ("รายการโปรด", "favorites"),
            ("โปรโมชั่นจากร้าน", "promotions"),
            ("ติดต่อฝ่ายช่วยเหลือ", "support"),
            ("ที่อยู่จัดส่ง", "location"),
        ):
            navigate(screens, "profile", control, page, page)
            adb(["shell", "input", "keyevent", "4"])
            wait_for(lambda current: page_ready(current, "profile"), 45)
    except Exception as error:
        failure = type(error).__name__ + ": " + str(error)
    finally:
        captured = {entry["screen"] for entry in screens}
        missing = [name for name in EXPECTED if name not in captured]
        metadata = {
            "source_head": os.environ.get("GITHUB_SHA", ""),
            "workflow_run": os.environ.get("GITHUB_RUN_ID", ""),
            "captured_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "package": PKG, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "screens": screens, "expected_screens": list(EXPECTED),
            "missing_screens": missing, "review_complete": not missing and failure is None,
            "physical_device": False, "visual_parity_certified": False,
            "production_order_or_dispatch_mutations": False,
            "scope": "Authenticated read-only Customer entry and navigation; no checkout, order submission or account changes",
            "blocker": failure,
        }
        (OUT / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
    if failure or missing:
        raise SystemExit("Customer authenticated review INCOMPLETE: " + (failure or ", ".join(missing)))
    print("QUEUEGO_CUSTOMER_AUTHENTICATED_REVIEW_PASS screens=" + str(len(screens)))


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) == 1:
        main()
    else:
        raise SystemExit("Usage: capture-customer-authenticated-review.py [--self-test]")
