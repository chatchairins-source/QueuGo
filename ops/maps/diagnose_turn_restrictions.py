#!/usr/bin/env python3
"""Diagnostic probes for turn restrictions that the primary audit cannot yet prove.

This file NEVER upgrades a release-gate status. It records anchor candidates,
actual routed way sequences and transition proximity so the next fix can be based
on real graph evidence instead of guessed turn rules.
"""
import argparse,json,math,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/"queuego-map-engine"))
from core import Engine,decode_shape,haversine

p=argparse.ArgumentParser()
p.add_argument("--database",required=True)
p.add_argument("--config",required=True)
p.add_argument("--audit",required=True)
p.add_argument("--output",required=True)
a=p.parse_args()
engine=Engine(a.database,a.config)
audit=json.loads(Path(a.audit).read_text())
audit_by_id={r["id"]:r for r in audit.get("rules",[])}
target_ids={rid for rid,r in audit_by_id.items() if r.get("status")!="passed_approach"}

def heading(a,b):
    return (math.degrees(math.atan2((b[0]-a[0])*math.cos(math.radians(a[1])),b[1]-a[1]))+360)%360

def line_length(line):
    return sum(haversine(x,y) for x,y in zip(line,line[1:]))

def point_on_branch(line,origin,metres,approach=False,tags=None):
    if len(line)<2:return list(origin)
    index=min(range(len(line)),key=lambda i:haversine(line[i],origin))
    before=line[index::-1]
    after=line[index:]
    direction=(tags or {}).get("oneway:motorcycle",(tags or {}).get("oneway"))
    if direction in ("yes","1","true","-1"):
        forward=direction!="-1"
        branch=before if approach==forward else after
    else:
        candidates=[b for b in (before,after) if len(b)>=2]
        if not candidates:return list(origin)
        branch=max(candidates,key=line_length)
    remaining=metres
    for x,y in zip(branch,branch[1:]):
        length=haversine(x,y)
        if length<=0:continue
        if length>=remaining:
            f=remaining/length
            return [x[0]+(y[0]-x[0])*f,x[1]+(y[1]-x[1])*f]
        remaining-=length
    return list(branch[-1])

def locate_way_ids(location):
    try:
        result=engine._call("locate",{"locations":[location],"costing":"motorcycle","verbose":True})
        edges=result[0].get("edges",[]) if result else []
        values=[]
        for edge in edges[:12]:
            info=edge.get("edge_info",{})
            way=info.get("way_id")
            if way is not None:
                values.append({"way_id":way,"distance":edge.get("distance"),"heading":edge.get("heading")})
        return values
    except Exception as error:
        return [{"error":str(error)}]

def route_edges(query):
    route=engine._call("route",{"locations":query,"costing":"motorcycle"})
    edges=[]
    for leg in route["trip"]["legs"]:
        coords=decode_shape(leg["shape"])
        attrs=engine._call("trace_attributes",{"encoded_polyline":leg["shape"],"shape_match":"edge_walk","costing":"motorcycle"})
        for edge in attrs.get("edges",[]):
            idx=min(int(edge.get("end_shape_index",0)),len(coords)-1)
            edges.append({**edge,"end_point":coords[idx]})
    return route,edges

def compressed_way_sequence(edges):
    seq=[]
    for edge in edges:
        way=edge.get("way_id")
        if way is not None and (not seq or seq[-1]!=way):seq.append(way)
    return seq

def transition_details(edges,froms,tos,via):
    found=[]
    for x,y in zip(edges,edges[1:]):
        if x.get("way_id") not in froms:continue
        if x.get("way_id")==y.get("way_id") and x.get("forward")==y.get("forward"):continue
        near=haversine(x["end_point"],via)
        delta=(float(y.get("begin_heading",0))-float(x.get("end_heading",0))+180)%360-180
        found.append({
            "from_way":x.get("way_id"),
            "to_way":y.get("way_id"),
            "via_distance_m":round(near,2),
            "delta_deg":round(delta,1),
            "to_member":y.get("way_id") in tos,
            "from_forward":x.get("forward"),
            "to_forward":y.get("forward")
        })
    return found

