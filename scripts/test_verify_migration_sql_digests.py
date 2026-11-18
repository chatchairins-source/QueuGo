#!/usr/bin/env python3
"""Regression tests for fail-closed migration SQL digest verification.

Synthetic files only. No database credentials, connections or Production writes.
"""
import hashlib
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from verify_migration_sql_digests import verify


class DigestParityRegression(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)
        self.file = self.root / "20260101000000_alpha.sql"
        self.file.write_bytes(b"SELECT 1;\n")
        self.entry = {"version": "20260101000000",
                      "sha256": hashlib.sha256(self.file.read_bytes()).hexdigest()}

    def check(self, entries):
        return verify({"migrations": entries}, self.root)

    def test_exact_bytes_pass(self):
        self.assertTrue(self.check([self.entry])["verified"])

    def test_newline_change_fails(self):
        self.file.write_bytes(b"SELECT 1;\r\n")
        self.assertEqual(self.check([self.entry])["digest_mismatches"],
                         ["20260101000000"])

    def test_manifest_missing_digest_rejected(self):
        with self.assertRaises(ValueError):
            self.check([{"version": self.entry["version"]}])

    def test_duplicate_manifest_version_rejected(self):
        with self.assertRaises(ValueError):
            self.check([self.entry, self.entry])

    def test_extra_source_migration_fails(self):
        (self.root / "20260101000001_beta.sql").write_bytes(b"SELECT 2;")
        self.assertEqual(self.check([self.entry])["source_only"],
                         ["20260101000001"])

    def test_missing_source_migration_fails(self):
        self.file.unlink()
        self.assertEqual(self.check([self.entry])["production_only"],
                         ["20260101000000"])

    def test_duplicate_source_version_rejected(self):
        (self.root / "20260101000000_other.sql").write_bytes(b"SELECT 2;")
        with self.assertRaises(ValueError):
            self.check([self.entry])

    def test_unversioned_sql_fails(self):
        (self.root / "manual.sql").write_bytes(b"SELECT 2;")
        self.assertEqual(self.check([self.entry])["invalid_source_files"],
                         ["manual.sql"])

    def test_uppercase_digest_rejected(self):
        with self.assertRaises(ValueError):
            self.check([{**self.entry, "sha256": "A" * 64}])


if __name__ == "__main__":
    unittest.main()
