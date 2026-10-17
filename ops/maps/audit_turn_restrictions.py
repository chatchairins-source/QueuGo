#!/usr/bin/env python3
"""Adversarial approach/departure queries from actual OSM restriction members.

The audit never promotes malformed or direction-conflicting source relations. For a
valid relation it tries several short approach distances because routing engines can
correlate a point a few metres from a junction onto an adjacent graph edge. A rule
passes only when the actual routed first/last OSM way IDs anchor to the declared
members and no prohibited near-via transition is observed.
"""
import argparse,json,math,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'queuego-map-engine'))
from core import Engine,decode_shape,haversine

p=argparse.ArgumentParser()
p.add_argument('--database',required=True)
p.add_argument('--config',required=True)
p.add_argument('--output',required=True)
a=p.parse_args()
v=Engine(a.database,a.config)

PROBE_DISTANCES=(5,12,20,35)
JUNCTION_TOLERANCE_M=12

def heading(a,b):
    return (math.degrees(math.atan2((b[0]-a[0])*math.cos(math.radians(a[1])),b[1]-a[1]))+360)%360

def on_branch(line,origin,metres,tags=None,approach=False):
    index=min(range(len(line)),key=lambda i:haversine(line[i],origin))
    branches=[line[index::-1],line[index:]]
    direction=(tags or {}).get('oneway:motorcycle',(tags or {}).get('oneway'))
    if direction in ('yes','1','true','-1'):
        forward=direction!='-1'
        branch=branches[0 if approach==forward else 1]
    else:
        branch=max(branches,key=lambda b:sum(haversine(x,y) for x,y in zip(b,b[1:])))
    remaining=metres
    for x,y in zip(branch,branch[1:]):
        length=haversine(x,y)
        if length>=remaining and length>0:
            f=remaining/length
            return [x[0]+(y[0]-x[0])*f,x[1]+(y[1]-x[1])*f]
        remaining-=length
    return branch[-1]

def one_way_allows(db,wid,via,approach):
    row=db.execute('SELECT node_ids,tags FROM ways WHERE id=?',(wid,)).fetchone()
    if not row:return False
    nodes=json.loads(row['node_ids']);tags=json.loads(row['tags'])
    if via not in nodes:return False
    direction=tags.get('oneway:motorcycle',tags.get('oneway'))
    if direction not in ('yes','1','true','-1'):return True
    index=nodes.index(via)
    if direction=='-1':return index<len(nodes)-1 if approach else index>0
    return index>0 if approach else index<len(nodes)-1

def route_edges(route):
    edges=[]
    for leg in route['trip']['legs']:
        coords=decode_shape(leg['shape'])
        attrs=v._call('trace_attributes',{'encoded_polyline':leg['shape'],'shape_match':'edge_walk','costing':'motorcycle'})
        for edge in attrs.get('edges',[]):
            idx=min(int(edge.get('end_shape_index',0)),len(coords)-1)
            edges.append({**edge,'end_point':coords[idx]})
    return edges

def prohibited_transition(kind,froms,tos,x,y):
    to=y.get('way_id')
    prohibited=(kind.startswith('only_') and to not in tos) or (kind.startswith('no_') and to in tos)
    if kind=='no_u_turn' and froms==tos:
        prohibited=to in tos and x.get('forward')!=y.get('forward')
    delta=(float(y.get('begin_heading',0))-float(x.get('end_heading',0))+180)%360-180
    if kind=='no_left_turn':prohibited=prohibited and -165<delta<-30
    elif kind=='no_right_turn':prohibited=prohibited and 30<delta<165
    elif kind=='no_straight_on':prohibited=prohibited and abs(delta)<=45
    elif kind=='no_u_turn':prohibited=prohibited and abs(delta)>=150
    return prohibited,delta

results=[]
with v.db() as db:
    rules=db.execute('SELECT * FROM turn_restrictions ORDER BY id').fetchall()

