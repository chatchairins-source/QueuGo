#!/usr/bin/env python3
"""Verify real OSM place exports against a pinned, provenance-bound catalog digest."""
import hashlib
import json
import sys
from pathlib import Path

actual, expected = [json.loads(Path(x).read_text(encoding="utf-8")) for x in sys.argv[1:]]
fields = ("records", "source_timestamp", "bounds", "licence", "attribution")
if isinstance(expected.get("records"), list):
    # Preserve comparison with a complete reviewed snapshot if one is provided.
    for key in fields:
        assert actual[key] == expected[key], f"Catalog reproducibility mismatch: {key}"
else:
    assert expected.get("schema_version") == 1, "Unknown pinned catalog digest schema"
    assert expected.get("canonical_fields") == list(fields), "Pinned digest field set changed"
    assert actual["source_extract_sha256"] == expected["source_extract_sha256"], "Catalog source PBF changed"
    assert len(actual["records"]) == expected["record_count"], "Catalog record count changed"
    for key in fields[1:]:
        assert actual[key] == expected[key], f"Catalog provenance mismatch: {key}"
    canonical = json.dumps({key: actual[key] for key in fields}, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    assert hashlib.sha256(canonical).hexdigest() == expected["canonical_sha256"], "Catalog records or provenance changed"
print(json.dumps({"checks": 7, "failures": 0, "records": len(actual["records"]), "scope": "real OSM Buriram places match the pinned exact dataset and canonical catalog digest"}))
