#!/usr/bin/env python3
"""Turn-restriction verifier using exact directional branches at via nodes.

This complements the older adversarial audit. It distinguishes:
- a prohibited maneuver that the engine actually traverses (FAIL),
- a start/end anchored on the intended member branches where the engine detours (PASS avoidance),
- a legal target of an only_* rule that the engine traverses directly,
- profile-inapplicable source members caused by one-way/access direction,
- malformed upstream relations (OPEN).

No source rule is invented and physical verification remains separate.
"""
import argparse,json,math,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/"queuego-map-engine"))
from core import Engine,decode_shape,haversine

P=argparse.ArgumentParser()
P.add_argument("--database",required=True)
P.add_argument("--config",required=True)
P.add_argument("--legacy-audit",required=True)
P.add_argument("--output",required=True)
A=P.parse_args()
engine=Engine(A.database,A.config)
legacy=json.loads(Path(A.legacy_audit).read_text())
legacy_by={r["id"]:r for r in legacy.get("rules",[])}

def bearing(a,b):
    return (math.degrees(math.atan2((b[0]-a[0])*math.cos(math.radians((a[1]+b[1])/2)),b[1]-a[1]))+360)%360

def delta(incoming,outgoing):
    return (outgoing-incoming+180)%360-180

def oneway(tags):
    raw=tags.get("oneway:motorcycle",tags.get("oneway"))
    if raw in ("yes","1","true"):return 1
    if raw=="-1":return -1
    return 0

def motorcycle_accessible(tags):
    if tags.get("highway")=="construction":return False
    value=tags.get("motorcycle") or tags.get("motor_vehicle") or tags.get("vehicle") or tags.get("access")
    return value not in ("no","private")

def point_along(branch,metres):
    remaining=metres
    for x,y in zip(branch,branch[1:]):
        length=haversine(x,y)
        if length<=0:continue
        if length>=remaining:
            f=remaining/length
            return [x[0]+(y[0]-x[0])*f,x[1]+(y[1]-x[1])*f]
        remaining-=length
    return list(branch[-1])

def branches_for_way(row,via,role):
    nodes=json.loads(row["node_ids"]);coords=json.loads(row["coordinates"]);tags=json.loads(row["tags"])
    if via not in nodes or not motorcycle_accessible(tags):return []
    i=nodes.index(via);direction=oneway(tags);out=[]
    # Branch geometry always starts at via and points outward.
    # prev side: approach prev->via is OSM forward; departure via->prev is reverse.
    if i>0:
        travel_forward=(role=="approach")
        allowed=direction==0 or (direction==1 and travel_forward) or (direction==-1 and not travel_forward)
        if allowed:
            out.append({"side":"prev","geometry":coords[i::-1],"travel_forward":travel_forward})
    # next side: approach next->via is reverse; departure via->next is OSM forward.
    if i+1<len(nodes):
        travel_forward=(role=="depart")
        allowed=direction==0 or (direction==1 and travel_forward) or (direction==-1 and not travel_forward)
        if allowed:
            out.append({"side":"next","geometry":coords[i:],"travel_forward":travel_forward})
    return out

def restriction_match(kind,d):
    if kind=="no_left_turn":return -165<d<-30
    if kind=="no_right_turn":return 30<d<165
    if kind=="no_straight_on":return abs(d)<=45
    if kind=="no_u_turn":return abs(d)>=150
    if kind=="only_straight_on":return abs(d)<=45
    return False

def query_for_pair(via,from_id,from_branch,to_id,to_branch,metres):
    start=point_along(from_branch["geometry"],metres)
    end=point_along(to_branch["geometry"],metres)
    incoming=bearing(start,via);outgoing=bearing(via,end)
    q=[
        {"lon":start[0],"lat":start[1],"heading":round(incoming),"heading_tolerance":12,"radius":8,"node_snap_tolerance":0},
        {"lon":end[0],"lat":end[1],"heading":round(outgoing),"heading_tolerance":12,"radius":8,"node_snap_tolerance":0}
    ]
    return q,incoming,outgoing,delta(incoming,outgoing)

def route_edges(query):
    route=engine._call("route",{"locations":query,"costing":"motorcycle"})
    edges=[]
    shape=[]
    for leg in route["trip"]["legs"]:
        coords=decode_shape(leg["shape"])
        if not shape:shape.extend(coords)
        else:shape.extend(coords[1:] if shape[-1]==coords[0] else coords)
        attrs=engine._call("trace_attributes",{"encoded_polyline":leg["shape"],"shape_match":"edge_walk","costing":"motorcycle"})
        for e in attrs.get("edges",[]):
            idx=min(int(e.get("end_shape_index",0)),len(coords)-1)
            edges.append({**e,"end_point":coords[idx]})
    return route,edges,shape

def direction_ok(edge,expected):
    value=edge.get("forward")
    return value is None or bool(value)==bool(expected)

