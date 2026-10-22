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
check('weak GPS over 35m rejected',not engine.progress({**fix,'accuracy':36})['accepted'])
check('poor GPS accuracy rejected',not engine.progress({**fix,'accuracy':100})['accepted'])
check('stale GPS rejected',not engine.progress({**fix,'context':progress['context']})['accepted'])
# Controlled timing around an actual different OSM road node; explicitly NOT a recorded road trace.
far=points[2];context={};events=[]
for stamp in (now,now+2.999,now+3.0):
    result=engine.progress({**fix,**far,'timestamp':stamp,'context':context});context=result['context'];events.append(result['reroute'])
check('off route requires three continuous seconds',events==[False,False,True],events)
check('reroute context uses duration evidence','off_route_since' in context and 'off_route_hits' not in context,context)
rerouted=engine.route({'points':[far,points[1]]})['routes'][0];check('off route to actual reroute',rerouted['distance']>0)
fresh_context=engine.progress({'route_id':rerouted['route_id'],**far,'accuracy':10,'timestamp':now+4,'context':{**context,'route_id':route['route_id'],'last_reroute_at':now+999,'arrival_hits':2}})
check('new route resets prior route navigation evidence',fresh_context['context']['route_id']==rerouted['route_id'] and fresh_context['context']['last_reroute_at']==0 and fresh_context['context']['arrival_hits']==0 and not fresh_context['reroute'])
cooldown_context=context;cooldown_events=[]
for stamp in (now+4,now+7,now+22.999):
    result=engine.progress({**fix,**far,'timestamp':stamp,'context':cooldown_context});cooldown_context=result['context'];cooldown_events.append(result['reroute'])
check('reroute cooldown blocks a sustained new off-route episode before twenty seconds',cooldown_events==[False,False,False],cooldown_events)
result=engine.progress({**fix,**far,'timestamp':now+23,'context':cooldown_context});check('reroute resumes after cooldown with sustained evidence',result['reroute'],result)
end=route['geometry']['coordinates'][-1];context={};events=[]
for i in range(3):
    result=engine.progress({**fix,'lon':end[0],'lat':end[1],'timestamp':now+i,'context':context});context=result['context'];events.append(result['arrived'])
check('arrival repeated evidence',events==[False,False,True],events)
# Time-labelled geometry is a controlled test using real OSM route positions, not recorded GPS.
timed=[];clock=now
for lon,lat in route['geometry']['coordinates'][:12]:
    if timed:clock+=max(1,haversine([timed[-1]['lon'],timed[-1]['lat']],[lon,lat])/8)
    timed.append({'lon':lon,'lat':lat,'accuracy':15,'speed':8,'timestamp':clock})
matched=engine.match({'points':timed});check('timestamp and accuracy aware HMM',matched['matching_method']=='Valhalla HMM')
check('latest inaccurate fix rejected',not engine.match({'points':timed+[{**timed[-1],'accuracy':100}]})['accepted'])
gap=engine.match({'points':[timed[0],{**timed[-1],'timestamp':clock+30}]});check('signal loss resets HMM trace',gap['matching_method']=='nearest_initialization')
context={};events=[]
for stamp in (now,now+2.999,now+3.0):
    result=engine.match({'points':[{**far,'accuracy':10,'timestamp':stamp}],'route_id':route['route_id'],'context':context});context=result['context'];events.append(result['reroute'])
check('matching preserves three-second raw off route evidence',events==[False,False,True],events)
with engine.db() as db:
    extra=[]
    for lat,lon in [(14.985,103.11),(15.015,103.11)]:
        row=db.execute("SELECT n.id,n.latitude AS lat,n.longitude AS lon FROM segments s JOIN nodes n ON n.id=s.from_node WHERE s.road_class='residential' ORDER BY (n.latitude-?)*(n.latitude-?)+(n.longitude-?)*(n.longitude-?) LIMIT 1",(lat,lat,lon,lon)).fetchone();extra.append(dict(row))
