"""Exercise the release gate against isolated migration ledgers."""

from pathlib import Path
import json
import hashlib
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
    metadata = root / "metadata.tsv"

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

    def verify_metadata(*rows: str) -> subprocess.CompletedProcess[str]:
        metadata.write_text("\n".join(rows) + "\n")
        return subprocess.run(
            [sys.executable, str(script), str(ledger), "--root", str(root),
             "--metadata", str(metadata)],
            capture_output=True, text=True, check=False,
        )

    base_hash = hashlib.md5(b"select 1;\n").hexdigest()
    orders_hash = hashlib.md5(b"select 2;\n").hexdigest()
    ledger.write_text("20261001000000\n20261003000000\n")
    rows = (f"20261001000000\tbase\t{base_hash}",
            f"20261003000000\torders\t{orders_hash}")
    equivalent = verify_metadata(*rows)
    assert equivalent.returncode == 1
    assert "20261002000000 -> 20261003000000" in equivalent.stdout
    assert "Missing in Production (1): 20261002000000" in equivalent.stdout

    mismatch = verify_metadata(rows[0], f"20261003000000\torders\t{base_hash}")
    assert mismatch.returncode == 1 and "byte-identical SQL under another deployed version (0)" in mismatch.stdout

    missing_metadata = verify_metadata(rows[0])
    assert missing_metadata.returncode == 1 and "does not match the ledger" in missing_metadata.stderr

    malformed_metadata = verify_metadata(rows[0], "20261003000000\torders\tnot-a-digest")
    assert malformed_metadata.returncode == 1 and "invalid or duplicate" in malformed_metadata.stderr

    duplicate_metadata = verify_metadata(rows[0], rows[0])
    assert duplicate_metadata.returncode == 1 and "invalid or duplicate" in duplicate_metadata.stderr

    # A nameless migration must fail cleanly, not crash during equivalence diagnostics.
    nameless = migrations / "20261004000000.sql"
    nameless.write_text("select 4;\\n")
    rejected = verify("20261001000000", "20261002000000")
    assert rejected.returncode == 1 and "invalid migration filename" in rejected.stderr
    nameless.unlink()

    # Duplicate migration versions must be rejected even with distinct filenames.
    duplicate_file = migrations / "20261001000000_duplicate.sql"
    duplicate_file.write_text("select 5;\\n")
    duplicate_version = verify("20261001000000", "20261002000000")
    assert duplicate_version.returncode == 1 and "duplicate migration version" in duplicate_version.stderr
    duplicate_file.unlink()

    # The JSON report is diagnostic: mismatches must still fail closed.
    report = root / "reconciliation.json"
    ledger.write_text("20261001000000\\n")
    generated = subprocess.run(
        [sys.executable, str(script), str(ledger), "--root", str(root),
         "--report", str(report)],
        capture_output=True, text=True, check=False,
    )
    assert generated.returncode == 1 and report.is_file()
    details = json.loads(report.read_text())
    assert details["reconciled"] is False
    assert details["missing_in_production"] == ["20261002000000"]
    assert details["untracked_in_production"] == []
    assert details["checked_in_count"] == 2 and details["production_count"] == 1

    ledger.write_text("20261001000000\\n20261002000000\\n")
    exact = subprocess.run(
        [sys.executable, str(script), str(ledger), "--root", str(root),
         "--report", str(report)],
        capture_output=True, text=True, check=False,
    )
    assert exact.returncode == 0
    assert json.loads(report.read_text())["reconciled"] is True

print("PASS migration ledger gate and read-only SQL equivalence diagnostics")
