"""Fail-closed package-manager readiness preflight on Android emulators.

A successful sys.boot_completed property does not prove PackageManager is alive.
Do not substitute this probe for installed Android instrumentation or JUnit XML.
"""
import subprocess
import time


def await_package_service(adb, emulator_process, *, invoke=subprocess.run,
                          pause=time.sleep, attempts=30):
    last = ""
    for i in range(attempts):
        if emulator_process.poll() is not None:
            raise RuntimeError("Emulator exited while awaiting Android package service")
        try:
            result = invoke(
                list(adb) + ["shell", "service", "check", "package"],
                capture_output=True, text=True, check=False, timeout=15,
            )
            last = (result.stdout or "") + "\n" + (result.stderr or "")
            if result.returncode == 0 and "Service package: found" in last:
                return "Service package: found"
        except subprocess.TimeoutExpired:
            last = "Android package-service check timed out"
        if i < attempts - 1:
            pause(2)
    raise RuntimeError("Android PackageManager unavailable; refusing runtime certification: " + last[-250:])
