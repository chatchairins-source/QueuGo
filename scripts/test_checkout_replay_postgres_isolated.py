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
            self.fail("Integration test NOT RUN: set QUEUEGO_DISPOSABLE_DB=1 and supply a loopback test database")
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
                    # Execute RPC as the exposed anon role, not as fixture owner.
                    cur.execute("set local role anon")
                    cur.execute("select current_user, session_user")
                    active_role, login_role = cur.fetchone()
                    self.assertEqual(active_role, "anon", "RPC must execute as anon, not fixture owner")
                    self.assertNotEqual(login_role, "anon", "Fixture connection must retain a privileged setup role")
                    # Verify fixture is a successful replay before testing revocation.
                    cur.execute(
                        "select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                        (session, device, request, items, lat, lng),
                    )
                    replay = cur.fetchone()[0]
                    self.assertTrue(replay["duplicate"], "Fixture must be replayable")
                    self.assertEqual(replay["reason"], "same_request")
                    cur.execute("reset role")
                    cur.execute(
                        "update public.qg_table_sessions set revoked_at=now() where id=%s and revoked_at is null",
                        (session,),
                    )
                    self.assertEqual(cur.rowcount, 1, "Fixture needs active session")
                    # The exception must originate from the function, not from
                    # an invalid fixture or a missing PostgreSQL dependency.
                    # Savepoint permits post-error assertions without masking a failed SQL transaction.
                    cur.execute("set local role anon")
                    cur.execute("select current_user, session_user")
                    active_role, login_role = cur.fetchone()
                    self.assertEqual(active_role, "anon", "RPC must execute as anon, not fixture owner")
                    self.assertNotEqual(login_role, "anon", "Fixture connection must retain a privileged setup role")
                    cur.execute("savepoint rejected_replay")
                    with self.assertRaises(psycopg.Error) as caught:
                        cur.execute(
                            "select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                            (session, device, request, items, lat, lng),
                        )
                    self.assertIn("หมดอายุ", str(caught.exception))
                    cur.execute("rollback to savepoint rejected_replay")
                    cur.execute("reset role")
                    cur.execute(
                        "select count(*) from public.qg_table_requests where session_id=%s and request_id=%s",
                        (session, request),
                    )
                    self.assertEqual(cur.fetchone()[0], 1, "Rejected replay must not create another request")
            finally:
                conn.rollback()


    def test_expired_session_cannot_replay_existing_request(self):
        """A previously successful checkout must not bypass session expiry."""
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.fail("Integration test NOT RUN: set QUEUEGO_DISPOSABLE_DB=1 and supply a loopback test database")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        if parsed.scheme not in ("postgres", "postgresql") or (
            parsed.hostname or ""
        ).lower() not in ("localhost", "127.0.0.1", "::1"):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        if "pkypiqhlrmzocysgeqew" in url.lower():
            self.fail("Production project reference is forbidden")
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
                    cur.execute("select current_database()")
                    self.assertNotIn(
                        cur.fetchone()[0], ("postgres", "template0", "template1"),
                        "Use a dedicated disposable test database",
                    )
                    cur.execute(
                        "select 1 from public.qg_table_requests where session_id=%s and request_id=%s",
                        (session, request),
                    )
                    self.assertIsNotNone(cur.fetchone(), "Fixture needs existing checkout")
                    cur.execute("set local role anon")
                    cur.execute("select current_user, session_user")
                    active_role, login_role = cur.fetchone()
                    self.assertEqual(active_role, "anon", "RPC must execute as anon, not fixture owner")
                    self.assertNotEqual(login_role, "anon", "Fixture connection must retain a privileged setup role")
                    cur.execute(
                        "select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                        (session, device, request, items, lat, lng),
                    )
                    replay = cur.fetchone()[0]
                    self.assertTrue(replay["duplicate"], "Fixture must be replayable")
                    self.assertEqual(replay["reason"], "same_request")
                    cur.execute("reset role")
                    cur.execute(
                        "update public.qg_table_sessions set expires_at=now()-interval '1 second' "
                        "where id=%s and revoked_at is null and expires_at>now()",
                        (session,),
                    )
                    self.assertEqual(cur.rowcount, 1, "Fixture needs unexpired active session")
                    # Savepoint permits post-error assertions without masking a failed SQL transaction.
                    cur.execute("set local role anon")
                    cur.execute("select current_user, session_user")
                    active_role, login_role = cur.fetchone()
                    self.assertEqual(active_role, "anon", "RPC must execute as anon, not fixture owner")
                    self.assertNotEqual(login_role, "anon", "Fixture connection must retain a privileged setup role")
                    cur.execute("savepoint rejected_replay")
                    with self.assertRaises(psycopg.Error) as caught:
                        cur.execute(
                            "select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                            (session, device, request, items, lat, lng),
                        )
                    self.assertIn("หมดอายุ", str(caught.exception))
                    cur.execute("rollback to savepoint rejected_replay")
                    cur.execute("reset role")
                    cur.execute(
                        "select count(*) from public.qg_table_requests where session_id=%s and request_id=%s",
                        (session, request),
                    )
                    self.assertEqual(cur.fetchone()[0], 1, "Rejected replay must not create another request")
            finally:
                conn.rollback()


    def test_null_expiry_session_cannot_replay_existing_request(self):
        """A previously successful checkout must not bypass NULL session expiry."""
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.fail("Integration test NOT RUN: set QUEUEGO_DISPOSABLE_DB=1 and supply a loopback test database")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        if parsed.scheme not in ("postgres", "postgresql") or (
            parsed.hostname or ""
        ).lower() not in ("localhost", "127.0.0.1", "::1"):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        if "pkypiqhlrmzocysgeqew" in url.lower():
            self.fail("Production project reference is forbidden")
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
                    cur.execute("select current_database()")
                    self.assertNotIn(
                        cur.fetchone()[0], ("postgres", "template0", "template1"),
                        "Use a dedicated disposable test database",
                    )
                    cur.execute(
                        "select 1 from public.qg_table_requests where session_id=%s and request_id=%s",
                        (session, request),
                    )
                    self.assertIsNotNone(cur.fetchone(), "Fixture needs existing checkout")
                    cur.execute("set local role anon")
                    cur.execute("select current_user, session_user")
                    active_role, login_role = cur.fetchone()
                    self.assertEqual(active_role, "anon", "RPC must execute as anon, not fixture owner")
                    self.assertNotEqual(login_role, "anon", "Fixture connection must retain a privileged setup role")
                    cur.execute(
                        "select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                        (session, device, request, items, lat, lng),
                    )
                    replay = cur.fetchone()[0]
                    self.assertTrue(replay["duplicate"], "Fixture must be replayable")
                    self.assertEqual(replay["reason"], "same_request")
                    cur.execute("reset role")
                    cur.execute(
                        "update public.qg_table_sessions set expires_at=NULL "
                        "where id=%s and revoked_at is null and expires_at>now()",
                        (session,),
                    )
                    self.assertEqual(cur.rowcount, 1, "Fixture needs active session with expiry")
                    # Savepoint permits post-error assertions without masking a failed SQL transaction.
                    cur.execute("set local role anon")
                    cur.execute("select current_user, session_user")
                    active_role, login_role = cur.fetchone()
                    self.assertEqual(active_role, "anon", "RPC must execute as anon, not fixture owner")
                    self.assertNotEqual(login_role, "anon", "Fixture connection must retain a privileged setup role")
                    cur.execute("savepoint rejected_replay")
                    with self.assertRaises(psycopg.Error) as caught:
                        cur.execute(
                            "select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                            (session, device, request, items, lat, lng),
                        )
                    self.assertIn("หมดอายุ", str(caught.exception))
                    cur.execute("rollback to savepoint rejected_replay")
                    cur.execute("reset role")
                    cur.execute(
                        "select count(*) from public.qg_table_requests where session_id=%s and request_id=%s",
                        (session, request),
                    )
                    self.assertEqual(cur.fetchone()[0], 1, "Rejected replay must not create another request")
            finally:
                conn.rollback()


    def test_anon_cannot_mutate_table_session_directly(self):
        """Verify anon cannot revoke a session directly, even if UPDATE is granted.

        Run against the same disposable seeded database as the replay tests.
        A denied UPDATE may raise insufficient_privilege or affect zero rows
        under RLS; both are acceptable, but an actual row update is a failure.
        """
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.fail("Integration test NOT RUN: disposable database required")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        if (parsed.scheme not in ("postgres", "postgresql")
                or (parsed.hostname or "").lower() not in ("localhost", "127.0.0.1", "::1")
                or "pkypiqhlrmzocysgeqew" in url.lower()):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        try:
            import psycopg
        except ImportError:
            self.fail("psycopg is required for integration tests")
        session = str(UUID(os.environ["QUEUEGO_TEST_SESSION"]))
        with psycopg.connect(url, autocommit=False) as conn:
            try:
                with conn.cursor() as cur:
                    cur.execute("select current_database()")
                    self.assertNotIn(cur.fetchone()[0], ("postgres", "template0", "template1"))
                    cur.execute("select revoked_at from public.qg_table_sessions where id=%s", (session,))
                    row = cur.fetchone()
                    self.assertIsNotNone(row, "Fixture session must exist")
                    self.assertIsNone(row[0], "Fixture session must not be revoked")
                    cur.execute("set local role anon")
                    cur.execute("select current_user")
                    self.assertEqual(cur.fetchone()[0], "anon")
                    cur.execute("savepoint anon_update_attempt")
                    affected = None
                    try:
                        cur.execute(
                            "update public.qg_table_sessions set revoked_at=now() where id=%s",
                            (session,),
                        )
                        affected = cur.rowcount
                    except psycopg.errors.InsufficientPrivilege:
                        pass
                    finally:
                        cur.execute("rollback to savepoint anon_update_attempt")
                        cur.execute("reset role")
                    self.assertEqual(affected or 0, 0, "anon must not modify table sessions")
                    cur.execute("select revoked_at from public.qg_table_sessions where id=%s", (session,))
                    self.assertIsNone(cur.fetchone()[0], "Session must remain unchanged")
            finally:
                conn.rollback()



    def test_wrong_device_cannot_replay_existing_request(self):
        """An exposed anon RPC must not return an existing order to a different device."""
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.fail("Integration test NOT RUN: disposable database required")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        if (parsed.scheme not in ("postgres", "postgresql")
                or (parsed.hostname or "").lower() not in ("localhost", "127.0.0.1", "::1")
                or "pkypiqhlrmzocysgeqew" in url.lower()):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        try:
            import psycopg
        except ImportError:
            self.fail("psycopg is required for integration tests")
        session = str(UUID(os.environ["QUEUEGO_TEST_SESSION"]))
        device = UUID(os.environ["QUEUEGO_TEST_DEVICE_KEY"])
        wrong_device = str(UUID(int=device.int ^ 1))
        request = str(UUID(os.environ["QUEUEGO_TEST_REQUEST"]))
        items = json.dumps(json.loads(os.environ["QUEUEGO_TEST_ITEMS_JSON"]))
        lat = float(os.environ["QUEUEGO_TEST_LAT"])
        lng = float(os.environ["QUEUEGO_TEST_LNG"])
        with psycopg.connect(url, autocommit=False) as conn:
            try:
                with conn.cursor() as cur:
                    cur.execute("select current_database()")
                    self.assertNotIn(cur.fetchone()[0], ("postgres", "template0", "template1"))
                    cur.execute("select count(*) from public.qg_table_requests where session_id=%s and request_id=%s",
                                (session, request))
                    before = cur.fetchone()[0]
                    self.assertEqual(before, 1, "Fixture must have exactly one successful checkout")
                    cur.execute("set local role anon")
                    cur.execute("select current_user")
                    self.assertEqual(cur.fetchone()[0], "anon")
                    cur.execute("savepoint wrong_device_replay")
                    with self.assertRaises(psycopg.Error) as caught:
                        cur.execute("select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                                    (session, wrong_device, request, items, lat, lng))
                    self.assertIn("ไม่พบสิทธิ์", str(caught.exception))
                    cur.execute("rollback to savepoint wrong_device_replay")
                    cur.execute("reset role")
                    cur.execute("select count(*) from public.qg_table_requests where session_id=%s and request_id=%s",
                                (session, request))
                    self.assertEqual(cur.fetchone()[0], before, "Denied replay must not insert requests")
            finally:
                conn.rollback()


    def test_concurrent_existing_request_replay_is_idempotent(self):
        """Two anon clients replaying one successful request must see one durable row.

        Each client owns its connection and transaction; the barrier synchronizes
        the calls. This is an isolated replay race, not a new-checkout race.
        """
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.fail("Integration test NOT RUN: disposable database required")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        if (parsed.scheme not in ("postgres", "postgresql")
                or (parsed.hostname or "").lower() not in ("localhost", "127.0.0.1", "::1")
                or "pkypiqhlrmzocysgeqew" in url.lower()):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        try:
            import psycopg
        except ImportError:
            self.fail("psycopg is required for integration tests")
        from concurrent.futures import ThreadPoolExecutor
        from threading import Barrier

        session = str(UUID(os.environ["QUEUEGO_TEST_SESSION"]))
        device = str(UUID(os.environ["QUEUEGO_TEST_DEVICE_KEY"]))
        request = str(UUID(os.environ["QUEUEGO_TEST_REQUEST"]))
        items = json.dumps(json.loads(os.environ["QUEUEGO_TEST_ITEMS_JSON"]))
        lat = float(os.environ["QUEUEGO_TEST_LAT"])
        lng = float(os.environ["QUEUEGO_TEST_LNG"])
        barrier = Barrier(2, timeout=15)

        def replay_once(_):
            with psycopg.connect(url, autocommit=False, connect_timeout=10) as conn:
                try:
                    with conn.cursor() as cur:
                        cur.execute("select current_database()")
                        if cur.fetchone()[0] in ("postgres", "template0", "template1"):
                            raise AssertionError("Use a dedicated disposable test database")
                        cur.execute("set local statement_timeout = '15000ms'")
                        cur.execute("set local role anon")
                        cur.execute("select current_user")
                        if cur.fetchone()[0] != "anon":
                            raise AssertionError("Replay must execute as anon")
                        barrier.wait()
                        cur.execute(
                            "select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                            (session, device, request, items, lat, lng),
                        )
                        result = cur.fetchone()[0]
                        if result["duplicate"] is not True or result["reason"] != "same_request":
                            raise AssertionError("Concurrent replay was not recognized as same_request")
                        return True
                finally:
                    conn.rollback()

        with psycopg.connect(url, autocommit=False) as check_conn:
            try:
                with check_conn.cursor() as cur:
                    cur.execute("select current_database()")
                    self.assertNotIn(cur.fetchone()[0], ("postgres", "template0", "template1"))
                    cur.execute(
                        "select count(*) from public.qg_table_requests where session_id=%s and request_id=%s",
                        (session, request),
                    )
                    self.assertEqual(cur.fetchone()[0], 1, "Fixture needs one committed checkout")
            finally:
                check_conn.rollback()
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(replay_once, range(2)))
        self.assertEqual(results, [True, True])
        with psycopg.connect(url, autocommit=False) as check_conn:
            try:
                with check_conn.cursor() as cur:
                    cur.execute(
                        "select count(*) from public.qg_table_requests where session_id=%s and request_id=%s",
                        (session, request),
                    )
                    self.assertEqual(cur.fetchone()[0], 1, "Concurrent replay must not duplicate requests")
            finally:
                check_conn.rollback()


    def test_anon_cannot_insert_forged_checkout_request(self):
        """Anon must not bypass checkout authorization by writing dedupe rows.

        Uses a savepoint to roll back any attempted insert. An RLS denial or
        permission error is acceptable; an inserted row is a release failure.
        """
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.fail("Integration test NOT RUN: disposable database required")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        if (parsed.scheme not in ("postgres", "postgresql")
                or (parsed.hostname or "").lower() not in ("localhost", "127.0.0.1", "::1")
                or "pkypiqhlrmzocysgeqew" in url.lower()):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        try:
            import psycopg
        except ImportError:
            self.fail("psycopg is required for integration tests")
        from uuid import uuid4

        session = str(UUID(os.environ["QUEUEGO_TEST_SESSION"]))
        request = str(UUID(os.environ["QUEUEGO_TEST_REQUEST"]))
        forged_request = str(uuid4())
        with psycopg.connect(url, autocommit=False) as conn:
            try:
                with conn.cursor() as cur:
                    cur.execute("select current_database()")
                    self.assertNotIn(cur.fetchone()[0], ("postgres", "template0", "template1"))
                    cur.execute(
                        "select payload_hash, order_id from public.qg_table_requests "
                        "where session_id=%s and request_id=%s",
                        (session, request),
                    )
                    existing = cur.fetchone()
                    self.assertIsNotNone(existing, "Fixture must have an existing checkout")
                    cur.execute("set local role anon")
                    cur.execute("select current_user")
                    self.assertEqual(cur.fetchone()[0], "anon")
                    cur.execute("savepoint forged_request_attempt")
                    affected = None
                    try:
                        cur.execute(
                            "insert into public.qg_table_requests"
                            "(session_id,request_id,payload_hash,order_id) "
                            "values (%s,%s,%s,%s)",
                            (session, forged_request, existing[0], existing[1]),
                        )
                        affected = cur.rowcount
                    except (psycopg.errors.InsufficientPrivilege,
                            psycopg.errors.CheckViolation):
                        # PostgreSQL may reject the INSERT through GRANT or
                        # through RLS WITH CHECK; both enforce the boundary.
                        pass
                    finally:
                        cur.execute("rollback to savepoint forged_request_attempt")
                        cur.execute("reset role")
                    self.assertEqual(affected or 0, 0, "anon must not forge checkout request rows")
                    cur.execute(
                        "select count(*) from public.qg_table_requests "
                        "where session_id=%s and request_id=%s",
                        (session, forged_request),
                    )
                    self.assertEqual(cur.fetchone()[0], 0)
            finally:
                conn.rollback()

    def test_anon_cannot_delete_checkout_dedupe_row(self):
        """A guest cannot erase a completed request and bypass replay protection."""
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.fail("Integration test NOT RUN: disposable database required")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        if (parsed.scheme not in ("postgres", "postgresql")
                or (parsed.hostname or "").lower() not in ("localhost", "127.0.0.1", "::1")
                or "pkypiqhlrmzocysgeqew" in url.lower()):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        try:
            import psycopg
        except ImportError:
            self.fail("psycopg is required for integration tests")
        session = str(UUID(os.environ["QUEUEGO_TEST_SESSION"]))
        request = str(UUID(os.environ["QUEUEGO_TEST_REQUEST"]))
        with psycopg.connect(url, autocommit=False) as conn:
            try:
                with conn.cursor() as cur:
                    cur.execute("select current_database()")
                    self.assertNotIn(cur.fetchone()[0], ("postgres", "template0", "template1"))
                    cur.execute(
                        "select count(*) from public.qg_table_requests "
                        "where session_id=%s and request_id=%s", (session, request))
                    self.assertEqual(cur.fetchone()[0], 1, "Fixture needs a committed checkout")
                    cur.execute("set local role anon")
                    cur.execute("select current_user")
                    self.assertEqual(cur.fetchone()[0], "anon")
                    cur.execute("savepoint anon_delete_attempt")
                    affected = None
                    try:
                        cur.execute(
                            "delete from public.qg_table_requests "
                            "where session_id=%s and request_id=%s", (session, request))
                        affected = cur.rowcount
                    except psycopg.errors.InsufficientPrivilege:
                        pass
                    finally:
                        cur.execute("rollback to savepoint anon_delete_attempt")
                        cur.execute("reset role")
                    self.assertEqual(affected or 0, 0, "anon must not delete dedupe rows")
                    cur.execute(
                        "select count(*) from public.qg_table_requests "
                        "where session_id=%s and request_id=%s", (session, request))
                    self.assertEqual(cur.fetchone()[0], 1, "Original dedupe row must remain")
            finally:
                conn.rollback()


    def test_anon_cannot_modify_existing_checkout_hash(self):
        """Anon must not change the payload hash of an existing dedupe row."""
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.fail("Integration test NOT RUN: disposable database required")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        if (parsed.scheme not in ("postgres", "postgresql")
                or (parsed.hostname or "").lower() not in ("localhost", "127.0.0.1", "::1")
                or "pkypiqhlrmzocysgeqew" in url.lower()):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        try:
            import psycopg
        except ImportError:
            self.fail("psycopg is required for integration tests")
        from uuid import uuid4
        session = str(UUID(os.environ["QUEUEGO_TEST_SESSION"]))
        request = str(UUID(os.environ["QUEUEGO_TEST_REQUEST"]))
        with psycopg.connect(url, autocommit=False) as conn:
            try:
                with conn.cursor() as cur:
                    cur.execute("select current_database()")
                    self.assertNotIn(cur.fetchone()[0], ("postgres", "template0", "template1"))
                    cur.execute(
                        "select payload_hash from public.qg_table_requests "
                        "where session_id=%s and request_id=%s", (session, request))
                    original = cur.fetchone()
                    self.assertIsNotNone(original, "Fixture needs a committed checkout")
                    cur.execute("set local role anon")
                    cur.execute("select current_user")
                    self.assertEqual(cur.fetchone()[0], "anon")
                    cur.execute("savepoint anon_hash_update_attempt")
                    affected = None
                    try:
                        cur.execute(
                            "update public.qg_table_requests set payload_hash=%s "
                            "where session_id=%s and request_id=%s",
                            (uuid4().hex, session, request))
                        affected = cur.rowcount
                    except psycopg.errors.InsufficientPrivilege:
                        pass
                    finally:
                        cur.execute("rollback to savepoint anon_hash_update_attempt")
                        cur.execute("reset role")
                    self.assertEqual(affected or 0, 0,
                                     "anon must not alter an existing checkout hash")
                    cur.execute(
                        "select payload_hash from public.qg_table_requests "
                        "where session_id=%s and request_id=%s", (session, request))
                    self.assertEqual(cur.fetchone()[0], original[0])
            finally:
                conn.rollback()


    def test_anon_has_no_truncate_privilege_on_checkout_tables(self):
        """TRUNCATE bypasses row-level security; deny it even on empty tables."""
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.fail("Integration test NOT RUN: disposable database required")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        if (parsed.scheme not in ("postgres", "postgresql")
                or (parsed.hostname or "").lower() not in ("localhost", "127.0.0.1", "::1")
                or "pkypiqhlrmzocysgeqew" in url.lower()):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        try:
            import psycopg
        except ImportError:
            self.fail("psycopg is required for integration tests")
        with psycopg.connect(url, autocommit=False) as conn:
            try:
                with conn.cursor() as cur:
                    cur.execute("select current_database()")
                    self.assertNotIn(cur.fetchone()[0], ("postgres", "template0", "template1"))
                    cur.execute("set local role anon")
                    cur.execute("select current_user, session_user")
                    active_role, login_role = cur.fetchone()
                    self.assertEqual(active_role, "anon")
                    self.assertNotEqual(login_role, "anon")
                    for table in ("qg_table_requests", "qg_table_sessions"):
                        with self.subTest(table=table):
                            cur.execute(
                                "select has_table_privilege(current_user, %s, 'TRUNCATE')",
                                ("public." + table,),
                            )
                            self.assertFalse(cur.fetchone()[0],
                                             "anon must not bypass RLS via TRUNCATE on " + table)
            finally:
                conn.rollback()


    def test_same_request_different_cart_is_rejected_without_mutation(self):
        """An existing request ID cannot be replayed with a changed cart payload."""
        if os.getenv("QUEUEGO_DISPOSABLE_DB") != "1":
            self.fail("Integration test NOT RUN: disposable database required")
        url = os.environ["QUEUEGO_TEST_DATABASE_URL"]
        parsed = urlparse(url)
        if (parsed.scheme not in ("postgres", "postgresql")
                or (parsed.hostname or "").lower() not in ("localhost", "127.0.0.1", "::1")
                or "pkypiqhlrmzocysgeqew" in url.lower()):
            self.fail("Only loopback disposable PostgreSQL is allowed")
        try:
            import psycopg
        except ImportError:
            self.fail("psycopg is required for integration tests")
        session = str(UUID(os.environ["QUEUEGO_TEST_SESSION"]))
        device = str(UUID(os.environ["QUEUEGO_TEST_DEVICE_KEY"]))
        request = str(UUID(os.environ["QUEUEGO_TEST_REQUEST"]))
        items = json.loads(os.environ["QUEUEGO_TEST_ITEMS_JSON"])
        # The RPC hashes the serialized cart. A different valid JSON shape
        # must never resolve to the prior successful order for this request ID.
        altered_items = json.dumps({"invalid_replay_cart": items})
        lat = float(os.environ["QUEUEGO_TEST_LAT"])
        lng = float(os.environ["QUEUEGO_TEST_LNG"])
        with psycopg.connect(url, autocommit=False) as conn:
            try:
                with conn.cursor() as cur:
                    cur.execute("select current_database()")
                    self.assertNotIn(cur.fetchone()[0], ("postgres", "template0", "template1"))
                    cur.execute(
                        "select payload_hash, order_id from public.qg_table_requests "
                        "where session_id=%s and request_id=%s", (session, request))
                    original = cur.fetchone()
                    self.assertIsNotNone(original, "Fixture needs an existing checkout")
                    cur.execute("set local role anon")
                    cur.execute("select current_user")
                    self.assertEqual(cur.fetchone()[0], "anon")
                    cur.execute("savepoint mismatched_replay")
                    with self.assertRaises(psycopg.Error) as caught:
                        cur.execute(
                            "select public.qg_table_checkout(%s,%s,%s,%s::jsonb,%s,%s)",
                            (session, device, request, altered_items, lat, lng))
                    # Fail if rejected only by a later invalid-cart validation:
                    # the idempotency hash mismatch must be checked first.
                    self.assertIn("request", str(caught.exception).lower())
                    cur.execute("rollback to savepoint mismatched_replay")
                    cur.execute("reset role")
                    cur.execute(
                        "select payload_hash, order_id from public.qg_table_requests "
                        "where session_id=%s and request_id=%s", (session, request))
                    self.assertEqual(cur.fetchone(), original)
                    cur.execute(
                        "select count(*) from public.qg_table_requests "
                        "where session_id=%s and request_id=%s", (session, request))
                    self.assertEqual(cur.fetchone()[0], 1)
            finally:
                conn.rollback()


if __name__ == "__main__":
    unittest.main()
