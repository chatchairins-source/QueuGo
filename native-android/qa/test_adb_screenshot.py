"""Regression-test genuine screenshot capture across transient ADB failures."""

from pathlib import Path
import subprocess
import tempfile

from adb_screenshot import PNG_SIGNATURE, capture_png


PNG = PNG_SIGNATURE + (b"queuego-test-pixels" * 100)


def main():
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)

        def paths(name):
            return root / f"{name}.png", root / f"{name}.errors.txt"

        screenshot, errors = paths("timeout-then-success")
        calls = []
        sleeps = []

        def timeout_then_success(command, stdout, stderr, timeout):
            calls.append((command, timeout))
            if len(calls) == 1:
                raise subprocess.TimeoutExpired(command, timeout, stderr=b"ADB busy")
            stdout.write(PNG)
            return subprocess.CompletedProcess(command, 0, b"", b"")

        assert capture_png(["adb", "-s", "emulator-5556"], screenshot, errors,
                           runner=timeout_then_success, sleep=sleeps.append)
        assert len(calls) == 2 and calls[0][1] == 30
        assert screenshot.read_bytes() == PNG
        assert "timeout=30s" in errors.read_text()
        assert sleeps == [3]

        screenshot, errors = paths("timeout-exhausted")
        calls.clear()

        def always_timeout(command, stdout, stderr, timeout):
            calls.append(command)
            raise subprocess.TimeoutExpired(command, timeout)

        assert not capture_png(["adb"], screenshot, errors,
                               runner=always_timeout, sleep=lambda _: None)
        assert len(calls) == 3 and not screenshot.exists()
        assert len(errors.read_text().splitlines()) == 3

        screenshot, errors = paths("invalid-png-then-success")
        calls.clear()

        def invalid_then_success(command, stdout, stderr, timeout):
            calls.append(command)
            stdout.write(b"not a PNG" if len(calls) == 1 else PNG)
            return subprocess.CompletedProcess(command, 0, b"", b"")

        assert capture_png(["adb"], screenshot, errors,
                           runner=invalid_then_success, sleep=lambda _: None)
        assert len(calls) == 2 and screenshot.read_bytes() == PNG
        assert "returncode=0" in errors.read_text()

    print("PASS ADB screenshot capture retries real PNGs after transport failures")


if __name__ == "__main__":
    main()
