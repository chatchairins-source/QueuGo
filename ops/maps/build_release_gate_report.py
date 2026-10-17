#!/usr/bin/env python3
"""Build a fail-closed QueueGo Map release-gate report from CI evidence.

This script does not promote physical or external-hosting gates. Missing evidence stays
OPEN. It exists so "green CI" cannot be confused with "safe sole-navigation release".
"""
import argparse,json
from pathlib import Path

def load(path,required=True):
    p=Path(path)
    if not p.is_file():
        if required:raise FileNotFoundError(str(p))
        return None
    return json.loads(p.read_text())

def all_runtime_checks(runtime):
    return bool(runtime and runtime.get("checks") and all(x.get("passed") for x in runtime["checks"]))

def road_cases_pass(real):
    return bool(real and real.get("cases") and not any(x.get("status")=="open" for x in real["cases"]))

def browser_pass(browser):
    return bool(browser and browser.get("engine")=="QueueGo/Valhalla" and browser.get("distance",0)>0 and browser.get("rendered",0)>0 and browser.get("sameMap") is True and browser.get("restoration_recomputed") is True and browser.get("retained_on_visibility") is True and not browser.get("external_requests_blocked") and not browser.get("page_errors"))

def offline_pass(offline):
    return bool(offline and offline.get("route_distance_m",0)>0 and offline.get("dataset_sha256") and len(offline.get("checks") or [])>=6)

def container_pass(container):
    return bool(container and container.get("failures")==0 and container.get("network")=="none" and container.get("read_only") is True and container.get("distance_m",0)>0)

def staging_pass(staging):
    return bool(staging and staging.get("healthy") is True and staging.get("public_bind") is False and staging.get("read_only_root") is True and staging.get("data_read_only") is True and staging.get("no_new_privileges") is True and staging.get("capabilities_dropped")=="ALL")

def directional_pass(value):
    return bool(value and value.get("well_formed_motorcycle_gate") is True and not value.get("summary",{}).get("failed",0))

def source_open(runtime):
    data=(runtime or {}).get("dataset") or {}
    return {
        "unresolved_turn_relations":sorted(data.get("unresolved_turn_relations") or []),
        "source_member_conflicts":sorted(data.get("source_member_conflicts") or []),
        "source_direction_conflicts":sorted(data.get("source_direction_conflicts") or []),
    }

def field_summary(field_dir):
    if not field_dir:return {"sessions":0,"candidate_sessions":0,"required_scenarios":[],"complete":False}
    root=Path(field_dir)
    files=sorted(root.glob("*.json")) if root.is_dir() else []
    sessions=[]
    for file in files:
        try:
            value=json.loads(file.read_text())
        except Exception:
            continue
        sessions.append(value)
    candidate=[x for x in sessions if x.get("candidate_evidence") is True]
    scenarios={x.get("scenario") for x in candidate}
    required={"village_lane","frontage_road","u_turn","parallel_road","signal_loss","background_foreground"}
    # Active reroute/arrival observations can be in generic or any physical scenario.
    reroute=any((x.get("metrics") or {}).get("reroute_observations",0)>0 for x in candidate)
    arrival=any((x.get("metrics") or {}).get("arrival_observations",0)>0 for x in candidate)
    complete=required.issubset(scenarios) and reroute and arrival
    return {
        "sessions":len(sessions),
        "candidate_sessions":len(candidate),
        "required_scenarios":sorted(required),
        "covered_scenarios":sorted(x for x in scenarios if x),
        "reroute_observed":reroute,
        "arrival_observed":arrival,
        "complete":complete,
    }

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--evidence-dir",required=True)
    p.add_argument("--browser",required=True)
    p.add_argument("--field-evidence-dir")
    p.add_argument("--external-staging-authorized",action="store_true")
    p.add_argument("--android-runtime-validated",action="store_true")
    p.add_argument("--output",required=True)
    p.add_argument("--require-code-gate",action="store_true")
    a=p.parse_args()
    e=Path(a.evidence_dir)
    runtime=load(e/"runtime.json")
    roads=load(e/"real-roads.json")
    turns=load(e/"turns.json")
    directional=load(e/"turns-directional.json")
    offline=load(e/"offline.json")
    container=load(e/"container.json")
    staging=load(e/"staging.json")
    browser=load(a.browser)
    fields=field_summary(a.field_evidence_dir)
    source=source_open(runtime)
    source_open_count=sum(len(v) for v in source.values())

    code_gates={
        "runtime":all_runtime_checks(runtime),
        "real_road_queries":road_cases_pass(roads),
        "turn_directional_well_formed_motorcycle":directional_pass(directional),
        "browser_same_origin_offline":browser_pass(browser),
        "offline_package":offline_pass(offline),
        "offline_container":container_pass(container),
        "loopback_staging_contract":staging_pass(staging),
    }
    code_candidate_ready=all(code_gates.values())
    open_release_gates=[]
    if not code_candidate_ready:open_release_gates.append("candidate_code_evidence")
    if source_open_count:open_release_gates.append("source_turn_relations")
    if not fields["complete"]:open_release_gates.append("physical_buriram_field_evidence")
    if not a.external_staging_authorized:open_release_gates.append("authorized_external_staging")
    if not a.android_runtime_validated:open_release_gates.append("android_offline_runtime")

    report={
        "dataset_sha256":(runtime.get("dataset") or {}).get("dataset_sha256"),
        "source_timestamp":(runtime.get("dataset") or {}).get("source_timestamp"),
        "code_gates":code_gates,
        "code_candidate_ready":code_candidate_ready,
        "legacy_turn_summary":turns.get("summary") or {},
        "directional_turn_summary":directional.get("summary") or {},
        "source_open":source,
        "source_open_count":source_open_count,
        "physical_field_evidence":fields,
        "external_staging_authorized":bool(a.external_staging_authorized),
        "android_runtime_validated":bool(a.android_runtime_validated),
        "open_release_gates":open_release_gates,
        "sole_navigation_release":not open_release_gates,
        "production_deployment_authorized":False,
        "notes":[
            "A green code candidate does not certify physical signs, missing source turn members, or one-way-conflicting source relations.",
            "Loopback staging contract evidence is not an external staging deployment.",
            "This report never authorizes Production deployment."
        ]
    }
    Path(a.output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps({"code_candidate_ready":code_candidate_ready,"sole_navigation_release":report["sole_navigation_release"],"open_release_gates":open_release_gates},ensure_ascii=False))
    if a.require_code_gate and not code_candidate_ready:raise SystemExit("QueueGo Map candidate code gate is not green")

if __name__=="__main__":main()
