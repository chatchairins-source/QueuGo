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
        (self.output / 'APK-MANIFEST.json').write_bytes(self.manifest.read_bytes())
        self.git = patch.object(module.subprocess, 'check_output', return_value=SHA + '\n')
        self.git.start()
        self.addCleanup(self.git.stop)

    def sync_retained_manifest(self):
        # Tests targeting source validation must not fail earlier on stale retained evidence.
        (self.output / 'APK-MANIFEST.json').write_bytes(self.manifest.read_bytes())

    def test_country_export_workflow_covers_stacked_apk_export_branches(self):
        # GitHub pull_request filters match the target/base branch, while push
        # filters match the source/head branch. Both must cover successor PRs.
        workflow = (SCRIPT.parents[2] / '.github/workflows/export-tested-country-apks.yml').read_text()
        trigger_lines = [line.strip() for line in workflow.splitlines()
                         if line.strip().startswith('branches:')]
        self.assertEqual(len(trigger_lines), 2)
        self.assertTrue(all("'fix/apk-export-**'" in line for line in trigger_lines),
                        'stacked APK exporter PR bases and pushes must trigger exact-head CI')

    def test_rejects_invalid_git_head_before_staging(self):
        for invalid in ('', 'not-a-commit', 'A' * 40, 'g' * 40, 'a' * 39):
            with self.subTest(invalid=invalid):
                with patch.object(module.subprocess, 'check_output', return_value=invalid + '\n'):
                    with self.assertRaisesRegex(ValueError, 'valid 40-character Git source SHA'):
                        module.export(self.manifest, self.output)
                self.assertFalse((self.output / 'customer').exists())
                self.assertFalse((self.output / 'merchant').exists())
                self.assertFalse((self.output / 'rider').exists())

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

    def test_rejects_missing_retained_manifest_before_writing(self):
        retained = self.output / 'APK-MANIFEST.json'
        retained.unlink()
        with self.assertRaisesRegex(ValueError, 'requires retained manifest evidence'):
            module.export(self.manifest, self.output)
        for role in ('customer', 'merchant', 'rider'):
            self.assertFalse((self.output / role).exists())
        self.assertFalse(retained.exists())

    def test_rejects_manifest_changed_during_validation(self):
        # A source/retained pair replaced after JSON parsing must not certify
        # bytes other than the snapshot actually parsed and validated.
        original_loads = json.loads

        def replace_evidence_after_parse(raw, **kwargs):
            # Preserve the exporter's strict JSON object_pairs_hook contract.
            rows = original_loads(raw, **kwargs)
            changed = original_loads(self.manifest.read_text())
            changed[0]['bytes'] += 1
            replacement = json.dumps(changed).encode()
            self.manifest.write_bytes(replacement)
            (self.output / 'APK-MANIFEST.json').write_bytes(replacement)
            return rows

        with patch.object(module.json, 'loads', side_effect=replace_evidence_after_parse):
            with self.assertRaisesRegex(ValueError, 'retained manifest differs'):
                module.export(self.manifest, self.output)
        for role in ('customer', 'merchant', 'rider'):
            self.assertFalse((self.output / role).exists())

    def test_rejects_stale_retained_manifest_before_writing(self):
        retained = self.output / 'APK-MANIFEST.json'
        retained.write_text('stale but regular manifest')
        with self.assertRaisesRegex(ValueError, 'retained manifest differs'):
            module.export(self.manifest, self.output)
        self.assertEqual(retained.read_text(), 'stale but regular manifest')
        self.assertFalse((self.output / 'customer').exists())
        self.assertFalse((self.output / 'merchant').exists())
        self.assertFalse((self.output / 'rider').exists())

    def test_rejects_symlinked_retained_manifest_before_writing(self):
        retained = self.output / 'APK-MANIFEST.json'
        target = self.root / 'outside-manifest.json'
        target.write_text('keep unchanged')
        retained.unlink()
        retained.symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'export manifest evidence must be a regular file'):
            module.export(self.manifest, self.output)
        self.assertEqual(target.read_text(), 'keep unchanged')
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_directory_retained_manifest_before_writing(self):
        retained = self.output / 'APK-MANIFEST.json'
        retained.unlink()
        retained.mkdir()
        (retained / 'sentinel').write_text('keep unchanged')
        with self.assertRaisesRegex(ValueError, 'export manifest evidence must be a regular file'):
            module.export(self.manifest, self.output)
        self.assertEqual((retained / 'sentinel').read_text(), 'keep unchanged')
        self.assertFalse((self.output / 'customer').exists())

    def test_rejects_symlinked_export_metadata_before_writing(self):
        for evidence_name in ('TESTED-SOURCE.json', 'SHA256SUMS'):
            with self.subTest(evidence_name=evidence_name):
                target = self.root / ('outside-' + evidence_name)
                target.write_text('keep unchanged')
                folder = self.output / 'rider'
                folder.mkdir(exist_ok=True)
                evidence = folder / evidence_name
                evidence.symlink_to(target)
                with self.assertRaisesRegex(ValueError, 'export metadata must be a regular file'):
                    module.export(self.manifest, self.output)
                self.assertEqual(target.read_text(), 'keep unchanged')
                self.assertFalse((self.output / 'customer').exists())
                evidence.unlink()

    def test_rejects_directory_disguised_as_export_metadata(self):
        folder = self.output / 'rider'
        folder.mkdir()
        evidence = folder / 'TESTED-SOURCE.json'
        evidence.mkdir()
        (evidence / 'sentinel').write_text('keep unchanged')
        with self.assertRaisesRegex(ValueError, 'export metadata must be a regular file'):
            module.export(self.manifest, self.output)
        self.assertEqual((evidence / 'sentinel').read_text(), 'keep unchanged')
        self.assertFalse((self.output / 'customer').exists())

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
                self.sync_retained_manifest()
                with self.assertRaisesRegex(ValueError, 'invalid APK integrity metadata'):
                    module.export(self.manifest, self.output)
                self.assertFalse((self.output / 'customer').exists())
                rows[2][field] = (hashlib.sha256(b'rider-apk').hexdigest()
                                  if field == 'sha256' else len(b'rider-apk'))
                self.manifest.write_text(json.dumps(rows))

    def test_discards_batch_if_last_source_changes_during_copy(self):
        customer = self.output / 'customer'
        customer.mkdir()
        signature = customer / 'APK-SIGNATURE.txt'
        signature.write_text('original signature')
        copyfile = module.shutil.copyfile

        def copy_with_change(source, destination):
            if source.name == 'rider-debug.apk':
                source.write_bytes(b'changed during copy')
            return copyfile(source, destination)

        with patch.object(module.shutil, 'copyfile', side_effect=copy_with_change):
            with self.assertRaisesRegex(ValueError, 'rider: staged APK integrity mismatch'):
                module.export(self.manifest, self.output)
        self.assertEqual(list(customer.iterdir()), [signature])
        self.assertEqual(signature.read_text(), 'original signature')
        self.assertEqual({p.name for p in self.output.iterdir()},
                         {'customer', 'APK-MANIFEST.json'})

    def test_rejects_retained_manifest_replaced_during_staging(self):
        original_copy = module.shutil.copyfile
        retained = self.output / 'APK-MANIFEST.json'

        def replace_evidence_on_last_copy(source, destination):
            result = original_copy(source, destination)
            if source.name == 'rider-debug.apk':
                retained.write_text('replaced during staging')
            return result

        with patch.object(module.shutil, 'copyfile', side_effect=replace_evidence_on_last_copy):
            with self.assertRaisesRegex(ValueError, 'retained manifest changed during staging'):
                module.export(self.manifest, self.output)
        self.assertEqual(retained.read_text(), 'replaced during staging')
        self.assertEqual({p.name for p in self.output.iterdir()}, {'APK-MANIFEST.json'})

    def test_rolls_back_if_retained_manifest_changes_during_publication(self):
        retained = self.output / 'APK-MANIFEST.json'
        original_link = Path.hardlink_to

        def replace_evidence_on_last_link(destination, target):
            result = original_link(destination, target)
            if destination.parent.name == 'rider' and destination.suffix == '.apk':
                retained.write_text('replaced during publication')
            return result

        with patch.object(Path, 'hardlink_to', replace_evidence_on_last_link):
            with self.assertRaisesRegex(ValueError, 'retained manifest changed during publication'):
                module.export(self.manifest, self.output)
        self.assertEqual(retained.read_text(), 'replaced during publication')
        self.assertEqual({p.name for p in self.output.iterdir()}, {'APK-MANIFEST.json'})

    def test_rolls_back_published_files_if_a_later_link_fails(self):
        original_link = Path.hardlink_to

        def fail_rider(destination, target):
            if destination.parent.name == 'rider':
                raise OSError('publication failed')
            return original_link(destination, target)

        with patch.object(Path, 'hardlink_to', fail_rider):
            with self.assertRaisesRegex(OSError, 'publication failed'):
                module.export(self.manifest, self.output)
        self.assertEqual(list(self.output.iterdir()), [self.output / 'APK-MANIFEST.json'])

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

    def test_rejects_non_string_role_before_writing(self):
        for invalid in (None, 42, ['rider']):
            with self.subTest(invalid=invalid):
                rows = json.loads(self.manifest.read_text())
                rows[2]['role'] = invalid
                self.manifest.write_text(json.dumps(rows))
                self.sync_retained_manifest()
                with self.assertRaisesRegex(ValueError, 'role must be a string'):
                    module.export(self.manifest, self.output)
                self.assertFalse((self.output / 'customer').exists())
                rows[2]['role'] = 'rider'
                self.manifest.write_text(json.dumps(rows))

    def test_rejects_missing_or_non_string_source_fields_before_writing(self):
        for field, invalid in (('source_sha', None), ('apk', 123)):
            with self.subTest(field=field):
                rows = json.loads(self.manifest.read_text())
                rows[2][field] = invalid
                self.manifest.write_text(json.dumps(rows))
                self.sync_retained_manifest()
                with self.assertRaisesRegex(ValueError, 'requires string source_sha and apk'):
                    module.export(self.manifest, self.output)
                self.assertFalse((self.output / 'customer').exists())
                rows[2][field] = SHA if field == 'source_sha' else 'rider-debug.apk'
                self.manifest.write_text(json.dumps(rows))

    def test_rejects_duplicate_manifest_keys_before_writing(self):
        # Duplicate keys can make provenance ambiguous to downstream JSON readers.
        rows = json.loads(self.manifest.read_text())
        rider = json.dumps(rows[2])
        rider = rider[:-1] + ', "sha256": "' + '0' * 64 + '"}'
        self.manifest.write_text('[' + ','.join(json.dumps(row) for row in rows[:2]) + ',' + rider + ']')
        self.sync_retained_manifest()
        with self.assertRaisesRegex(ValueError, 'duplicate JSON key: sha256'):
            module.export(self.manifest, self.output)
        self.assertEqual({p.name for p in self.output.iterdir()}, {'APK-MANIFEST.json'})

    def test_rejects_malformed_manifest(self):
        self.manifest.write_text('{"not": "a list"}')
        self.sync_retained_manifest()
        with self.assertRaisesRegex(ValueError, 'list of role records'):
            module.export(self.manifest, self.output)


if __name__ == '__main__':
    unittest.main()
