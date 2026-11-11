"""Exercise the read-only Production public-table access release gate."""

from pathlib import Path
import subprocess
import sys
import tempfile


root = Path(__file__).resolve().parents[1]
script = root / "ops" / "verify-public-table-access.py"
workflow = (root / ".github" / "workflows" / "native-release-certification.yml").read_text()
assert "Require safe Production public table access" in workflow
assert "pg_catalog.pg_class" in workflow and "not c.relrowsecurity" in workflow
assert "pg_catalog.pg_policy" in workflow and "has_table_privilege('anon'" in workflow
assert "has_table_privilege('authenticated'" in workflow
assert "'TRUNCATE'" in workflow and "'TRIGGER'" in workflow and "'REFERENCES'" in workflow
assert "'MAINTAIN'" in workflow
assert "pg_catalog.pg_default_acl" in workflow and "pg_catalog.aclexplode" in workflow
normalized_workflow = "".join(workflow.split())
assert "rolnamein('anon','authenticated')" in normalized_workflow
assert "acl.grantee=0" in normalized_workflow
assert "d.defaclrole=(selectr.oidfrompg_catalog.pg_rolesrwherer.rolname='postgres')" in normalized_workflow
assert "python3 ops/verify-public-table-access.py" in workflow

with tempfile.TemporaryDirectory() as directory:
    count_file = Path(directory) / "unsafe-public-table-count.txt"

    def verify(value: str) -> subprocess.CompletedProcess[str]:
        count_file.write_text(value)
        return subprocess.run(
            [sys.executable, str(script), str(count_file)],
            capture_output=True, text=True, check=False,
        )

    safe = verify("0\n")
    assert safe.returncode == 0 and "enable RLS" in safe.stdout

    unsafe = verify("42\n")
    assert unsafe.returncode == 1 and "unsafe access: 42" in unsafe.stdout
    assert "Revoke anon/authenticated" in unsafe.stderr
    assert "default grants" in unsafe.stderr

    empty = verify("\n")
    assert empty.returncode == 1 and "empty or malformed" in empty.stderr

    malformed = verify("0\n1\n")
    assert malformed.returncode == 1 and "empty or malformed" in malformed.stderr

    negative = verify("-1\n")
    assert negative.returncode == 1 and "empty or malformed" in negative.stderr

    count_file.unlink()
    unavailable = subprocess.run(
        [sys.executable, str(script), str(count_file)],
        capture_output=True, text=True, check=False,
    )
    assert unavailable.returncode == 1 and "output unavailable" in unavailable.stderr

print("PASS fail-closed Production public-table access readiness gate")
