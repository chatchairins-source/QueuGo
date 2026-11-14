"""Fail closed on Android 16 KB page-size packaging regressions.

Checks each APK with zipalign -P 16 and verifies 64-bit shared-library ELF LOAD
segments are aligned to at least 16 KB. Pure Kotlin/Java APKs pass the ELF part
when no arm64-v8a/x86_64 .so files are present.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ABIS = ("arm64-v8a", "x86_64")
MIN_ALIGNMENT = 0x4000


def run(args: list[str]) -> str:
    completed = subprocess.run(
        args,
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return completed.stdout


def android_build_tool(name: str) -> str:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise ValueError("ANDROID_HOME or ANDROID_SDK_ROOT is required")
    build_tools = Path(sdk) / "build-tools"
    versions = sorted(
        (p for p in build_tools.iterdir() if p.is_dir()),
        key=lambda p: tuple(int(x) for x in re.findall(r"\d+", p.name)),
        reverse=True,
    )
    for directory in versions:
        candidate = directory / name
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    raise ValueError(f"Android build tool unavailable: {name}")


def verify_apk(apk: Path) -> dict:
    if not apk.is_file() or apk.stat().st_size <= 0:
        raise ValueError(f"APK missing or empty: {apk}")
    zipalign = android_build_tool("zipalign")
    run([zipalign, "-c", "-P", "16", "-v", "4", str(apk)])

    readelf = shutil.which("readelf")
    if not readelf:
        raise ValueError("readelf is required for ELF 16 KB alignment verification")

    checked = []
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix="qg-16kb-") as tmp:
        for name in archive.namelist():
            if not name.endswith(".so") or not any(name.startswith(f"lib/{abi}/") for abi in ABIS):
                continue
            target = Path(tmp) / Path(name).name
            target.write_bytes(archive.read(name))
            output = run([readelf, "-lW", str(target)])
            loads = []
            for line in output.splitlines():
                if not re.match(r"^\s*LOAD\s", line):
                    continue
                token = line.split()[-1]
                try:
                    alignment = int(token, 0)
                except ValueError as error:
                    raise ValueError(f"Unable to parse ELF LOAD alignment for {name}: {token}") from error
                loads.append(alignment)
            if not loads:
                raise ValueError(f"No ELF LOAD segments found: {name}")
            bad = [value for value in loads if value < MIN_ALIGNMENT]
            if bad:
                raise ValueError(f"ELF LOAD alignment below 16 KB in {name}: {bad}")
            checked.append({"library": name, "load_alignments": loads})

    return {
        "apk": str(apk),
        "zip_alignment_16kb": True,
        "elf_64bit_libraries_checked": checked,
        "result": "PASS",
    }


def main() -> None:
    if len(sys.argv) < 2:
        raise ValueError("Pass at least one APK path")
    results = [verify_apk((ROOT / arg).resolve() if not Path(arg).is_absolute() else Path(arg)) for arg in sys.argv[1:]]
    print(json.dumps({"page_size_16kb": "PASS", "apks": results}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f"16 KB page-size verification BLOCKED: {error}", file=sys.stderr)
        sys.exit(1)
