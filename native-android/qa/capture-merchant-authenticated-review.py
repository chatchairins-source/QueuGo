"""Secret-backed Android Merchant review; never persist credentials or UI hierarchy."""
from pathlib import Path
import datetime, hashlib, json, os, re, subprocess, time
import xml.etree.ElementTree as ET

from adb_install_recovery import install_verified_apk

ADB=[str(Path(os.environ["ANDROID_HOME"])/"platform-tools/adb"), "-s", "emulator-5554"]
PKG="com.queuego.merchant"
OUT=Path("artifacts/merchant-authenticated-review")
OUT.mkdir(parents=True, exist_ok=True)

def run(args, **kw):
    return subprocess.run(ADB+args, check=True, timeout=kw.pop("timeout",30), **kw)

def nodes():
    # UIAutomator may exit zero with "null root", leaving a previous hierarchy
    # on the device. Every attempt must delete that file and prove a fresh root.
    last_error = None
    for _ in range(5):
        try:
            run(["shell", "rm", "-f", "/sdcard/qg-review.xml"], timeout=15,
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            dumped = subprocess.run(
                ADB + ["shell", "uiautomator", "dump", "/sdcard/qg-review.xml"],
                timeout=30, capture_output=True, text=True)
            if dumped.returncode != 0:
                raise RuntimeError(f"uiautomator dump exited {dumped.returncode}")
            if "null root" in (dumped.stdout + dumped.stderr).lower():
                raise RuntimeError("uiautomator returned null root")
            raw = run(["exec-out", "cat", "/sdcard/qg-review.xml"],
                      timeout=30, capture_output=True).stdout
            root = ET.fromstring(raw)
            if root.tag != "hierarchy":
                raise ET.ParseError("uiautomator XML has no hierarchy root")
            return list(root.iter("node"))
        except (RuntimeError, ET.ParseError, subprocess.CalledProcessError,
                subprocess.TimeoutExpired, OSError) as failure:
            last_error = failure
        time.sleep(0.7)
    raise RuntimeError("Merchant Android UI hierarchy unavailable after retries") from last_error

def rect(n):
    v=[int(x) for x in re.findall(r"\d+",n.get("bounds",""))]
    return v if len(v)==4 and v[2]>v[0] and v[3]>v[1] else None

def tap(n):
    b=rect(n)
    assert b, "Visible Merchant control has no bounds"
    run(["shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)])

def wait(pred, seconds=75):
    end=time.monotonic()+seconds
    ns=[]
    while time.monotonic()<end:
        ns=nodes()
        if pred(ns): return ns
        time.sleep(1)
    review_labels={"หน้าหลัก","คำสั่งซื้อ","สินค้า","โปรโมชั่น","บัญชี","＋ รับออเดอร์","โต๊ะ","คิดเงิน"}
    (OUT/"hierarchy-status.json").write_text(json.dumps({
        "node_count":len(ns),
        "known_labels":sorted({label for n in ns for label in labels(n)} & review_labels)
    },ensure_ascii=False)+"\n")
    return []

def labels(n):
    # Compose can expose merged tablet labels through content-desc after recreation.
    return {line.strip() for key in ("text", "content-desc")
            for line in n.get(key, "").splitlines() if line.strip()}

def has_label(n, label):
    return label in labels(n) and rect(n) is not None

# A bottom navigation label remains visible on every primary tab. Its presence
# alone cannot prove that a tap changed the screen before taking a QA image.
# These markers are rendered by the actual Native Merchant page content.
PRIMARY_TAB_MARKERS = {
    "คำสั่งซื้อ": {"ดูสถานะและจัดการออเดอร์ของร้าน"},
    "สินค้า": {"สินค้าในร้าน", "สินค้าและเมนู"},
    "โปรโมชั่น": {"สร้างโปรโมชั่นใหม่", "ปิดฟอร์มสร้างโปรโมชั่น"},
    "บัญชี": {"ข้อมูลร้าน"},
}

def primary_tab_ready(ns, label):
    markers = PRIMARY_TAB_MARKERS[label]
    visible = {text for n in ns if rect(n) for text in labels(n)}
    return bool(visible & markers)

def capture_primary_tab(screens, label, filename, screen):
    tap_label(nodes(), label)
    fresh = wait(lambda xs: primary_tab_ready(xs, label), 45)
    assert fresh, "Merchant tab content did not render: " + label
    time.sleep(1)
    screens.append({"screen": screen, "account": "merchant-test",
                    "read_only": True, **shot(filename)})

def tap_label(ns,label):
    matches=[n for n in ns if has_label(n,label)]
    assert matches, "Merchant screen/tab missing: "+label
    clickable=[n for n in matches if n.get("clickable")=="true"]
    tap(clickable[-1] if clickable else matches[-1])

def shot(name):
    p=OUT/(name+".png")
    with p.open("wb") as f: run(["exec-out","screencap","-p"],stdout=f,timeout=30)
    data=p.read_bytes()
    assert data.startswith(b"\x89PNG\r\n\x1a\n"), "Invalid emulator screenshot"
    return {"file":p.name,"sha256":hashlib.sha256(data).hexdigest()}

def home_ready(ns):
    texts={label for n in ns if rect(n) for label in labels(n)}
    required={"หน้าหลัก","คำสั่งซื้อ","สินค้า","โปรโมชั่น","บัญชี",
              "ออเดอร์ทั้งหมด","เสร็จแล้ว","กำลังเตรียม","ยกเลิก"}
    return required.issubset(texts) and not texts.intersection({"…","..."})

def swipe_vertical(direction):
    """Keep Merchant review gestures within the active phone/tablet viewport."""
    if direction not in ("up", "down"):
        raise ValueError("Unknown Merchant scroll direction")
    output = run(["shell", "wm", "size"], capture_output=True, text=True).stdout
    sizes = re.findall(r"(?:Physical|Override) size:\s*(\d+)x(\d+)", output)
    if not sizes:
        raise RuntimeError("Merchant Android viewport size unavailable")
    width, height = map(int, sizes[-1])
    if width < 100 or height < 200:
        raise RuntimeError("Invalid Merchant Android viewport dimensions")
    x = width // 2
    upper, lower = round(height * 0.30), round(height * 0.80)
    start, end = (lower, upper) if direction == "up" else (upper, lower)
    run(["shell", "input", "swipe", str(x), str(start), str(x), str(end), "450"])


def scroll_to_label(label):
    for _ in range(7):
        ns=nodes()
        matches=[n for n in ns if has_label(n,label)]
        if matches: return ns
        swipe_vertical("up")
    raise AssertionError("Merchant control not visible: "+label)

def capture_pos(screens, prefix):
    ns=nodes()
    if not any(has_label(n,"＋ รับออเดอร์") for n in ns):
        tap_label(ns,"หน้าหลัก")
        tap_label(scroll_to_label("หน้าร้าน POS"),"หน้าร้าน POS")
    ns=wait(lambda xs:any(has_label(n,"＋ รับออเดอร์") for n in xs),90)
    assert ns, "Merchant POS failed to render"
    time.sleep(5)
    for name,label in (("counter","＋ รับออเดอร์"),("tables","โต๊ะ"),("cashier","คิดเงิน")):
        tap_label(nodes(),label)
        time.sleep(3)
        screens.append({"screen":prefix+"pos_"+name,"account":"merchant-test","read_only":True,
                        **shot(prefix+"pos-"+name)})

def input_credential(value):
    try:
        subprocess.run(ADB+["shell","input","text",value],check=True,timeout=30,
                       stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    except (subprocess.SubprocessError,OSError,ValueError):
        raise SystemExit("Credential entry failed") from None

def main():
    phone=os.environ.get("QG_MERCHANT_REVIEW_PHONE","")
    password=os.environ.get("QG_MERCHANT_REVIEW_PASSWORD","")
    if not phone or not password: raise SystemExit("Merchant review account secrets are missing")
    run(["shell","wm","size","1080x2280"])
    run(["shell","wm","density","440"])
    apk=Path("native-android/merchant/build/outputs/apk/debug/merchant-debug.apk")
    assert apk.is_file(), "Build Merchant debug APK first"
    install_verified_apk(ADB, apk, PKG)
    run(["shell","pm","clear",PKG],timeout=60)
    run(["shell","am","start","-W","-n",PKG+"/.MainActivity"],capture_output=True,text=True,timeout=60)
    ns=wait(lambda xs:any("เข้าสู่ระบบ" in (n.get("text","")+n.get("content-desc","")) for n in xs))
    assert ns, "Merchant login did not render"
    login_screen=shot("phone-login") if os.environ.get("QG_MERCHANT_VISUAL_PARITY")=="1" else None
    by_description={n.get("content-desc","").strip():n for n in ns if rect(n)}
    fields=[by_description.get("เบอร์โทรศัพท์ / อีเมล"),by_description.get("รหัสผ่าน")]
    if any(field is None for field in fields):
        fields=sorted([n for n in ns if n.get("class")=="android.widget.EditText" and rect(n)],
                      key=lambda n:rect(n)[1])
    if len(fields)<2: raise SystemExit("Merchant login fields are inaccessible to Android UI automation")
    for n,value in zip(fields[:2],(phone,password)):
        tap(n)
        input_credential(value)
    run(["shell","input","keyevent","4"])
    tap_label(nodes(),"เข้าสู่ระบบ")
    tabs=("หน้าหลัก","คำสั่งซื้อ","สินค้า","โปรโมชั่น","บัญชี")
    ns=wait(home_ready,90)
    if not ns: raise SystemExit("Merchant home summary did not finish loading; no loading screenshot was saved")
    screens=[{"screen":"home","account":"merchant-test","read_only":True,**shot("01-home")}]
    for i,label in enumerate(tabs[1:],2):
        name = ("orders" if label == "คำสั่งซื้อ" else "products" if label == "สินค้า"
                else "promotions" if label == "โปรโมชั่น" else "profile")
        capture_primary_tab(screens, label, f"{i:02d}-" + name, label)
    if os.environ.get("QG_MERCHANT_VISUAL_PARITY")=="1":
        capture_pos(screens,"phone-")
        run(["shell","wm","size","1600x2560"])
        run(["shell","wm","density","240"])
        # Start a fresh Activity/window after the display configuration changes.
        # Keep persisted Auth and business data; this also verifies cold-session recovery.
        run(["shell","am","force-stop",PKG])
        run(["shell","am","start","-W","-n",PKG+"/.MainActivity"],capture_output=True,text=True,timeout=60)
        ns=wait(lambda xs:any(has_label(n,"หน้าหลัก") for n in xs),90)
        if not ns:
            shot("tablet-session-recovery-blocker")
            raise AssertionError("Tablet Merchant did not restore authenticated primary navigation")
        tap_label(ns,"หน้าหลัก")
        for _ in range(3):
            swipe_vertical("down")
        if not wait(home_ready,90):
            shot("tablet-home-blocker")
            raise AssertionError("Tablet Merchant home did not finish loading")
        screens.append({"screen":"tablet-home","account":"merchant-test","read_only":True,**shot("tablet-home")})
        for label,name in (("คำสั่งซื้อ","orders"),("สินค้า","products"),("โปรโมชั่น","promotions"),("บัญชี","profile")):
            capture_primary_tab(screens, label, "tablet-" + name, "tablet-" + name)
        capture_pos(screens,"tablet-")
    meta={
      "source_head":os.environ.get("GITHUB_SHA",""),
      "workflow_run":os.environ.get("GITHUB_RUN_ID",""),
      "captured_at_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
      "package":PKG,"apk_sha256":hashlib.sha256(apk.read_bytes()).hexdigest(),
      "android_api":run(["shell","getprop","ro.build.version.sdk"],capture_output=True,text=True).stdout.strip(),
      "viewport_px":"1080x2280","screens":screens,"physical_device":False,
      "login_screen":login_screen,
      "visual_viewports": {"phone":{"px":"1080x2280","density":440},"tablet":{"px":"1600x2560","density":240}} if os.environ.get("QG_MERCHANT_VISUAL_PARITY")=="1" else None,
      "production_order_or_dispatch_mutations":False,"visual_parity_certified":False,
      "scope":"Authenticated Merchant dashboard and primary tabs; read-only review",
      "expected_screens":["home","คำสั่งซื้อ","สินค้า","โปรโมชั่น","บัญชี"],
      "missing_screens":[],"review_complete":True
    }
    (OUT/"metadata.json").write_text(json.dumps(meta,ensure_ascii=False,indent=2)+"\n")
    print("QUEUEGO_MERCHANT_AUTHENTICATED_REVIEW_PASS: all expected Merchant screens captured")

if __name__=="__main__": main()
