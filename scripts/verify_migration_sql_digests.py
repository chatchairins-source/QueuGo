#!/usr/bin/env python3
"""Fail-closed comparison of local migration SQL against independently exported SQL digests.

Input manifest format: {"migrations":[{"version":"YYYYMMDDHHMMSS","sha256":"64 lowercase hex"}]}.
The digests MUST come from independently verified Production migration SQL bodies,
not local filenames, local SQL, or Supabase migration-history names alone.
Never connects to or writes to Production. Byte-for-byte comparison (no normalization).
"""
import argparse
import hashlib
import json
import pathlib
import re
import tempfile

FILE_RE = re.compile(r"^(\d{14})_.+\.sql$")
HASH_RE = re.compile(r"^[0-9a-f]{64}$")


def verify(manifest, source):
    entries = manifest.get("migrations") if isinstance(manifest, dict) else None
    if not isinstance(entries, list) or not entries:
        raise ValueError("nonempty independently exported migrations manifest required")
    expected = {}
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError("invalid manifest entry")
        version, digest = entry.get("version"), entry.get("sha256")
        if not isinstance(version, str) or not re.fullmatch(r"\d{14}", version):
            raise ValueError("invalid version")
        if not isinstance(digest, str) or not HASH_RE.fullmatch(digest):
            raise ValueError("missing or invalid SQL sha256 for " + version)
        if version in expected:
            raise ValueError("duplicate version " + version)
        expected[version] = digest
    actual = {}
    invalid = []
    for path in sorted(pathlib.Path(source).glob("*.sql")):
        match = FILE_RE.fullmatch(path.name)
        if not match:
            invalid.append(path.name)
            continue
        version = match.group(1)
        if version in actual:
            raise ValueError("duplicate source version " + version)
        actual[version] = hashlib.sha256(path.read_bytes()).hexdigest()
    shared = expected.keys() & actual.keys()
    mismatches = sorted(v for v in shared if expected[v] != actual[v])
    missing = sorted(expected.keys() - actual.keys())
    extra = sorted(actual.keys() - expected.keys())
    passed = not (mismatches or missing or extra or invalid)
    return {"verified": passed, "expected_count": len(expected),
            "source_count": len(actual), "matched_count": len(shared) - len(mismatches),
            "digest_mismatches": mismatches, "production_only": missing,
            "source_only": extra, "invalid_source_files": invalid,
            "warning": "Requires independent Production SQL-body digests; history versions alone cannot establish content parity."}


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        p = root / "20260101000000_alpha.sql"
        p.write_bytes(b"select 1;\n")
        digest = hashlib.sha256(p.read_bytes()).hexdigest()
        manifest = {"migrations": [{"version": "20260101000000", "sha256": digest}]}
        assert verify(manifest, root)["verified"]
        p.write_bytes(b"select 2;\n")
        assert verify(manifest, root)["digest_mismatches"] == ["20260101000000"]
        assert not verify(manifest, root)["verified"]
        # Fail closed when the independently exported manifest is incomplete.
        p.write_bytes(b"select 1;\n")
        extra = root / "20260101000001_beta.sql"
        extra.write_bytes(b"select 3;\n")
        result = verify(manifest, root)
        assert result["source_only"] == ["20260101000001"]
        assert not result["verified"]
        # Fail closed when the repository lacks a Production migration.
        extra.unlink()
        missing_manifest = {"migrations": manifest["migrations"] + [
            {"version": "20260101000002", "sha256": digest}]}
        result = verify(missing_manifest, root)
        assert result["production_only"] == ["20260101000002"]
        assert not result["verified"]
        # Reject unversioned SQL rather than silently excluding it.
        stray = root / "unversioned.sql"
        stray.write_bytes(b"select 4;\n")
        result = verify(manifest, root)
        assert result["invalid_source_files"] == ["unversioned.sql"]
        assert not result["verified"]
        stray.unlink()
        for invalid in ({"migrations": [{"version": "20260101000000"}]},
                        {"migrations": []},
                        {"migrations": [manifest["migrations"][0]] * 2}):
            try:
                verify(invalid, root)
            except ValueError:
                pass
            else:
                raise AssertionError("invalid manifest accepted")
    print("content parity synthetic tests passed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=pathlib.Path)
    parser.add_argument("--source", type=pathlib.Path, default=pathlib.Path("supabase/migrations"))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if not args.manifest or not args.source.is_dir():
        parser.error("--manifest and existing --source required")
    result = verify(json.loads(args.manifest.read_text(encoding="utf-8")), args.source)
    print(json.dumps(result, indent=2))
    if not result["verified"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
