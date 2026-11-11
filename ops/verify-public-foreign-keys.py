#!/usr/bin/env python3
"""Fail closed unless every Production public foreign-key constraint is validated."""

import argparse
from pathlib import Path
import re
import sys


COUNT = re.compile(r"^[0-9]+$")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("count_file", type=Path, help="psql output from the public unvalidated-FK count query")
    args = parser.parse_args()
    try:
        value = args.count_file.read_text(encoding="ascii").strip()
    except (OSError, UnicodeError) as error:
        print(f"::error::Foreign-key readiness query output unavailable: {error}", file=sys.stderr)
        return 1
    if not COUNT.fullmatch(value):
        print("::error::Foreign-key readiness query output is empty or malformed", file=sys.stderr)
        return 1
    unvalidated = int(value)
    print(f"Production public foreign keys not validated: {unvalidated}")
    if unvalidated:
        print("::error::Validate all existing Production public foreign-key constraints before release", file=sys.stderr)
        return 1
    print("Production public foreign-key constraints are all validated; undeclared application references are outside this check")
    return 0


if __name__ == "__main__":
    sys.exit(main())
