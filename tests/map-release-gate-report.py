#!/usr/bin/env python3
"""Static/logic contract for QueueGo Map release-gate aggregation."""
import importlib.util,json,tempfile
from pathlib import Path

path=Path("ops/maps/build_release_gate_report.py")
spec=importlib.util.spec_from_file_location("release_report",path)
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
checks=0

def ok(value,message):
    global checks
    assert value,message
    checks+=1

ok(mod.all_runtime_checks({"checks":[{"passed":True},{"passed":True}]}),"runtime pass")
ok(not mod.all_runtime_checks({"checks":[{"passed":True},{"passed":False}]}),"runtime fail closed")
ok(mod.road_cases_pass({"cases":[{"status":"passed"},{"status":"passed_excluded"}]}),"road cases pass")
ok(not mod.road_cases_pass({"cases":[{"status":"open"}]}),"open road case fails")
ok(mod.directional_pass({"well_formed_motorcycle_gate":True,"summary":{"passed_directional":1}}),"directional pass")
ok(not mod.directional_pass({"well_formed_motorcycle_gate":False,"summary":{}}),"directional false fails")
for status in ('failed','uncovered','uncovered_via_way'):
    ok(not mod.directional_pass({'well_formed_motorcycle_gate':True,'summary':{status:1}}),'summary cannot override '+status)
source=path.read_text()
for token in [
    '"source_member_conflicts"',
    '"source_direction_conflicts"',
    '"physical_buriram_field_evidence"',
    '"authorized_external_staging"',
    '"android_offline_runtime"',
    '"sole_navigation_release"',
    '"production_deployment_authorized":False',
    "require_code_gate",
]:
    ok(token in source,f"missing release safety token: {token}")

print(json.dumps({"checks":checks,"failures":0,"scope":"QueueGo Map release-gate aggregation"}))
