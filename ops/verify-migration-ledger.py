#!/usr/bin/env python3
"""Compare checked-in migration versions with a read-only Production ledger dump."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


VERSION = re.compile(r"^\d{14}$")
MD5 = re.compile(r"^[0-9a-f]{32}$")


def checked_in_versions(root: Path) -> set[str]:
    versions: set[str] = set()
    for path in sorted((root / "supabase" / "migrations").glob("*.sql")):
        parts = path.stem.split("_", 1)
        version = parts[0]
        if len(parts) != 2 or not parts[1] or not VERSION.fullmatch(version):
            raise ValueError(f"invalid migration filename: {path.name}")
        if version in versions:
            raise ValueError(f"duplicate migration version: {version}")
        versions.add(version)
    if not versions:
        raise ValueError("no checked-in migrations found")
    return versions


def deployed_versions(path: Path) -> set[str]:
    values = [line.strip() for line in path.read_text().splitlines() if line.strip()]
    if not values or any(not VERSION.fullmatch(value) for value in values):
        raise ValueError("Production migration ledger is empty or contains an invalid version")
    if len(values) != len(set(values)):
        raise ValueError("Production migration ledger contains duplicate versions")
    return set(values)


def equivalent_sql(root: Path, path: Path, deployed: set[str]) -> dict[str, list[str]]:
    """Find byte-identical SQL with the same name under a different version.

    This is diagnostic only: an equivalent does not certify ledger alignment.
    """
    production: dict[tuple[str, str], list[str]] = {}
    seen: set[str] = set()
    for line in path.read_text().splitlines():
        fields = line.split("\t")
        if len(fields) != 3:
            raise ValueError("invalid Production migration metadata row")
        version, name, digest = fields
        if (not VERSION.fullmatch(version) or not name or "\r" in name
                or not MD5.fullmatch(digest) or version in seen):
            raise ValueError("invalid or duplicate Production migration metadata")
        seen.add(version)
        production.setdefault((name, digest), []).append(version)
    if seen != deployed:
        raise ValueError("Production migration metadata does not match the ledger")

    matches: dict[str, list[str]] = {}
    for migration in sorted((root / "supabase" / "migrations").glob("*.sql")):
        version, name = migration.stem.split("_", 1)
        if version in deployed:
            continue
        digest = hashlib.md5(migration.read_bytes()).hexdigest()
        counterparts = [v for v in production.get((name, digest), []) if v != version]
        if counterparts:
            matches[version] = sorted(counterparts)
    return matches


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ledger", type=Path, help="psql output: one version per line")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--metadata", type=Path,
                        help="psql TSV: version, name, MD5 of recorded SQL (diagnostic only)")
    parser.add_argument("--report", type=Path,
                        help="Write machine-readable reconciliation diagnostics without altering Production")
    args = parser.parse_args()
    try:
        checked_in = checked_in_versions(args.root)
        deployed = deployed_versions(args.ledger)
        equivalents = equivalent_sql(args.root, args.metadata, deployed) if args.metadata else {}
    except (OSError, ValueError) as error:
        print(f"::error::Migration ledger verification failed: {error}", file=sys.stderr)
        return 1
    missing = sorted(checked_in - deployed)
    untracked = sorted(deployed - checked_in)
    print(f"Checked-in migrations: {len(checked_in)}; Production ledger: {len(deployed)}")
    print(f"Missing in Production ({len(missing)}): {', '.join(missing) or 'none'}")
    print(f"Untracked Production versions ({len(untracked)}): {', '.join(untracked) or 'none'}")
    if args.metadata:
        print(f"Missing versions with same-name, byte-identical SQL under another deployed version ({len(equivalents)}):")
        for version in sorted(equivalents):
            print(f"  {version} -> {', '.join(equivalents[version])}")
        print("These matches are diagnostic; every version mismatch still blocks certification.")
    if args.report:
        report = {
            "checked_in_count": len(checked_in),
            "production_count": len(deployed),
            "missing_in_production": missing,
            "untracked_in_production": untracked,
            "same_name_sql_equivalents": equivalents,
            "reconciled": not (missing or untracked),
        }
        try:
            args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\\n")
        except OSError as error:
            print(f"::error::Cannot write reconciliation report: {error}", file=sys.stderr)
            return 1
    if missing or untracked:
        print("::error::Reconcile migration history and schema before release certification", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
