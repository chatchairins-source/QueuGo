#!/usr/bin/env python3
"""Compare checked-in migration versions with a read-only Production ledger dump."""

import argparse
from pathlib import Path
import re
import sys


VERSION = re.compile(r"^\d{14}$")


def checked_in_versions(root: Path) -> set[str]:
    versions: set[str] = set()
    for path in (root / "supabase" / "migrations").glob("*.sql"):
        version = path.name.split("_", 1)[0]
        if not VERSION.fullmatch(version):
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


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ledger", type=Path, help="psql output: one version per line")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        checked_in = checked_in_versions(args.root)
        deployed = deployed_versions(args.ledger)
    except (OSError, ValueError) as error:
        print(f"::error::Migration ledger verification failed: {error}", file=sys.stderr)
        return 1
    missing = sorted(checked_in - deployed)
    untracked = sorted(deployed - checked_in)
    print(f"Checked-in migrations: {len(checked_in)}; Production ledger: {len(deployed)}")
    print(f"Missing in Production ({len(missing)}): {', '.join(missing) or 'none'}")
    print(f"Untracked Production versions ({len(untracked)}): {', '.join(untracked) or 'none'}")
    if missing or untracked:
        print("::error::Reconcile migration history and schema before release certification", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
