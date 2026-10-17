#!/usr/bin/env python3
"""Contract checks for physical field evidence validation; no fabricated road pass."""
import importlib.util,json
from pathlib import Path

path=Path("ops/maps/validate_field_evidence.py")
spec=importlib.util.spec_from_file_location("field_validator",path)
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
checks=0

def ok(value,message):
    global checks
    assert value,message
    checks+=1

report=module.validate({})
ok(report["candidate_evidence"] is False,"empty evidence must fail closed")
ok(report["certified"] is False,"validator must never self-certify physical roads")
ok(report["release_gate_changed"] is False,"validator must never close release gate")
ok("unsupported_schema" in report["failures"],"schema mismatch must fail")
ok("too_few_fixes" in report["failures"],"too few fixes must fail")
ok("insufficient_accurate_fix_ratio" in report["failures"],"poor GPS evidence must fail")
ok("recording_too_short" in report["failures"],"short sessions must fail")

source=path.read_text()
for token in [
    'scenario=="signal_loss"',
    'scenario=="background_foreground"',
    '"frontage_road","u_turn","parallel_road"',
    "accepted_fix_outside_buriram_package",
    "insufficient_map_matching_evidence",
    "not_recorded_during_active_navigation",
    "Candidate evidence is not an authoritative road-sign",
]:
    ok(token in source,f"missing field-evidence guard: {token}")

print(json.dumps({"checks":checks,"failures":0,"scope":"QueueGo Map physical field evidence fail-closed contract"}))
