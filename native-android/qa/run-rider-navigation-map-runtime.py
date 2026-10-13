"""Run the isolated Rider navigation-map Android instrumentation gate."""
from pathlib import Path
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
run(
    [str(manager / "sdkmanager"), "emulator", "platform-tools", "system-images;android-30;default;x86_64"],
    input="y\n" * 100,
    text=True,
    timeout=300,
)
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
            run(adb + ["shell", "settings", "put", "global", scale, "0"])

        result = run(
            [gradle, "-p", "native-android",
             ":rider-navigation-map:connectedDebugAndroidTest",
             "--no-build-cache", "--stacktrace"],
            cwd=repo,
            capture_output=True,
            text=True,
            timeout=600,
        )
        (log_dir / "gradle.txt").write_text(result.stdout + result.stderr)
        print(result.stdout)
        print("QUEUEGO_NAVIGATION_MAP_RUNTIME_PASS")
    finally:
        try:
            subprocess.run(adb + ["logcat", "-d"], capture_output=True, text=True, timeout=15).stdout
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
