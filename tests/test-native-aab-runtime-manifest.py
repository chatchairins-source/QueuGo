"""Exercise real post-build manifest validation; XML fixtures are not release evidence."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('artifacts', Path(__file__).resolve().parents[1] / 'native-android/qa/verify-native-release-artifacts.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class BundleManifestTests(unittest.TestCase):
    def manifest(self, debug=None, target='36', application=True):
        flag = '' if debug is None else f' android:debuggable="{debug}"'
        app = f'<application{flag}/>' if application else ''
        sdk = '' if target is None else f'<uses-sdk android:targetSdkVersion="{target}"/>'
        return f'<manifest xmlns:android="http://schemas.android.com/apk/res/android">{sdk}{app}</manifest>'

    def test_release_default_and_explicit_false(self):
        for debug in (None, 'false', '0'):
            with self.subTest(debug=debug):
                result = module.verify_bundle_runtime_manifest(self.manifest(debug), 'rider')
                self.assertTrue(result['aab_non_debuggable'])
                self.assertEqual(result['aab_target_sdk'], 36)

    def test_debug_aab_cannot_borrow_non_debug_apk_acceptance(self):
        for debug in ('true', '1', 'TRUE', '@bool/debuggable', 'unknown'):
            with self.subTest(debug=debug):
                with self.assertRaisesRegex(ValueError, 'AAB must not be debuggable'):
                    module.verify_bundle_runtime_manifest(self.manifest(debug), 'customer')

    def test_actual_bundle_target_must_meet_api_36(self):
        for target in (None, '35', '0', 'Baklava', '@integer/target', '-36'):
            with self.subTest(target=target):
                with self.assertRaisesRegex(ValueError, 'AAB targetSdk'):
                    module.verify_bundle_runtime_manifest(self.manifest(target=target), 'merchant')
        self.assertEqual(module.verify_bundle_runtime_manifest(self.manifest(target='37'), 'rider')['aab_target_sdk'], 37)

    def test_missing_duplicate_or_malformed_structure(self):
        for xml in (self.manifest(application=False), self.manifest().replace('</manifest>', '<application/></manifest>'), '<manifest>', '<other/>'):
            with self.subTest(xml=xml):
                with self.assertRaises(ValueError):
                    module.verify_bundle_runtime_manifest(xml, 'rider')

if __name__ == '__main__':
    unittest.main()