for rule in rules:
    froms=json.loads(rule['from_ways']);tos=json.loads(rule['to_ways'])
    vias=json.loads(rule['via_nodes']);viaways=json.loads(rule['via_ways'])
    kind=rule['restriction'] or ''
    result={'id':rule['id'],'restriction':kind,'from':froms,'to':tos,'via_nodes':vias,'via_ways':viaways}

    if not froms or not tos or not (vias or viaways):
        result.update(status='source_incomplete',reason='Restriction is missing required from/to/via members')
        results.append(result);continue

    if vias and not viaways and len(vias)==1:
        via=vias[0]
        with v.db() as db:
            def member_connected(wid):
                row=db.execute('SELECT node_ids FROM ways WHERE id=?',(wid,)).fetchone()
                return bool(row and via in json.loads(row['node_ids']))
            member_ok=all(member_connected(wid) for wid in [*froms,*tos])
            from_ok=member_ok and all(one_way_allows(db,wid,via,True) for wid in froms)
            to_ok=member_ok and all(one_way_allows(db,wid,via,False) for wid in tos)
        if not member_ok:
            result.update(status='source_member_conflict',reason='Declared from/to member does not connect to the via node in the installed source geometry')
            results.append(result);continue
        if not from_ok or not to_ok:
            result.update(status='source_direction_conflict',reason='Restriction members conflict with current one-way travel direction',from_direction_ok=from_ok,to_direction_ok=to_ok)
            results.append(result);continue

    try:
        with v.db() as db:
            def way(wid):return db.execute('SELECT * FROM ways WHERE id=?',(wid,)).fetchone()
            first=way(froms[0]);last=way(tos[0])
            if not first or not last:raise ValueError('Restriction member way missing from installed database')
            fline=json.loads(first['coordinates']);tline=json.loads(last['coordinates'])
            ftags=json.loads(first['tags']);ttags=json.loads(last['tags'])
            if vias:
                node=db.execute('SELECT longitude,latitude FROM nodes WHERE id=?',(vias[0],)).fetchone()
                if not node:raise ValueError('Via node missing from installed database')
                entry=exit=list(node)
            elif viaways:
                fw=way(viaways[0]);lw=way(viaways[-1])
                shared=set(json.loads(first['node_ids']))&set(json.loads(fw['node_ids']))
                shared_end=set(json.loads(last['node_ids']))&set(json.loads(lw['node_ids']))
                if not shared or not shared_end:raise ValueError('Restriction members do not connect')
                entry=list(db.execute('SELECT longitude,latitude FROM nodes WHERE id=?',(min(shared),)).fetchone())
                exit=list(db.execute('SELECT longitude,latitude FROM nodes WHERE id=?',(min(shared_end),)).fetchone())
            else:raise ValueError('Missing via members')

        probes=[];passed=None;failed=None
        for probe_m in PROBE_DISTANCES:
            start=on_branch(fline,entry,probe_m,ftags,True)
            end_distance=max(35,probe_m) if froms==tos and vias else probe_m
            end=on_branch(tline,exit,end_distance,ttags,False)
            if haversine(start,entry)<1 or haversine(end,exit)<1 or haversine(start,end)<1:
                probes.append({'probe_m':probe_m,'status':'insufficient_geometry'})
                continue
            query=[
                {'lon':start[0],'lat':start[1],'heading':round(heading(start,entry)),'heading_tolerance':20,'radius':8,'node_snap_tolerance':0},
                {'lon':end[0],'lat':end[1],'heading':round(heading(exit,end)),'heading_tolerance':20,'radius':8,'node_snap_tolerance':0}
            ]
            try:
                route=v._call('route',{'locations':query,'costing':'motorcycle'})
                edges=route_edges(route)
            except Exception as error:
                probes.append({'probe_m':probe_m,'status':'route_error','reason':str(error),'query':query})
                continue

            anchored_start=bool(edges and edges[0].get('way_id') in froms)
            anchored_end=bool(edges and edges[-1].get('way_id') in tos)
            probe={'probe_m':probe_m,'query':query,'anchored_start':anchored_start,'anchored_end':anchored_end,'distance_m':route['trip']['summary']['length']*1000}
            if not (anchored_start and anchored_end):
                probe['status']='not_member_anchored';probes.append(probe);continue

            violations=[];examined=0
            if vias:
                for x,y in zip(edges,edges[1:]):
                    if x.get('way_id') not in froms:continue
                    if x.get('way_id')==y.get('way_id') and x.get('forward')==y.get('forward'):continue
                    near=haversine(x['end_point'],entry)
                    if near>JUNCTION_TOLERANCE_M:continue
                    examined+=1
                    prohibited,delta=prohibited_transition(kind,froms,tos,x,y)
                    if prohibited:violations.append([x.get('way_id'),y.get('way_id'),round(delta,1),round(near,2)])
            else:
                sequence=[]
                for edge in edges:
                    if not sequence or sequence[-1]!=edge.get('way_id'):sequence.append(edge.get('way_id'))
                forbidden=[froms[0],*viaways,tos[0]]
                for i in range(len(sequence)-len(forbidden)+1):
                    if sequence[i:i+len(forbidden)]==forbidden:violations.append(forbidden)
                examined=sum(edge.get('way_id') in froms and haversine(edge['end_point'],entry)<=JUNCTION_TOLERANCE_M for edge in edges)

            # Some no-U-turns stay on the same long OSM way, so trace_attributes may
            # not emit a distinct near-via way transition. A member-anchored route
            # that is a large detour compared with the raw endpoint separation is
            # evidence that the engine did not perform the prohibited immediate turn.
            detour_evidence=False
            if kind=='no_u_turn' and froms==tos and not violations and examined==0:
                direct=max(1,haversine(start,end));distance=route['trip']['summary']['length']*1000
                detour_evidence=distance>=direct*3+50
                if detour_evidence:examined=1

            probe.update(status='failed' if violations else 'passed_approach' if examined else 'uncovered_approach',examined_transitions=examined,violations=violations,detour_evidence=detour_evidence)
            probes.append(probe)
            if violations:
                failed=probe;break
            if examined:
                passed=probe;break

        result['probes']=probes
        if failed:
            result.update(status='failed',query=failed['query'],examined_transitions=failed['examined_transitions'],violations=failed['violations'],distance_m=failed['distance_m'],probe_m=failed['probe_m'])
        elif passed:
            result.update(status='passed_approach',query=passed['query'],examined_transitions=passed['examined_transitions'],violations=[],distance_m=passed['distance_m'],probe_m=passed['probe_m'],detour_evidence=passed.get('detour_evidence',False))
        else:
            result.update(status='uncovered_approach',reason='No member-anchored probe produced inspectable near-via transition or strong same-way U-turn detour evidence')
    except Exception as error:
        result.update(status='uncovered',reason=str(error))
    results.append(result)

summary={k:sum(r['status']==k for r in results) for k in sorted({r['status'] for r in results})}
report={
    'dataset':v.metadata(),
    'rules':results,
    'summary':summary,
    'all_rules_gate':all(r['status']=='passed_approach' for r in results),
    'physical_verification':False,
    'limitations':'Direction-constrained OSM member queries with multiple short member-anchoring probes; source-invalid relations are never promoted. Exceptions/conditional rules require separate profile/time fixtures. Same-way no-U-turn detour evidence proves engine avoidance for the probe, not field signage.'
}
Path(a.output).write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
print(json.dumps(summary))
