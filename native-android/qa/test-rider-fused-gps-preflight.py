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
        with self.assertRaisesRegex(RuntimeError, 'master Location switch not verified'):
            extracted_preflight(fake_adb)()
        self.assertEqual(sum(x[-1] == "is-location-enabled" for x in calls), 3)
        self.assertEqual(sum(x[-1] == "location_mode" for x in calls), 0)

    def test_exhausted_transport_never_trusts_legacy_mode(self):
        for mode in ('0', '1', '2', '3', 'null', ''):
            calls = []
            def fake_adb(*args, **kwargs):
                calls.append(args)
                if args[-1] == 'is-location-enabled':
                    raise subprocess.CalledProcessError(255, 'transport unavailable')
                return SimpleNamespace(stdout=mode + '\n')
            with self.assertRaisesRegex(RuntimeError, 'master Location switch not verified'):
                extracted_preflight(fake_adb)()
            self.assertEqual(sum(x[-1] == 'is-location-enabled' for x in calls), 3)
            self.assertFalse(any(x[-1] == 'location_mode' for x in calls))

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
    def test_real_fused_failure_requires_retained_platform_evidence(self):
        self.assertIn("capture_fused_failure_evidence(label)", SOURCE)
        self.assertIn('"location-provider": ["shell", "dumpsys", "location"]', SOURCE)
        self.assertIn('"fused-logcat": ["logcat", "-d", "-t", "300", "-s"', SOURCE)
        self.assertIn("raise AssertionError(\"Real Fused GPS test failed:", SOURCE)
        self.assertIn('"allowDebugStoreInjection", "false"', SOURCE)

    def test_rejected_fused_subscription_stops_navigation_service(self):
        # Source contract only; the exact-head Android emulator gate remains mandatory.
        service = (ROOT / "native-android/rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt").read_text()
        failure = service.split('.addOnFailureListener { error ->', 1)[1].split('fusedClient.lastLocation', 1)[0]
        self.assertIn('foreground_fused_subscription_failed', failure)
        # A synchronous/early failure callback must not be skipped while the
        # subscription-start flag is still provisional.
        self.assertIn('if (callback === nextCallback) {', failure)
        self.assertNotIn('if (callback === nextCallback && locationUpdatesStarted)', failure)
        self.assertIn('locationUpdatesStarted = false', failure)
        self.assertIn('RiderNavigationLocationStore.clear()', failure)
        self.assertIn('stopSelf()', failure)

    def test_synchronous_provider_failure_cannot_reenable_subscription(self):
        # Source regression: Play Services may complete a listener before
        # requestLocationUpdates returns. Emulator evidence remains mandatory.
        service = (ROOT / "native-android/rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt").read_text()
        start = service.split("private fun startLocationUpdates()", 1)[1].split("private fun stopLocationUpdates()", 1)[0]
        provisional = start.index("locationUpdatesStarted = true")
        request = start.index("fusedClient.requestLocationUpdates(request, nextCallback")
        self.assertLess(provisional, request)
        self.assertEqual(start.count("locationUpdatesStarted = true"), 1)
        self.assertIn("if (started && callback === nextCallback && locationUpdatesStarted)", start)
        self.assertIn("locationUpdatesStarted = false", start.split("} else {", 1)[-1])

    def test_stale_fused_callback_cannot_publish_after_restart(self):
        # Contract guard only: installed emulator GPS evidence is still required.
        service = (ROOT / "native-android/rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt").read_text()
        callback = service.split("override fun onLocationResult(result: LocationResult)", 1)[1].split("callback = nextCallback", 1)[0]
        self.assertIn("callback !== this || !locationUpdatesStarted", callback)
        cached = service.split("fusedClient.lastLocation.addOnSuccessListener", 1)[1].split("accept(location)", 1)[0]
        self.assertIn("callback !== nextCallback", cached)

    def test_rejected_fused_fix_diagnostic_preserves_privacy_and_gate(self):
        # This is a source contract, not evidence that the emulator delivered a fix.
        service = (ROOT / "native-android/rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt").read_text()
        section = service.split("private fun accept(location: Location)", 1)[1].split("val previous = lastAccepted", 1)[0]
        self.assertIn("riderNavigationFixEligible(", section)
        self.assertIn('Log.d("QueueGoNavLocation", "fused_fix_rejected_freshness_or_accuracy")', section)
        self.assertIn("return", section.split("fused_fix_rejected_freshness_or_accuracy", 1)[1])
        self.assertNotIn("location.latitude}", section)
        self.assertNotIn("location.longitude}", section)

    def test_preflight_required_before_real_instrumentation(self):
        self.assertIn("ensure_emulator_location_enabled()\n                run_case(label, longitude, latitude)", SOURCE)
        self.assertIn('"allowDebugStoreInjection", "false"', SOURCE)
        self.assertIn('"allowIncompleteTiles", "false"', SOURCE)
        self.assertIn('("bangkok", 100.5018, 13.7563)', SOURCE)
        self.assertIn('("buriram", 103.1029, 14.9930)', SOURCE)

if __name__ == "__main__":
    unittest.main()
