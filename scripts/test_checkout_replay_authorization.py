#!/usr/bin/env python3
"""Fail-closed source regression for guest checkout replay authorization.

This is a structural regression only. Isolated PostgreSQL integration tests are
still required for revoked/expired sessions, concurrency, and settlement.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MIGRATION = ROOT / "supabase/migrations/20261118222500_guard_table_checkout_replay_authorization.sql"

class CheckoutReplayAuthorizationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = MIGRATION.read_text(encoding="utf-8")
        cls.compact = re.sub(r"\s+", " ", cls.sql.lower())

    def test_guard_precedes_first_request_replay_lookup(self):
        guard = self.compact.index("if v.revoked_at is not null or now()>=v.expires_at then")
        replay = self.compact.index("from public.qg_table_requests")
        self.assertLess(guard, replay)

    def test_guard_follows_device_bound_session_lock(self):
        lock = self.compact.index("for update;")
        guard = self.compact.index("if v.revoked_at is not null or now()>=v.expires_at then")
        self.assertLess(lock, guard)
        self.assertIn("device_hash=encode(extensions.digest(p_device_key::text,'sha256'),'hex')", self.compact)

    def test_replay_idempotency_and_payload_mismatch_still_present(self):
        self.assertIn("if v_old.payload_hash<>v_hash then", self.compact)
        self.assertIn("'reason','same_request'", self.compact)
        self.assertIn("'reason','recent_same_cart'", self.compact)
        self.assertIn("for update;", self.compact)

    def test_no_privilege_expansion(self):
        self.assertNotRegex(self.compact, r"\bgrant\s+")
        self.assertNotRegex(self.compact, r"\balter\s+table\s+.*disable\s+row\s+level\s+security")

if __name__ == "__main__":
    unittest.main()
