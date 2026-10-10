"""Run QueueGo Native smoke launches on a real 16 KB Android emulator image.

This is software-runtime evidence only. It must never certify physical-device gates.
"""
from pathlib import Path
import datetime
import hashlib
import json
import os
import subprocess
import time


def run(args, **kwargs):
    return subprocess.run(args, check=True, timeout=kwargs.pop("timeout", 180), **kwargs)


repo = Path(__file__).resolve().parents[2]
sdk = Path(os.environ["ANDROID_HOME"])
output = repo / "artifacts/native-16kb-runtime"
output.mkdir(parents=True, exist_ok=True)

roles = ("customer", "merchant", "rider")
apks = {
    role: repo / f"native-android/{role}/build/outputs/apk/debug/{role}-debug.apk"
    for role in roles
}
for role, apk in apks.items():
    assert apk.is_file(), f"Missing actual {role} APK; build Native APKs first"

assert os.access("/dev/kvm", os.R_OK | os.W_OK), "An accelerated Android runner is required"

manager = sdk / "cmdline-tools/latest/bin"
system_image = "system-images;android-35;google_apis_ps16k;x86_64"
run(
    [str(manager / "sdkmanager"), "emulator", "platform-tools", system_image],
    input="y\n" * 120,
    text=True,
    timeout=600,
)

avd_registry = Path(os.environ["RUNNER_TEMP"]) / "queuego-avd-registry-16kb"
avd_registry.mkdir(parents=True, exist_ok=True)
os.environ["ANDROID_AVD_HOME"] = str(avd_registry)
name = "queuego_16kb_" + os.environ["GITHUB_RUN_ID"] + "_" + os.environ.get("GITHUB_RUN_ATTEMPT", "1")
avd_path = Path(os.environ["RUNNER_TEMP"]) / name
run(
    [
        str(manager / "avdmanager"), "create", "avd",
        "-n", name,
        "-k", system_image,
        "-p", str(avd_path),
        "-d", "pixel_4",
    ],
    input="no\n",
    text=True,
)
assert (avd_registry / (name + ".ini")).is_file(), "16 KB AVD registry was not created"

emulator_bin = str(sdk / "emulator/emulator")
adb_bin = str(sdk / "platform-tools/adb")
run([emulator_bin, "-accel-check"])
run([adb_bin, "start-server"])
adb = [adb_bin, "-s", "emulator-5556"]

results = {}
with (output / "emulator.log").open("w") as log:
    emulator = subprocess.Popen(
        [
            emulator_bin,
            "-avd", name,
            "-port", "5556",
            "-accel", "on",
            "-no-window",
            "-no-snapshot",
            "-no-audio",
            "-gpu", "swiftshader",
            "-memory", "2048",
            "-cores", "2",
        ],
        stdout=log,
        stderr=subprocess.STDOUT,
    )
    try:
        deadline = time.monotonic() + 360
        while True:
            assert emulator.poll() is None, "16 KB emulator exited before Android booted"
            boot = subprocess.run(
                adb + ["shell", "getprop", "sys.boot_completed"],
                capture_output=True,
                text=True,
                timeout=15,
            )
            if boot.returncode == 0 and boot.stdout.strip() == "1":
                break
            assert time.monotonic() < deadline, "16 KB Android boot timed out"
            time.sleep(5)

        page_size = run(adb + ["shell", "getconf", "PAGE_SIZE"], capture_output=True, text=True).stdout.strip()
        assert page_size == "16384", f"Expected 16 KB runtime PAGE_SIZE=16384, got {page_size!r}"
        api = run(adb + ["shell", "getprop", "ro.build.version.sdk"], capture_output=True, text=True).stdout.strip()
        assert api.isdigit() and int(api) >= 35, f"Expected Android 15/API 35+, got {api!r}"

        for role in roles:
            pkg = f"com.queuego.{role}"
            component = pkg + "/.MainActivity"
            apk = apks[role]
            run(adb + ["install", "-r", str(apk)], timeout=180)
            run(adb + ["shell", "am", "force-stop", pkg])
            run(adb + ["logcat", "-b", "all", "-c"])
            started = run(
                adb + ["shell", "am", "start", "-W", "-n", component],
                capture_output=True,
                text=True,
            )
            (output / f"{role}-activity-start.txt").write_text(started.stdout + started.stderr)
            assert "Status: ok" in started.stdout, f"{role} failed to launch on 16 KB runtime"
            time.sleep(5)
            pid = run(adb + ["shell", "pidof", pkg], capture_output=True, text=True).stdout.strip()
            assert pid, f"{role} process is not alive on 16 KB runtime"
            activities = run(
                adb + ["shell", "dumpsys", "activity", "activities"],
                capture_output=True,
                text=True,
            ).stdout
            assert any(
                "mResumedActivity" in line and component in line
                for line in activities.splitlines()
            ), f"{role} is not resumed on 16 KB runtime"
            runtime = run(
                adb + ["logcat", "-d", "-s", "AndroidRuntime:E"],
                capture_output=True,
                text=True,
            ).stdout
            (output / f"{role}-android-runtime.txt").write_text(runtime)
            assert "FATAL EXCEPTION" not in runtime, f"{role} crashed on 16 KB runtime"
            results[role] = {
                "package": pkg,
                "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                "launch_smoke": "PASS",
                "fatal_exception": False,
            }

        metadata = {
            "source_head": os.environ["GITHUB_SHA"],
            "workflow_run": os.environ["GITHUB_RUN_ID"],
            "captured_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "android_api": int(api),
            "page_size_bytes": int(page_size),
            "system_image": system_image,
            "hardware_accelerated_emulator": True,
            "physical_device": False,
            "physical_gate_certified": False,
            "production_account_or_order_writes": False,
            "roles": results,
            "verdict": "PASS_SOFTWARE_16KB_RUNTIME_ONLY",
        }
        (output / "metadata.json").write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2) + "\n"
        )
        print("Native 16 KB emulator runtime PASS for Customer/Merchant/Rider; physical 16 KB device remains OPEN")
    finally:
        try:
            subprocess.run(adb + ["emu", "kill"], timeout=15)
        except Exception:
            pass
        if emulator.poll() is None:
            emulator.terminate()
            try:
                emulator.wait(timeout=15)
            except subprocess.TimeoutExpired:
                emulator.kill()
