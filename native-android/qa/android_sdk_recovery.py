"""Fail-closed, bounded recovery from transient Android SDK package download errors.

The hosted runner occasionally receives a truncated Android Emulator ZIP. Retry
the official sdkmanager command; never disable TLS, use unverified archives, or
treat missing emulator binaries as a successful runtime test.
"""

import subprocess
import time
from pathlib import Path
from typing import Callable, Any


def install_android_sdk_packages(
    sdkmanager: Path,
    runner: Callable[..., Any],
    *,
    max_attempts: int = 3,
    sleep: Callable[[float], None] = time.sleep,
) -> Any:
    if not 1 <= max_attempts <= 3:
        raise ValueError("SDK installation retries must be in range 1..3")
    command = [
        str(sdkmanager),
        "emulator",
        "platform-tools",
        "system-images;android-30;default;x86_64",
    ]
    for attempt in range(1, max_attempts + 1):
        try:
            return runner(
                command,
                input="y\n" * 100,
                text=True,
                timeout=300,
            )
        except subprocess.CalledProcessError as failure:
            if attempt == max_attempts:
                print(
                    "QUEUEGO_ANDROID_SDK_INSTALL_FAILED "
                    f"attempts={max_attempts} returncode={failure.returncode}",
                    flush=True,
                )
                raise
            delay_seconds = attempt * 5
            print(
                "QUEUEGO_ANDROID_SDK_INSTALL_RETRY "
                f"attempt={attempt} of={max_attempts} delay_seconds={delay_seconds}",
                flush=True,
            )
            sleep(delay_seconds)
    raise AssertionError("SDK install retry loop should never exit silently")
