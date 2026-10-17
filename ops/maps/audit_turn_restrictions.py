#!/usr/bin/env python3
"""Adversarial approach/departure queries from actual OSM restriction members.
No sample is promoted to physical verification. Uncovered cases remain explicit.
"""
import argparse,json,math,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'queuego-map-engine'))
from core import Engine,decode_shape,haversine
p=argparse.ArgumentParser();p.add_argument('--database',required=True);p.add_argument('--config',required=True);p.add_argument('--output',required=True);a=p.parse_args();v=Engine(a.database,a.config)
def heading(a,b):return (math.degrees(math.atan2((b[0]-a[0])*math.cos(math.radians(a[1])),b[1]-a[1]))+360)%360
def on_branch(line,origin,metres,tags=None,approach=False):
    index=min(range(len(line)),key=lambda i:haversine(line[i],origin));branches=[line[index::-1],line[index:]]
    direction=(tags or {}).get('oneway:motorcycle',(tags or {}).get('oneway'))
    if direction in ('yes','1','true','-1'):
        forward=direction!='-1';branch=branches[0 if approach==forward else 1]
    else:branch=max(branches,key=lambda b:sum(haversine(x,y) for x,y in zip(b,b[1:])))
    remaining=metres
    for x,y in zip(branch,branch[1:]):
        length=haversine(x,y)
        if length>=remaining:
            f=remaining/length;return [x[0]+(y[0]-x[0])*f,x[1]+(y[1]-x[1])*f]
        remaining-=length
    return branch[-1]
results=[]
with v.db() as db:rules=db.execute('SELECT * FROM turn_restrictions ORDER BY id').fetchall()
for rule in rules:
    froms=json.loads(rule['from_ways']);tos=json.loads(rule['to_ways']);vias=json.loads(rule['via_nodes']);viaways=json.loads(rule['via_ways']);kind=rule['restriction'] or ''
    result={'id':rule['id'],'restriction':kind,'from':froms,'to':tos,'via_nodes':vias,'via_ways':viaways}
    try:
        with v.db() as db:
            def way(w):return db.execute('SELECT * FROM ways WHERE id=?',(w,)).fetchone()
            first=way(froms[0]);last=way(tos[0]);fline=json.loads(first['coordinates']);tline=json.loads(last['coordinates'])
            if vias:
                node=db.execute('SELECT longitude,latitude FROM nodes WHERE id=?',(vias[0],)).fetchone();entry=exit=list(node)
            elif viaways:
                fw=way(viaways[0]);lw=way(viaways[-1]);shared=set(json.loads(first['node_ids']))&set(json.loads(fw['node_ids']));shared_end=set(json.loads(last['node_ids']))&set(json.loads(lw['node_ids']))
                if not shared or not shared_end:raise ValueError('Restriction members do not connect')
                entry=list(db.execute('SELECT longitude,latitude FROM nodes WHERE id=?',(min(shared),)).fetchone());exit=list(db.execute('SELECT longitude,latitude FROM nodes WHERE id=?',(min(shared_end),)).fetchone())
            else:raise ValueError('Missing via members')
        start=on_branch(fline,entry,5,json.loads(first['tags']),True);end=on_branch(tline,exit,35 if froms==tos and vias else 15,json.loads(last['tags']),False)
        if haversine(start,entry)<1 or haversine(end,exit)<1 or haversine(start,end)<1:raise ValueError('Insufficient approach/departure geometry')
        query=[{'lon':start[0],'lat':start[1],'heading':round(heading(start,entry)),'heading_tolerance':15,'radius':5,'node_snap_tolerance':0},{'lon':end[0],'lat':end[1],'heading':round(heading(exit,end)),'heading_tolerance':15,'radius':5,'node_snap_tolerance':0}];result['query']=query
        route=v._call('route',{'locations':query,'costing':'motorcycle'});edges=[];shapes=[]
        for leg in route['trip']['legs']:
            coords=decode_shape(leg['shape']);attrs=v._call('trace_attributes',{'encoded_polyline':leg['shape'],'shape_match':'edge_walk','costing':'motorcycle'})
            for e in attrs['edges']:edges.append({**e,'end_point':coords[min(e['end_shape_index'],len(coords)-1)]})
        violations=[];examined=0
        if vias:
            for x,y in zip(edges,edges[1:]):
                if x.get('way_id') not in froms or haversine(x['end_point'],entry)>3:continue
                if x['way_id']==y.get('way_id') and x.get('forward')==y.get('forward'):continue
                examined+=1;to=y.get('way_id')
                prohibited=(kind.startswith('only_') and to not in tos) or (kind.startswith('no_') and to in tos)
                if kind=='no_u_turn' and froms==tos:prohibited=to in tos and x.get('forward')!=y.get('forward')
                # A to-way can continue on BOTH sides of an interior via node.
                # no_left_turn prohibits its left branch, not the legal right branch.
                delta=(y['begin_heading']-x['end_heading']+180)%360-180
                if kind=='no_left_turn':prohibited=prohibited and -165<delta<-30
                elif kind=='no_right_turn':prohibited=prohibited and 30<delta<165
                elif kind=='no_straight_on':prohibited=prohibited and abs(delta)<=45
                elif kind=='no_u_turn':prohibited=prohibited and abs(delta)>=150
                if prohibited:violations.append([x['way_id'],to,delta])
        else:
            sequence=[]
            for e in edges:
                if not sequence or sequence[-1]!=e['way_id']:sequence.append(e['way_id'])
            forbidden=[froms[0],*viaways,tos[0]]
            for i in range(len(sequence)-len(forbidden)+1):
                if sequence[i:i+len(forbidden)]==forbidden:violations.append(forbidden)
            examined=sum(e.get('way_id') in froms and haversine(e['end_point'],entry)<3 for e in edges)
        result.update(status='failed' if violations else 'passed_approach' if examined else 'uncovered_approach',examined_transitions=examined,violations=violations,distance_m=route['trip']['summary']['length']*1000)
    except Exception as error:result.update(status='uncovered',reason=str(error))
    results.append(result)
summary={k:sum(r['status']==k for r in results) for k in sorted({r['status'] for r in results})}
report={'dataset':v.metadata(),'rules':results,'summary':summary,'all_rules_gate':all(r['status']=='passed_approach' for r in results),'physical_verification':False,'limitations':'Direction-constrained OSM member queries; uncovered approaches are never counted as enforcement proof. Exceptions/conditional rules require separate profile/time fixtures.'}
Path(a.output).write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n');print(json.dumps(summary))
