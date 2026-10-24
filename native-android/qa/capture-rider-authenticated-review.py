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
    run(["shell","uiautomator","dump","/sdcard/qg-review.xml"], timeout=30)
    raw=run(["exec-out","cat","/sdcard/qg-review.xml"],capture_output=True,timeout=30).stdout
    return list(ET.fromstring(raw).iter("node"))

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
    run(["shell","am","start","-W","-n",PKG+"/.MainActivity"],capture_output=True,text=True,timeout=60)
    ns=wait(lambda xs:any("เข้าสู่ระบบ" in (n.get("text","")+n.get("content-desc","")) for n in xs))
    assert ns, "Rider login did not render"
    fields=sorted([n for n in ns if n.get("class")=="android.widget.EditText" and rect(n)],
                  key=lambda n:rect(n)[1])
    if len(fields)<2: raise SystemExit("Rider login fields are inaccessible to Android UI automation")
    for n,value in zip(fields[:2],(phone,password)):
        tap(n)
        # Never print adb input arguments or persist credentials.
        subprocess.run(ADB+["shell","input","text",value],check=True,timeout=30,
                       stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    run(["shell","input","keyevent","4"])
    tap_label(nodes(),"เข้าสู่ระบบ")
    labels=("หน้าแรก","ข้อความ","รายได้","โปรไฟล์")
    ns=wait(lambda xs:all(any(n.get("text","").strip()==t for n in xs) for t in labels),90)
    if not ns: raise SystemExit("Login did not reach active Rider Home; no login screenshot was saved")
    time.sleep(8)
    screens=[{"screen":"home_map",**shot("01-home-map")}]
    for label,name in (("ข้อความ","02-messages"),("รายได้","03-earnings"),("โปรไฟล์","04-profile")):
        tap_label(nodes(),label)
        time.sleep(4)
        screens.append({"screen":name.split("-",1)[1],**shot(name)})
    meta={
      "source_head":os.environ.get("GITHUB_SHA",""),
      "workflow_run":os.environ.get("GITHUB_RUN_ID",""),
      "captured_at_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
      "package":PKG,"apk_sha256":hashlib.sha256(apk.read_bytes()).hexdigest(),
      "android_api":run(["shell","getprop","ro.build.version.sdk"],capture_output=True,text=True).stdout.strip(),
      "viewport_px":"1080x2280","screens":screens,"physical_device":False,
      "production_order_or_dispatch_mutations":False,"visual_parity_certified":False,
      "scope":"Authenticated Rider Home map, messages, earnings and profile; no job actions",
    }
    (OUT/"metadata.json").write_text(json.dumps(meta,ensure_ascii=False,indent=2)+"\n")
    print("QUEUEGO_RIDER_AUTHENTICATED_REVIEW_PASS: home map, messages, earnings, profile")

if __name__=="__main__": main()
