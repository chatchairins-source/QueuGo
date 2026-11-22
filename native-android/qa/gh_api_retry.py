"""Fail-closed, bounded GitHub API transport retries for exact-source Rider QA.

Only transient network/server errors are retried. Authentication, authorization,
missing artifacts, and SHA validation failures remain fatal.
"""
from pathlib import Path
import subprocess
import time

_MAX_ATTEMPTS = 4
_TRANSIENT = (
    "i/o timeout",
    "timed out",
    "timeout",
    "connection reset",
    "connection refused",
    "network is unreachable",
    "temporary failure",
    "tls handshake",
    "unexpected eof",
    "http 500",
    "http 502",
    "http 503",
    "http 504",
    "502 bad gateway",
    "503 service unavailable",
    "504 gateway timeout",
)


def github_api(path: str, *, output_file: Path | None = None) -> bytes:
    """Fetch API bytes or stream a zipped artifact, retaining strict failure checks."""
    command = ["gh", "api", path]
    for attempt in range(_MAX_ATTEMPTS):
        try:
            if output_file is None:
                result = subprocess.run(
                    command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                    timeout=120, check=False,
                )
            else:
                # Reopen in truncating mode on every attempt: no partial ZIP reuse.
                with Path(output_file).open("wb") as stream:
                    result = subprocess.run(
                        command, stdout=stream, stderr=subprocess.PIPE,
                        timeout=300, check=False,
                    )
        except subprocess.TimeoutExpired:
            transient = True
        else:
            if result.returncode == 0:
                return result.stdout if output_file is None else b""
            stderr = (result.stderr or b"").decode("utf-8", "replace").lower()
            transient = any(marker in stderr for marker in _TRANSIENT)
            if not transient:
                raise RuntimeError(
                    "GitHub API rejected exact-source artifact request "
                    f"(exit {result.returncode}); refusing unsafe retry"
                )
        if attempt == _MAX_ATTEMPTS - 1:
            raise RuntimeError(
                f"Transient GitHub API failure persisted after {_MAX_ATTEMPTS} attempts"
            )
        print(
            f"::warning::Transient GitHub API transport error; retry "
            f"{attempt + 2}/{_MAX_ATTEMPTS} without bypassing artifact verification",
            flush=True,
        )
        time.sleep(min(20, 3 * 2 ** attempt))
    raise AssertionError("Unreachable GitHub API retry state")