def anchored(edges,from_id,from_branch,to_id,to_branch):
    if not edges:return False
    return (
        edges[0].get("way_id")==from_id and direction_ok(edges[0],from_branch["travel_forward"]) and
        edges[-1].get("way_id")==to_id and direction_ok(edges[-1],to_branch["travel_forward"])
    )

def direct_transition(edges,via,from_id,from_branch,to_id,to_branch,kind):
    hits=[]
    for x,y in zip(edges,edges[1:]):
        if x.get("way_id")!=from_id or y.get("way_id")!=to_id:continue
        if not direction_ok(x,from_branch["travel_forward"]) or not direction_ok(y,to_branch["travel_forward"]):continue
        if haversine(x["end_point"],via)>5:continue
        d=delta(float(x.get("end_heading",0)),float(y.get("begin_heading",0)))
        if restriction_match(kind,d):hits.append({"from_way":from_id,"to_way":to_id,"delta_deg":round(d,1),"via_distance_m":round(haversine(x["end_point"],via),2)})
    # Valhalla can sometimes represent a same-way U-turn without changing way ID.
    if kind=="no_u_turn" and from_id==to_id and not hits:
        for x,y in zip(edges,edges[1:]):
            if x.get("way_id")==from_id==y.get("way_id") and direction_ok(x,from_branch["travel_forward"]) and direction_ok(y,to_branch["travel_forward"]):
                if bool(x.get("forward"))!=bool(y.get("forward")) and haversine(x["end_point"],via)<=5:
                    d=delta(float(x.get("end_heading",0)),float(y.get("begin_heading",0)))
                    if abs(d)>=150:hits.append({"from_way":from_id,"to_way":to_id,"delta_deg":round(d,1),"via_distance_m":round(haversine(x["end_point"],via),2)})
    return hits

def connected_outgoing(db,via):
    rows=db.execute("SELECT DISTINCT w.* FROM ways w,json_each(w.node_ids) n WHERE n.value=?",(via,)).fetchall()
    result=[]
    for row in rows:
        for branch in branches_for_way(row,via,"depart"):
            result.append((row["id"],row,branch))
    return result

def probe_pair(via,from_id,fb,to_id,tb,kind,expect_forbidden):
    attempts=[]
    for metres in (12,20,35,60):
        q,inc,out,d=query_for_pair(via,from_id,fb,to_id,tb,metres)
        attempt={"metres":metres,"turn_delta_deg":round(d,1),"query":q}
        try:
            route,edges,shape=route_edges(q)
            attempt["distance_m"]=round(route["trip"]["summary"]["length"]*1000,1)
            attempt["way_sequence"]=[]
            for e in edges:
                wid=e.get("way_id")
                if wid is not None and (not attempt["way_sequence"] or attempt["way_sequence"][-1]!=wid):attempt["way_sequence"].append(wid)
            attempt["anchored"]=anchored(edges,from_id,fb,to_id,tb)
            attempt["direct_hits"]=direct_transition(edges,via,from_id,fb,to_id,tb,kind)
            if attempt["anchored"]:
                if expect_forbidden:
                    attempt["proof"]="failed_prohibited_traversal" if attempt["direct_hits"] else "passed_avoidance"
                else:
                    attempt["proof"]="passed_legal_direct" if attempt["direct_hits"] else "legal_target_not_direct"
                attempts.append(attempt)
                # Once an anchored attempt exists, larger distances are only redundant.
                break
        except Exception as error:
            attempt["error"]=str(error)
        attempts.append(attempt)
    return attempts

