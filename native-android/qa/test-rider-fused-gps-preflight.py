"""Unit contract for real Fused GPS CI emulator master-location preflight."""
import ast
from pathlib import Path
from types import SimpleNamespace
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / "native-android/qa/run-rider-fused-gps.py").read_text()

def extracted_preflight(adb):
    tree = ast.parse(SOURCE)
    matches = [node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == "ensure_emulator_location_enabled"]
    assert len(matches) == 1, "one mandatory master-location preflight"
    isolated = ast.Module(body=matches, type_ignores=[])
    ns = {"adb": adb, "subprocess": subprocess, "time": SimpleNamespace(sleep=lambda _: None)}
    exec(compile(isolated, "<preflight>", "exec"), ns)
    return ns["ensure_emulator_location_enabled"]

class FusedGpsMasterLocationPreflight(unittest.TestCase):
    def test_enables_and_verifies_location(self):
        calls = []
        def fake_adb(*args, **kwargs):
            calls.append(args)
            if args[-1] == "is-location-enabled":
                return SimpleNamespace(stdout="true\n")
            return SimpleNamespace(stdout='')
        extracted_preflight(fake_adb)()
        self.assertEqual(calls, [("shell", "cmd", "location", "set-location-enabled", "true"),
                                 ("shell", "cmd", "location", "is-location-enabled")])

    def test_disabled_location_is_blocking(self):
        def fake_adb(*args, **kwargs):
            return SimpleNamespace(stdout="false\n" if args[-1] == "is-location-enabled" else "")
        with self.assertRaisesRegex(RuntimeError, 'master Location switch is disabled'):
            extracted_preflight(fake_adb)()

    def test_failed_shell_command_is_blocking(self):
        def fake_adb(*args, **kwargs):
            raise RuntimeError("adb command failed")
        with self.assertRaisesRegex(RuntimeError, 'adb command failed'):
            extracted_preflight(fake_adb)()

    def test_transient_adb_transport_retries_only_then_succeeds(self):
        calls = []
        def fake_adb(*args, **kwargs):
            calls.append(args)
            if args[-1] == "is-location-enabled" and sum(x[-1] == "is-location-enabled" for x in calls) < 3:
                raise subprocess.CalledProcessError(255, "adb shell cmd location is-location-enabled")
            return SimpleNamespace(stdout="true\n" if args[-1] == "is-location-enabled" else "")
        extracted_preflight(fake_adb)()
        self.assertEqual(sum(x[-1] == "is-location-enabled" for x in calls), 3)

    def test_persistent_transport_error_remains_blocking(self):
        calls = []
        def fake_adb(*args, **kwargs):
            calls.append(args)
            if args[-1] == "is-location-enabled":
                raise subprocess.CalledProcessError(255, "adb shell cmd location is-location-enabled")
            return SimpleNamespace(stdout="")
        with self.assertRaises(subprocess.CalledProcessError):
            extracted_preflight(fake_adb)()
        self.assertEqual(sum(x[-1] == "is-location-enabled" for x in calls), 3)

    def test_other_adb_failure_is_not_retried(self):
        calls = []
        def fake_adb(*args, **kwargs):
            calls.append(args)
            if args[-1] == "is-location-enabled":
                raise subprocess.CalledProcessError(1, "unsupported location command")
            return SimpleNamespace(stdout="")
        with self.assertRaises(subprocess.CalledProcessError):
            extracted_preflight(fake_adb)()
        self.assertEqual(sum(x[-1] == "is-location-enabled" for x in calls), 1)
    def test_preflight_required_before_real_instrumentation(self):
        self.assertIn("ensure_emulator_location_enabled()\n                run_case(label, longitude, latitude)", SOURCE)
        self.assertIn('"allowDebugStoreInjection", "false"', SOURCE)
        self.assertIn('"allowIncompleteTiles", "false"', SOURCE)
        self.assertIn('("bangkok", 100.5018, 13.7563)', SOURCE)
        self.assertIn('("buriram", 103.1029, 14.9930)', SOURCE)

if __name__ == "__main__":
    unittest.main()
