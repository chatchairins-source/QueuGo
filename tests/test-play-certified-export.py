"""Failure-first unit coverage for certified Google Play AAB export."""
import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "ops/play/export-certified-play-aabs.py"
SPEC = importlib.util.spec_from_file_location("queuego_play_exporter", SCRIPT)
MOD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOD)


class PlayExportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.artifacts = self.root / "input"
        self.artifacts.mkdir()
        self.output = self.root / "output"
        self.cert = "a" * 64
        self.report = {"result": "PASS", "source_sha": "b" * 40, "version_name": "1.0.0",
                       "signer_certificate_sha256": self.cert, "roles": {}}
        for role, package in MOD.ROLES.items():
            aab = self.artifacts / f"{role}-release.aab"
            with zipfile.ZipFile(aab, "w") as z:
                z.writestr("base/manifest/AndroidManifest.xml", b"manifest")
                z.writestr("BundleConfig.pb", b"cfg")
            self.report["roles"][role] = {
                "application_id": package, "version_code": 11,
                "observed_play_max_version_code": 10, "version_name": "1.0.0",
                "aab": f"native-android/{role}/build/outputs/bundle/release/{role}-release.aab",
                "aab_sha256": MOD.digest(aab),
                "signer_certificate_sha256": self.cert,
                "aab_signer_certificate_sha256": self.cert,
                **{key: True for key in ("aab_manifest_metadata_verified", "aab_page_alignment_16kb",
                                         "aab_signature_verified", "apk_non_debuggable",
                                         "apk_signature_verified", "apk_aab_signer_match")},
            }
        self.save()

    def save(self):
        (self.artifacts / "native-release-artifacts.json").write_text(json.dumps(self.report))

    def test_valid_certified_artifacts_export(self):
        with patch.object(MOD, "signer_of", return_value=self.cert):
            result = MOD.export(self.artifacts, self.output)
        self.assertEqual(len(result["files"]), 3)
        for role in MOD.ROLES:
            self.assertTrue((self.output / f"QueueGo-{role}-v1.0.0-11.aab").is_file())
        self.assertTrue((self.output / "SHA256SUMS.txt").is_file())
        self.assertTrue((self.output / "PLAY-UPLOAD-MANIFEST.json").is_file())

    def test_tampered_bundle_fails_closed(self):
        with (self.artifacts / "rider-release.aab").open("ab") as f:
            f.write(b"changed")
        with patch.object(MOD, "signer_of", return_value=self.cert):
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch: rider"):
                MOD.export(self.artifacts, self.output)
        self.assertFalse(self.output.exists())

    def test_wrong_package_fails_closed(self):
        self.report["roles"]["merchant"]["application_id"] = "com.other.app"
        self.save()
        with patch.object(MOD, "signer_of", return_value=self.cert):
            with self.assertRaisesRegex(ValueError, "Package mismatch: merchant"):
                MOD.export(self.artifacts, self.output)

    def test_replayed_version_code_fails_closed(self):
        self.report["roles"]["customer"]["observed_play_max_version_code"] = 11
        self.save()
        with patch.object(MOD, "signer_of", return_value=self.cert):
            with self.assertRaisesRegex(ValueError, "Play versionCode not incremented: customer"):
                MOD.export(self.artifacts, self.output)

    def test_signer_mismatch_fails_closed(self):
        with patch.object(MOD, "signer_of", side_effect=[self.cert, "c" * 64]):
            with self.assertRaisesRegex(ValueError, "Actual AAB signer fingerprint mismatch: merchant"):
                MOD.export(self.artifacts, self.output)

    def test_non_certified_report_rejected(self):
        self.report["result"] = "DEBUG"
        self.save()
        with patch.object(MOD, "signer_of", return_value=self.cert):
            with self.assertRaisesRegex(ValueError, "report is not PASS"):
                MOD.export(self.artifacts, self.output)

    def test_refuses_output_directory_with_existing_files(self):
        self.output.mkdir()
        (self.output / "existing.txt").write_text("do not overwrite")
        with patch.object(MOD, "signer_of", return_value=self.cert):
            with self.assertRaisesRegex(ValueError, "Output directory must be empty"):
                MOD.export(self.artifacts, self.output)


if __name__ == "__main__":
    unittest.main()
