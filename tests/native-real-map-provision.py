"""Exercise the actual capture provisioner without starting Android or networking."""
import ast
import hashlib
import os
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest

SOURCE = Path('native-android/qa/capture-rider-launch.py').read_text()
TREE = ast.parse(SOURCE)
FUNCTION = next(n for n in TREE.body if isinstance(n, ast.FunctionDef) and n.name == 'provision_real_map')


class ProvisionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        self.archive = self.directory / 'queuego-ci-buriram.pmtiles'
        self.archive.write_bytes(b'unit fixture, never packaged or sent to Production')
        self.digest = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        self.commands = []
        self.device_digest = self.digest
        def run(args, **kwargs):
            self.commands.append(args)
            return SimpleNamespace(stdout=self.device_digest + '  files/queuego-ci-buriram.pmtiles\n')
        import json
        self.scope = dict(Path=Path, os=SimpleNamespace(environ={'RUNNER_TEMP': self.temp.name, 'QG_DEBUG_MAP_ARCHIVE': '1'}),
                          hashlib=hashlib, json=json, output=self.directory,
                          CI_MAP_URL='https://invalid.example/not-used', CI_MAP_SHA256=self.digest,
                          run=run, adb=['adb'])
        exec(compile(ast.Module(body=[FUNCTION], type_ignores=[]), '<actual capture provisioner>', 'exec'), self.scope)

    def tearDown(self):
        self.temp.cleanup()

    def test_without_explicit_opt_in_has_no_file_or_device_effect(self):
        self.scope["os"].environ.pop("QG_DEBUG_MAP_ARCHIVE")
        self.scope["provision_real_map"]("com.queuego.customer")
        self.assertEqual([], self.commands)

    def test_checksum_mismatch_cannot_reach_device(self):
        self.archive.write_bytes(b'tampered')
        with self.assertRaisesRegex(AssertionError, 'checksum mismatch'):
            self.scope['provision_real_map']('com.queuego.customer')
        self.assertEqual([], self.commands)

    def test_app_private_copy_is_verified_and_evidence_does_not_certify_release(self):
        self.scope['provision_real_map']('com.queuego.customer')
        self.assertIn(['adb', 'shell', 'run-as', 'com.queuego.customer', 'cp',
                       '/data/local/tmp/queuego-ci-buriram.pmtiles', 'files/queuego-ci-buriram.pmtiles'], self.commands)
        import json
        evidence = json.loads((self.directory / 'com.queuego.customer-map-source.json').read_text())
        self.assertEqual(self.digest, evidence['sha256'])
        for key in ('public_runtime_certified', 'routing_certified', 'physical_gps_certified'):
            self.assertFalse(evidence[key])

    def test_device_copy_mismatch_cannot_emit_success_evidence(self):
        self.device_digest = 'corrupt'
        with self.assertRaisesRegex(AssertionError, 'App-private'):
            self.scope['provision_real_map']('com.queuego.rider')
        self.assertFalse((self.directory / 'com.queuego.rider-map-source.json').exists())

    def test_requested_real_archive_cannot_be_reported_blocked(self):
        fn = next(n for n in TREE.body if isinstance(n, ast.FunctionDef) and n.name == 'record_map_runtime_blocked')
        exec(compile(ast.Module(body=[fn], type_ignores=[]), '<actual blocker>', 'exec'), self.scope)
        with self.assertRaisesRegex(AssertionError, 'must execute'):
            self.scope['record_map_runtime_blocked']('customer-location-allowed')


if __name__ == '__main__':
    unittest.main()
