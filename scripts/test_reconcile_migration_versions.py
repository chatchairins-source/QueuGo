"""Offline, synthetic regression coverage for read-only migration reconciliation."""
import pathlib
import tempfile
import unittest
from reconcile_migration_versions import reconcile


class MigrationReconciliationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)

    def source(self, name):
        (self.root / name).write_text("select 1;", encoding="utf-8")

    def test_shared_and_missing_versions_are_sorted_and_not_equivalence_proof(self):
        self.source("20260101000002_beta.sql")
        self.source("20260101000001_alpha.sql")
        report = reconcile({"migrations": [
            {"version": "20260101000003", "name": "gamma"},
            {"version": "20260101000001", "name": "alpha"}]}, self.root)
        self.assertEqual((report["production_count"], report["source_count"], report["shared_count"]), (2, 2, 1))
        self.assertEqual(report["production_only"], ["20260101000003"])
        self.assertEqual(report["source_only"], ["20260101000002"])
        self.assertIs(report["content_equivalence_verified"], False)

    def test_mismatched_names_and_invalid_files_reported(self):
        self.source("20260101000000_local.sql")
        self.source("bad.sql")
        report = reconcile([{"version": "20260101000000", "name": "remote"}], self.root)
        self.assertEqual(report["shared_name_mismatches"], [{
            "version": "20260101000000", "production_name": "remote", "source_name": "local"}])
        self.assertEqual(report["unrecognized_source_files"], ["bad.sql"])

    def test_duplicate_production_version_rejected(self):
        with self.assertRaisesRegex(ValueError, "duplicate Production version"):
            reconcile([{"version": "20260101000000"}, {"version": "20260101000000"}], self.root)

    def test_duplicate_source_version_rejected(self):
        self.source("20260101000000_one.sql")
        self.source("20260101000000_two.sql")
        with self.assertRaisesRegex(ValueError, "duplicate source version"):
            reconcile([], self.root)

    def test_invalid_history_rejected(self):
        for history in ({}, None, [{"version": 123}], [{"version": "bad"}], [None]):
            with self.subTest(history=history), self.assertRaises(ValueError):
                reconcile(history, self.root)

    def test_no_content_equivalence_claim_even_with_matching_versions(self):
        self.source("20260101000000_same.sql")
        report = reconcile([{"version": "20260101000000", "name": "same"}], self.root)
        self.assertEqual(report["shared_count"], 1)
        self.assertIs(report["content_equivalence_verified"], False)


if __name__ == "__main__":
    unittest.main()
