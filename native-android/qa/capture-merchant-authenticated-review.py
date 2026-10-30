"""Secret-backed Android Merchant review; never persist credentials or UI hierarchy."""
from pathlib import Path
import datetime, hashlib, json, os, re, subprocess, time
import xml.etree.ElementTree as ET

ADB=[str(Path(os.environ["ANDROID_HOME"])/"platform-tools/adb"), "-s", "emulator-5554"]
PKG="com.queuego.merchant"
OUT=Path("artifacts/merchant-authenticated-review")
OUT.mkdir(parents=True, exist_ok=True)

def run(args, **kw):
    return subprocess.run(ADB+args, check=True, timeout=kw.pop("timeout",30), **kw)

def nodes():
    run(["shell","uiautomator","dump","/sdcard/qg-review.xml"], timeout=30,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    raw=run(["exec-out","cat","/sdcard/qg-review.xml"],capture_output=True,timeout=30).stdout
    return list(ET.fromstring(raw).iter("node"))

def rect(n):
    v=[int(x) for x in re.findall(r"\d+",n.get("bounds",""))]
    return v if len(v)==4 and v[2]>v[0] and v[3]>v[1] else None

def tap(n):
    b=rect(n)
    assert b, "Visible Merchant control has no bounds"
    run(["shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)])

def wait(pred, seconds=75):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        ns=nodes()
        if pred(ns): return ns
        time.sleep(1)
    return []

def tap_label(ns,label):
    matches=[n for n in ns if n.get("text","").strip()==label and rect(n)]
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
    texts={n.get("text","").strip() for n in ns if rect(n)}
    required={"หน้าหลัก","คำสั่งซื้อ","สินค้า","โปรโมชั่น","บัญชี",
              "ออเดอร์ทั้งหมด","เสร็จแล้ว","กำลังเตรียม","ยกเลิก"}
    return required.issubset(texts) and not texts.intersection({"…","..."})

def scroll_to_label(label):
    for _ in range(7):
        ns=nodes()
        matches=[n for n in ns if n.get("text", "").strip()==label and rect(n)]
        if matches: return ns
        run(["shell","input","swipe","500","1800","500","700","400"])
    raise AssertionError("Merchant control not visible: "+label)

def capture_pos(screens, prefix):
    ns=nodes()
    if not any(n.get("text", "").strip()=="＋ รับออเดอร์" and rect(n) for n in ns):
        tap_label(ns,"หน้าหลัก")
        tap_label(scroll_to_label("หน้าร้าน POS"),"หน้าร้าน POS")
    ns=wait(lambda xs:any(n.get("text", "").strip()=="＋ รับออเดอร์" and rect(n) for n in xs),90)
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
    run(["install","-r",str(apk)],timeout=180)
    run(["shell","pm","clear",PKG],timeout=60)
    run(["shell","am","start","-W","-n",PKG+"/.MainActivity"],capture_output=True,text=True,timeout=60)
    ns=wait(lambda xs:any("เข้าสู่ระบบ" in (n.get("text","")+n.get("content-desc","")) for n in xs))
    assert ns, "Merchant login did not render"
    login_screen=shot("phone-login") if os.environ.get("QG_MERCHANT_VISUAL_PARITY")=="1" else None
    by_description={n.get("content-desc","").strip():n for n in ns if rect(n)}
    fields=[by_description.get("เบอร์โทรศัพท์ / อีเมล"),by_description.get("รหัสผ่าน")]
    if not all(fields):
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
        tap_label(nodes(),label)
        time.sleep(4)
        screens.append({"screen":label,"account":"merchant-test","read_only":True,
                        **shot(f"{i:02d}-"+("orders" if label=="คำสั่งซื้อ" else "products" if label=="สินค้า" else "promotions" if label=="โปรโมชั่น" else "profile"))})
    if os.environ.get("QG_MERCHANT_VISUAL_PARITY")=="1":
        capture_pos(screens,"phone-")
        run(["shell","wm","size","1600x2560"])
        run(["shell","wm","density","240"])
        time.sleep(5)
        run(["shell","input","keyevent","4"])
        assert wait(home_ready,90), "Tablet Merchant home did not finish loading"
        screens.append({"screen":"tablet-home","account":"merchant-test","read_only":True,**shot("tablet-home")})
        for label,name in (("คำสั่งซื้อ","orders"),("สินค้า","products"),("โปรโมชั่น","promotions"),("บัญชี","profile")):
            tap_label(nodes(),label)
            time.sleep(4)
            screens.append({"screen":"tablet-"+name,"account":"merchant-test","read_only":True,**shot("tablet-"+name)})
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
