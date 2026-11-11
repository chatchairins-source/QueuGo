#!/usr/bin/env python3
"""Fail closed when Production SECURITY DEFINER exposure differs from review."""

import argparse
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]


def read_json(path: Path, label: str) -> object:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(f"{label} is unavailable or malformed: {error}") from error


def count(value: object, key: str) -> int:
    result = value.get(key) if isinstance(value, dict) else None
    if isinstance(result, bool) or not isinstance(result, int) or result < 0:
        raise ValueError(f"{key} must be a nonnegative integer")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory_file", type=Path, help="read-only psql JSON catalog inventory")
    args = parser.parse_args()

    try:
        inventory = read_json(args.inventory_file, "Production inventory")
        anon_review = read_json(ROOT / "docs/security-anon-definer-allowlist.json", "anon allowlist")
        auth_review = read_json(ROOT / "docs/security-auth-definer-review.json", "authenticated review")

        reviewed_signatures = anon_review.get("rules") if isinstance(anon_review, dict) else None
        if not isinstance(reviewed_signatures, list):
            raise ValueError("anonymous review rules must be an array")
        expected_anon = [
            entry.get("signature")
            for entry in reviewed_signatures
            if isinstance(entry, dict) and isinstance(entry.get("signature"), str)
        ]
        if len(expected_anon) != len(reviewed_signatures) or len(set(expected_anon)) != len(expected_anon):
            raise ValueError("anonymous review signatures are missing or duplicated")

        expected_auth = count(auth_review, "authenticated_security_definer_total")
        actual_anon = inventory.get("anon_signatures") if isinstance(inventory, dict) else None
        if not isinstance(actual_anon, list) or any(not isinstance(item, str) for item in actual_anon):
            raise ValueError("anon_signatures must be an array of strings")
        if len(set(actual_anon)) != len(actual_anon):
            raise ValueError("Production anon_signatures contains duplicates")

        actual_auth = count(inventory, "authenticated_security_definer_count")
        unpinned_search_path = count(inventory, "unpinned_search_path_count")
        rls_exposed_without_policy = count(inventory, "rls_no_policy_with_crud_grants")
    except ValueError as error:
        print(f"::error::Production SECURITY DEFINER inventory unavailable or invalid: {error}", file=sys.stderr)
        return 1

    expected_anon = sorted(expected_anon)
    actual_anon = sorted(actual_anon)
    extra = sorted(set(actual_anon) - set(expected_anon))
    missing = sorted(set(expected_anon) - set(actual_anon))
    print(json.dumps({
        "reviewed_anon_count": len(expected_anon),
        "production_anon_count": len(actual_anon),
        "reviewed_authenticated_count": expected_auth,
        "production_authenticated_count": actual_auth,
        "unpinned_search_path_count": unpinned_search_path,
        "rls_no_policy_with_crud_grants": rls_exposed_without_policy,
        "unreviewed_anon_signatures": extra,
        "missing_anon_signatures": missing,
    }, sort_keys=True))

    failures = []
    if actual_anon != expected_anon:
        failures.append("Production anonymous SECURITY DEFINER signatures differ from the reviewed allowlist")
    if actual_auth != expected_auth:
        failures.append("Production authenticated SECURITY DEFINER count differs from the reviewed inventory")
    if unpinned_search_path:
        failures.append("Exposed SECURITY DEFINER functions must pin search_path")
    if rls_exposed_without_policy:
        failures.append("RLS-enabled public tables with direct CRUD grants must have an applicable policy")
    for failure in failures:
        print(f"::error::{failure}", file=sys.stderr)
    if failures:
        return 1

    print("Production SECURITY DEFINER exposure matches reviewed source inventory")
    return 0


if __name__ == "__main__":
    sys.exit(main())