results=[]
with engine.db() as db:
    rows=db.execute("SELECT * FROM turn_restrictions ORDER BY id").fetchall()
    for rule in rows:
        rid=rule["id"];kind=rule["restriction"] or ""
        froms=json.loads(rule["from_ways"]);tos=json.loads(rule["to_ways"]);vias=json.loads(rule["via_nodes"]);viaways=json.loads(rule["via_ways"])
        item={"id":rid,"restriction":kind,"from":froms,"to":tos,"via_nodes":vias,"via_ways":viaways,"tests":[]}
        if not froms or not tos or not (vias or viaways):
            item["status"]="source_invalid";item["reason"]="missing from/to/via source members";results.append(item);continue
        if viaways:
            old=legacy_by.get(rid,{})
            if old.get("status")=="passed_approach" and not old.get("violations"):
                item["status"]="passed_legacy_via_way";item["legacy_status"]=old.get("status")
            else:
                item["status"]="uncovered_via_way";item["legacy_status"]=old.get("status")
            results.append(item);continue
        via_id=vias[0]
        node=db.execute("SELECT longitude,latitude FROM nodes WHERE id=?",(via_id,)).fetchone()
        if not node:
            item["status"]="source_invalid";item["reason"]="via node missing from normalized package";results.append(item);continue
        via=[node["longitude"],node["latitude"]]
        from_candidates=[]
        for wid in froms:
            row=db.execute("SELECT * FROM ways WHERE id=?",(wid,)).fetchone()
            if row:
                from_candidates.extend((wid,row,b) for b in branches_for_way(row,via_id,"approach"))
        to_candidates=[]
        for wid in tos:
            row=db.execute("SELECT * FROM ways WHERE id=?",(wid,)).fetchone()
            if row:
                to_candidates.extend((wid,row,b) for b in branches_for_way(row,via_id,"depart"))
        if not from_candidates:
            item["status"]="profile_inapplicable";item["reason"]="motorcycle cannot approach via on source from member";results.append(item);continue

        failed=False;uncovered=False;proved=0
        if kind.startswith("no_"):
            pairs=[]
            for fw,fr,fb in from_candidates:
                for tw,tr,tb in to_candidates:
                    _,inc,out,d=query_for_pair(via,fw,fb,tw,tb,20)
                    if restriction_match(kind,d):pairs.append((fw,fb,tw,tb,d))
            if not pairs:
                item["status"]="profile_inapplicable";item["reason"]="no motorcycle-valid prohibited branch pair from source geometry";results.append(item);continue
            for fw,fb,tw,tb,d in pairs:
                attempts=probe_pair(via,fw,fb,tw,tb,kind,True)
                proof=next((x.get("proof") for x in attempts if x.get("proof")),None)
                item["tests"].append({"role":"prohibited","from_way":fw,"from_side":fb["side"],"to_way":tw,"to_side":tb["side"],"expected_delta_deg":round(d,1),"attempts":attempts,"proof":proof})
                if proof=="failed_prohibited_traversal":failed=True
                elif proof=="passed_avoidance":proved+=1
                else:uncovered=True
        elif kind.startswith("only_"):
            legal_pairs=[]
            for fw,fr,fb in from_candidates:
                for tw,tr,tb in to_candidates:
                    _,inc,out,d=query_for_pair(via,fw,fb,tw,tb,20)
                    if restriction_match(kind,d):legal_pairs.append((fw,fb,tw,tb,d))
            if not legal_pairs:
                item["status"]="profile_inapplicable";item["reason"]="no motorcycle-valid designated branch pair from source geometry";results.append(item);continue
            legal_ok=False
            for fw,fb,tw,tb,d in legal_pairs:
                attempts=probe_pair(via,fw,fb,tw,tb,kind,False)
                proof=next((x.get("proof") for x in attempts if x.get("proof")),None)
                item["tests"].append({"role":"designated_legal","from_way":fw,"from_side":fb["side"],"to_way":tw,"to_side":tb["side"],"expected_delta_deg":round(d,1),"attempts":attempts,"proof":proof})
                if proof=="passed_legal_direct":legal_ok=True;proved+=1
            if not legal_ok:uncovered=True
            outgoing=connected_outgoing(db,via_id)
            legal_keys={(tw,tb["side"]) for _,_,tw,tb,_ in legal_pairs}
            forbidden_seen=set()
            for fw,fr,fb in from_candidates:
                for tw,tr,tb in outgoing:
                    key=(tw,tb["side"])
                    if key in legal_keys or key in forbidden_seen:continue
                    forbidden_seen.add(key)
                    _,inc,out,d=query_for_pair(via,fw,fb,tw,tb,20)
                    attempts=probe_pair(via,fw,fb,tw,tb,kind,True)
                    proof=next((x.get("proof") for x in attempts if x.get("proof")),None)
                    item["tests"].append({"role":"only_rule_forbidden_exit","from_way":fw,"from_side":fb["side"],"to_way":tw,"to_side":tb["side"],"expected_delta_deg":round(d,1),"attempts":attempts,"proof":proof})
                    if proof=="failed_prohibited_traversal":failed=True
                    elif proof=="passed_avoidance":proved+=1
                    else:uncovered=True
        else:
            uncovered=True;item["reason"]="unsupported restriction kind"

        item["proved_tests"]=proved
        item["status"]="failed" if failed else "uncovered" if uncovered else "passed_directional"
        results.append(item)

summary={k:sum(r["status"]==k for r in results) for k in sorted({r["status"] for r in results})}
bad={"failed","uncovered","uncovered_via_way"}
well_formed_gate=not any(r["status"] in bad for r in results if r["status"]!="source_invalid")
report={
    "dataset":engine.metadata(),
    "rules":results,
    "summary":summary,
    "well_formed_motorcycle_gate":well_formed_gate,
    "all_source_rules_gate":well_formed_gate and not any(r["status"]=="source_invalid" for r in results),
    "physical_verification":False,
    "limitations":[
        "Directional branch probes prove the installed motorcycle graph behavior, not physical signs.",
        "Profile-inapplicable means the installed one-way/access graph cannot perform the source maneuver; it is not a claim about other vehicle profiles.",
        "Malformed source relations remain OPEN and are never repaired or guessed."
    ]
}
Path(A.output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
print(json.dumps({"summary":summary,"well_formed_motorcycle_gate":report["well_formed_motorcycle_gate"],"all_source_rules_gate":report["all_source_rules_gate"]},ensure_ascii=False))
if summary.get("failed",0):raise SystemExit("Installed graph traversed a prohibited turn")
