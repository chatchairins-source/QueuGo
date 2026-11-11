"""Exercise the read-only Production foreign-key readiness release gate."""

from pathlib import Path
import subprocess
import sys
import tempfile


root = Path(__file__).resolve().parents[1]
script = root / "ops" / "verify-public-foreign-keys.py"
workflow = (root / ".github" / "workflows" / "native-release-certification.yml").read_text()
assert "Require validated Production public foreign keys" in workflow
assert "pg_catalog.pg_constraint" in workflow and "not c.convalidated" in workflow
assert "python3 ops/verify-public-foreign-keys.py" in workflow

with tempfile.TemporaryDirectory() as directory:
    count_file = Path(directory) / "unvalidated-fk-count.txt"

    def verify(value: str) -> subprocess.CompletedProcess[str]:
        count_file.write_text(value)
        return subprocess.run(
            [sys.executable, str(script), str(count_file)],
            capture_output=True, text=True, check=False,
        )

    clean = verify("0\n")
    assert clean.returncode == 0 and "all validated" in clean.stdout

    outstanding = verify("2\n")
    assert outstanding.returncode == 1 and "not validated: 2" in outstanding.stdout

    empty = verify("\n")
    assert empty.returncode == 1 and "empty or malformed" in empty.stderr

    malformed = verify("0\n1\n")
    assert malformed.returncode == 1 and "empty or malformed" in malformed.stderr

    negative = verify("-1\n")
    assert negative.returncode == 1 and "empty or malformed" in negative.stderr

print("PASS fail-closed Production public foreign-key readiness gate")
