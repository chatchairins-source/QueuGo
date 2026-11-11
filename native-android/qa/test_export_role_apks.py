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
        self.git = patch.object(module.subprocess, 'check_output', return_value=SHA + '\n')
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

    def test_rejects_symlinked_role_directory_before_writing(self):
        target = self.root / 'outside'
        target.mkdir()
        (self.output / 'rider').symlink_to(target, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symlinked artifact directory'):
            module.export(self.manifest, self.output)
        self.assertEqual(list(target.iterdir()), [])
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_symlinked_source_apk_before_writing(self):
        source = Path('native-android/rider/build/outputs/apk/debug/rider-debug.apk')
        outside = self.root / 'outside.apk'
        outside.write_bytes(source.read_bytes())
        source.unlink()
        source.symlink_to(outside)
        with self.assertRaisesRegex(ValueError, 'regular file, not a symlink'):
            module.export(self.manifest, self.output)
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_symlinked_source_ancestor_before_writing(self):
        source_dir = Path('native-android/rider/build/outputs/apk/debug')
        real_dir = source_dir.with_name('debug-real')
        source_dir.rename(real_dir)
        source_dir.symlink_to(real_dir.name, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'including ancestors'):
            module.export(self.manifest, self.output)
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_symlinked_export_root(self):
        outside = self.root / 'outside'
        outside.mkdir()
        # The existing manifest is intentionally retained; remove it before replacing root.
        manifest_copy = self.output / 'APK-MANIFEST.json'
        if manifest_copy.exists():
            manifest_copy.unlink()
        self.output.rmdir()
        self.output.symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symlinked APK export root'):
            module.export(self.manifest, self.output)
        self.assertEqual(list(outside.iterdir()), [])

    def test_rejects_symlinked_export_ancestor(self):
        outside = self.root / 'outside'
        outside.mkdir()
        alias = self.root / 'alias'
        alias.symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symlinked APK export root or ancestor'):
            module.export(self.manifest, alias / 'nested' / 'artifacts')
        self.assertEqual(list(outside.iterdir()), [])
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_export_root_as_regular_file(self):
        root_file = self.root / 'export-file'
        root_file.write_text('do not modify')
        with self.assertRaisesRegex(ValueError, 'APK export root must be a directory'):
            module.export(self.manifest, root_file)
        self.assertEqual(root_file.read_text(), 'do not modify')
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_symlinked_signature_evidence(self):
        target = self.root / 'outside-signature.txt'
        target.write_text('do not modify')
        folder = self.output / 'rider'
        folder.mkdir()
        (folder / 'APK-SIGNATURE.txt').symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'symlinked signature evidence'):
            module.export(self.manifest, self.output)
        self.assertEqual(target.read_text(), 'do not modify')
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_directory_disguised_as_signature_evidence(self):
        folder = self.output / 'rider'
        folder.mkdir()
        signature = folder / 'APK-SIGNATURE.txt'
        signature.mkdir()
        (signature / 'sentinel').write_text('keep intact')
        with self.assertRaisesRegex(ValueError, 'signature evidence must be a regular file'):
            module.export(self.manifest, self.output)
        self.assertEqual((signature / 'sentinel').read_text(), 'keep intact')
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_role_artifact_path_that_is_a_file(self):
        (self.output / 'rider').write_text('not a directory')
        with self.assertRaisesRegex(ValueError, 'must be a directory'):
            module.export(self.manifest, self.output)
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_invalid_integrity_metadata(self):
        for field, invalid in [('sha256', 'not-a-sha'), ('bytes', True), ('bytes', -1)]:
            with self.subTest(field=field, invalid=invalid):
                rows = json.loads(self.manifest.read_text())
                rows[2][field] = invalid
                self.manifest.write_text(json.dumps(rows))
                with self.assertRaisesRegex(ValueError, 'invalid APK integrity metadata'):
                    module.export(self.manifest, self.output)
                self.assertFalse((self.output / 'customer').exists())
                rows[2][field] = (hashlib.sha256(b'rider-apk').hexdigest()
                                  if field == 'sha256' else len(b'rider-apk'))
                self.manifest.write_text(json.dumps(rows))

    def test_rejects_modified_source_before_writing(self):
        apk = Path('native-android/rider/build/outputs/apk/debug/rider-debug.apk')
        apk.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'changed after validation'):
            module.export(self.manifest, self.output)
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_symlinked_manifest_before_writing(self):
        real = self.root / 'real-manifest.json'
        self.manifest.rename(real)
        self.manifest.symlink_to(real)
        with self.assertRaisesRegex(ValueError, 'APK manifest must be a regular file'):
            module.export(self.manifest, self.output)
        self.assertFalse((self.output / 'customer').exists())
        self.assertTrue(real.is_file())

    def test_rejects_symlinked_manifest_ancestor_before_writing(self):
        directory = self.root / 'manifest-real'
        directory.mkdir()
        real = directory / 'APK-MANIFEST.json'
        real.write_bytes(self.manifest.read_bytes())
        alias = self.root / 'manifest-alias'
        alias.symlink_to(directory, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'APK manifest must be a regular file'):
            module.export(alias / real.name, self.output)
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_manifest_directory_before_writing(self):
        self.manifest.unlink()
        self.manifest.mkdir()
        with self.assertRaisesRegex(ValueError, 'APK manifest must be a regular file'):
            module.export(self.manifest, self.output)
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_malformed_manifest(self):
        self.manifest.write_text('{"not": "a list"}')
        with self.assertRaisesRegex(ValueError, 'list of role records'):
            module.export(self.manifest, self.output)


if __name__ == '__main__':
    unittest.main()
