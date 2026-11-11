#!/usr/bin/env python3
"""Write a SHA-256 manifest for the exact debug APKs uploaded by Native Pilot."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


SOURCE_SHA = re.compile(r"^[0-9a-f]{40}$")


def apk_record(role: str, source_path: str) -> dict[str, object]:
    path = Path(source_path)
    data = path.read_bytes()
    if not data:
        raise ValueError(f"{role} APK is empty: {path}")
    return {
        "repository_path": source_path,
        "size_bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--customer", required=True)
    parser.add_argument("--merchant", required=True)
    parser.add_argument("--rider", required=True)
    args = parser.parse_args()

    if not SOURCE_SHA.fullmatch(args.source_sha):
        parser.error("--source-sha must be a 40-character lowercase Git commit SHA")

    try:
        manifest = {
            "format_version": 1,
            "source_commit": args.source_sha,
            "build_type": "debug",
            "production_release_certified": False,
            "apks": {
                role: apk_record(role, path)
                for role, path in (
                    ("customer", args.customer),
                    ("merchant", args.merchant),
                    ("rider", args.rider),
                )
            },
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    except (OSError, ValueError) as error:
        print(f"APK manifest generation failed: {error}", file=sys.stderr)
        return 1

    print(json.dumps(manifest, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
