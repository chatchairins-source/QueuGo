"""Deterministic tests for the real GitHub runner's sdkmanager recovery."""

import subprocess
import unittest
from pathlib import Path

from android_sdk_recovery import install_android_sdk_packages


class AndroidSdkRecoveryTest(unittest.TestCase):
    def test_two_transient_corrupt_zip_failures_retry_then_succeed(self):
        seen = []
        delays = []
        result = object()

        def runner(args, **kwargs):
            seen.append((list(args), kwargs))
            if len(seen) < 3:
                raise subprocess.CalledProcessError(1, args)
            return result

        actual = install_android_sdk_packages(
            Path("/sdk/cmdline-tools/latest/bin/sdkmanager"),
            runner,
            sleep=delays.append,
        )
        self.assertIs(actual, result)
        self.assertEqual([5, 10], delays)
        self.assertEqual(3, len(seen))
        self.assertTrue(all(
            invocation[0][1:] == [
                "emulator",
                "platform-tools",
                "system-images;android-30;default;x86_64",
            ] and invocation[1]["timeout"] == 300
            for invocation in seen
        ))

    def test_persistent_download_failure_propagates_and_blocks_runtime(self):
        attempts = []
        delays = []

        def runner(args, **_kwargs):
            attempts.append(args)
            raise subprocess.CalledProcessError(1, args)

        with self.assertRaises(subprocess.CalledProcessError):
            install_android_sdk_packages(Path("/sdk/sdkmanager"), runner, sleep=delays.append)
        self.assertEqual(3, len(attempts))
        self.assertEqual([5, 10], delays)

    def test_non_transient_programming_error_is_not_hidden_by_retry(self):
        calls = []
        def runner(args, **_kwargs):
            calls.append(args)
            raise ValueError("sdkmanager command is invalid")
        with self.assertRaisesRegex(ValueError, "invalid"):
            install_android_sdk_packages(Path("/sdk/sdkmanager"), runner, sleep=lambda _: None)
        self.assertEqual(1, len(calls))

    def test_retry_limit_must_remain_bounded(self):
        for attempts in (0, 4):
            with self.assertRaises(ValueError):
                install_android_sdk_packages(
                    Path("/sdk/sdkmanager"),
                    lambda *_args, **_kwargs: None,
                    max_attempts=attempts,
                )


if __name__ == "__main__":
    unittest.main()
