#!/usr/bin/env python3
"""Road-category queries selected from installed OSM, never fabricated road geometry."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'queuego-map-engine'))
from core import Engine
p=argparse.ArgumentParser();p.add_argument('--database',required=True);p.add_argument('--config',required=True);p.add_argument('--output',required=True);a=p.parse_args();engine=Engine(a.database,a.config)
cases={
 'city_street':"road_class='residential' AND abs(json_extract(geometry,'$[0][1]')-14.993)<.03 AND abs(json_extract(geometry,'$[0][0]')-103.103)<.03",
 'nang_rong_street':"road_class='residential' AND abs(json_extract(geometry,'$[0][1]')-14.6341724)<.02 AND abs(json_extract(geometry,'$[0][0]')-102.793082)<.02",
 'satuek_street':"road_class='residential' AND abs(json_extract(geometry,'$[0][1]')-15.2944564)<.02 AND abs(json_extract(geometry,'$[0][0]')-103.2901529)<.02",
 'tunnel':"tunnel='yes'",
 'rural_residential':"road_class='residential' AND abs(json_extract(geometry,'$[0][1]')-14.993)>.2",
 'rural_road':"road_class='unclassified' AND abs(json_extract(geometry,'$[0][1]')-14.993)>.2",
 'divided_highway':"road_class='trunk' AND oneway IN ('yes','1')",
 'service_road':"road_class='service' AND coalesce(access,'') NOT IN ('no','private') AND coalesce(service,'')!='driveway'",
 'bridge':"bridge='yes'",
 'ramp':"ramp=1",
 'traffic_signals':"(from_node IN (SELECT id FROM nodes WHERE traffic_signal=1) OR to_node IN (SELECT id FROM nodes WHERE traffic_signal=1))",
 'railway_crossing':"way_id IN (SELECT w.id FROM ways w,json_each(w.node_ids) n WHERE n.value IN (SELECT id FROM nodes WHERE railway='level_crossing'))"
}
results=[]
for name,condition in cases.items():
    with engine.db() as db:rows=db.execute('SELECT * FROM segments WHERE distance_m>100 AND ('+condition+') ORDER BY rowid LIMIT 25').fetchall()
    attempts=[];passed=None
    for row in rows:
        geometry=json.loads(row['geometry']);points=[{'lon':p[0],'lat':p[1]} for p in [geometry[0],geometry[-1]]]
        try:
            route=engine.route({'points':points})['routes'][0]
            traversed=row['way_id'] in route['osm_way_ids'];attempts.append({'osm_way_id':row['way_id'],'traversed':traversed})
            if traversed and route['distance']>0:
                passed={'case':name,'status':'passed','osm_way_id':row['way_id'],'segment':row['id'],'points':points,'source_tags':json.loads(row['attributes']),'route_distance_m':route['distance'],'traversed_way_ids':route['osm_way_ids'],'traversed_edge_ids':route['osm_edge_ids'],'expected':'route traverses source way on an allowed graph edge','source':'OpenStreetMap','last_verified':row['last_verified']};break
        except Exception as error:attempts.append({'osm_way_id':row['way_id'],'error':str(error)})
    results.append(passed or {'case':name,'status':'open','attempts':attempts})
# Actual inaccessible ways must not become traversed through-routes.
with engine.db() as db:closed=db.execute("SELECT * FROM segments WHERE distance_m>100 AND (motorcycle_access='no' OR (access='no' AND motorcycle_access IS NULL)) ORDER BY rowid LIMIT 5").fetchall()
for row in closed:
    geometry=json.loads(row['geometry']);points=[{'lon':p[0],'lat':p[1]} for p in [geometry[0],geometry[-1]]]
    try:
        route=engine.route({'points':points})['routes'][0];assert row['way_id'] not in route['osm_way_ids'],'Inaccessible way traversed';status='passed_excluded'
    except ValueError:status='passed_unavailable'
    results.append({'case':'inaccessible_road','osm_way_id':row['way_id'],'points':points,'status':status,'tags':json.loads(row['attributes'])})
# Explicit private access is not permission for QueueGo to enter; retain source overrides.
with engine.db() as db:
    private=db.execute("SELECT id,coordinates,tags FROM ways WHERE json_extract(tags,'$.access')='private' AND json_extract(tags,'$.motorcycle') IS NULL AND json_extract(tags,'$.motor_vehicle') IS NULL AND json_extract(tags,'$.vehicle') IS NULL ORDER BY id LIMIT 5").fetchall()
for row in private:
    geometry=json.loads(row['coordinates']);points=[{'lon':p[0],'lat':p[1]} for p in [geometry[0],geometry[-1]]]
    try:
        route=engine.route({'points':points})['routes'][0]
        assert row['id'] not in route['osm_way_ids'],'Private road traversed without source access permission'
        status='passed_excluded'
    except ValueError:status='passed_unavailable'
    results.append({'case':'private_road','osm_way_id':row['id'],'points':points,'status':status,'expected':'not traversed without explicit profile access','tags':json.loads(row['tags'])})
assert len(private)==5,'Actual private-road source cases missing'
report={'dataset':engine.metadata(),'cases':results,'physical_road_test':False,'limitations':['service road is not certified as frontage without source tags/field verification','rural residential category does not certify a named village-lane field test','U-turn and parallel-road GPS recordings still required','no background/signal-loss physical certification']}
Path(a.output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'cases':len(results),'open':[r['case'] for r in results if r['status']=='open']}))

if any(r['status']=='open' for r in results) or len(closed)<5:raise SystemExit('Required actual road-category/access coverage missing')
