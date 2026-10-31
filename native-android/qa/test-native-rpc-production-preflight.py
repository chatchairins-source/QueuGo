#!/usr/bin/env python3
"""No-network tests for the read-only Native RPC production preflight."""
import importlib.util
from pathlib import Path
import unittest

TARGET = Path(__file__).with_name("native-rpc-production-preflight.py")
SPEC = importlib.util.spec_from_file_location("qg_rpc_preflight", TARGET)
assert SPEC and SPEC.loader
preflight = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(preflight)


class ReadOnlyProductionRpcPreflightTest(unittest.TestCase):
    def test_native_call_extraction(self):
        names = preflight.required_rpcs()
        for expected in (
            "queuego_request_ride",
            "qg_express_pickup_with_photo",
            "qg_express_complete_with_photo",
            "qg_rider_proof_committed",
            "qg_rider_submit_public_license",
            "qg_customer_cancel_order",
        ):
            self.assertIn(expected, names)
        self.assertGreaterEqual(len(names), 85)

    def test_sql_only_reads_catalog(self):
        text = preflight.sql_query(["qg_b", "qg_a", "qg_b"])
        self.assertEqual(text.count("('qg_b')"), 1)
        self.assertIn("pg_catalog.pg_proc", text)
        self.assertIn("pg_catalog.pg_namespace", text)
        self.assertIn("has_function_privilege", text)
        self.assertNotRegex(text.lower(), r"\b(insert|delete|update|alter|drop|create|call)\b")

    def test_public_namespace_filter_preserves_missing_rpc_rows(self):
        sql = preflight.sql_query(["qg_only_in_private_schema"])
        self.assertIn("where n.nspname = 'public'", sql)
        self.assertIn(") p on p.proname = r.name", sql)
        self.assertNotIn("where p.oid is null or n.oid is not null", sql)
        ok, evidence = preflight.verify_rows(
            ["qg_only_in_private_schema"],
            [{"name": "qg_only_in_private_schema", "overloads": "0",
              "authenticated_execute": "f", "anonymous_execute": "f"}],
        )
        self.assertFalse(ok)
        self.assertEqual(evidence["missing"], ["qg_only_in_private_schema"])

    def test_unknown_or_missing_catalog_denies_release(self):
        ok, evidence = preflight.verify_rows(
            ["queuego_request_ride", "qg_rider_proof_committed"],
            [{"name": "queuego_request_ride", "overloads": "1",
              "authenticated_execute": "t", "anonymous_execute": "f"}],
        )
        self.assertFalse(ok)
        self.assertEqual(evidence["status"], "BLOCKED")
        self.assertEqual(evidence["missing"], ["qg_rider_proof_committed"])

    def test_ungranted_execution_denies_release(self):
        ok, evidence = preflight.verify_rows(
            ["qg_private_only"],
            [{"name": "qg_private_only", "overloads": "1",
              "authenticated_execute": "f", "anonymous_execute": "f"}],
        )
        self.assertFalse(ok)
        self.assertEqual(evidence["authenticated_execute_missing"], ["qg_private_only"])

    def test_expected_public_rpcs_pass(self):
        ok, evidence = preflight.verify_rows(
            ["queuego_ride_state"],
            [{"name": "queuego_ride_state", "overloads": "2",
              "authenticated_execute": "t", "anonymous_execute": "f"}],
        )
        self.assertTrue(ok)
        self.assertEqual(evidence["checked"], 1)

    def test_sql_rejects_injection(self):
        with self.assertRaises(ValueError):
            preflight.sql_query(["qg_safe'); drop table users;--"])
        with self.assertRaises(ValueError):
            preflight.sql_query([])


if __name__ == "__main__":
    unittest.main()
