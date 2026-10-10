#!/usr/bin/env python3
import argparse
import hashlib
import importlib.util
import json
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERIFIER = Path(__file__).with_name("verify-native-release-gate.py")

spec = importlib.util.spec_from_file_location("queuego_native_release_gate", VERIFIER)
if spec is None or spec.loader is None:
    raise SystemExit("Unable to load canonical Native release gate verifier")
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)

ROLE_VALUES = {"customer", "merchant", "rider"}
NETWORK_TYPES = {"wifi", "mobile", "ethernet", "other"}
KIND_RE = re.compile(r"[a-z0-9_-]{2,40}")


def run(args, *, binary=False):
    result = subprocess.run(args, check=False, capture_output=True, text=not binary)
    if result.returncode != 0:
        stderr = result.stderr.decode(errors="replace") if binary else result.stderr
        raise ValueError(f"command failed ({' '.join(args)}): {stderr.strip()}")
    return result.stdout


def git_head():
    return run(["git", "rev-parse", "HEAD"]).strip()


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def adb_text(adb: str, serial: str, *args: str) -> str:
    return run([adb, "-s", serial, *args]).strip()


def physical_device(adb: str, role: str, serial: str) -> dict:
    if role not in ROLE_VALUES:
        raise ValueError(f"invalid role: {role}")
    if not serial or serial.startswith("emulator-"):
        raise ValueError("physical evidence rejects emulator serials")
    if adb_text(adb, serial, "get-state") != "device":
        raise ValueError(f"ADB device is not ready: {role}")
    qemu = adb_text(adb, serial, "shell", "getprop", "ro.kernel.qemu")
    hardware = adb_text(adb, serial, "shell", "getprop", "ro.hardware").lower()
    fingerprint = adb_text(adb, serial, "shell", "getprop", "ro.build.fingerprint")
    model = adb_text(adb, serial, "shell", "getprop", "ro.product.model")
    api_raw = adb_text(adb, serial, "shell", "getprop", "ro.build.version.sdk")
    if qemu == "1" or "ranchu" in hardware or "goldfish" in hardware:
        raise ValueError(f"physical evidence rejects emulator device: {role}")
    if not model or not fingerprint or not api_raw.isdigit():
        raise ValueError(f"incomplete physical device metadata: {role}")
    api = int(api_raw)
    if api < 23 or api > 100:
        raise ValueError(f"invalid Android API from device: {role}")
    opaque = f"{serial}\0{fingerprint}\0{model}".encode()
    return {
        "device_id_hash": sha256_bytes(opaque),
        "android_api": api,
        "model": model,
        "role": role,
        "physical": True,
        "emulator": False,
    }


def copy_artifact(source: Path, kind: str, bundle_root: Path, index: int) -> dict:
    if not KIND_RE.fullmatch(kind):
        raise ValueError(f"invalid artifact kind: {kind}")
    source = source.resolve()
    if not source.is_file():
        raise ValueError(f"artifact file does not exist: {source}")
    artifact_dir = bundle_root / "artifacts"
    artifact_dir.mkdir(parents=True, exist_ok=True)
    safe_name = re.sub(r"[^A-Za-z0-9._-]+", "_", source.name) or "artifact.bin"
    target = artifact_dir / f"{index:02d}-{safe_name}"
    shutil.copyfile(source, target)
    return {
        "file": target.relative_to(bundle_root).as_posix(),
        "kind": kind,
        "sha256": sha256_file(target),
    }


def capture_screenshot(adb: str, role: str, serial: str, bundle_root: Path, index: int) -> dict:
    artifact_dir = bundle_root / "artifacts"
    artifact_dir.mkdir(parents=True, exist_ok=True)
    target = artifact_dir / f"{index:02d}-{role}-screen.png"
    payload = run([adb, "-s", serial, "exec-out", "screencap", "-p"], binary=True)
    if not payload:
        raise ValueError(f"empty screenshot from physical device: {role}")
    target.write_bytes(payload)
    return {
        "file": target.relative_to(bundle_root).as_posix(),
        "kind": "screenshot",
        "sha256": sha256_file(target),
    }


def parse_pair(raw: str, allowed_first: set[str], label: str) -> tuple[str, str]:
    first, sep, second = raw.partition(":")
    if not sep or first not in allowed_first or not second:
        raise ValueError(f"{label} must be <type>:<value>")
    return first, second


