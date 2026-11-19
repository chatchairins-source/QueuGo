#!/usr/bin/env python3
"""Isolated PostgreSQL checkout replay authorization regression.

Requires a disposable database with QueueGo migrations and seeded guest checkout
fixtures. Never points at Production. Runs in a transaction that always rolls back.
Environment: QUEUEGO_DISPOSABLE_DB=1, QUEUEGO_TEST_DATABASE_URL,
QUEUEGO_TEST_SESSION, QUEUEGO_TEST_DEVICE_KEY, QUEUEGO_TEST_REQUEST,
QUEUEGO_TEST_ITEMS_JSON, QUEUEGO_TEST_LAT, QUEUEGO_TEST_LNG.
Fixture must be an ACTIVE session with an existing successful checkout for the
request/items, and its order must exist. This script revokes the session within
the same rollback-only transaction and verifies that a replay cannot succeed.
"""
import json
import os
from urllib.parse import urlparse
import unittest
from uuid import UUID


class RevokedReplayDatabaseTest(unittest.TestCase):
    def test_revoked_session_cannot_replay_existing_request(self):
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.skipTest("Disposable database explicitly required")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        host = (parsed.hostname or "").lower()
        if parsed.scheme not in ("postgres", "postgresql") or not host:
            self.fail("Expected explicit PostgreSQL connection URL")
        if "pkypiqhlrmzocysgeqew" in url.lower():
            self.fail("Production project reference is forbidden")
        # An explicit disposable flag is not enough to trust a remote host.
        # Restrict to loopback only; run against local ephemeral PostgreSQL.
        if host not in ("localhost", "127.0.0.1", "::1"):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        try:
            import psycopg
        except ImportError:
            self.fail("psycopg is required for integration tests")

        session = str(UUID(os.environ["QUEUEGO_TEST_SESSION"]))
        device = str(UUID(os.environ["QUEUEGO_TEST_DEVICE_KEY"]))
        request = str(UUID(os.environ["QUEUEGO_TEST_REQUEST"]))
        items = json.dumps(json.loads(os.environ["QUEUEGO_TEST_ITEMS_JSON"]))
        lat = float(os.environ["QUEUEGO_TEST_LAT"])
        lng = float(os.environ["QUEUEGO_TEST_LNG"])
        with psycopg.connect(url, autocommit=False) as conn:
            try:
                with conn.cursor() as cur:
                    cur.execute("select current_database(), current_user")
                    database, user = cur.fetchone()
                    if database in ("postgres", "template0", "template1"):
                        self.fail("Use a dedicated disposable test database")
                    cur.execute(
                        "select 1 from public.qg_table_requests where session_id=%s and request_id=%s",
                        (session, request),
                    )
                    self.assertIsNotNone(cur.fetchone(), "Fixture needs existing checkout")
                    # Verify fixture is a successful replay before testing revocation.
                    cur.execute(
                        "select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                        (session, device, request, items, lat, lng),
                    )
                    replay = cur.fetchone()[0]
                    self.assertTrue(replay["duplicate"], "Fixture must be replayable")
                    self.assertEqual(replay["reason"], "same_request")
                    cur.execute(
                        "update public.qg_table_sessions set revoked_at=now() where id=%s and revoked_at is null",
                        (session,),
                    )
                    self.assertEqual(cur.rowcount, 1, "Fixture needs active session")
                    # The exception must originate from the function, not from
                    # an invalid fixture or a missing PostgreSQL dependency.
                    with self.assertRaises(psycopg.Error) as caught:
                        cur.execute(
                            "select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                            (session, device, request, items, lat, lng),
                        )
                    self.assertIn("หมดอายุ", str(caught.exception))
            finally:
                conn.rollback()


if __name__ == "__main__":
    unittest.main()
