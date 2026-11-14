#!/usr/bin/env python3
"""Validate exported QueueGo Map physical field evidence without promoting it to certification."""
import argparse,json,math
from pathlib import Path
from datetime import datetime,timezone

BURIRAM_BOUNDS=(102.369,14.109,103.592,15.819)
MIN_FIXES=10
MIN_ACCEPTED_RATIO=.5
MAX_MEDIAN_ACCURACY_M=35
MIN_DURATION_S=15

def parse_time(value):
    if not value:return None
    try:return datetime.fromisoformat(value.replace("Z","+00:00")).timestamp()
    except ValueError:return None

def haversine(a,b):
    lon1,lat1,lon2,lat2=map(math.radians,[a[0],a[1],b[0],b[1]])
    q=math.sin((lat2-lat1)/2)**2+math.cos(lat1)*math.cos(lat2)*math.sin((lon2-lon1)/2)**2
    return 12742017.6*math.atan2(math.sqrt(q),math.sqrt(max(0,1-q)))

def validate(doc):
    failures=[];warnings=[]
    if doc.get("schema")!="queuego-map-field-evidence/v1":failures.append("unsupported_schema")
    fixes=doc.get("fixes") if isinstance(doc.get("fixes"),list) else []
    events=doc.get("events") if isinstance(doc.get("events"),list) else []
    if len(fixes)<MIN_FIXES:failures.append("too_few_fixes")
    accepted=[f for f in fixes if f.get("accepted") is True]
    ratio=len(accepted)/max(1,len(fixes))
    if ratio<MIN_ACCEPTED_RATIO:failures.append("insufficient_accurate_fix_ratio")
    accuracies=sorted(float(f["accuracy"]) for f in accepted if isinstance(f.get("accuracy"),(int,float)) and math.isfinite(float(f["accuracy"])))
    median=accuracies[len(accuracies)//2] if accuracies else None
    if median is None or median>MAX_MEDIAN_ACCURACY_M:failures.append("median_accuracy_too_poor")
    start=parse_time(doc.get("started_at"));stop=parse_time(doc.get("stopped_at"))
    duration=(stop-start) if start is not None and stop is not None else None
    if duration is None or duration<MIN_DURATION_S:failures.append("recording_too_short")

    west,south,east,north=BURIRAM_BOUNDS
    outside=0
    for f in accepted:
        try:lat=float(f["lat"]);lon=float(f["lon"])
        except (KeyError,TypeError,ValueError):outside+=1;continue
        if not (south<=lat<=north and west<=lon<=east):outside+=1
    if outside:failures.append("accepted_fix_outside_buriram_package")

    distance=0.0
    for a,b in zip(accepted,accepted[1:]):
        try:distance+=haversine([float(a["lon"]),float(a["lat"])],[float(b["lon"]),float(b["lat"])])
        except (KeyError,TypeError,ValueError):pass

    matched=sum(bool(f.get("matching_available") and isinstance(f.get("snapped_position"),list)) for f in accepted)
    reroutes=sum(bool(f.get("reroute")) for f in fixes)
    arrivals=sum(bool(f.get("arrived")) for f in fixes)
    gps_gaps=[e for e in events if e.get("type")=="gps_gap" and isinstance(e.get("gap_ms"),(int,float)) and e["gap_ms"]>15000]
    visibility=[e.get("state") for e in events if e.get("type")=="visibility"]
    scenario=doc.get("scenario")
    nav=doc.get("navigation") if isinstance(doc.get("navigation"),dict) else {}

    if scenario=="signal_loss" and not gps_gaps:failures.append("signal_loss_scenario_missing_real_gap")
    if scenario=="background_foreground" and not ("hidden" in visibility and "visible" in visibility):failures.append("background_foreground_scenario_missing_transition")
    if scenario in {"village_lane","frontage_road","u_turn","parallel_road","generic"} and distance<30:
        failures.append("insufficient_physical_motion")
    if scenario in {"frontage_road","u_turn","parallel_road"} and matched<3:
        failures.append("insufficient_map_matching_evidence")
    if nav.get("active") and not nav.get("route_id"):failures.append("active_navigation_missing_route_id")
    if not nav.get("active"):warnings.append("not_recorded_during_active_navigation")
    if matched==0:warnings.append("no_successful_map_matching")
    if reroutes==0:warnings.append("no_reroute_observed")
    if arrivals==0:warnings.append("no_arrival_observed")

    report={
        "session_id":doc.get("session_id"),
        "scenario":scenario,
        "candidate_evidence":not failures,
        "certified":False,
        "release_gate_changed":False,
        "failures":failures,
        "warnings":warnings,
        "metrics":{
            "fixes":len(fixes),
            "accepted_fixes":len(accepted),
            "accepted_ratio":round(ratio,3),
            "median_accuracy_m":median,
            "duration_s":duration,
            "travelled_raw_m":round(distance,1),
            "matched_fixes":matched,
            "reroute_observations":reroutes,
            "arrival_observations":arrivals,
            "gps_gap_events":len(gps_gaps),
            "visibility_events":visibility,
            "navigation_active_at_start":bool(nav.get("active")),
            "route_id_present":bool(nav.get("route_id"))
        },
        "limitations":[
            "Candidate evidence is not an authoritative road-sign or turn-restriction certification.",
            "Raw GPS distance can contain measurement noise and is never used as road geometry.",
            "Physical evidence requires human review against the intended road/test scenario."
        ]
    }
    return report

def main():
    p=argparse.ArgumentParser()
    p.add_argument("input")
    p.add_argument("--output")
    p.add_argument("--allow-open",action="store_true",help="return success even when evidence requirements remain open")
    a=p.parse_args()
    doc=json.loads(Path(a.input).read_text())
    report=validate(doc)
    payload=json.dumps(report,ensure_ascii=False,indent=2)+"\n"
    if a.output:Path(a.output).write_text(payload)
    print(payload,end="")
    if not report["candidate_evidence"] and not a.allow_open:raise SystemExit(2)

if __name__=="__main__":main()