def list_contract() -> dict:
    return {
        gate: {
            "required_roles": sorted(contract.PHYSICAL_GATE_REQUIRED_ROLES.get(gate, set())),
            "required_checks": list(contract.PHYSICAL_GATE_REQUIRED_CHECKS.get(gate, ())),
            "blueprint": gate in contract.BLUEPRINT_GATES,
        }
        for gate in sorted(contract.PHYSICAL_GATES)
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Capture QueueGo real-device release evidence without auto-certifying physical behavior."
    )
    parser.add_argument("--list-gates", action="store_true", help="Print canonical physical gate roles/checks and exit.")
    parser.add_argument("--gate", choices=sorted(contract.PHYSICAL_GATES))
    parser.add_argument("--device", action="append", default=[], metavar="ROLE:ADB_SERIAL")
    parser.add_argument("--network", action="append", default=[], metavar="TYPE:OPAQUE_LABEL")
    parser.add_argument("--check", action="append", default=[], metavar="CHECK")
    parser.add_argument("--artifact", action="append", default=[], metavar="KIND:PATH")
    parser.add_argument("--capture-screenshot", action="store_true")
    parser.add_argument("--blocking-differences", type=int)
    parser.add_argument("--certify", action="store_true", help="Emit PASS only after all canonical checks are explicitly observed.")
    parser.add_argument("--output", help="Envelope JSON path inside an external evidence bundle.")
    args = parser.parse_args()

    if args.list_gates:
        print(json.dumps(list_contract(), indent=2, sort_keys=True))
        return 0

    if not args.gate or not args.output:
        parser.error("--gate and --output are required unless --list-gates is used")

    gate = args.gate
    required_checks = set(contract.PHYSICAL_GATE_REQUIRED_CHECKS.get(gate, ()))
    observed_checks = set(args.check)
    unknown_checks = observed_checks - required_checks
    if unknown_checks:
        raise ValueError(f"unknown checks for {gate}: {', '.join(sorted(unknown_checks))}")
    if args.certify:
        missing_checks = required_checks - observed_checks
        if missing_checks:
            raise ValueError(f"cannot certify {gate}; missing checks: {', '.join(sorted(missing_checks))}")

    output = Path(args.output).resolve()
    source_root = ROOT.resolve()
    if output == source_root or source_root in output.parents:
        raise ValueError("physical evidence output must be outside the source checkout")
    bundle_root = output.parent
    bundle_root.mkdir(parents=True, exist_ok=True)

    adb = shutil.which("adb")
    if not adb:
        raise ValueError("adb is required for physical evidence capture")

    device_specs = [parse_pair(raw, ROLE_VALUES, "device") for raw in args.device]
    devices = [physical_device(adb, role, serial) for role, serial in device_specs]
    observed_roles = {item["role"] for item in devices}
    required_roles = set(contract.PHYSICAL_GATE_REQUIRED_ROLES.get(gate, set()))
    if not required_roles.issubset(observed_roles):
        missing = ", ".join(sorted(required_roles - observed_roles))
        raise ValueError(f"missing required physical roles for {gate}: {missing}")

    artifacts = []
    artifact_index = 1
    for raw in args.artifact:
        kind, sep, path_value = raw.partition(":")
        if not sep or not KIND_RE.fullmatch(kind) or not path_value:
            raise ValueError("artifact must be KIND:PATH using a lowercase evidence kind")
        artifacts.append(copy_artifact(Path(path_value), kind, bundle_root, artifact_index))
        artifact_index += 1

    if args.capture_screenshot:
        for role, serial in device_specs:
            artifacts.append(capture_screenshot(adb, role, serial, bundle_root, artifact_index))
            artifact_index += 1

    if not artifacts:
        raise ValueError("at least one --artifact or --capture-screenshot is required")

    networks = []
    for raw in args.network:
        network_type, opaque_label = parse_pair(raw, NETWORK_TYPES, "network")
        networks.append({
            "network_id_hash": sha256_bytes(opaque_label.encode()),
            "type": network_type,
        })

    if gate in {"physical_voice_two_devices_two_networks", "turn_relay"}:
        if len({d["device_id_hash"] for d in devices}) < 2:
            raise ValueError(f"{gate} requires two distinct physical devices")
        if len({n["network_id_hash"] for n in networks}) < 2:
            raise ValueError(f"{gate} requires two distinct network labels")

    if gate in contract.BLUEPRINT_GATES:
        if args.blocking_differences is None:
            raise ValueError("blueprint evidence requires --blocking-differences")
        if args.certify and args.blocking_differences != 0:
            raise ValueError("cannot certify blueprint evidence with blocking differences")

    envelope = {
        "gate": gate,
        "source_sha": git_head(),
        "status": "PASS" if args.certify else "DRAFT",
        "observed_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "operator_certified": bool(args.certify),
        "checks": {name: True for name in sorted(observed_checks)},
        "artifacts": artifacts,
        "devices": devices,
        "captured_by": "capture-physical-release-evidence.py",
    }
    if networks:
        envelope["networks"] = networks
    if gate in contract.BLUEPRINT_GATES:
        envelope["blocking_differences"] = args.blocking_differences

    output.write_text(json.dumps(envelope, indent=2, sort_keys=True) + "\n")
    print(json.dumps({
        "output": str(output),
        "gate": gate,
        "status": envelope["status"],
        "operator_certified": envelope["operator_certified"],
        "artifacts": len(artifacts),
        "devices": len(devices),
        "networks": len(networks),
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