five=points+[{k:r[k] for k in ('lat','lon')} for r in extra]
check('five stop route',len(engine.route({'points':five})['routes'][0]['legs'])==4,ids+[r['id'] for r in extra])
check('five by five ETA matrix',len(engine.matrix({'points':five})['durations'])==5)
optimized=engine.optimize({'points':five});check('five stop optimization fixed endpoints',sorted(optimized['order'])==list(range(5)) and optimized['order'][0]==0 and optimized['order'][-1]==4,optimized['order'])
check('unverified source restrictions quarantined',set(engine.unresolved_rules)=={2148441,17320674,20613951,20613952} and set(engine.member_conflicting_rules)=={6982243} and set(engine.direction_conflicting_rules)=={1538817} and len(engine.quarantined_ways)==12)
loop_query=[{'lon':102.49269829386337,'lat':14.584844360887121,'heading':67,'heading_tolerance':12,'radius':8,'node_snap_tolerance':0},{'lon':102.49255232127798,'lat':14.58478590985722,'heading':248,'heading_tolerance':12,'radius':8,'node_snap_tolerance':0}]
loop_trip=engine._call('route',{'locations':loop_query,'costing':'motorcycle'})['trip']
loop_edges=engine.route_trace_edges(loop_trip['legs'][0],'motorcycle')
check('actual loop trace covers full routed length',abs(sum(e['length']*1000 for e in loop_edges)-loop_trip['summary']['length']*1000)<10)
check('actual loop trace retains traversed quarantined member',474747491 in {e['way_id'] for e in loop_edges})
try:
    engine._convert(loop_trip,'motorcycle',remember=False)
    check('actual loop quarantine enforced',False)
except ValueError as error:
    check('actual loop quarantine enforced','quarantined' in str(error),str(error))
with engine.db() as db:
    blocked_line=json.loads(db.execute('SELECT coordinates FROM ways WHERE id=474747491').fetchone()[0])
blocked_fix=blocked_line[len(blocked_line)//2]
nearest_blocked=engine.nearest({'lon':blocked_fix[0],'lat':blocked_fix[1]})
check('nearest excludes actual quarantined way',all(w['osm_way_id'] not in engine.quarantined_ways for w in nearest_blocked['waypoints']))
try:
    initialization=engine.match({'points':[{'lon':blocked_fix[0],'lat':blocked_fix[1],'accuracy':5}]})
    check('single fix never initializes on quarantined road',any(initialization.get('snapped_position')==w['location'] and w['osm_way_id'] not in engine.quarantined_ways and w['distance']<=35 for w in nearest_blocked['waypoints']))
except ValueError as error:
    check('single quarantined fix fails safely',True,str(error))
runtime_no_u_turn_ids={r['relation_id'] for r in engine.runtime_no_u_turn_rules}
check('same-way no-U-turn runtime enforcement loaded',6982135 in runtime_no_u_turn_ids and 6982244 in runtime_no_u_turn_ids and len(runtime_no_u_turn_ids)>=10,sorted(runtime_no_u_turn_ids))
check('planned alternatives avoid unverified rule members',all(not engine.quarantined_ways.intersection(r['osm_way_ids']) for r in routes))
with engine.db() as db:blocked=db.execute('SELECT coordinates FROM ways WHERE id=170350136').fetchone()
blocked_line=json.loads(blocked['coordinates']);blocked_points=[{'lon':q[0],'lat':q[1]} for q in [blocked_line[0],blocked_line[-1]]]
try:
    blocked_routes=engine.route({'points':blocked_points},True)['routes'];check('unverified junction cannot yield unsafe route',all(not engine.quarantined_ways.intersection(r['osm_way_ids']) for r in blocked_routes))
except ValueError:check('unverified junction route fails closed',True)
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
        attrs={'edges':engine.route_trace_edges(leg,'motorcycle')}
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
