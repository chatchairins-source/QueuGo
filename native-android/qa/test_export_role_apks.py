"""Regression tests for the three-role debug APK artifact exporter."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name('export-role-apks.py')
spec = importlib.util.spec_from_file_location('export_role_apks', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
SHA = 'a' * 40


class ExportRoleApksTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.cwd = Path.cwd()
        import os
        os.chdir(self.root)
        self.addCleanup(os.chdir, self.cwd)
        rows = []
        for role in ('customer', 'merchant', 'rider'):
            apk = Path(f'native-android/{role}/build/outputs/apk/debug/{role}-debug.apk')
            apk.parent.mkdir(parents=True)
            data = f'{role}-apk'.encode()
            apk.write_bytes(data)
            rows.append(dict(role=role, source_sha=SHA, apk=apk.name,
                             sha256=hashlib.sha256(data).hexdigest(), bytes=len(data)))
        self.manifest = self.root / 'APK-MANIFEST.json'
        self.manifest.write_text(json.dumps(rows))
        self.output = self.root / 'artifacts'
        self.output.mkdir()
        (self.output / 'APK-MANIFEST.json').write_text('pre-existing manifest is allowed')
        self.git = patch.object(module.subprocess, 'check_output', return_value=SHA + '\\n')
        self.git.start()
        self.addCleanup(self.git.stop)

    def test_exports_three_verified_apks_with_debug_disclosure(self):
        signature_dir = self.output / 'rider'
        signature_dir.mkdir()
        (signature_dir / 'APK-SIGNATURE.txt').write_text('verified signature evidence')
        module.export(self.manifest, self.output)
        self.assertTrue((signature_dir / 'APK-SIGNATURE.txt').exists())
        for role in ('customer', 'merchant', 'rider'):
            folder = self.output / role
            apk = next(folder.glob('*.apk'))
            meta = json.loads((folder / 'TESTED-SOURCE.json').read_text())
            self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), meta['sha256'])
            self.assertEqual(meta['source_sha'], SHA)
            self.assertEqual(meta['build_type'], 'debug')
            self.assertFalse(meta['production_certified'])

    def test_rejects_existing_role_directory_without_overwrite(self):
        folder = self.output / 'rider'
        folder.mkdir()
        sentinel = folder / 'old.apk'
        sentinel.write_bytes(b'old')
        with self.assertRaisesRegex(ValueError, 'existing role artifacts'):
            module.export(self.manifest, self.output)
        self.assertEqual(sentinel.read_bytes(), b'old')
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_modified_source_before_writing(self):
        apk = Path('native-android/rider/build/outputs/apk/debug/rider-debug.apk')
        apk.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'changed after validation'):
            module.export(self.manifest, self.output)
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_malformed_manifest(self):
        self.manifest.write_text('{"not": "a list"}')
        with self.assertRaisesRegex(ValueError, 'list of role records'):
            module.export(self.manifest, self.output)


if __name__ == '__main__':
    unittest.main()
