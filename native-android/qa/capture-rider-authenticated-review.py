"""Secret-backed Android Rider review; credentials never enter evidence files."""
from pathlib import Path
import datetime, hashlib, json, os, re, subprocess, time
import xml.etree.ElementTree as ET

from adb_install_recovery import install_verified_apk

ADB=[str(Path(os.environ["ANDROID_HOME"])/"platform-tools/adb"), "-s", "emulator-5554"]
PKG="com.queuego.rider"
OUT=Path("artifacts/rider-authenticated-review")
OUT.mkdir(parents=True, exist_ok=True)

def run(args, **kw):
    return subprocess.run(ADB+args, check=True, timeout=kw.pop("timeout",30), **kw)

def nodes():
    # UIAutomator can return zero after a "null root" while leaving its previous
    # XML file intact. A stale authenticated hierarchy must never pass visual QA.
    last_error=None
    for _ in range(5):
        try:
            run(["shell","rm","-f","/sdcard/qg-review.xml"],timeout=15,
                stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            dumped=subprocess.run(ADB+["shell","uiautomator","dump","/sdcard/qg-review.xml"],
                                  timeout=30,capture_output=True,text=True)
            if dumped.returncode != 0:
                raise RuntimeError(f"uiautomator dump exited {dumped.returncode}")
            if "null root" in (dumped.stdout+dumped.stderr).lower():
                raise RuntimeError("uiautomator returned null root")
            raw=run(["exec-out","cat","/sdcard/qg-review.xml"],
                    timeout=30,capture_output=True).stdout
            root=ET.fromstring(raw)
            if root.tag != "hierarchy":
                raise ET.ParseError("uiautomator XML has no hierarchy root")
            return list(root.iter("node"))
        except (RuntimeError, ET.ParseError, subprocess.CalledProcessError,
                subprocess.TimeoutExpired, OSError) as failure:
            last_error=failure
        time.sleep(0.7)
    raise RuntimeError("Android UI hierarchy unavailable after retries") from last_error

def rect(n):
    v=[int(x) for x in re.findall(r"\d+",n.get("bounds",""))]
    return v if len(v)==4 and v[2]>v[0] and v[3]>v[1] else None

def tap(n):
    b=rect(n)
    assert b, "Visible Rider control has no bounds"
    run(["shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)])

def wait(pred, seconds=75):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        ns=nodes()
        if pred(ns): return ns
        time.sleep(1)
    return []

def rider_tab_ready(ns, label):
    # Bottom navigation labels persist on all tabs. Require actual page
    # content before recording a screenshot as authenticated visual evidence.
    visible = {n.get("text", "").strip() for n in ns if rect(n)}
    if label == "ข้อความ":
        # A loading spinner or API error is not a successfully rendered inbox.
        return "ยังไม่มีการสนทนา" in visible or any(
            text.startswith("ออเดอร์ ") for text in visible
        )
    if label == "รายได้":
        # The period controls alone can appear while data is still loading.
        return {"วันนี้", "7 วัน", "30 วัน", "รายได้จากค่าส่ง"}.issubset(visible)
    if label == "โปรไฟล์":
        return "รูปแบบและประเภทงาน" in visible
    raise ValueError("Unknown Rider tab")

def capture_rider_tab(label, name, screen):
    tap_label(nodes(), label)
    ready = wait(lambda xs: rider_tab_ready(xs, label), 60)
    assert ready, "Rider tab content did not render: " + label
    time.sleep(1)
    screens.append({"screen": screen, **shot(name)})

def rider_earnings_summary_ready(ns):
    # The period controls and loaded summary can be on a different viewport
    # from the ledger on compact phones. Never require both at once.
    visible = {n.get("text", "").strip() for n in ns if rect(n)}
    if any(text.startswith("โหลดสรุปงานไม่สำเร็จ") for text in visible):
        return False
    return {"วันนี้", "7 วัน", "30 วัน", "รายได้จากค่าส่ง",
            "งานสำเร็จ"}.issubset(visible)


def rider_earnings_ledger_ready(ns, label):
    if label not in ("7 วัน", "30 วัน"):
        raise ValueError("Unknown Rider earnings period")
    visible = [n for n in ns if rect(n)]
    texts = {n.get("text", "").strip() for n in visible}
    if any(text.startswith("โหลดบัญชีเงินสดไม่สำเร็จ") for text in texts):
        return False
    if not {"บัญชีเงินสด", "จ่ายร้าน", "เก็บลูกค้า"}.issubset(texts):
        return False
    # A selected period button alone is NOT proof of the new ledger. Require
    # the period label to sit on the same heading row as "บัญชีเงินสด".
    headings = [rect(n) for n in visible if n.get("text", "").strip() == "บัญชีเงินสด"]
    periods = [rect(n) for n in visible if n.get("text", "").strip() == label]
    return any(
        p[0] >= h[2] - 5 and
        abs((p[1] + p[3]) - (h[1] + h[3])) <= max(80, h[3] - h[1])
        for h in headings for p in periods
    )


def scroll_earnings_until(predicate, direction):
    if direction not in ("up", "down"):
        raise ValueError("Unknown earnings scroll direction")
    for _ in range(8):
        current = nodes()
        if predicate(current):
            return current
        y_start, y_end = (1900, 600) if direction == "up" else (600, 1900)
        run(["shell", "input", "swipe", "900", str(y_start), "900", str(y_end), "450"])
        time.sleep(0.5)
    return []


def capture_rider_earnings_period(label, filename):
    if label not in ("7 วัน", "30 วัน"):
        raise ValueError("Unknown Rider earnings period")
    tap_label(nodes(), label)
    time.sleep(1)
    summary = wait(rider_earnings_summary_ready, 60)
    assert summary, "Rider earnings summary did not finish loading: " + label
    ledger = scroll_earnings_until(
        lambda xs: rider_earnings_ledger_ready(xs, label), "up"
    )
    assert ledger, "Rider cash ledger did not show selected period: " + label
    # Two genuine viewport images: ledger first, then scroll back to the
    # summary. Append neither to accepted metadata until both are verified.
    ledger_image = shot(filename + "-ledger")
    summary = scroll_earnings_until(rider_earnings_summary_ready, "down")
    assert summary, "Rider earnings summary did not return to viewport: " + label
    summary_image = shot(filename)
    screens.extend((
        {"screen": filename, **summary_image},
        {"screen": filename + "-ledger", **ledger_image},
    ))


def tap_label(ns,label):
    choices=[n for n in ns if n.get("text","").strip()==label and rect(n)]
    assert choices, "Rider tab missing: "+label
    clickable=[n for n in choices if n.get("clickable")=="true"]
    tap(clickable[-1] if clickable else choices[-1])

def shot(name):
    p=OUT/(name+".png")
    with p.open("wb") as f: run(["exec-out","screencap","-p"],stdout=f,timeout=30)
    data=p.read_bytes()
    assert data.startswith(b"\x89PNG\r\n\x1a\n"), "Invalid emulator screenshot"
    return {"file":p.name,"sha256":hashlib.sha256(data).hexdigest()}

def provision_map():
    if os.environ.get("QG_DEBUG_MAP_ARCHIVE") != "1":
        return
    import shutil
    import urllib.request
    url = "https://github.com/chatchairins-source/QueuGo/releases/download/rider-buriram-offline-20261016/buriram-shortbread.pmtiles"
    expected = "30699573ed2068d3c7e4393ad477dbe8febf68f453f27ae88437f5c652a3ddfc"
    archive = Path(os.environ.get("RUNNER_TEMP", ".")) / "queuego-ci-buriram.pmtiles"
    if not archive.is_file():
        with urllib.request.urlopen(url, timeout=90) as response, archive.open("wb") as target:
            shutil.copyfileobj(response, target)
    actual = hashlib.sha256(archive.read_bytes()).hexdigest()
    assert actual == expected, "Pinned real map archive checksum mismatch"
    device_path = "/data/local/tmp/queuego-ci-buriram.pmtiles"
    run(["push", str(archive), device_path], timeout=120)
    run(["shell", "run-as", PKG, "mkdir", "-p", "files"])
    run(["shell", "run-as", PKG, "cp", device_path, "files/queuego-ci-buriram.pmtiles"])
    verified = run(["shell", "run-as", PKG, "sha256sum", "files/queuego-ci-buriram.pmtiles"],
                   capture_output=True, text=True).stdout.split()[0]
    assert verified == expected, "App-private map archive checksum mismatch"


def scroll_to_text(label):
    for _ in range(8):
        ns=nodes()
        if any(n.get("text","").strip()==label and rect(n) for n in ns): return ns
        run(["shell","input","swipe","900","1900","900","600","450"])
        time.sleep(0.5)
    raise SystemExit("Read-only Rider profile entry not visible: "+label)

def expand_profile_extra_tools():
    ns=nodes()
    if any(n.get("text","").strip()=="แนะนำบริการและวิธีใช้งาน" and rect(n) for n in ns):
        return
    ns=scroll_to_text("เครื่องมือ Rider เพิ่มเติม")
    tap_label(ns,"เครื่องมือ Rider เพิ่มเติม")
    # Expansion inserts these rows below the current scroll viewport. Scroll
    # the child into view before treating its absence from UIAutomator as a
    # failed toggle.
    expanded=scroll_to_text("แนะนำบริการและวิธีใช้งาน")
    assert expanded, "Rider extra tools did not expand"
    time.sleep(1)


def node_exact(n,marker):
    return n.get("text","").strip()==marker or n.get("content-desc","").strip()==marker

def capture_profile_dialog(entry,title,name):
    ns=scroll_to_text(entry)
    tap_label(ns,entry)
    ns=wait(lambda xs:any(node_exact(n,title) and rect(n) for n in xs),20)
    assert ns, "Rider read-only profile screen did not open: "+title
    screens.append({"screen":name,**shot(name)})
    closes=[n for n in ns if (n.get("text","").strip()=="ปิด" or n.get("content-desc","").strip() in ("ปิด","ปิดหน้าต่าง","Close")) and rect(n)]
    if closes:
        clickable=[n for n in closes if n.get("clickable")=="true"]
        tap(clickable[-1] if clickable else closes[-1])
    else:
        # Some read-only dialogs expose only a drawn X icon without accessibility text.
        run(["shell","input","keyevent","4"])
    closed=wait(lambda xs:not any(node_exact(n,title) and rect(n) for n in xs),15)
    assert closed, "Read-only Rider profile screen did not close: "+title
    time.sleep(1)


def home_ready(ns):
    visible=[n for n in ns if rect(n)]
    texts={n.get("text","").strip() for n in visible}
    descriptions={n.get("content-desc","").strip() for n in visible}
    tabs={"หน้าแรก","ข้อความ","รายได้","โปรไฟล์"}
    content_ready=(
        "พร้อมรับงาน" in descriptions
        or "การ์ดไรเดอร์ กดหรือลากเพื่อเปิดและย่อ" in descriptions
        or "บัญชียังไม่พร้อมรับงาน" in texts
        or any(t.startswith("รับงาน · ") for t in texts)
    )
    return tabs.issubset(texts) and content_ready

def input_credential(value):
    try:
        subprocess.run(ADB+["shell","input","text",value],check=True,timeout=30,
                       stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    except (subprocess.SubprocessError,OSError,ValueError):
        raise SystemExit("Credential entry failed") from None

def main():
    phone=os.environ.get("QG_RIDER_REVIEW_PHONE","")
    password=os.environ.get("QG_RIDER_REVIEW_PASSWORD","")
    if not phone or not password: raise SystemExit("Review account secrets are missing")
    run(["shell","wm","size","1080x2280"])
    run(["shell","wm","density","440"])
    apk=Path("native-android/rider/build/outputs/apk/debug/rider-debug.apk")
    assert apk.is_file(), "Build Rider debug APK first"
    install_verified_apk(ADB, apk, PKG)
    run(["shell","pm","clear",PKG],timeout=60)
    provision_map()
    run(["shell","am","start","-W","-n",PKG+"/.MainActivity"],capture_output=True,text=True,timeout=60)
    ns=wait(lambda xs:any("เข้าสู่ระบบ" in (n.get("text","")+n.get("content-desc","")) for n in xs))
    assert ns, "Rider login did not render"
    fields=sorted([n for n in ns if n.get("class")=="android.widget.EditText" and rect(n)],
                  key=lambda n:rect(n)[1])
    if len(fields)<2: raise SystemExit("Rider login fields are inaccessible to Android UI automation")
    for n,value in zip(fields[:2],(phone,password)):
        tap(n)
        # Never print adb input arguments or persist credentials.
        input_credential(value)
    run(["shell","input","keyevent","4"])
    tap_label(nodes(),"เข้าสู่ระบบ")
    labels=("หน้าแรก","ข้อความ","รายได้","โปรไฟล์")
    ns=wait(home_ready,90)
    if not ns: raise SystemExit("Rider Home content did not become ready; no loading screenshot was saved")
    global screens
    screens=[{"screen":"home_map",**shot("01-home-map")}]
    capture_rider_tab("ข้อความ", "02-messages-chat", "messages_chat")
    capture_rider_tab("รายได้", "04-earnings-today", "earnings_today")
    for label,name in (("7 วัน","05-earnings-7-days"),("30 วัน","06-earnings-30-days")):
        capture_rider_earnings_period(label, name)
    if any(n.get("text","").strip()=="วันนี้" and rect(n) for n in nodes()):
        tap_label(nodes(),"วันนี้")

    capture_rider_tab("โปรไฟล์", "07-profile", "profile")
    # Capture the Web-backed general work toggle separately from the collapsed
    # native-only tools so authenticated review covers the current Rider surface.
    laundry_profile=scroll_to_text("รับงานฝากซัก")
    assert any(n.get("text","").strip()=="รับงานฝากซัก" and rect(n) for n in laundry_profile), \
        "Current Web Laundry mode control is not visible in Rider Profile"
    screens.append({"screen":"profile_laundry",**shot("03-profile-laundry")})
    # Support is part of the core Web-aligned Profile and sits above the collapsed
    # Native-only tools. Capture it before scrolling down to expand extras.
    capture_profile_dialog(
        "แจ้งปัญหา / ติดตามเรื่อง",
        "ติดต่อฝ่ายดูแล",
        "10-support"
    )
    expand_profile_extra_tools()
    for entry,title,name in (
        ("แนะนำบริการและวิธีใช้งาน","ศูนย์เรียนรู้ Rider","08-learning-center"),
        ("ศูนย์ความปลอดภัย","กรณีฉุกเฉิน","09-safety-center"),
        ("ตรวจสอบระบบ Rider","ตรวจจากสถานะจริงของโทรศัพท์เครื่องนี้","11-diagnostics"),
    ):
        capture_profile_dialog(entry,title,name)
    expected_screens=(
      "home_map","messages_chat",
      "earnings_today","05-earnings-7-days","05-earnings-7-days-ledger",
      "06-earnings-30-days","06-earnings-30-days-ledger",
      "profile","profile_laundry","08-learning-center","09-safety-center","10-support","11-diagnostics",
    )
    captured={item["screen"] for item in screens}
    missing_screens=[name for name in expected_screens if name not in captured]
    meta={
      "source_head":os.environ.get("GITHUB_SHA",""),
      "workflow_run":os.environ.get("GITHUB_RUN_ID",""),
      "captured_at_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
      "package":PKG,"apk_sha256":hashlib.sha256(apk.read_bytes()).hexdigest(),
      "android_api":run(["shell","getprop","ro.build.version.sdk"],capture_output=True,text=True).stdout.strip(),
      "viewport_px":"1080x2280","screens":screens,"physical_device":False,
      "production_order_or_dispatch_mutations":False,"visual_parity_certified":False,
      "scope":"Authenticated Rider Home map, messages, earnings, profile and Laundry work toggle; no job actions",
      "expected_screens":list(expected_screens),
      "missing_screens":missing_screens,
      "review_complete":not missing_screens,
    }
    (OUT/"metadata.json").write_text(json.dumps(meta,ensure_ascii=False,indent=2)+"\n")
    if missing_screens:
        raise SystemExit("Authenticated Rider review incomplete; missing screens: "+", ".join(missing_screens))
    print("QUEUEGO_RIDER_AUTHENTICATED_REVIEW_PASS: all expected Rider screens captured")

if __name__=="__main__": main()
