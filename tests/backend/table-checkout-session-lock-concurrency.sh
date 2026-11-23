#!/usr/bin/env bash
# Dedicated disposable PostgreSQL only. No production connection or credentials.
set -euo pipefail
: "${PGHOST:=localhost}" "${PGPORT:=5432}" "${PGUSER:=postgres}" "${PGPASSWORD:=postgres}"
export PGHOST PGPORT PGUSER PGPASSWORD
if [[ "$PGHOST" != "localhost" && "$PGHOST" != "127.0.0.1" ]]; then
  echo "FAIL: only loopback PostgreSQL permitted" >&2; exit 1
fi
db="qg_checkout_lock_$$"
tmp="$(mktemp -d)"
holder=""
cleanup() {
  if [[ -n "$holder" ]]; then kill "$holder" 2>/dev/null || true; wait "$holder" 2>/dev/null || true; fi
  dropdb --if-exists "$db" >/dev/null 2>&1 || true
  rm -rf "$tmp"
}
trap cleanup EXIT
createdb "$db"
# Reuse exactly the fixtures and function exercised by the accepted isolated
# replay test, but leave the database open for independent concurrent sessions.
sed -e '/^SET ROLE qg_checkout_test_anon;$/,$d' -e '/^BEGIN;$/d' \
  tests/backend/table-checkout-revoked-replay-postgres.sql > "$tmp/setup.sql"
psql -X -v ON_ERROR_STOP=1 -d "$db" -f "$tmp/setup.sql" > "$tmp/setup.log"
# The holder locks the same session row that the SECURITY DEFINER RPC must lock.
# pg_sleep holds it for 3s. The competing RPC must wait and replay, not write.
cat > "$tmp/holder.sql" <<'SQL'
BEGIN;
SELECT id FROM public.qg_table_sessions
 WHERE id='00000000-0000-0000-0000-000000000003' FOR UPDATE;
SELECT pg_sleep(3);
COMMIT;
SQL
psql -X -v ON_ERROR_STOP=1 -d "$db" -f "$tmp/holder.sql" > "$tmp/holder.log" &
holder=$!
sleep 0.6
if ! kill -0 "$holder" 2>/dev/null; then echo 'FAIL: holder exited before competing RPC' >&2; exit 1; fi
start="$(date +%s%3N)"
result="$(psql -X -A -t -v ON_ERROR_STOP=1 -d "$db" <<'SQL'
SET ROLE qg_checkout_test_anon;
SELECT public.qg_table_checkout(
 '00000000-0000-0000-0000-000000000003',
 '00000000-0000-0000-0000-000000000010',
 '00000000-0000-0000-0000-000000000021',
 '[{"id":"00000000-0000-0000-0000-000000000040","quantity":1}]'::jsonb,
 13.0,100.0)::text;
SQL
)"
elapsed="$(( $(date +%s%3N) - start ))"
wait "$holder"
holder=""
if (( elapsed < 1500 )); then
  echo "FAIL: session lock was not observed (elapsed ${elapsed}ms)" >&2; exit 1
fi
if [[ "$result" != *'"duplicate": true'* || "$result" != *'"reason": "same_request"'* ]]; then
  echo "FAIL: competing request did not replay existing order" >&2; exit 1
fi
counts="$(psql -X -A -t -v ON_ERROR_STOP=1 -d "$db" -c 'SELECT (SELECT count(*) FROM public.orders), (SELECT count(*) FROM public.qg_table_requests)')"
if [[ "$counts" != '1|2' ]]; then
  echo "FAIL: concurrent replay changed order/request counts: $counts" >&2; exit 1
fi
echo "PASS: isolated PostgreSQL session FOR UPDATE serializes replay; same order; no duplicate rows"
echo "LIMITATION: existing-request replay only, not new-order concurrency, financial settlement, or Production parity"
