"""Run the isolated Rider navigation-map Android instrumentation gate."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import time

def run(args, **kwargs):
    return subprocess.run(args, check=True, timeout=kwargs.pop("timeout", 240), **kwargs)

repo = Path(__file__).resolve().parents[2]
sdk = Path(os.environ["ANDROID_HOME"])
temp = Path(os.environ.get("RUNNER_TEMP", "/tmp"))
registry = temp / "queuego-nav-map-avd-registry"
registry.mkdir(parents=True, exist_ok=True)
os.environ["ANDROID_AVD_HOME"] = str(registry)

manager = sdk / "cmdline-tools/latest/bin"
sdk_packages = ["emulator", "platform-tools", "system-images;android-30;default;x86_64"]
for attempt in range(1, 4):
    try:
        run(
            [str(manager / "sdkmanager"), *sdk_packages],
            input="y\n" * 100,
            text=True,
            # Keep the real emulator/runtime gate intact. Hosted runners can be
            # slow while fetching the pinned Android 30 x86_64 system image.
            timeout=600,
        )
        break
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        if attempt == 3:
            raise
        # sdkmanager can leave a truncated archive under .temp after a transient
        # CDN/network failure ("unknown archive"). Remove only download staging
        # before retrying; installed SDK packages and the runtime test stay intact.
        shutil.rmtree(sdk / ".temp", ignore_errors=True)
        time.sleep(5)
name = "queuego_nav_map_" + os.environ.get("GITHUB_RUN_ID", "local")
avd_path = temp / name
run(
    [str(manager / "avdmanager"), "create", "avd", "-n", name,
     "-k", "system-images;android-30;default;x86_64", "-p", str(avd_path), "-d", "pixel_4"],
    input="no\n",
    text=True,
)
assert (registry / (name + ".ini")).is_file(), "Navigation-map AVD registry entry missing"

adb_bin = str(sdk / "platform-tools/adb")
run([adb_bin, "start-server"])
adb = [adb_bin, "-s", "emulator-5554"]
emulator_bin = str(sdk / "emulator/emulator")
gradle = shutil.which("gradle")
assert gradle, "Gradle executable is required"

log_dir = repo / "artifacts/rider-navigation-map-runtime"
log_dir.mkdir(parents=True, exist_ok=True)

with (log_dir / "emulator.log").open("w") as log:
    emulator = subprocess.Popen(
        [emulator_bin, "-avd", name, "-port", "5554", "-accel", "on",
         "-no-window", "-no-snapshot", "-no-audio", "-gpu", "swiftshader",
         "-memory", "2048", "-cores", "2"],
        stdout=log,
        stderr=subprocess.STDOUT,
    )
    try:
        deadline = time.monotonic() + 240
        while True:
            assert emulator.poll() is None, "Navigation-map emulator exited before boot"
            boot = subprocess.run(
                adb + ["shell", "getprop", "sys.boot_completed"],
                capture_output=True,
                text=True,
                timeout=15,
            )
            if boot.returncode == 0 and boot.stdout.strip() == "1":
                break
            assert time.monotonic() < deadline, "Navigation-map Android boot timed out"
            time.sleep(4)

        for scale in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            run(adb + ["shell", "settings", "put", "global", scale, "1"])

        # Exercise actual Android interpolation; animator_duration_scale=0 would
        # bypass the marker animation and conceal overlap/jank regressions.
        if os.environ.get("QG_NAV_RUNTIME_OFFLINE") == "1":
            run(adb + ["shell", "settings", "put", "global", "airplane_mode_on", "1"])
            run(adb + ["shell", "svc", "wifi", "disable"])
            run(adb + ["shell", "svc", "data", "disable"])
            state = subprocess.run(adb + ["shell", "dumpsys", "connectivity"], capture_output=True, text=True, check=True)
            (log_dir / "offline-connectivity.txt").write_text(state.stdout)
        result = subprocess.run(
            [gradle, "-p", "native-android",
             ":rider-navigation-map:connectedDebugAndroidTest",
             "-Pandroid.testInstrumentationRunnerArguments.offlineRequired=" + ("true" if os.environ.get("QG_NAV_RUNTIME_OFFLINE") == "1" else "false"),
             "--no-build-cache", "--stacktrace"],
            cwd=repo,
            capture_output=True,
            text=True,
            timeout=600,
        )
        (log_dir / "gradle.txt").write_text(result.stdout + result.stderr)
        print(result.stdout)
        if result.stderr:
            print(result.stderr)
        if os.environ.get("QG_NAV_RUNTIME_OFFLINE") == "1":
            # Shared test-owned output survives Gradle uninstalling its test APK.
            pulled = subprocess.run(adb + ["pull", "/sdcard/Download/QueueGoOfflineEvidence", str(log_dir / "country-screenshots")], capture_output=True, text=True, timeout=60)
            (log_dir / "screenshot-transfer.txt").write_text(pulled.stdout + pulled.stderr)
        if result.returncode != 0:
            raise SystemExit("Navigation-map connected Android test failed")
        if os.environ.get("QG_NAV_RUNTIME_OFFLINE") == "1":
            images = list((log_dir / "country-screenshots").rglob("*.png"))
            expected = {"bangkok.png", "buriram.png", "chiangmai.png", "phuket.png", "narathiwat.png"}
            if pulled.returncode != 0 or {image.name for image in images} != expected or len(images) != 5:
                raise SystemExit("Country screenshot evidence is incomplete")
            head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=repo, capture_output=True, text=True, check=True).stdout.strip()
            (log_dir / "country-evidence-metadata.json").write_text(json.dumps({
                "source_head": head, "workflow_run": os.environ.get("GITHUB_RUN_ID"),
                "tests_exit_code": result.returncode, "offline_required": True,
                "physical_device": False,
                "screenshots": {image.name: hashlib.sha256(image.read_bytes()).hexdigest() for image in images}
            }, indent=2) + "\n")
        print("QUEUEGO_NAVIGATION_MAP_RUNTIME_PASS")
    finally:
        try:
            logs = subprocess.run(adb + ["logcat", "-d"], capture_output=True, text=True, timeout=15)
            (log_dir / "logcat.txt").write_text(logs.stdout + logs.stderr)
        except subprocess.TimeoutExpired:
            pass
        try:
            subprocess.run(adb + ["emu", "kill"], capture_output=True, timeout=15)
        except subprocess.TimeoutExpired:
            pass
        emulator.terminate()
        try:
            emulator.wait(timeout=15)
        except subprocess.TimeoutExpired:
            emulator.kill()
