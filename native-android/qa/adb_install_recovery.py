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


def start_verified_activity(adb, component, evidence_dir, *, invoke=subprocess.run):
    """Persist Android activity-launch errors before failing the real APK gate.

    ADB can return status 224 after a verified install. This is not proof of
    a Kotlin crash or a transient transport fault: preserve the actual am
    output and bounded device diagnostics. Never retry or certify a failed
    launch without knowing the underlying Android error.
    """
    import json
    from pathlib import Path

    evidence_dir = Path(evidence_dir)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    command = list(adb) + ["shell", "am", "start", "-W", "-n", component]
    result = invoke(command, capture_output=True, text=True, timeout=60, check=False)
    stdout, stderr = result.stdout or "", result.stderr or ""
    (evidence_dir / "activity-start.txt").write_text(
        "exit_code=" + str(result.returncode) + "\nstdout:\n" + stdout
        + "\nstderr:\n" + stderr + "\n", encoding="utf-8"
    )
    if result.returncode == 0 and "Status: ok" in stdout:
        return result

    package = component.split("/", 1)[0]
    diagnostics = {"exit_code": result.returncode, "component": component,
                   "status_ok": "Status: ok" in stdout}
    probes = {
        "boot": list(adb) + ["shell", "getprop", "sys.boot_completed"],
        "launcher_resolution": list(adb) + [
            "shell", "pm", "resolve-activity", "--brief",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER", "-p", package],
        "crash_buffer": list(adb) + ["logcat", "-b", "crash", "-d", "-t", "100"],
    }
    for label, probe in probes.items():
        try:
            detail = invoke(probe, capture_output=True, text=True,
                            timeout=20, check=False)
            diagnostics[label] = {
                "exit_code": detail.returncode,
                "stdout_tail": (detail.stdout or "")[-6000:],
                "stderr_tail": (detail.stderr or "")[-1500:],
            }
        except (OSError, subprocess.SubprocessError) as error:
            diagnostics[label] = {"probe_error": type(error).__name__}
    (evidence_dir / "activity-start-diagnostics.json").write_text(
        json.dumps(diagnostics, indent=2) + "\n", encoding="utf-8"
    )
    raise RuntimeError(
        "Android activity launch not certified (exit "
        + str(result.returncode) + "); inspect activity-start.txt and "
        "activity-start-diagnostics.json. am output: "
        + (stdout + " " + stderr)[-500:]
    )
