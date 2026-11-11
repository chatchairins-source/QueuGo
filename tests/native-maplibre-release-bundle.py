#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from zipfile import ZIP_STORED, ZipFile

HELPER = Path("native-android/qa/maplibre-swiftshader/prepare-repository.py")


class MapLibreReleaseBundleTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.repository = self.root / "component"
        self.repository.mkdir()
        self.payloads = {"x86_64": b"verified-source-x86", "arm64-v8a": b"verified-source-arm64"}
        components = {
            abi: {"native_sha256": hashlib.sha256(data).hexdigest()}
            for abi, data in self.payloads.items()
        }
        (self.repository / "component.json").write_text(json.dumps({
            "version": "13.7.1-qg4625.1",
            "source_commit": "4cf121e515d8d065b57cd555a0b231ea27ff0e5d",
            "patch_sha256": "9d3ff4ac5d6ae1c6e74b0f5dead516d9e32d540b6ed7f5d8cb63fd2b76bc8af0",
            "builder_commit": "5859c94d69d3b67f26638b3a75847f63bc42a0d8",
            "components": components,
        }), encoding="utf-8")
        self.apk = self.root / "rider.apk"
        self.aab = self.root / "rider.aab"
        self.write_archive(self.apk, "lib/")
        self.write_archive(self.aab, "base/lib/")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_archive(self, path: Path, prefix: str) -> None:
        with ZipFile(path, "w", compression=ZIP_STORED) as archive:
            for abi, data in self.payloads.items():
                archive.writestr(f"{prefix}{abi}/libmaplibre.so", data)

    def run_verifier(self, action: str, artifact: Path) -> subprocess.CompletedProcess[str]:
        flag = "--apk" if action == "verify-apk" else "--aab"
        return subprocess.run(
            [sys.executable, str(HELPER), action, "--repository", str(self.repository), flag, str(artifact)],
            check=False, capture_output=True, text=True,
        )

    def test_signed_release_apk_and_aab_must_use_both_verified_libraries(self) -> None:
        for action, artifact in (("verify-apk", self.apk), ("verify-aab", self.aab)):
            result = self.run_verifier(action, artifact)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(result.stdout)["source_built_sdk_abis"], ["arm64-v8a", "x86_64"])

    def test_release_apk_and_aab_reject_library_hash_mismatch(self) -> None:
        for action, artifact, prefix in (
            ("verify-apk", self.apk, "lib/"), ("verify-aab", self.aab, "base/lib/"),
        ):
            broken = self.root / f"broken-{action}.zip"
            with ZipFile(broken, "w", compression=ZIP_STORED) as archive:
                for abi, data in self.payloads.items():
                    archive.writestr(f"{prefix}{abi}/libmaplibre.so", b"different library")
            result = self.run_verifier(action, broken)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Artifact does not contain source-built SDK", result.stderr)


if __name__ == "__main__":
    unittest.main()
