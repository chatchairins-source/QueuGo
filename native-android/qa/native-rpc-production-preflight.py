#!/usr/bin/env python3
"""Generate or execute a read-only native/Supabase Production RPC parity audit.

The script never calls the RPCs and never changes the database. For --check,
provide a *read-only* PostgreSQL login in QG_READONLY_DATABASE_URL.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
SOURCES = (
    "native-android/customer/src/main/java/com/queuego/customer/CustomerApi.kt",
    "native-android/customer/src/main/java/com/queuego/customer/CustomerRideScreen.kt",
    "native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt",
    "native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt",
)
RPC_PATTERN = re.compile(r'\b(?:http\.)?rpc\s*\(\s*"([a-z][a-z0-9_]*)"', re.M)
SAFE_NAME = re.compile(r"^[a-z][a-z0-9_]*$")


def required_rpcs(root: Path = ROOT) -> dict[str, list[str]]:
    references: dict[str, list[str]] = {}
    for filename in SOURCES:
        content = (root / filename).read_text(encoding="utf-8")
        for name in RPC_PATTERN.findall(content):
            if not SAFE_NAME.fullmatch(name):
                raise ValueError("Unsafe RPC function name")
            references.setdefault(name, [])
            if filename not in references[name]:
                references[name].append(filename)
    if not references:
        raise ValueError("No Native RPC calls found; source paths or parser changed")
    return dict(sorted(references.items()))


def sql_query(names: list[str]) -> str:
    if not names or any(not SAFE_NAME.fullmatch(name) for name in names):
        raise ValueError("Invalid RPC names")
    values = ",\n    ".join("('%s')" % name for name in sorted(set(names)))
    return (
        "-- QueueGo: read-only pg_catalog introspection; NO RPC invocations or DDL.\n"
        "-- Run with an explicitly read-only database role, never service credentials in CI logs.\n"
        "with requested(name) as (\n  values\n    " + values + "\n),\n"
        "catalog as (\n"
        "  select r.name, p.oid,\n"
        "    pg_catalog.has_function_privilege('authenticated', p.oid, 'EXECUTE') as auth_execute,\n"
        "    pg_catalog.has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute\n"
        "  from requested r\n"
        "  left join (\n"
        "    select p.oid, p.proname\n"
        "    from pg_catalog.pg_proc p\n"
        "    join pg_catalog.pg_namespace n on n.oid = p.pronamespace\n"
        "    where n.nspname = 'public'\n"
        "  ) p on p.proname = r.name\n"
        ")\n"
        "select name,\n"
        "  count(oid)::integer as overloads,\n"
        "  coalesce(bool_or(auth_execute), false) as authenticated_execute,\n"
        "  coalesce(bool_or(anon_execute), false) as anonymous_execute\n"
        "from catalog group by name order by name;\n"
    )


def verify_rows(names: list[str], rows: list[dict]) -> tuple[bool, dict]:
    by_name = {str(r.get("name", "")): r for r in rows}
    missing = sorted(name for name in names if
                     int(by_name.get(name, {}).get("overloads") or 0) < 1)
    no_auth = sorted(name for name in names if
                     name not in missing and
                     str(by_name.get(name, {}).get("authenticated_execute", "")).lower()
                     not in ("t", "true", "1"))
    return not missing and not no_auth, {
        "checked": len(names),
        "missing": missing,
        "authenticated_execute_missing": no_auth,
        "status": "PASS" if not missing and not no_auth else "BLOCKED",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sql", action="store_true", help="Print read-only SQL for review")
    parser.add_argument("--check", action="store_true", help="Run SQL using read-only psql login")
    parser.add_argument("--sources", action="store_true", help="Print RPC names and source files")
    args = parser.parse_args()
    refs = required_rpcs()
    names = list(refs)
    if args.sources:
        print(json.dumps(refs, ensure_ascii=False, indent=2))
    if args.sql or (not args.sources and not args.check):
        print(sql_query(names))
    if args.check:
        url = os.getenv("QG_READONLY_DATABASE_URL", "")
        if not url:
            print("BLOCKED: missing QG_READONLY_DATABASE_URL (read-only database login)", file=sys.stderr)
            return 2
        # -X disables local psql startup scripts. The credential never appears
        # in process argv, command logs, output artifacts or git source.
        private_env = os.environ.copy()
        private_env["PGDATABASE"] = url
        result = subprocess.run(
            ["psql", "-X", "-A", "-F", "\t", "-t", "--set=ON_ERROR_STOP=1"],
            input=sql_query(names), env=private_env, text=True,
            capture_output=True, timeout=30, check=False,
        )
        if result.returncode != 0:
            print("BLOCKED: catalog query failed; inspect access independently", file=sys.stderr)
            return 2
        rows = []
        for line in result.stdout.splitlines():
            parts = line.split("\t")
            if len(parts) != 4:
                print("BLOCKED: unexpected catalog output", file=sys.stderr)
                return 2
            rows.append(dict(zip(("name", "overloads", "authenticated_execute",
                                  "anonymous_execute"), parts)))
        accepted, evidence = verify_rows(names, rows)
        print(json.dumps(evidence, ensure_ascii=False, indent=2))
        return 0 if accepted else 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
