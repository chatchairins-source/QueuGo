"""Exercise the release gate against isolated migration ledgers."""

from pathlib import Path
import subprocess
import sys
import tempfile


script = Path(__file__).resolve().parents[1] / "ops" / "verify-migration-ledger.py"

with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    migrations = root / "supabase" / "migrations"
    migrations.mkdir(parents=True)
    (migrations / "20261001000000_base.sql").write_text("select 1;\n")
    (migrations / "20261002000000_orders.sql").write_text("select 2;\n")
    ledger = root / "ledger.txt"

    def verify(*versions: str) -> subprocess.CompletedProcess[str]:
        ledger.write_text("\n".join(versions) + "\n")
        return subprocess.run(
            [sys.executable, str(script), str(ledger), "--root", str(root)],
            capture_output=True, text=True, check=False,
        )

    matched = verify("20261001000000", "20261002000000")
    assert matched.returncode == 0, matched.stderr

    missing = verify("20261001000000")
    assert missing.returncode == 1 and "Missing in Production (1): 20261002000000" in missing.stdout

    untracked = verify("20261001000000", "20261002000000", "20261003000000")
    assert untracked.returncode == 1 and "Untracked Production versions (1): 20261003000000" in untracked.stdout

    invalid = verify("20261001000000", "not-a-version")
    assert invalid.returncode == 1 and "invalid version" in invalid.stderr

    duplicate = verify("20261001000000", "20261001000000")
    assert duplicate.returncode == 1 and "duplicate versions" in duplicate.stderr

print("PASS migration ledger release gate with matching, missing, untracked and malformed histories")
