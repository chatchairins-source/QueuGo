#!/usr/bin/env python3
"""Read-only QueueGo migration version reconciliation.

Usage: python3 scripts/reconcile_migration_versions.py --history history.json --source supabase/migrations
The history JSON must be an exported {"migrations":[{"version":"...","name":"..."}]} object.
Never treats missing history entries as permission to apply migrations to Production.
"""
import argparse
import json
import pathlib
import re
import sys

VERSION = re.compile(r"^(\d{14})_(.+)\.sql$")


def reconcile(history, directory):
    if isinstance(history, dict):
        history = history.get("migrations")
    if not isinstance(history, list):
        raise ValueError("history must contain a migrations array")
    versions = {}
    for entry in history:
        if not isinstance(entry, dict) or not isinstance(entry.get("version"), str):
            raise ValueError("every migration must have a string version")
        version = entry["version"]
        if not re.fullmatch(r"\d{14}", version):
            raise ValueError("invalid migration version: " + version)
        if version in versions:
            raise ValueError("duplicate Production version: " + version)
        versions[version] = entry.get("name")
    source = {}
    invalid = []
    for path in sorted(pathlib.Path(directory).glob("*.sql")):
        match = VERSION.fullmatch(path.name)
        if not match:
            invalid.append(path.name)
            continue
        version = match.group(1)
        if version in source:
            raise ValueError("duplicate source version: " + version)
        source[version] = {"file": path.name, "name": match.group(2)}
    prod, repo = set(versions), set(source)
    shared = prod & repo
    return {
        "production_count": len(prod),
        "source_count": len(repo),
        "shared_count": len(shared),
        "production_only": sorted(prod - repo),
        "source_only": sorted(repo - prod),
        "shared_name_mismatches": [
            {"version": v, "production_name": versions[v], "source_name": source[v]["name"]}
            for v in sorted(shared)
            if versions[v] is not None and versions[v] != source[v]["name"]
        ],
        "unrecognized_source_files": invalid,
        "content_equivalence_verified": False,
        "warning": "Version comparison only. Never auto-apply missing migrations to Production.",
    }


def self_test():
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        (root / "20260101000000_alpha.sql").write_text("select 1;")
        (root / "20260101000001_beta.sql").write_text("select 2;")
        result = reconcile({"migrations": [
            {"version": "20260101000000", "name": "alpha"},
            {"version": "20260101000002", "name": "gamma"}]}, root)
        assert result["shared_count"] == 1
        assert result["production_only"] == ["20260101000002"]
        assert result["source_only"] == ["20260101000001"]
        assert result["content_equivalence_verified"] is False
        try:
            reconcile([{"version": "20260101000000"}, {"version": "20260101000000"}], root)
        except ValueError:
            pass
        else:
            raise AssertionError("duplicate version was accepted")
    print("self-test passed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--history", type=pathlib.Path)
    parser.add_argument("--source", type=pathlib.Path, default=pathlib.Path("supabase/migrations"))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if not args.history or not args.source.is_dir():
        parser.error("--history and an existing --source directory are required")
    report = reconcile(json.loads(args.history.read_text(encoding="utf-8")), args.source)
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