results=[]
with engine.db() as db:
    rules=db.execute("SELECT * FROM turn_restrictions ORDER BY id").fetchall()
    for rule in rules:
        if rule["id"] not in target_ids:continue
        froms=json.loads(rule["from_ways"]);tos=json.loads(rule["to_ways"])
        vias=json.loads(rule["via_nodes"]);viaways=json.loads(rule["via_ways"])
        source_status=audit_by_id.get(rule["id"],{}).get("status")
        item={"id":rule["id"],"restriction":rule["restriction"],"from":froms,"to":tos,"via_nodes":vias,"via_ways":viaways,"source_audit_status":source_status,"probes":[]}
        if source_status in ("source_incomplete","source_direction_conflict"):
            item["diagnostic_status"]=source_status
            item["reason"]=audit_by_id[rule["id"]].get("reason")
            results.append(item);continue
        if not froms or not tos or not (vias or viaways):
            item["diagnostic_status"]="source_incomplete"
            results.append(item);continue
        try:
            def way(wid):return db.execute("SELECT * FROM ways WHERE id=?",(wid,)).fetchone()
            first=way(froms[0]);last=way(tos[0])
            fline=json.loads(first["coordinates"]);tline=json.loads(last["coordinates"])
            ftags=json.loads(first["tags"]);ttags=json.loads(last["tags"])
            if vias:
                node=db.execute("SELECT longitude,latitude FROM nodes WHERE id=?",(vias[0],)).fetchone()
                entry=exit=list(node)
            else:
                fw=way(viaways[0]);lw=way(viaways[-1])
                shared=set(json.loads(first["node_ids"]))&set(json.loads(fw["node_ids"]))
                shared_end=set(json.loads(last["node_ids"]))&set(json.loads(lw["node_ids"]))
                if not shared or not shared_end:raise ValueError("Restriction members do not connect")
                entry=list(db.execute("SELECT longitude,latitude FROM nodes WHERE id=?",(min(shared),)).fetchone())
                exit=list(db.execute("SELECT longitude,latitude FROM nodes WHERE id=?",(min(shared_end),)).fetchone())
            distances=[2,3,5,8,12,20,35,60]
            for distance in distances:
                start=point_on_branch(fline,entry,distance,True,ftags)
                end=point_on_branch(tline,exit,35 if froms==tos and vias else distance,False,ttags)
                if haversine(start,entry)<.5 or haversine(end,exit)<.5:continue
                query=[
                    {"lon":start[0],"lat":start[1],"heading":round(heading(start,entry)),"heading_tolerance":20,"radius":8,"node_snap_tolerance":0},
                    {"lon":end[0],"lat":end[1],"heading":round(heading(exit,end)),"heading_tolerance":20,"radius":8,"node_snap_tolerance":0}
                ]
                probe={
                    "distance_probe_m":distance,
                    "query":query,
                    "start_candidates":locate_way_ids(query[0]),
                    "end_candidates":locate_way_ids(query[1])
                }
                probe["start_member_anchor"]=any(x.get("way_id") in froms for x in probe["start_candidates"] if isinstance(x,dict))
                probe["end_member_anchor"]=any(x.get("way_id") in tos for x in probe["end_candidates"] if isinstance(x,dict))
                try:
                    route,edges=route_edges(query)
                    probe["route_distance_m"]=round(route["trip"]["summary"]["length"]*1000,1)
                    probe["route_way_sequence"]=compressed_way_sequence(edges)[:40]
                    probe["transitions_from_member"]=transition_details(edges,froms,tos,entry)
                    probe["anchored_route_start"]=bool(edges and edges[0].get("way_id") in froms)
                    probe["anchored_route_end"]=bool(edges and edges[-1].get("way_id") in tos)
                except Exception as error:
                    probe["route_error"]=str(error)
                item["probes"].append(probe)
            anchored=[x for x in item["probes"] if x.get("start_member_anchor") and x.get("end_member_anchor")]
            transitions=[t for x in anchored for t in x.get("transitions_from_member",[])]
            item["diagnostic_status"]="anchored_probes_available" if anchored else "no_member_anchor"
            item["anchored_probe_count"]=len(anchored)
            item["nearest_transition_to_via_m"]=min((t["via_distance_m"] for t in transitions),default=None)
            item["member_transition_count"]=sum(bool(x.get("transitions_from_member")) for x in anchored)
        except Exception as error:
            item["diagnostic_status"]="diagnostic_error";item["reason"]=str(error)
        results.append(item)

report={
    "dataset":engine.metadata(),
    "source_audit_summary":audit.get("summary",{}),
    "release_gate_changed":False,
    "rules":results
}
Path(a.output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
print(json.dumps({
    "rules":len(results),
    "status":{s:sum(r.get("diagnostic_status")==s for r in results) for s in sorted({r.get("diagnostic_status") for r in results})},
    "anchored_probes":sum(r.get("anchored_probe_count",0) for r in results),
    "release_gate_changed":False
},ensure_ascii=False))
