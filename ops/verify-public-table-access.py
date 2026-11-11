#!/usr/bin/env python3
"""Fail closed on unsafe public table grants or broad future table defaults."""

import argparse
from pathlib import Path
import re
import sys


COUNT = re.compile(r"^[0-9]+$")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("count_file", type=Path, help="psql output from public table/default ACL audit")
    args = parser.parse_args()
    try:
        value = args.count_file.read_text(encoding="ascii").strip()
    except (OSError, UnicodeError) as error:
        print(f"::error::Public-table access audit output unavailable: {error}", file=sys.stderr)
        return 1
    if not COUNT.fullmatch(value):
        print("::error::Public-table access audit output is empty or malformed", file=sys.stderr)
        return 1

    unsafe_objects = int(value)
    print(f"Production public table/default ACL entries with unsafe access: {unsafe_objects}")
    if unsafe_objects:
        print(
            "::error::Revoke anon/authenticated TRUNCATE, TRIGGER, REFERENCES and MAINTAIN grants; "
            "remove broad anon/authenticated/PUBLIC table default grants; enable RLS on every public table "
            "and add policies wherever direct CRUD is granted",
            file=sys.stderr,
        )
        return 1
    print("Public tables enable RLS, exposed CRUD tables have policies, and no unsafe table/default grants remain")
    return 0


if __name__ == "__main__":
    sys.exit(main())
