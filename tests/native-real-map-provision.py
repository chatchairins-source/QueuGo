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


class CustomerPermissionTests(unittest.TestCase):
    def exercise(self, configured, initial_nodes, permission_nodes):
        import re
        calls = []
        def wait_ui(path, predicate):
            self.assertTrue(predicate(permission_nodes), "Actual permission dialog must be required")
            return permission_nodes
        fn = next(n for n in TREE.body if isinstance(n, ast.FunctionDef) and n.name == 'customer_location_permission_nodes')
        scope = dict(queuego_map_runtime_configured=lambda: configured, re=re, adb=['adb'],
                     run=lambda args: calls.append(args), wait_ui=wait_ui)
        exec(compile(ast.Module(body=[fn], type_ignores=[]), '<actual permission capture>', 'exec'), scope)
        result = scope['customer_location_permission_nodes'](initial_nodes, Path('/evidence'))
        return calls, result

    def test_ready_map_waits_for_automatic_permission_without_tapping_hidden_gps(self):
        # Matches exact run3367 picker.xml, where the Android dialog owns the UI.
        dialog = [{'resource-id': 'com.android.permissioncontroller:id/permission_deny_button'}]
        calls, result = self.exercise(True, dialog, dialog)
        self.assertEqual([], calls)
        self.assertEqual(dialog, result)

    def test_unconfigured_map_still_exercises_explicit_gps_permission(self):
        calls, _ = self.exercise(False, [{'text': 'ใช้ตำแหน่ง GPS ปัจจุบัน', 'bounds': '[10,20][30,40]'}],
                                 [{'resource-id': 'permission_deny_button'}])
        self.assertEqual([['adb', 'shell', 'input', 'tap', '20', '30']], calls)

    def test_ready_map_cannot_pass_without_actual_permission_dialog(self):
        with self.assertRaisesRegex(AssertionError, 'Actual permission dialog'):
            self.exercise(True, [], [])



class NavigationLocationLifecycleEvidenceTests(unittest.TestCase):
    def helper(self):
        fn = next(n for n in TREE.body if isinstance(n, ast.FunctionDef) and n.name == 'assert_navigation_location_lifecycle')
        scope = {}
        exec(compile(ast.Module(body=[fn], type_ignores=[]), '<location lifecycle evidence>', 'exec'), scope)
        return scope['assert_navigation_location_lifecycle']

    def test_allowed_service_survives_eight_cycles_restarts_on_recreate_and_cleans_up(self):
        log = "\n".join(
            ["foreground_updates_started"] +
            [f"lifecycle_stress_cycle={i}" for i in range(1, 9)] +
            ["foreground_updates_stopped", "foreground_updates_started", "foreground_updates_stopped"]
        )
        self.assertEqual((2, 2, 8), self.helper()(log, True, "phone-allowed"))

    def test_allowed_rejects_missing_recreate_restart(self):
        log = "\n".join(
            ["foreground_updates_started"] +
            [f"lifecycle_stress_cycle={i}" for i in range(1, 9)] +
            ["foreground_updates_stopped"]
        )
        with self.assertRaisesRegex(AssertionError, "did not restart"):
            self.helper()(log, True, "phone-allowed")

    def test_allowed_rejects_cleanup_imbalance(self):
        log = "\n".join(
            ["foreground_updates_started", "foreground_updates_started"] +
            [f"lifecycle_stress_cycle={i}" for i in range(1, 9)] +
            ["foreground_updates_stopped"]
        )
        with self.assertRaisesRegex(AssertionError, "cleanup imbalance"):
            self.helper()(log, True, "phone-allowed")

    def test_denied_rejects_any_foreground_location_service(self):
        log = "\n".join([f"lifecycle_stress_cycle={i}" for i in range(1, 9)])
        self.assertEqual((0, 0, 8), self.helper()(log, False, "phone-denied"))
        with self.assertRaisesRegex(AssertionError, "without permission"):
            self.helper()(log + "\nforeground_updates_started", False, "phone-denied")


if __name__ == '__main__':
    unittest.main()
