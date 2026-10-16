#!/usr/bin/env python3
"""Actual installed-graph checks. No network routing or substitute Engine objects."""
import argparse,json,sys,time,sqlite3
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'queuego-map-engine'))
from core import Engine,haversine
p=argparse.ArgumentParser();p.add_argument('--database',required=True);p.add_argument('--config',required=True);p.add_argument('--output');args=p.parse_args()
engine=Engine(args.database,args.config);checks=[]
def check(name,value,detail=None):
    if not value:raise AssertionError(name+': '+str(detail))
    checks.append({'name':name,'passed':True,'detail':detail})
with engine.db() as db:
    ids=[11593547727,1661819325,12712892020]
    points=[dict(db.execute('SELECT latitude AS lat,longitude AS lon FROM nodes WHERE id=?',(i,)).fetchone()) for i in ids]
routes=engine.route({'points':points[:2],'profile':'motorcycle'},True)['routes'];route=routes[0]
check('real A to B',route['distance']>1000,{'nodes':ids[:2],'distance_m':route['distance']})
check('alternatives',len(routes)>=2,len(routes))
for preference in ('shortest','fewer_signals','reliable'):
    result=engine.route({'points':points[:2],'preference':preference},True)
    check(preference,bool(result['routes']))
multi=engine.route({'points':points})['routes'][0];check('multi stop',len(multi['legs'])==2)
matrix=engine.matrix({'points':points});check('matrix',len(matrix['durations'])==3 and matrix['durations'][0][1]>0)
optimized=engine.optimize({'points':points});check('optimization fixed endpoints',optimized['order'][0]==0 and optimized['order'][-1]==2,optimized['order'])
check('signal traversal count',route['signalCount']>0,route['signalCount'])
check('geocode',len(engine.geocode({'q':'บุรีรัมย์'}))>0)
check('nearest',bool(engine.nearest(points[0])['waypoints']))
trace=[{'lon':lon,'lat':lat,'accuracy':10} for lon,lat in route['geometry']['coordinates'][:12]]
match=engine.match({'points':trace});check('HMM matching',match['accepted'] and match['matching_method']=='Valhalla HMM')
start=route['geometry']['coordinates'][0];now=time.time()
fix={'route_id':route['route_id'],'lon':start[0],'lat':start[1],'accuracy':10,'timestamp':now}
progress=engine.progress(fix);check('route progress',progress['accepted'] and progress['remaining_distance_m']>1000)
check('negative GPS accuracy rejected',not engine.progress({**fix,'accuracy':-1})['accepted'])
check('poor GPS accuracy rejected',not engine.progress({**fix,'accuracy':100})['accepted'])
check('stale GPS rejected',not engine.progress({**fix,'context':progress['context']})['accepted'])
# Controlled timing around an actual different OSM road node; explicitly NOT a recorded road trace.
far=points[2];context={};events=[]
for i in range(3):
    result=engine.progress({**fix,**far,'timestamp':now+i,'context':context});context=result['context'];events.append(result['reroute'])
check('off route requires repeated evidence',events==[False,False,True],events)
rerouted=engine.route({'points':[far,points[1]]})['routes'][0];check('off route to actual reroute',rerouted['distance']>0)
result=engine.progress({**fix,**far,'timestamp':now+3,'context':context});check('reroute cooldown',not result['reroute'])
end=route['geometry']['coordinates'][-1];context={};events=[]
for i in range(3):
    result=engine.progress({**fix,'lon':end[0],'lat':end[1],'timestamp':now+i,'context':context});context=result['context'];events.append(result['arrived'])
check('arrival repeated evidence',events==[False,False,True],events)
# Validate actual edge orientation for reverse requests around real one-way roads.
with engine.db() as db:
    ways=db.execute("SELECT id,coordinates,tags FROM ways WHERE json_extract(tags,'$.oneway') IN ('yes','1','-1') AND json_extract(tags,'$.highway') IN ('residential','tertiary','secondary','primary','trunk') ORDER BY id LIMIT 12").fetchall()
audited=[]
for way in ways:
    line=json.loads(way['coordinates'])
    if len(line)<2 or haversine(line[0],line[-1])<30:continue
    query=[{'lon':x[0],'lat':x[1]} for x in [line[-1],line[0]]]
    result=engine._call('route',{'locations':query,'costing':'motorcycle'})
    checked=0
    for leg in result['trip']['legs']:
        attrs=engine._call('trace_attributes',{'encoded_polyline':leg['shape'],'shape_match':'edge_walk','costing':'motorcycle'})
        with engine.db() as db:
            for edge in attrs.get('edges',[]):
                row=db.execute('SELECT tags FROM ways WHERE id=?',(edge.get('way_id'),)).fetchone()
                if not row:continue
                tags=json.loads(row['tags']);direction=tags.get('oneway:motorcycle',tags.get('oneway'))
                if direction in ('yes','1','true','-1'):
                    check('oneway edge direction',bool(edge['forward'])==(direction!='-1'),{'way':edge['way_id'],'forward':edge['forward'],'oneway':direction});checked+=1
    audited.append({'query_way':way['id'],'directed_edges_checked':checked})
check('real one-way query coverage',len(audited)>=3,audited)
report={'dataset':engine.metadata(),'checks':checks,'physical_road_test':False,'trace_provenance':'OSM route-derived matching; controlled GPS/timing evidence checks are algorithm fixtures, not physical recordings','remaining_gates':['all turn relations and real road categories','browser/background restoration','offline activation/rollback','public deployment','Android integration']}
if args.output:Path(args.output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'checks':len(checks),'failures':0,'oneway_queries':len(audited)}))
