"""Verify the source-bound Native Pilot APK manifest generator."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile


root = Path(__file__).resolve().parents[1]
generator = root / "native-android" / "qa" / "create-apk-manifest.py"
checks = 0

with tempfile.TemporaryDirectory() as directory:
    temp = Path(directory)
    apks = {
        "customer": temp / "customer.apk",
        "merchant": temp / "merchant.apk",
        "rider": temp / "rider.apk",
    }
    payloads = {
        "customer": b"customer-debug-apk-fixture",
        "merchant": b"merchant-debug-apk-fixture",
        "rider": b"rider-debug-apk-fixture",
    }
    for role, path in apks.items():
        path.write_bytes(payloads[role])

    output = temp / "artifacts" / "APK-MANIFEST.json"
    args = [
        sys.executable, str(generator),
        "--source-sha", "a" * 40,
        "--output", str(output),
        "--customer", str(apks["customer"]),
        "--merchant", str(apks["merchant"]),
        "--rider", str(apks["rider"]),
    ]
    run = subprocess.run(args, capture_output=True, text=True, check=False)
    assert run.returncode == 0, run.stderr
    checks += 1

    manifest = json.loads(output.read_text())
    assert manifest["source_commit"] == "a" * 40
    assert manifest["build_type"] == "debug"
    assert manifest["production_release_certified"] is False
    assert set(manifest["apks"]) == {"customer", "merchant", "rider"}
    checks += 4

    for role, path in apks.items():
        record = manifest["apks"][role]
        assert record["size_bytes"] == len(payloads[role])
        assert record["sha256"] == hashlib.sha256(payloads[role]).hexdigest()
        assert record["repository_path"] == str(path)
        checks += 3

    invalid = subprocess.run(
        [sys.executable, str(generator), "--source-sha", "not-a-sha",
         "--output", str(output), "--customer", str(apks["customer"]),
         "--merchant", str(apks["merchant"]), "--rider", str(apks["rider"])],
        capture_output=True, text=True, check=False,
    )
    assert invalid.returncode != 0
    checks += 1

    missing = subprocess.run(
        [sys.executable, str(generator), "--source-sha", "b" * 40,
         "--output", str(output), "--customer", str(apks["customer"]),
         "--merchant", str(apks["merchant"]), "--rider", str(temp / "missing.apk")],
        capture_output=True, text=True, check=False,
    )
    assert missing.returncode == 1 and "APK manifest generation failed" in missing.stderr
    checks += 1

print(json.dumps({"checks": checks, "failures": 0,
                  "scope": "exact APK byte SHA-256, byte size, source commit binding and debug-only certification boundary"}))
