#!/usr/bin/env python3
import argparse
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERIFIER = Path(__file__).with_name("verify-native-release-gate.py")
MAX_FILES = 1000
MAX_UNCOMPRESSED_BYTES = 4 * 1024 * 1024 * 1024
RESERVED = {"certified-release-metadata.json"}

spec = importlib.util.spec_from_file_location("queuego_native_release_gate", VERIFIER)
if spec is None or spec.loader is None:
    raise SystemExit("Unable to load canonical Native release gate verifier")
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)


def run(args):
    result = subprocess.run(args, check=False, capture_output=True, text=True)
    if result.returncode != 0:
        raise ValueError(f"command failed ({' '.join(args)}): {result.stderr.strip()}")
    return result.stdout.strip()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def ensure_external(path: Path, label: str) -> Path:
    resolved = path.resolve()
    source = ROOT.resolve()
    if resolved == source or source in resolved.parents:
        raise ValueError(f"{label} must be outside the source checkout")
    return resolved


def git_head() -> str:
    return run(["git", "rev-parse", "HEAD"])


def require_clean_checkout() -> None:
    dirty = run(["git", "status", "--porcelain", "--untracked-files=normal"])
    if dirty:
        raise ValueError("release evidence finalization requires a clean source checkout")


def expected_status(gate: str, envelope: dict) -> str:
    status = envelope.get("status")
    allowed = {"PASS", "PASS_FREE_PLAN_CONTROLS"} if gate == "security_platform_auth" else {"PASS"}
    if status not in allowed:
        raise ValueError(f"gate is not certified: {gate}")
    return status


def validate_bundle(bundle_root: Path, head: str) -> dict:
    gates = {}
    for gate in contract.GATES:
        envelope_path = bundle_root / f"{gate}.json"
        if not envelope_path.is_file():
            raise ValueError(f"missing gate envelope: {gate}.json")
        try:
            envelope = json.loads(envelope_path.read_text())
        except (json.JSONDecodeError, UnicodeDecodeError) as error:
            raise ValueError(f"invalid gate envelope JSON: {gate}") from error
        if not isinstance(envelope, dict):
            raise ValueError(f"gate envelope must be an object: {gate}")
        status = expected_status(gate, envelope)
        contract.verify_gate_envelope(envelope_path, bundle_root, gate, head, status)
        gates[gate] = {
            "status": status,
            "evidence_file": envelope_path.name,
            "sha256": sha256_file(envelope_path),
        }
    return gates


def inventory(bundle_root: Path) -> list[Path]:
    files = []
    total = 0
    for path in sorted(bundle_root.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"evidence bundle may not contain symlinks: {path.relative_to(bundle_root)}")
        if not path.is_file():
            continue
        rel = path.relative_to(bundle_root)
        rel_text = rel.as_posix()
        if "\\" in rel_text or rel.is_absolute() or ".." in rel.parts:
            raise ValueError(f"unsafe evidence bundle path: {rel_text}")
        if rel.name in RESERVED:
            raise ValueError(f"reserved certification output must not be pre-created: {rel.name}")
        files.append(path)
        total += path.stat().st_size
        if len(files) > MAX_FILES:
            raise ValueError("evidence bundle exceeds 1000 files")
        if total > MAX_UNCOMPRESSED_BYTES:
            raise ValueError("evidence bundle exceeds 4 GiB uncompressed limit")
    return files


def create_zip(bundle_root: Path, target: Path) -> None:
    files = inventory(bundle_root)
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, allowZip64=True) as archive:
        for path in files:
            rel = path.relative_to(bundle_root).as_posix()
            archive.write(path, arcname=rel)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Finalize a complete QueueGo release evidence directory into the certification ZIP."
    )
    parser.add_argument("--list-gates", action="store_true")
    parser.add_argument("--bundle-dir")
    parser.add_argument("--zip-output")
    parser.add_argument("--p0", type=int)
    parser.add_argument("--p1", type=int)
    args = parser.parse_args()

    if args.list_gates:
        print(json.dumps(list(contract.GATES), indent=2))
        return 0

    if args.bundle_dir is None or args.zip_output is None or args.p0 is None or args.p1 is None:
        parser.error("--bundle-dir, --zip-output, --p0 and --p1 are required unless --list-gates is used")
    if args.p0 != 0 or args.p1 != 0:
        raise ValueError("cannot finalize release evidence while P0 or P1 is non-zero")

    require_clean_checkout()
    head = git_head()
    bundle_root = ensure_external(Path(args.bundle_dir), "evidence bundle directory")
    zip_output = ensure_external(Path(args.zip_output), "evidence ZIP output")
    if not bundle_root.is_dir():
        raise ValueError("evidence bundle directory does not exist")
    if zip_output == bundle_root or bundle_root in zip_output.parents:
        raise ValueError("evidence ZIP output must be outside the evidence bundle directory")
    zip_output.parent.mkdir(parents=True, exist_ok=True)

    report_path = bundle_root / "native-release-evidence.json"
    if report_path.is_symlink():
        raise ValueError("native-release-evidence.json may not be a symlink")

    gates = validate_bundle(bundle_root, head)
    report = {
        "source_sha": head,
        "p0": 0,
        "p1": 0,
        "gates": gates,
    }
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")

    create_zip(bundle_root, zip_output)
    digest = sha256_file(zip_output)
    print(json.dumps({
        "source_sha": head,
        "gate_count": len(gates),
        "report": str(report_path),
        "zip": str(zip_output),
        "zip_sha256": digest,
        "status": "READY_FOR_PRIVATE_STORAGE_UPLOAD",
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
