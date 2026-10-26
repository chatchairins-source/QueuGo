"""Secret-backed Android Rider review; credentials never enter evidence files."""
from pathlib import Path
import datetime, hashlib, json, os, re, subprocess, time
import xml.etree.ElementTree as ET

ADB=[str(Path(os.environ["ANDROID_HOME"])/"platform-tools/adb"), "-s", "emulator-5554"]
PKG="com.queuego.rider"
OUT=Path("artifacts/rider-authenticated-review")
OUT.mkdir(parents=True, exist_ok=True)

def run(args, **kw):
    return subprocess.run(ADB+args, check=True, timeout=kw.pop("timeout",30), **kw)

def nodes():
    last_error=None
    for _ in range(5):
        dumped=subprocess.run(ADB+["shell","uiautomator","dump","/sdcard/qg-review.xml"],
                              timeout=30,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        if dumped.returncode==0:
            raw=subprocess.run(ADB+["exec-out","cat","/sdcard/qg-review.xml"],
                               timeout=30,capture_output=True).stdout
            try:
                root=ET.fromstring(raw)
                return list(root.iter("node"))
            except ET.ParseError as failure:
                last_error=failure
        else:
            last_error=f"uiautomator dump exited {dumped.returncode}"
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
    expanded=wait(
        lambda xs:any(n.get("text","").strip()=="แนะนำบริการและวิธีใช้งาน" and rect(n) for n in xs),
        15
    )
    assert expanded, "Rider extra tools did not expand"
    time.sleep(1)


def capture_profile_dialog(entry,title,name):
    ns=scroll_to_text(entry)
    tap_label(ns,entry)
    ns=wait(lambda xs:any(title in n.get("text","") for n in xs),20)
    assert ns, "Rider read-only profile screen did not open: "+title
    screens.append({"screen":name,**shot(name)})
    closes=[n for n in ns if (n.get("text","").strip()=="ปิด" or n.get("content-desc","").strip() in ("ปิด","ปิดหน้าต่าง","Close")) and rect(n)]
    if closes:
        clickable=[n for n in closes if n.get("clickable")=="true"]
        tap(clickable[-1] if clickable else closes[-1])
    else:
        # Some read-only dialogs expose only a drawn X icon without accessibility text.
        run(["shell","input","keyevent","4"])
    closed=wait(lambda xs:not any(title in n.get("text","") for n in xs),15)
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
    run(["install","-r",str(apk)],timeout=180)
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
    tap_label(nodes(),"ข้อความ")
    time.sleep(4)
    screens.append({"screen":"messages_chat",**shot("02-messages-chat")})
    tap_label(nodes(),"รายได้")
    time.sleep(4)
    screens.append({"screen":"earnings_today",**shot("04-earnings-today")})
    for label,name in (("7 วัน","05-earnings-7-days"),("30 วัน","06-earnings-30-days")):
        ns=nodes()
        if any(n.get("text","").strip()==label and rect(n) for n in ns):
            tap_label(ns,label)
            time.sleep(3)
            screens.append({"screen":name,**shot(name)})
    if any(n.get("text","").strip()=="วันนี้" and rect(n) for n in nodes()):
        tap_label(nodes(),"วันนี้")

    tap_label(nodes(),"โปรไฟล์")
    time.sleep(3)
    screens.append({"screen":"profile",**shot("07-profile")})
    expand_profile_extra_tools()
    for entry,title,name in (
        ("แนะนำบริการและวิธีใช้งาน","ศูนย์เรียนรู้ Rider","08-learning-center"),
        ("ศูนย์ความปลอดภัย","กรณีฉุกเฉิน","09-safety-center"),
        ("แจ้งปัญหา / ติดตามเรื่อง","แจ้งปัญหาและติดตามสถานะจากระบบ","10-support"),
        ("ตรวจสอบระบบ Rider","สถานะจริงของโทรศัพท์เครื่องนี้","11-diagnostics"),
    ):
        capture_profile_dialog(entry,title,name)
    expected_screens=(
      "home_map","messages_chat",
      "earnings_today","05-earnings-7-days","06-earnings-30-days",
      "profile","08-learning-center","09-safety-center","10-support","11-diagnostics",
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
      "scope":"Authenticated Rider Home map, messages, earnings and profile; no job actions",
      "expected_screens":list(expected_screens),
      "missing_screens":missing_screens,
      "review_complete":not missing_screens,
    }
    (OUT/"metadata.json").write_text(json.dumps(meta,ensure_ascii=False,indent=2)+"\n")
    if missing_screens:
        raise SystemExit("Authenticated Rider review incomplete; missing screens: "+", ".join(missing_screens))
    print("QUEUEGO_RIDER_AUTHENTICATED_REVIEW_PASS: all expected Rider screens captured")

if __name__=="__main__": main()
