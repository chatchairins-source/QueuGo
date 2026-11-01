"""Fail-closed Android emulator APK installation for native screenshot CI.

A successful Gradle build is not a successful emulator installation. In particular,
large streamed installs can lose the package-service pipe on hosted emulators.
Retry only an identified transient transport error and verify the installed package.
"""
from __future__ import annotations

import subprocess
import time


_TRANSIENT = ("broken pipe", "connection reset", "device offline", "closed", "transport error")


def install_verified_apk(adb, apk, package, *, invoke=subprocess.run, pause=time.sleep):
    """Install exact APK bytes without streaming; verify package after bounded retry."""
    command = list(adb) + ["install", "--no-streaming", "-r", str(apk)]
    for attempt in range(2):
        result = invoke(command, capture_output=True, text=True, timeout=240, check=False)
        output = (result.stdout or "") + "\n" + (result.stderr or "")
        if result.returncode == 0 and "Success" in output:
            break
        if attempt != 0 or not any(token in output.lower() for token in _TRANSIENT):
            raise RuntimeError("Android APK install failed (not certified): " + output[-700:])
        # Never retry while the emulator is unavailable; do not turn a dead
        # emulator or a real package failure into a false CI success.
        ready = invoke(list(adb) + ["wait-for-device"], capture_output=True,
                       text=True, timeout=40, check=False)
        boot = invoke(list(adb) + ["shell", "getprop", "sys.boot_completed"],
                      capture_output=True, text=True, timeout=20, check=False)
        if ready.returncode != 0 or boot.returncode != 0 or boot.stdout.strip() != "1":
            raise RuntimeError("Android emulator unhealthy after transport failure")
        pause(3)
    installed = invoke(list(adb) + ["shell", "pm", "path", package],
                       capture_output=True, text=True, timeout=30, check=False)
    if installed.returncode != 0 or not any(
        line.startswith("package:") for line in (installed.stdout or "").splitlines()
    ):
        raise RuntimeError("APK install returned success but package is absent: " + package)
    print("QUEUEGO_EMULATOR_APK_INSTALLED " + package + " verified by pm path")
