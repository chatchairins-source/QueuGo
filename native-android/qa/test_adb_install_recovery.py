"""Offline tests for strict, bounded emulator APK install recovery."""
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

from adb_install_recovery import install_verified_apk, start_verified_activity


def result(code=0, out="", err=""):
    return subprocess.CompletedProcess([], code, out, err)


class EmulatorInstallRecoveryTests(unittest.TestCase):
    def test_every_emulator_apk_install_is_verified(self):
        # Prevent regressions where the initial Rider APK, merchant map
        # instrumentation or viewport matrix still use streamed adb install.
        source = Path(__file__).with_name("capture-rider-launch.py").read_text(
            encoding="utf-8"
        )
        self.assertNotIn('run(adb + ["install"', source)
        self.assertNotIn('adb + ["install"', source)
        self.assertEqual(source.count("install_verified_apk(adb,"), 9)
        self.assertEqual(source.count('install_verified_apk(adb, test_apk, "com.queuego.rider.test")'), 3)
        self.assertEqual(source.count('install_verified_apk(adb, test_apk, "com.queuego.merchant.test")'), 1)
        for call in (
            'install_verified_apk(adb, apk, package)',
            'install_verified_apk(adb, customer_apk, pkg)',
            'install_verified_apk(adb, role_apk, f"com.queuego.{role}")',
            'install_verified_apk(adb, apk, pkg)',
            'install_verified_apk(adb, test_apk, "com.queuego.merchant.test")',
            'install_verified_apk(adb, test_apk, "com.queuego.rider.test")',
        ):
            self.assertIn(call, source)

    def test_authenticated_review_installs_are_fail_closed(self):
        # Both secret-backed screenshot flows must install the exact APK with
        # bounded transport recovery and a verified package, not streamed adb.
        for role in ("rider", "merchant"):
            with self.subTest(role=role):
                source = Path(__file__).with_name(
                    f"capture-{role}-authenticated-review.py"
                ).read_text(encoding="utf-8")
                self.assertIn(
                    "from adb_install_recovery import install_verified_apk", source
                )
                self.assertEqual(source.count("install_verified_apk(ADB, apk, PKG)"), 1)
                self.assertNotIn('run(["install"', source)
                self.assertNotIn('ADB+["install"', source)

    def test_verified_non_streaming_install(self):
        invoke = Mock(side_effect=[result(out="Success\n"), result(out="package:/data/app/rider/base.apk\n")])
        install_verified_apk(["adb", "-s", "emulator-5554"], "/tmp/rider.apk",
                             "com.queuego.rider", invoke=invoke)
        self.assertEqual(invoke.call_args_list[0].args[0][-4:],
                         ["install", "--no-streaming", "-r", "/tmp/rider.apk"])
        self.assertEqual(invoke.call_args_list[-1].args[0][-3:],
                         ["pm", "path", "com.queuego.rider"])

    def test_broken_pipe_retries_once_only_when_booted(self):
        invoke = Mock(side_effect=[
            result(1, err="Broken pipe (32)"),
            result(), result(out="1\n"),
            result(out="Success\n"),
            result(out="package:/data/app/rider/base.apk\n"),
        ])
        sleep = Mock()
        install_verified_apk(["adb"], "/tmp/rider.apk", "com.queuego.rider",
                             invoke=invoke, pause=sleep)
        self.assertEqual(invoke.call_count, 5)
        self.assertEqual(invoke.call_args_list[0].args[0],
                         invoke.call_args_list[3].args[0])
        sleep.assert_called_once_with(3)

    def test_real_package_error_is_not_retried(self):
        invoke = Mock(return_value=result(1, err="INSTALL_FAILED_VERSION_DOWNGRADE"))
        with self.assertRaisesRegex(RuntimeError, "not certified"):
            install_verified_apk(["adb"], "/tmp/rider.apk", "com.queuego.rider",
                                 invoke=invoke, pause=Mock())
        self.assertEqual(invoke.call_count, 1)

    def test_dead_emulator_cannot_retry(self):
        invoke = Mock(side_effect=[
            result(1, err="Broken pipe (32)"), result(1), result(out=""),
        ])
        with self.assertRaisesRegex(RuntimeError, "unhealthy"):
            install_verified_apk(["adb"], "/tmp/rider.apk", "com.queuego.rider",
                                 invoke=invoke, pause=Mock())
        self.assertEqual(invoke.call_count, 3)

    def test_second_transport_failure_is_fatal(self):
        invoke = Mock(side_effect=[
            result(1, err="Broken pipe (32)"), result(), result(out="1"),
            result(1, err="Broken pipe (32)"),
        ])
        with self.assertRaisesRegex(RuntimeError, "not certified"):
            install_verified_apk(["adb"], "/tmp/rider.apk", "com.queuego.rider",
                                 invoke=invoke, pause=Mock())
        self.assertEqual(invoke.call_count, 4)

    def test_success_without_installed_package_is_fatal(self):
        invoke = Mock(side_effect=[result(out="Success"), result(out="")])
        with self.assertRaisesRegex(RuntimeError, "package is absent"):
            install_verified_apk(["adb"], "/tmp/rider.apk", "com.queuego.rider",
                                 invoke=invoke)
        self.assertEqual(invoke.call_count, 2)


    def test_activity_launch_success_requires_android_status_ok(self):
        with tempfile.TemporaryDirectory() as tmp:
            invoke = Mock(return_value=result(out="Status: ok\nActivity: com.queuego.rider/.MainActivity\n"))
            start_verified_activity(["adb"], "com.queuego.rider/.MainActivity", tmp,
                                    invoke=invoke)
            self.assertEqual(invoke.call_count, 1)
            self.assertEqual(invoke.call_args.args[0],
                             ["adb", "shell", "am", "start", "-W", "-n",
                              "com.queuego.rider/.MainActivity"])
            self.assertIn("exit_code=0", (Path(tmp) / "activity-start.txt").read_text())
            self.assertFalse((Path(tmp) / "activity-start-diagnostics.json").exists())

    def test_activity_exit_224_is_not_silently_retried_and_preserves_am_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            invoke = Mock(side_effect=[
                result(224, out="Error type 3\n", err="Activity class does not exist\n"),
                result(out="1\n"),
                result(out="com.queuego.rider/.MainActivity\n"),
                result(out="FATAL EXCEPTION: main\n"),
            ])
            with self.assertRaisesRegex(RuntimeError, "exit 224"):
                start_verified_activity(["adb"], "com.queuego.rider/.MainActivity",
                                        tmp, invoke=invoke)
            self.assertEqual(invoke.call_count, 4)
            self.assertIn("Activity class does not exist",
                          (Path(tmp) / "activity-start.txt").read_text())
            diagnostics = json.loads(
                (Path(tmp) / "activity-start-diagnostics.json").read_text())
            self.assertEqual(diagnostics["exit_code"], 224)
            self.assertIn("FATAL EXCEPTION", diagnostics["crash_buffer"]["stdout_tail"])

    def test_activity_zero_exit_without_ok_status_is_not_certified(self):
        with tempfile.TemporaryDirectory() as tmp:
            invoke = Mock(side_effect=[
                result(out="Status: timeout\n"),
                result(out="1\n"), result(out=""), result(out=""),
            ])
            with self.assertRaisesRegex(RuntimeError, "not certified"):
                start_verified_activity(["adb"], "com.queuego.rider/.MainActivity",
                                        tmp, invoke=invoke)
            self.assertEqual(invoke.call_count, 4)
            self.assertFalse(json.loads(
                (Path(tmp) / "activity-start-diagnostics.json").read_text())["status_ok"])

    def test_activity_probe_failure_does_not_hide_original_launch_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            invoke = Mock(side_effect=[
                result(224, err="am launch failed"), OSError("device disconnected"),
                result(out=""), result(out=""),
            ])
            with self.assertRaisesRegex(RuntimeError, "exit 224"):
                start_verified_activity(["adb"], "com.queuego.rider/.MainActivity",
                                        tmp, invoke=invoke)
            diagnostics = json.loads(
                (Path(tmp) / "activity-start-diagnostics.json").read_text())
            self.assertEqual(diagnostics["boot"]["probe_error"], "OSError")


if __name__ == "__main__":
    unittest.main()
