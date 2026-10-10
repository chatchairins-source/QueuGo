#!/usr/bin/env python3
"""Collect reproducible Native Android physical-test artifacts without certifying PASS.

The collector intentionally creates an OPEN template:
- operator_certified=false
- device physical=false
- all behavioral observations=false

A human operator must review the real-device captures and explicitly certify observations
before verify_native_physical_evidence.py can accept the bundle.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROLES=("customer","merchant","rider")
PACKAGES={
    "customer":"com.queuego.customer",
    "merchant":"com.queuego.merchant",
    "rider":"com.queuego.rider",
}
PUSH_KEYS=("foreground","background","killed","refresh_login","logout_revocation","stale_token_exclusion","order_notification","call_notification")
LIFECYCLE_KEYS=("permissions","single_back_to_home","upload","location","session_persistence","offline_timeout","reconnect","background_foreground")
VOICE_KEYS=("two_devices","two_networks","bidirectional_audio","forced_turn_relay","foreground_background","controls_complete","hangup_cleanup","session_order_block_authorization")
FLOATING_KEYS=("overlay_granted_path","overlay_denied_path","tap_returns_to_active_job","persistent_notification_return","stops_outside_active_work")
BLUEPRINT_KEYS=("all_required_screens_observed","all_required_states_observed","pixel_diff_reviewed")
REVOCATION_KEYS=("real_order","session_revocation","block_revocation","private_topic_revocation")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""):
            h.update(chunk)
    return h.hexdigest()


def git_head() -> str:
    return subprocess.check_output(["git","rev-parse","HEAD"],text=True).strip()


def false_map(keys):
    return {key:False for key in keys}


def empty_manifest(source_sha: str) -> dict:
    return {
        "schema_version":1,
        "source_sha":source_sha,
        "captured_at":datetime.now(timezone.utc).isoformat(),
        "operator_certified":False,
        "p0":None,
        "p1":None,
        "devices":[],
        "checks":{
            "push":{
                role:{**false_map(PUSH_KEYS),"evidence":[]}
                for role in ROLES
            },
            "voice":{
                **false_map(VOICE_KEYS),
                "device_ids_sha256":[],
                "evidence":[],
            },
            "rider_floating_q":{
                **false_map(FLOATING_KEYS),
                "evidence":[],
            },
            "blueprint":{
                role:{**false_map(BLUEPRINT_KEYS),"blocking_differences":None,"evidence":[]}
                for role in ROLES
            },
            "lifecycle":{
                role:{**false_map(LIFECYCLE_KEYS),"evidence":[]}
                for role in ROLES
            },
            "order_session_block_private_topic_revocation":{
                **false_map(REVOCATION_KEYS),
                "evidence":[],
            },
        },
    }


def ensure_bundle(out: Path) -> Path:
    out=out.resolve()
    out.mkdir(parents=True,exist_ok=True)
    manifest=out/"native-physical-evidence.json"
    return manifest


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value,indent=2,ensure_ascii=False,sort_keys=True)+"\n")


def adb(serial: str, *args: str, binary=False):
    cmd=["adb","-s",serial,*args]
    proc=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=False)
    if proc.returncode:
        raise RuntimeError("adb command failed: "+" ".join(args)+" :: "+proc.stderr.decode("utf-8","replace").strip())
    return proc.stdout if binary else proc.stdout.decode("utf-8","replace").strip()


def safe_label(value: str) -> str:
    value=re.sub(r"[^A-Za-z0-9._-]+","-",value.strip()).strip("-")
    if not value or len(value)>80:
        raise ValueError("label must contain safe visible characters and be <=80 chars")
    return value


def evidence_ref(bundle: Path, file: Path) -> dict:
    rel=file.resolve().relative_to(bundle.resolve()).as_posix()
    return {"file":rel,"sha256":sha256_file(file)}


def init_command(args) -> None:
    out=Path(args.out)
    manifest_path=ensure_bundle(out)
    if manifest_path.exists() and not args.force:
        raise ValueError("native-physical-evidence.json already exists; use --force only to intentionally reset an OPEN template")
    source=args.source_sha or git_head()
    if not re.fullmatch(r"[0-9a-f]{40}",source):
        raise ValueError("source SHA must be a 40-hex git commit")
    write_json(manifest_path,empty_manifest(source))
    print(manifest_path)


def capture_device_command(args) -> None:
    bundle=Path(args.out).resolve()
    manifest_path=bundle/"native-physical-evidence.json"
    if not manifest_path.is_file():
        raise ValueError("run init before capture-device")
    manifest=json.loads(manifest_path.read_text())
    roles=tuple(dict.fromkeys(args.roles))
    if not roles or any(role not in ROLES for role in roles):
        raise ValueError("roles must use customer/merchant/rider")

    state=adb(args.serial,"get-state")
    if state!="device":
        raise ValueError("selected adb target is not in device state")
    qemu=adb(args.serial,"shell","getprop","ro.kernel.qemu")=="1"
    if qemu:
        raise ValueError("emulator/qemu target is not accepted by the physical collector")

    model=adb(args.serial,"shell","getprop","ro.product.model").strip()
    sdk_raw=adb(args.serial,"shell","getprop","ro.build.version.sdk").strip()
    if not model or not sdk_raw.isdigit():
        raise ValueError("device model/API metadata unavailable")
    sdk=int(sdk_raw)
    serial_hash=sha256_bytes(args.serial.encode())
    if any(row.get("device_id_sha256")==serial_hash for row in manifest.get("devices",[])):
        raise ValueError("this device identity already exists in the evidence manifest")

    capture_dir=bundle/"captures"/serial_hash[:12]
    capture_dir.mkdir(parents=True,exist_ok=True)
    metadata={
        "device_id_sha256":serial_hash,
        "model":model,
        "android_api":sdk,
        "roles":list(roles),
        "adb_state":state,
        "ro_kernel_qemu":False,
        "captured_at":datetime.now(timezone.utc).isoformat(),
        "note":"Raw adb serial intentionally not stored.",
    }
    metadata_path=capture_dir/"device-metadata.json"
    write_json(metadata_path,metadata)

    screenshot_path=capture_dir/"initial-screen.png"
    screenshot_path.write_bytes(adb(args.serial,"exec-out","screencap","-p",binary=True))
    if screenshot_path.stat().st_size<100:
        raise ValueError("captured screenshot is unexpectedly empty")

    package_state_refs=[]
    for role in roles:
        package=PACKAGES[role]
        pid=adb(args.serial,"shell","pidof",package)
        state_path=capture_dir/f"{role}-app-state.txt"
        parts=[
            f"package={package}",
            f"pid_present={'yes' if pid else 'no'}",
            adb(args.serial,"shell","dumpsys","package",package),
        ]
        if pid:
            parts.append(adb(args.serial,"logcat","-d","--pid",pid,"-v","threadtime","-t","300"))
        state_path.write_text("\n".join(parts)+"\n")
        package_state_refs.append(evidence_ref(bundle,state_path))

    manifest.setdefault("devices",[]).append({
        "device_id_sha256":serial_hash,
        # Deliberately NOT certified. Operator must set physical=true after reviewing
        # the captured metadata and confirming this is a real test device.
        "physical":False,
        "emulator":False,
        "android_api":sdk,
        "model":model,
        "roles":list(roles),
        "evidence":[
            evidence_ref(bundle,metadata_path),
            evidence_ref(bundle,screenshot_path),
            *package_state_refs,
        ],
        "collector_note":"physical=false is intentional until operator review",
    })
    manifest["captured_at"]=datetime.now(timezone.utc).isoformat()
    write_json(manifest_path,manifest)
    print(json.dumps({"device_id_sha256":serial_hash,"roles":roles,"manifest":str(manifest_path)}))


def capture_screen_command(args) -> None:
    bundle=Path(args.out).resolve()
    manifest_path=bundle/"native-physical-evidence.json"
    if not manifest_path.is_file():
        raise ValueError("run init before capture-screen")
    label=safe_label(args.label)
    capture_dir=bundle/"captures"/"manual"
    capture_dir.mkdir(parents=True,exist_ok=True)
    target=capture_dir/(label+".png")
    target.write_bytes(adb(args.serial,"exec-out","screencap","-p",binary=True))
    if target.stat().st_size<100:
        raise ValueError("captured screenshot is unexpectedly empty")
    print(json.dumps(evidence_ref(bundle,target)))


def hash_file_command(args) -> None:
    bundle=Path(args.out).resolve()
    target=Path(args.file).resolve()
    try:
        target.relative_to(bundle)
    except ValueError as exc:
        raise ValueError("evidence file must be inside the evidence bundle") from exc
    if not target.is_file():
        raise ValueError("evidence file is unavailable")
    print(json.dumps(evidence_ref(bundle,target)))


def main() -> int:
    parser=argparse.ArgumentParser()
    subs=parser.add_subparsers(dest="command",required=True)

    p=subs.add_parser("init")
    p.add_argument("--out",required=True)
    p.add_argument("--source-sha")
    p.add_argument("--force",action="store_true")
    p.set_defaults(func=init_command)

    p=subs.add_parser("capture-device")
    p.add_argument("--out",required=True)
    p.add_argument("--serial",required=True)
    p.add_argument("--roles",nargs="+",required=True)
    p.set_defaults(func=capture_device_command)

    p=subs.add_parser("capture-screen")
    p.add_argument("--out",required=True)
    p.add_argument("--serial",required=True)
    p.add_argument("--label",required=True)
    p.set_defaults(func=capture_screen_command)

    p=subs.add_parser("hash-file")
    p.add_argument("--out",required=True)
    p.add_argument("--file",required=True)
    p.set_defaults(func=hash_file_command)

    args=parser.parse_args()
    try:
        args.func(args)
        return 0
    except (ValueError,RuntimeError,subprocess.CalledProcessError,OSError) as error:
        print(f"physical evidence collector BLOCKED: {error}",file=__import__("sys").stderr)
        return 1

if __name__=="__main__":
    raise SystemExit(main())
