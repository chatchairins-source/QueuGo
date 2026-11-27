"""Disposable ZIP verifier regressions; fixtures are NOT installable APK evidence."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile

SOURCE = 'a' * 40
REFERENCE = 'a2a6985556e6ba26ef4509ba3605266ae6aa1d541bc6d772b076eac1f7a0e9b0'
URL = 'https://download.geofabrik.de/asia/thailand-261029.osm.pbf'
MAP = b'isolated map payload fixture'

class CountryPackagingTests(unittest.TestCase):
    def check(self, recovery=None, extra=None, role_map='rider', corrupt=False, duplicate=False, source=True):
        with tempfile.TemporaryDirectory() as folder:
            paths = []
            for role in ('customer', 'merchant', 'rider'):
                path = Path(folder) / f'{role}-debug.apk'
                with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_STORED) as archive:
                    archive.writestr('classes.dex', SOURCE if source else 'no-source')
                    if role == role_map:
                        archive.writestr('assets/maps/thailand-shortbread.pmtiles', MAP + (b'changed' if corrupt else b''))
                        archive.writestr('assets/maps/thailand-shortbread.json', json.dumps({'sha256': hashlib.sha256(MAP).hexdigest(), 'bytes': len(MAP), 'source_url': URL}))
                        if recovery is not None:
                            name = 'assets/maps/thailand-shortbread-recovery.json'
                            archive.writestr(name, json.dumps(recovery))
                            if duplicate:
                                with warnings.catch_warnings():
                                    warnings.simplefilter('ignore', UserWarning)
                                    archive.writestr(name, json.dumps(recovery))
                        if extra:
                            archive.writestr('assets/maps/unverified.json', extra)
                paths.append(str(path))
            return subprocess.run([sys.executable, 'native-android/qa/verify-bundled-thailand-apks.py', '--source-sha', SOURCE, *paths], text=True, capture_output=True)

    def provenance(self):
        return {'reference_run': 41029740374, 'reference_artifact': 12965729022,
                'reference_zip_sha256': REFERENCE, 'reference_apk_sha256': 'b' * 64,
                'data_only': True, 'rendering_source_url': URL}

    def test_primary_and_verified_recovery(self):
        for recovery in (None, self.provenance()):
            with self.subTest(recovery=recovery):
                result = self.check(recovery)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(len(json.loads(result.stdout)), 3)

    def test_untrusted_provenance(self):
        for field, value in (('reference_run', 1), ('reference_artifact', 1), ('reference_zip_sha256', '0' * 64), ('reference_apk_sha256', 'missing'), ('data_only', False), ('rendering_source_url', 'https://untrusted.invalid/data')):
            with self.subTest(field=field):
                recovery = self.provenance()
                recovery[field] = value
                result = self.check(recovery)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('recovery provenance', result.stderr)

    def test_existing_guards_remain(self):
        for arguments in ({'extra': 'unknown'}, {'recovery': self.provenance(), 'duplicate': True}, {'corrupt': True}, {'role_map': 'customer'}, {'source': False}):
            with self.subTest(arguments=arguments):
                self.assertNotEqual(self.check(**arguments).returncode, 0)

if __name__ == '__main__':
    unittest.main()
