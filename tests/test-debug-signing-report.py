"""Signing-report parser fixtures are not release signing evidence."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
spec = importlib.util.spec_from_file_location('debug_store', 'native-android/qa/resolve-debug-signing-store.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class SigningReportTests(unittest.TestCase):
    def test_actual_reported_path_with_spaces_not_assumed_home(self):
        with tempfile.TemporaryDirectory() as root:
            store = Path(root) / 'alternate Android home' / 'debug.keystore'
            store.parent.mkdir()
            store.write_bytes(b'parser fixture only')
            report = f'> Task :rider:signingReport\nVariant: debug\nConfig: debug\nStore: {store}\nAlias: AndroidDebugKey\nSHA-256: public-fingerprint\n----------\nVariant: release\nConfig: null\nStore: null\nAlias: null\n'
            self.assertEqual(module.resolve(report), (store.resolve(), 'AndroidDebugKey'))
            for bad in (report.replace('Variant: debug', 'Variant: debugAndroidTest'), report + report, report.replace(str(store), str(store)+'-missing'), report.replace('Config: debug', 'Config: queuegoRelease')):
                with self.subTest(report=bad), self.assertRaises(ValueError):
                    module.resolve(bad)

if __name__ == '__main__':
    unittest.main()
