#!/usr/bin/env python3
"""Stage missing historical Production migration SQL for manual review ONLY.

Accept a PRIVATE, operator-exported JSON file:
  {"migrations":[{"version":"YYYYMMDDHHMMSS","name":"safe_name",
                  "statements":["exact executed SQL text"]}, ...]}
The source of truth is supabase_migrations.schema_migrations, not guessed SQL.
SQL may contain secrets. Never place the export or staging folder in a Git repo.
Never run staged SQL, mutate Production, or mark migrations as applied.
All source SQL comparisons are byte-exact UTF-8 SHA-256 (no normalization).
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile

VERSION = re.compile(r"[0-9]{14}\Z")
NAME = re.compile(r"[a-z][a-z0-9_]*\Z")
FILE = re.compile(r"([0-9]{14})_([a-z][a-z0-9_]*)\.sql\Z")


def sha(value):
    return hashlib.sha256(value).hexdigest()


def check_history(history):
    if not isinstance(history, dict) or not isinstance(history.get("migrations"), list):
        raise ValueError("history must contain a migrations array")
    if not history["migrations"]:
        raise ValueError("refuse empty/incomplete history export")
    result = {}
    for row in history["migrations"]:
        if not isinstance(row, dict):
            raise ValueError("invalid history row")
        ver, name, statements = row.get("version"), row.get("name"), row.get("statements")
        if not isinstance(ver, str) or not VERSION.fullmatch(ver):
            raise ValueError("invalid history version")
        if not isinstance(name, str) or not NAME.fullmatch(name):
            raise ValueError("unsafe migration name for version " + ver)
        if ver in result:
            raise ValueError("duplicate Production version " + ver)
        if not isinstance(statements, list) or len(statements) != 1:
            raise ValueError("expected one exact stored SQL body for version " + ver)
        if not isinstance(statements[0], str) or not statements[0].strip():
            raise ValueError("missing SQL body for version " + ver)
        result[ver] = (name, statements[0].encode("utf-8"))
    return result


def stage(history, source, output, repo_root, expected_count):
    source, output, repo_root = source.resolve(), output.resolve(), repo_root.resolve()
    if not source.is_dir() or source.is_symlink():
        raise ValueError("source migration directory missing or unsafe")
    if output.is_relative_to(repo_root):
        raise ValueError("PRIVATE SQL staging must be OUTSIDE the repository")
    if output.exists():
        raise ValueError("staging path already exists; never overwrite")
    records = check_history(history)
    if not isinstance(expected_count, int) or expected_count <= 0 or len(records) != expected_count:
        raise ValueError("history row count differs from independently checked Production count")
    actual = {}
    for file in sorted(source.glob("*.sql")):
        if not file.is_file() or file.is_symlink():
            raise ValueError("unsafe source migration file")
        match = FILE.fullmatch(file.name)
        if match is None:
            raise ValueError("unversioned or invalid source migration filename")
        ver, name = match.groups()
        if ver in actual:
            raise ValueError("duplicate source version " + ver)
        actual[ver] = (name, sha(file.read_bytes()))
    matched, different = [], []
    for ver in sorted(set(records) & set(actual)):
        name, body = records[ver]
        old_name, old_hash = actual[ver]
        if old_name == name and old_hash == sha(body):
            matched.append(ver)
        else:
            different.append(ver)
    absent = sorted(set(records) - set(actual))
    uninstalled = sorted(set(actual) - set(records))
    # Deliberately stage *only* Production-history SQL absent from the source.
    # A mismatch is never overwritten or assumed equivalent.
    previous_umask = os.umask(0o077)
    try:
        output.mkdir(parents=True, mode=0o700)
        for ver in absent:
            name, body = records[ver]
            target = output / (ver + "_" + name + ".sql")
            with target.open("xb") as dest:
                dest.write(body)
        summary = {
            "scope": "review-only; no SQL execution, Production write or migration history update",
            "production_versions": len(records),
            "source_versions": len(actual),
            "matching_exact_bytes": len(matched),
            "production_only_staged": len(absent),
            "source_only_unapplied_or_different": uninstalled,
            "same_version_name_or_digest_conflicts": different,
            "production_only_files": [
                {"file": ver + "_" + records[ver][0] + ".sql",
                 "sha256": sha(records[ver][1])} for ver in absent
            ],
            "ready_for_deployment": False,
        }
        with (output / "REVIEW_ONLY_MANIFEST.json").open("x", encoding="utf-8") as manifest:
            json.dump(summary, manifest, indent=2, ensure_ascii=False)
            manifest.write("\n")
    finally:
        os.umask(previous_umask)
    return summary


def self_test():
    with tempfile.TemporaryDirectory() as root:
        base = Path(root)
        repo = base / "repo"
        source = repo / "supabase" / "migrations"
        source.mkdir(parents=True)
        existing = source / "20260101000000_existing.sql"
        existing.write_bytes(b"select 1;\n")
        (source / "20260101000002_future.sql").write_bytes(b"select 3;\n")
        data = {"migrations": [
            {"version": "20260101000000", "name": "existing", "statements": ["select 1;\n"]},
            {"version": "20260101000001", "name": "missing", "statements": ["select 2;\n"]},
        ]}
        private = base / "staging"
        result = stage(data, source, private, repo, 2)
        assert result["production_versions"] == 2
        assert result["matching_exact_bytes"] == 1
        assert result["production_only_staged"] == 1
        assert result["source_only_unapplied_or_different"] == ["20260101000002"]
        assert (private / "20260101000001_missing.sql").read_bytes() == b"select 2;\n"
        assert existing.read_bytes() == b"select 1;\n"
        assert not (private / "20260101000000_existing.sql").exists()
        assert result["ready_for_deployment"] is False
        manifest = (private / "REVIEW_ONLY_MANIFEST.json").read_text()
        assert "select 2" not in manifest, "manifest must not embed SQL"
        if os.name == "posix":
            assert private.stat().st_mode & 0o077 == 0, "private directory permission"
        # An incomplete but otherwise valid ledger export must fail closed.
        try:
            stage({"migrations": data["migrations"][:1]}, source, base / "partial", repo, 2)
        except ValueError:
            pass
        else:
            raise AssertionError("partial Production history export was accepted")
        for tampered in [
            {"migrations": data["migrations"] * 2},
            {"migrations": [{"version": "20260101000004", "name": "../bad", "statements": ["x"]}]},
            {"migrations": [{"version": "20260101000004", "name": "test", "statements": []}]},
            {"migrations": []},
        ]:
            try:
                stage(tampered, source, base / ("blocked" + str(len(tampered["migrations"]))), repo, 2)
            except ValueError:
                pass
            else:
                raise AssertionError("malformed history was accepted")
        try:
            stage(data, source, repo / "unsafe", repo, 2)
        except ValueError:
            pass
        else:
            raise AssertionError("SQL staging inside repo was allowed")
        try:
            stage(data, source, private, repo, 2)
        except ValueError:
            pass
        else:
            raise AssertionError("existing staging folder was overwritten")
        existing.write_bytes(b"select changed;\n")
        mismatch = stage(data, source, base / "different", repo, 2)
        assert mismatch["same_version_name_or_digest_conflicts"] == ["20260101000000"]
        assert mismatch["matching_exact_bytes"] == 0
    print("PASS: private review-only migration reconstruction, exact bytes, no overwrites, negative tests")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--history", type=Path)
    p.add_argument("--source", type=Path, default=Path("supabase/migrations"))
    p.add_argument("--output", type=Path)
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--expected-count", type=int, help="independently verified live Production ledger count")
    args = p.parse_args()
    if args.self_test:
        self_test()
        return
    if args.history is None or args.output is None or args.expected_count is None:
        p.error("--history, --output and --expected-count required unless --self-test")
    repo = Path(__file__).resolve().parents[1]
    history = json.loads(args.history.read_text(encoding="utf-8"))
    report = stage(history, args.source, args.output, repo, args.expected_count)
    # Output counts only: never emit SQL, credentials, or paths into CI logs.
    print(json.dumps({
        k: v for k, v in report.items()
        if k in ("production_versions", "source_versions", "matching_exact_bytes",
                 "production_only_staged", "ready_for_deployment")
    }))
    if report["source_only_unapplied_or_different"] or report["same_version_name_or_digest_conflicts"]:
        raise SystemExit("REVIEW REQUIRED: history/source migration parity is not established")


if __name__ == "__main__":
    main()
