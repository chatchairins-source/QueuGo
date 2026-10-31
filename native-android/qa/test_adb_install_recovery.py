"""Offline tests for strict, bounded emulator APK install recovery."""
import subprocess
import unittest
from unittest.mock import Mock

from adb_install_recovery import install_verified_apk


def result(code=0, out="", err=""):
    return subprocess.CompletedProcess([], code, out, err)


class EmulatorInstallRecoveryTests(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
