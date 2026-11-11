"""Exercise the read-only Production SECURITY DEFINER release gate."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile


root = Path(__file__).resolve().parents[1]
script = root / "ops" / "verify-production-security-definers.py"
workflow = (root / ".github" / "workflows" / "native-release-certification.yml").read_text()
assert "Require reviewed Production SECURITY DEFINER inventory" in workflow
assert "pg_catalog.pg_proc" in workflow and "p.prosecdef" in workflow
assert "pg_catalog.oidvectortypes(p.proargtypes)" in workflow
assert "has_function_privilege('anon'" in workflow
assert "has_function_privilege('authenticated'" in workflow
assert "pg_catalog.pg_policy" in workflow and "rls_no_policy_with_crud_grants" in workflow
assert "python3 ops/verify-production-security-definers.py" in workflow

anon_review = json.loads((root / "docs/security-anon-definer-allowlist.json").read_text())
auth_review = json.loads((root / "docs/security-auth-definer-review.json").read_text())
expected_anon = sorted(rule["signature"] for rule in anon_review["rules"])
expected_auth = auth_review["authenticated_security_definer_total"]

with tempfile.TemporaryDirectory() as directory:
    inventory_file = Path(directory) / "production-security-definers.json"

    def verify(inventory: object) -> subprocess.CompletedProcess[str]:
        inventory_file.write_text(json.dumps(inventory), encoding="utf-8")
        return subprocess.run(
            [sys.executable, str(script), str(inventory_file)],
            capture_output=True, text=True, check=False,
        )

    safe = verify({
        "anon_signatures": expected_anon,
        "authenticated_security_definer_count": expected_auth,
        "unpinned_search_path_count": 0,
        "rls_no_policy_with_crud_grants": 0,
    })
    assert safe.returncode == 0 and "matches reviewed source inventory" in safe.stdout

    extra_anon = verify({
        "anon_signatures": expected_anon + ["queuego_ride_config()"],
        "authenticated_security_definer_count": expected_auth,
        "unpinned_search_path_count": 0,
        "rls_no_policy_with_crud_grants": 0,
    })
    assert extra_anon.returncode == 1 and "queuego_ride_config()" in extra_anon.stdout
    assert "differ from the reviewed allowlist" in extra_anon.stderr

    changed_auth_inventory = verify({
        "anon_signatures": expected_anon,
        "authenticated_security_definer_count": expected_auth + 1,
        "unpinned_search_path_count": 0,
        "rls_no_policy_with_crud_grants": 0,
    })
    assert changed_auth_inventory.returncode == 1 and "count differs" in changed_auth_inventory.stderr

    unpinned = verify({
        "anon_signatures": expected_anon,
        "authenticated_security_definer_count": expected_auth,
        "unpinned_search_path_count": 1,
        "rls_no_policy_with_crud_grants": 0,
    })
    assert unpinned.returncode == 1 and "pin search_path" in unpinned.stderr

    missing_policy = verify({
        "anon_signatures": expected_anon,
        "authenticated_security_definer_count": expected_auth,
        "unpinned_search_path_count": 0,
        "rls_no_policy_with_crud_grants": 1,
    })
    assert missing_policy.returncode == 1 and "applicable policy" in missing_policy.stderr

    missing = verify({})
    assert missing.returncode == 1 and "anon_signatures" in missing.stderr

    duplicate = verify({
        "anon_signatures": expected_anon + expected_anon[:1],
        "authenticated_security_definer_count": expected_auth,
        "unpinned_search_path_count": 0,
        "rls_no_policy_with_crud_grants": 0,
    })
    assert duplicate.returncode == 1 and "duplicates" in duplicate.stderr

    inventory_file.write_text("not json", encoding="utf-8")
    malformed = subprocess.run(
        [sys.executable, str(script), str(inventory_file)],
        capture_output=True, text=True, check=False,
    )
    assert malformed.returncode == 1 and "malformed" in malformed.stderr

print("PASS fail-closed Production SECURITY DEFINER inventory gate")
