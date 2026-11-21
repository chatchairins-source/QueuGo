"""Tests of strict Android PackageManager readiness; no device or network needed."""
import subprocess
import unittest
from unittest.mock import Mock
from emulator_package_service import await_package_service


def reply(code=0, text=""):
    return subprocess.CompletedProcess([], code, text, "")


class PackageServiceTests(unittest.TestCase):
    def test_waits_for_real_package_service(self):
        emulator = Mock(poll=Mock(return_value=None))
        invoke = Mock(side_effect=[
            reply(0, "Service package: not found\n"),
            reply(0, "Service package: found\n"),
        ])
        pause = Mock()
        result = await_package_service(["adb", "-s", "emulator-5554"], emulator,
                                       invoke=invoke, pause=pause, attempts=3)
        self.assertEqual(result, "Service package: found")
        self.assertEqual(invoke.call_count, 2)
        self.assertEqual(invoke.call_args.args[0][-4:],
                         ["shell", "service", "check", "package"])
        pause.assert_called_once_with(2)

    def test_exited_emulator_never_certifies(self):
        emulator = Mock(poll=Mock(return_value=1))
        invoke = Mock()
        with self.assertRaisesRegex(RuntimeError, "exited"):
            await_package_service(["adb"], emulator, invoke=invoke, pause=Mock())
        invoke.assert_not_called()

    def test_service_unavailable_fails_closed(self):
        emulator = Mock(poll=Mock(return_value=None))
        invoke = Mock(return_value=reply(1, "Service package: not found"))
        with self.assertRaisesRegex(RuntimeError, "refusing runtime certification"):
            await_package_service(["adb"], emulator, invoke=invoke, pause=Mock(), attempts=2)
        self.assertEqual(invoke.call_count, 2)

    def test_timeout_is_not_success(self):
        emulator = Mock(poll=Mock(return_value=None))
        invoke = Mock(side_effect=subprocess.TimeoutExpired(["adb"], 15))
        with self.assertRaisesRegex(RuntimeError, "unavailable"):
            await_package_service(["adb"], emulator, invoke=invoke, pause=Mock(), attempts=1)


if __name__ == "__main__":
    unittest.main()
