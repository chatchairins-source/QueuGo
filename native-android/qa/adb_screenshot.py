"""Capture genuine Android screenshots while tolerating transient ADB timeouts."""

from pathlib import Path
import subprocess
import time


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def capture_png(adb_target, output_path, error_path, attempts=3, timeout=30,
                runner=subprocess.run, sleep=time.sleep):
    """Return true only for a real PNG captured by ADB within the bounded retries."""
    output_path = Path(output_path)
    error_path = Path(error_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    for attempt in range(1, attempts + 1):
        try:
            with output_path.open("wb") as stream:
                result = runner(
                    [*adb_target, "exec-out", "screencap", "-p"],
                    stdout=stream,
                    stderr=subprocess.PIPE,
                    timeout=timeout,
                )
            stderr = result.stderr or b""
            if isinstance(stderr, bytes):
                stderr = stderr.decode(errors="replace")
            valid_png = (
                result.returncode == 0
                and output_path.is_file()
                and output_path.stat().st_size > 1024
                and output_path.read_bytes().startswith(PNG_SIGNATURE)
            )
            if valid_png:
                return True
            detail = f"returncode={result.returncode} stderr={stderr}"
        except subprocess.TimeoutExpired as error:
            stderr = error.stderr or b""
            if isinstance(stderr, bytes):
                stderr = stderr.decode(errors="replace")
            detail = f"timeout={timeout}s stderr={stderr}"

        with error_path.open("a", encoding="utf-8") as errors:
            errors.write(f"attempt={attempt} {detail}\n")
        output_path.unlink(missing_ok=True)
        if attempt < attempts:
            sleep(3)

    return False
