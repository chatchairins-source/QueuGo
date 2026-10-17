"""QueueGo's shared map service core. One real Valhalla graph; no public-routing fallback."""
from collections import OrderedDict
import hashlib
import json
import math
from pathlib import Path
import sqlite3
import threading
import time
from contextlib import contextmanager

PROFILES={'motorcycle':'motorcycle','car':'auto','rider_delivery':'motorcycle','ride_passenger':'auto','express':'motorcycle'}
EXPECTED_SIGNAL_DELAY_SECONDS=20
MAX_POINTS=20


def haversine(a,b):
    lon1,lat1,lon2,lat2=map(math.radians,[a[0],a[1],b[0],b[1]])
    q=math.sin((lat2-lat1)/2)**2+math.cos(lat1)*math.cos(lat2)*math.sin((lon2-lon1)/2)**2
    return 12742017.6*math.atan2(math.sqrt(q),math.sqrt(max(0,1-q)))


def decode_shape(shape):
    if isinstance(shape,dict): return shape['coordinates']
    result=[];index=0;latitude=longitude=0
    while index<len(shape):
        values=[]
        for _ in range(2):
            number=shift=0
            while True:
                if index>=len(shape):raise ValueError('Truncated route geometry')
                value=ord(shape[index])-63;index+=1;number|=(value&31)<<shift;shift+=5
                if shift>35:raise ValueError('Invalid route geometry')
                if value<32:break
            values.append(~(number>>1) if number&1 else number>>1)
        latitude+=values[0];longitude+=values[1];result.append([longitude/1e6,latitude/1e6])
    return result


def projection(point,line,heading=None):
    best=None;progress=0.0;total=0.0
    for index,(a,b) in enumerate(zip(line,line[1:])):
        length=haversine(a,b);total+=length
        lat=math.radians((point[1]+a[1]+b[1])/3);scale=111320*math.cos(lat)
        dx=(b[0]-a[0])*scale;dy=(b[1]-a[1])*110540
        px=(point[0]-a[0])*scale;py=(point[1]-a[1])*110540
        t=max(0,min(1,(px*dx+py*dy)/(dx*dx+dy*dy))) if dx*dx+dy*dy else 0
        distance=math.hypot(px-t*dx,py-t*dy)
        bearing=(math.degrees(math.atan2(dx,dy))+360)%360
        compatible=heading is None or abs((bearing-heading+180)%360-180)<=100
        score=distance+(0 if compatible else 45)
        if best is None or score<best['score']:
            best={'point':[a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])],'distance_m':distance,'progress_m':progress+t*length,'shape_index':index,'bearing':bearing,'score':score}
        progress+=length
    if best is None:raise ValueError('Route needs a valid geometry')
    best['remaining_m']=max(0,total-best['progress_m']);best['total_m']=total
    return best


def thai_maneuver(maneuver):
    labels={1:'เริ่มเดินทาง',2:'เริ่มเดินทางทางขวา',3:'เริ่มเดินทางทางซ้าย',4:'ถึงจุดหมาย',5:'จุดหมายอยู่ทางขวา',6:'จุดหมายอยู่ทางซ้าย',7:'เดินทางต่อ',8:'ตรงต่อไป',9:'เบี่ยงขวา',10:'เลี้ยวขวา',11:'เลี้ยวขวาหักศอก',12:'กลับรถทางขวา',13:'กลับรถทางซ้าย',14:'เลี้ยวซ้ายหักศอก',15:'เลี้ยวซ้าย',16:'เบี่ยงซ้าย',17:'ขึ้นทางเชื่อมตรงไป',18:'ขึ้นทางเชื่อมทางขวา',19:'ขึ้นทางเชื่อมทางซ้าย',20:'ใช้ทางออกขวา',21:'ใช้ทางออกซ้าย',22:'ชิดทางตรง',23:'ชิดขวา',24:'ชิดซ้าย',25:'รวมเข้าทางหลัก',26:'เข้าวงเวียน',27:'ออกจากวงเวียน',28:'ขึ้นเรือข้ามฟาก',29:'ออกจากเรือข้ามฟาก'}
    label=labels.get(maneuver.get('type'),maneuver.get('instruction','เดินทางต่อ'))
    if maneuver.get('roundabout_exit_count'):label+=' ใช้ทางออกที่ '+str(maneuver['roundabout_exit_count'])
    names=maneuver.get('street_names') or []
    if names and maneuver.get('type') not in (4,5,6):label+=' · '+names[0]
    return label


def restriction_source_error(froms,tos,vias,viaways,kind):
    # OSM Relation:restriction member cardinality, never inferred/repaired members.
    if not froms or not tos or not (vias or viaways):return 'missing from/to/via source members'
    if (vias and viaways) or len(vias)>1:return 'source must have one via node OR a via-way chain'
    if len(froms)!=1 and kind!='no_entry':return 'multiple from members only valid for no_entry'
    if len(tos)!=1 and kind!='no_exit':return 'multiple to members only valid for no_exit'
    return None


class Engine:
    def __init__(self,database,config):
        import valhalla
        self.actor=valhalla.Actor(str(config))
        self.version=valhalla.__version__
        self.database=str(Path(database).resolve())
        self.unresolved_rules=[];self.member_conflicting_rules=[];self.direction_conflicting_rules=[];self.quarantined_ways=set();self.runtime_no_u_turn_rules=[]
        with self.db() as db:
            self.dataset=json.loads(db.execute("SELECT value FROM metadata WHERE key='dataset'").fetchone()[0])
            def quarantine(rule):
                self.quarantined_ways.update(m['ref'] for m in json.loads(rule['members']) if m['type']=='w')
            def member_way(wid,via):
                way=db.execute('SELECT node_ids,tags FROM ways WHERE id=?',(wid,)).fetchone()
                if not way:return None
                nodes=json.loads(way['node_ids'])
                if via not in nodes:return None
                return way,nodes,json.loads(way['tags'])
            def one_way_allows(wid,via,approach):
                member=member_way(wid,via)
                if not member:return False
                way,nodes,tags=member
                direction=tags.get('oneway:motorcycle',tags.get('oneway'))
                if direction not in ('yes','1','true','-1'):return True
                index=nodes.index(via)
                if direction=='-1':
                    return index<len(nodes)-1 if approach else index>0
                return index>0 if approach else index<len(nodes)-1
            for rule in db.execute('SELECT * FROM turn_restrictions'):
                froms=json.loads(rule['from_ways']);tos=json.loads(rule['to_ways']);vias=json.loads(rule['via_nodes']);viaways=json.loads(rule['via_ways'])
                if restriction_source_error(froms,tos,vias,viaways,rule['restriction']):
                    self.unresolved_rules.append(rule['id']);quarantine(rule);continue
                if vias and not viaways and len(vias)==1:
                    via=vias[0]
                    if not all(member_way(wid,via) for wid in [*froms,*tos]):
                        self.member_conflicting_rules.append(rule['id']);quarantine(rule);continue
                    if not all(one_way_allows(wid,via,True) for wid in froms) or not all(one_way_allows(wid,via,False) for wid in tos):
                        self.direction_conflicting_rules.append(rule['id']);quarantine(rule);continue
                    if rule['restriction']=='no_u_turn' and len(froms)==1 and froms==tos:
                        node=db.execute('SELECT longitude,latitude FROM nodes WHERE id=?',(via,)).fetchone()
                        if node:
                            self.runtime_no_u_turn_rules.append({'relation_id':rule['id'],'way_id':froms[0],'via_node':via,'point':[node['longitude'],node['latitude']]})
        self.lock=threading.RLock();self.cache=OrderedDict()
    @contextmanager
    def db(self):
        connection=sqlite3.connect('file:'+self.database+'?mode=ro',uri=True);connection.row_factory=sqlite3.Row
        try:yield connection
        finally:connection.close()
    def metadata(self):
        return {'api_version':1,'engine':'QueueGo/Valhalla','engine_version':self.version,'dataset_sha256':self.dataset['extract_sha256'],'source_timestamp':self.dataset['source_replication_timestamp'],'bounds':self.dataset['bounds'],'attribution':'© OpenStreetMap contributors','licence':'ODbL-1.0','live_traffic':False,'historical_speed_feed':False,'unresolved_turn_relations':self.unresolved_rules,'source_member_conflicts':self.member_conflicting_rules,'source_direction_conflicts':self.direction_conflicting_rules,'runtime_no_u_turn_relations':[r['relation_id'] for r in self.runtime_no_u_turn_rules],'quarantined_way_count':len(self.quarantined_ways),'restriction_policy':'incomplete, disconnected-member, or one-way-direction-conflicting source turn relations fail closed on all member ways; same-way no_u_turn reversals are independently rejected at the QueueGo route boundary'}
    def profile(self,body):
        profile=body.get('profile','motorcycle')
        if profile not in PROFILES:raise ValueError('Unsupported vehicle profile')
        return profile,PROFILES[profile]
    def point(self,value):
        if not isinstance(value,dict):raise ValueError('Point must contain lat/lon')
        raw_lat=value.get('lat');raw_lon=value.get('lon',value.get('lng'))
        if isinstance(raw_lat,bool) or isinstance(raw_lon,bool):raise ValueError('Invalid coordinate')
        lat,lon=float(raw_lat),float(raw_lon)
        west,south,east,north=self.dataset['bounds']
        if not math.isfinite(lat) or not math.isfinite(lon) or not west<=lon<=east or not south<=lat<=north:raise ValueError('Coordinate outside installed Buriram package')
        return {'lat':lat,'lon':lon}
    def points(self,body,minimum=2):
        points=body.get('points',[])
        if not isinstance(points,list) or not minimum<=len(points)<=MAX_POINTS:raise ValueError(f'Need {minimum}..{MAX_POINTS} points')
        return [self.point(p) for p in points]
    def _call(self,action,request):
        with self.lock:return getattr(self.actor,action)(request)
    def _remember(self,route):
        identifier=hashlib.sha256(json.dumps([self.dataset['extract_sha256'],route['profile'],route['geometry']['coordinates']],separators=(',',':')).encode()).hexdigest()[:32]
        route['route_id']=identifier
        with self.lock:
            self.cache[identifier]=(time.monotonic(),route);self.cache.move_to_end(identifier)
            while len(self.cache)>128:self.cache.popitem(last=False)
        return route
    def cached(self,identifier):
        with self.lock:
            entry=self.cache.get(identifier)
            if not entry or time.monotonic()-entry[0]>3600:raise ValueError('Route expired; restore by recalculating from saved stops')
            self.cache[identifier]=(time.monotonic(),entry[1]);self.cache.move_to_end(identifier)
            return entry[1]
    def _enforce_runtime_turn_restrictions(self,trace_edges):
        if not self.runtime_no_u_turn_rules or len(trace_edges)<2:return
        by_way={}
        for rule in self.runtime_no_u_turn_rules:by_way.setdefault(rule['way_id'],[]).append(rule)
        for incoming,outgoing in zip(trace_edges,trace_edges[1:]):
            way=incoming.get('way_id')
            if way is None or outgoing.get('way_id')!=way or way not in by_way:continue
            before=incoming.get('forward');after=outgoing.get('forward')
            if not isinstance(before,bool) or not isinstance(after,bool) or before==after:continue
            point=incoming.get('_end_point')
            if not isinstance(point,list) or len(point)<2:continue
            for rule in by_way[way]:
                if haversine(point,rule['point'])<=5:
                    raise ValueError('Route violates source no_u_turn relation '+str(rule['relation_id'])+'; QueueGo rejects the raw engine maneuver')

    def route_trace_edges(self,leg,profile):
        """Audit the complete routed shape; edge_walk can collapse a loop to a shortcut."""
        points=decode_shape(leg['shape'])
        expected=float(leg['summary']['length'])*1000
        def complete(attributes):
            edges=attributes.get('edges') or []
            return bool(edges and edges[0].get('begin_shape_index')==0
                and edges[-1].get('end_shape_index')==len(points)-1
                and all(x.get('end_shape_index')==y.get('begin_shape_index') for x,y in zip(edges,edges[1:]))
                and abs(sum(float(e.get('length',0))*1000 for e in edges)-expected)<=max(10,expected*.03))
        for method in ('edge_walk','map_snap'):
            attributes=self._call('trace_attributes',{'encoded_polyline':leg['shape'],'shape_match':method,'costing':PROFILES[profile]})
            if complete(attributes):return attributes['edges']
        raise ValueError('Cannot audit complete routed edge sequence; refusing partial trace')

    def _convert(self,trip,profile,remember=True):
        coordinates=[];legs=[];trace_edges=[]
        for leg in trip['legs']:
            points=decode_shape(leg['shape']);offset=max(0,len(coordinates)-1)
            coordinates.extend(points[1:] if coordinates and coordinates[-1]==points[0] else points)
            steps=[]
            for maneuver in leg.get('maneuvers',[]):
                begin=int(maneuver.get('begin_shape_index',0));end=int(maneuver.get('end_shape_index',begin))
                code=maneuver.get('type',0)
                modifier='left' if code in (3,13,14,15,16,19,21,24) else 'right' if code in (2,9,10,11,12,18,20,23) else 'straight'
                kind='arrive' if code in (4,5,6) else 'roundabout' if code==26 else 'exit roundabout' if code==27 else 'on ramp' if code in (17,18,19) else 'off ramp' if code in (20,21) else 'turn'
                steps.append({'distance':maneuver.get('length',0)*1000,'duration':maneuver.get('time',0),'name':(maneuver.get('street_names') or [''])[0],'instruction':thai_maneuver(maneuver),'begin_shape_index':begin+offset,'end_shape_index':end+offset,'maneuver':{'type':kind,'modifier':'uturn' if code in (12,13) else modifier,'location':points[min(begin,len(points)-1)],'bearing_before':maneuver.get('bearing_before'),'bearing_after':maneuver.get('bearing_after')},'vendor_type':code})
            legs.append({'distance':leg['summary']['length']*1000,'duration':leg['summary']['time'],'steps':steps})
            # Coincident stops have no traversed edges; do not ask edge_walk to invent one.
            if leg['summary']['length']>0 and any(haversine(a,b)>.01 for a,b in zip(points,points[1:])):
                for edge in self.route_trace_edges(leg,profile):
                    record=dict(edge)
                    begin=min(max(0,int(record.get('begin_shape_index',0))),len(points)-1)
                    end=min(max(0,int(record.get('end_shape_index',begin))),len(points)-1)
                    record['_begin_point']=points[begin];record['_end_point']=points[end]
                    trace_edges.append(record)
        self._enforce_runtime_turn_restrictions(trace_edges)
        signal_count=sum(bool(e.get('end_node',{}).get('traffic_signal')) for e in trace_edges)
        rough_m=sum(e.get('length',0)*1000 for e in trace_edges if e.get('surface') in ('dirt','gravel','impassable') or e.get('unpaved'))
        base=float(trip['summary']['time']);distance=float(trip['summary']['length'])*1000
        ways=list(dict.fromkeys(e['way_id'] for e in trace_edges if e.get('way_id')))
        # These are explicit planning penalties, not measured historical traffic or a safety guarantee.
        estimated=base+signal_count*EXPECTED_SIGNAL_DELAY_SECONDS
        route={'distance':distance,'duration':base,'estimatedDuration':estimated,'geometry':{'type':'LineString','coordinates':coordinates},'legs':legs,'profile':profile,'signalCount':signal_count,'restrictionCount':0,'osm_way_ids':ways,'cost_components':{'engine_seconds':base,'signal_expected_delay_seconds':signal_count*EXPECTED_SIGNAL_DELAY_SECONDS,'rough_surface_metres':rough_m,'reliability_penalty_seconds':rough_m/10,'historical_samples':0},'data':self.metadata()}
        if self.quarantined_ways.intersection(ways):raise ValueError('Route touches an unresolved, disconnected-member, or direction-conflicting source turn rule; member ways are quarantined until verified')
        # Enforce known hard exclusions on the actual traversed way IDs. Never invent a detour.
        if ways:
            with self.db() as db:
                rows=db.execute('SELECT id,tags FROM ways WHERE id IN ('+','.join('?' for _ in ways)+')',ways).fetchall()
            for row in rows:
                tags=json.loads(row['tags']);access=tags.get('motorcycle') if PROFILES[profile]=='motorcycle' else tags.get('motorcar')
                access=access or tags.get('motor_vehicle') or tags.get('vehicle') or tags.get('access')
                if tags.get('highway')=='construction' or access=='no':raise ValueError('Engine candidate uses a known closed/inaccessible way')
                if any('conditional' in key for key in tags):raise ValueError('Conditional road rule requires a supported time-aware package')
        return self._remember(route) if remember else route
    def route(self,body,alternatives=False):
        points=self.points(body);profile,costing=self.profile(body)
        preference=body.get('preference','fastest')
        if preference not in ('fastest','shortest','fewer_signals','reliable'):raise ValueError('Unsupported route preference')
        options={}
        if preference=='shortest':options={'shortest':True,'disable_hierarchy_pruning':True}
        elif preference=='reliable':options={'use_tracks':0.0,'exclude_unpaved':True}
        request={'locations':points,'costing':costing,'costing_options':{costing:options},'directions_options':{'units':'kilometers'},'alternates':2 if alternatives and len(points)==2 else 0}
        response=self._call('route',request)
        trips=[response['trip']]+[r['trip'] for r in response.get('alternates',[])]
        routes=[];errors=[]
        for trip in trips:
            try:
                route=self._convert(trip,profile)
                if not any(r['route_id']==route['route_id'] for r in routes):routes.append(route)
            except ValueError as error:errors.append(str(error))
        if not routes:raise ValueError('; '.join(errors) or 'No legal route')
        score={'fastest':lambda r:r['estimatedDuration'],'shortest':lambda r:r['distance'],'fewer_signals':lambda r:(r['signalCount'],r['estimatedDuration']),'reliable':lambda r:r['estimatedDuration']+r['cost_components']['reliability_penalty_seconds']}[preference]
        routes.sort(key=score)
        return {'code':'Ok','routes':routes,'waypoints':[{'location':[p['lon'],p['lat']]} for p in points],'preference':preference,'selection_scope':'Best of engine-generated legal candidates; not a global fewest-signals/safest-path guarantee','data':self.metadata()}
    def nearest(self,body):
        point=self.point(body);profile,costing=self.profile(body)
        result=self._call('locate',{'locations':[point],'costing':costing,'verbose':True})
        edges=result[0].get('edges',[]) if result else []
        if not edges:return {'code':'NoSegment','waypoints':[],'data':self.metadata()}
        edge=min(edges,key=lambda e:e.get('distance',float('inf')))
        info=edge.get('edge_info',{})
        return {'code':'Ok','waypoints':[{'location':[edge['correlated_lon'],edge['correlated_lat']],'distance':edge.get('distance',0),'name':(info.get('names') or [''])[0],'osm_way_id':info.get('way_id'),'heading':edge.get('heading'),'access':edge.get('edge',{}).get('access',{})}],'data':self.metadata()}
    def matrix(self,body):
        points=self.points(body);profile,costing=self.profile(body)
        result=self._call('matrix',{'sources':points,'targets':points,'costing':costing})
        rows=result['sources_to_targets'];count=len(points)
        durations=[[None]*count for _ in points];distances=[[None]*count for _ in points]
        for row in rows:
            for cell in row:
                i,j=cell['from_index'],cell['to_index'];durations[i][j]=cell.get('time');distances[i][j]=None if cell.get('distance') is None else cell['distance']*1000
        # Native matrix has no traversed-edge output. Audit each reachable pair against
        # the same canonical exclusions; unknown permission must not become a usable ETA.
        if self.quarantined_ways:
            for i in range(count):
                for j in range(count):
                    if i==j or durations[i][j] is None:continue
                    try:
                        trip=self._call('route',{'locations':[points[i],points[j]],'costing':costing,'directions_options':{'units':'kilometers'}})['trip']
                        self._convert(trip,profile,remember=False)
                    except (ValueError,RuntimeError):durations[i][j]=None;distances[i][j]=None
        return {'code':'Ok','durations':durations,'distances':distances,'profile':profile,'data':self.metadata(),'unavailable_policy':'Native unreachable or canonical restriction audit rejected; null is not a fabricated ETA'}
    def optimize(self,body):
        points=self.points(body);profile,costing=self.profile(body)
        result=self._call('optimized_route',{'locations':points,'costing':costing,'directions_options':{'units':'kilometers'}})
        order=[p['original_index'] for p in result['trip']['locations']]
        if sorted(order)!=list(range(len(points))) or order[0]!=0 or order[-1]!=len(points)-1:raise ValueError('Optimizer did not preserve fixed start/end')
        route=self._convert(result['trip'],profile)
        return {'code':'Ok','points':[points[i] for i in order],'order':order,'trip':route,'trips':[route],'data':self.metadata()}
    def geocode(self,body):
        query=str(body.get('q','')).strip()
        if not 1<=len(query)<=200:raise ValueError('Search text required, max 200 characters')
        pattern='%'+query.replace('\\','\\\\').replace('%','\\%').replace('_','\\_')+'%'
        with self.db() as db:rows=db.execute("SELECT * FROM places WHERE name LIKE ? ESCAPE '\\' ORDER BY length(name),id LIMIT 20",(pattern,)).fetchall()
        return [self._place(row) for row in rows]
    def _place(self,row):
        return {'place_id':'osm:'+row['osm_type']+':'+str(row['osm_id']),'osm_type':row['osm_type'],'osm_id':row['osm_id'],'display_name':row['name'],'name':row['name'],'lat':str(row['latitude']),'lon':str(row['longitude']),'category':row['category'],'address':{},'source':'OpenStreetMap','licence':'ODbL-1.0'}
    def nearby(self,body):
        point=self.point(body);radius=float(body.get('radius',5000))
        if not math.isfinite(radius) or not 1<=radius<=20000:raise ValueError('Radius must be 1..20000 metres')
        category=body.get('category','all')
        filters={'food':{'restaurant','cafe','fast_food'},'fuel':{'fuel'},'hospital':{'hospital','clinic','pharmacy'},'police':{'police'},'atm':{'atm'},'school':{'school','kindergarten','college','university'},'toilet':{'toilets'}}
        if category not in filters and category!='all':raise ValueError('Unsupported nearby category')
        pad=radius/100000
        with self.db() as db:rows=db.execute('SELECT p.* FROM places p JOIN place_spatial s ON p.id=s.id WHERE s.min_lon<=? AND s.max_lon>=? AND s.min_lat<=? AND s.max_lat>=? LIMIT 10000',(point['lon']+pad,point['lon']-pad,point['lat']+pad,point['lat']-pad)).fetchall()
        result=[]
        for row in rows:
            if category!='all' and row['category'] not in filters[category]:continue
            metres=haversine([point['lon'],point['lat']],[row['longitude'],row['latitude']])
            if metres<=radius:
                item=self._place(row);item['distance']=metres;result.append(item)
        return sorted(result,key=lambda item:item['distance'])[:50]
    def reverse(self,body):
        point=self.point(body);near=self.nearby({**point,'radius':1000,'category':'all'})
        road=self.nearest(body)['waypoints']
        if near:
            result=near[0];result['nearest_road']=road[0] if road else None;result['address_precision']='nearest mapped named feature, not a verified postal address';return result
        return {'lat':str(point['lat']),'lon':str(point['lon']),'display_name':road[0]['name'] if road else '','address':{},'nearest_road':road[0] if road else None,'source':'OpenStreetMap','address_precision':'nearest mapped road only'}
    def road_features(self,body,kind):
        bounds=body.get('bounds') or self.dataset['bounds']
        if not isinstance(bounds,list) or len(bounds)!=4:raise ValueError('Bounds must be west,south,east,north')
        west,south,east,north=map(float,bounds)
        if not all(math.isfinite(x) for x in (west,south,east,north)) or not west<east or not south<north or east-west>2 or north-south>2:raise ValueError('Invalid regional bounds')
        result=[]
        with self.db() as db:
            if kind=='signals':
                rows=db.execute('SELECT n.* FROM nodes n JOIN node_spatial s ON n.id=s.id WHERE n.traffic_signal=1 AND s.min_lon<=? AND s.max_lon>=? AND s.min_lat<=? AND s.max_lat>=?',(east,west,north,south)).fetchall()
                for row in rows:result.append({'id':row['id'],'point':[row['longitude'],row['latitude']],'tags':json.loads(row['tags']),'source':'OSM','confidence':row['confidence'],'last_verified':row['last_verified']})
            else:
                rows=db.execute("SELECT s.* FROM segments s JOIN segment_spatial b ON s.rowid=b.id WHERE b.min_lon<=? AND b.max_lon>=? AND b.min_lat<=? AND b.max_lat>=? AND (s.road_class='construction' OR s.access IN ('no','private') OR s.motor_vehicle_access='no' OR s.motorcycle_access='no' OR s.closure IS NOT NULL) LIMIT 1000",(east,west,north,south)).fetchall()
                for row in rows:
                    geometry=json.loads(row['geometry']);result.append({'id':row['id'],'osm_way_id':row['way_id'],'point':geometry[len(geometry)//2],'geometry':geometry,'kind':'construction' if row['road_class']=='construction' else 'restriction','tags':json.loads(row['attributes']),'source':'OSM','confidence':row['confidence']})
        return {'items':result,'data':self.metadata(),'authoritative_live_closure_feed':False}
    def progress(self,body):
        route=self.cached(body['route_id']);point=self.point(body);context=body.get('context') or {}
        accuracy=float(body.get('accuracy',float('inf')))
        accurate=math.isfinite(accuracy) and 0<=accuracy<=50
        if not accurate:return {'accepted':False,'reason':'poor_or_missing_accuracy','context':context,'reroute':False,'arrived':False}
        heading=body.get('heading');speed=body.get('speed')
        heading=float(heading)%360 if isinstance(heading,(float,int)) and math.isfinite(heading) and isinstance(speed,(float,int)) and speed>=2 else None
        current=[point['lon'],point['lat']];line=route['geometry']['coordinates']
        result=projection(current,line,heading)
        now=float(body.get('timestamp',time.time()))
        if not math.isfinite(now) or now<=0:raise ValueError('Invalid fix timestamp')
        previous=float(context.get('last_timestamp',0))
        if not math.isfinite(previous):raise ValueError('Invalid previous timestamp')
        if now<=previous:return {'accepted':False,'reason':'stale_fix','context':context,'reroute':False,'arrived':False}
        if context.get('last_raw') and previous and now-previous<=15:
            implied=haversine(context['last_raw'],current)/max(.1,now-previous)
            if implied>60:return {'accepted':False,'reason':'implausible_jump','context':context,'reroute':False,'arrived':False}
        reset=not previous or now-previous>15
        hits=0 if reset else max(0,min(10,int(context.get('off_route_hits',0))))
        threshold=max(80,float(body['accuracy'])*2)
        hits=hits+1 if result['distance_m']>threshold else 0
        last_reroute=float(context.get('last_reroute_at',0))
        reroute=hits>=3 and now-last_reroute>=20
        if reroute:last_reroute=now;hits=0
        arrival_hits=0 if reset else max(0,min(10,int(context.get('arrival_hits',0))))
        near_end=haversine(current,line[-1])<35 and result['remaining_m']<60 and float(body['accuracy'])<=25
        arrival_hits=arrival_hits+1 if near_end else 0
        steps=[step for leg in route['legs'] for step in leg['steps']]
        upcoming=next((step for step in steps if step['begin_shape_index']>result['shape_index']),steps[-1] if steps else None)
        to_maneuver=max(0,sum(haversine(a,b) for a,b in zip(line[:upcoming['begin_shape_index']],line[1:upcoming['begin_shape_index']+1]))-result['progress_m']) if upcoming else 0
        context={'last_timestamp':now,'last_raw':current,'off_route_hits':hits,'last_reroute_at':last_reroute,'arrival_hits':arrival_hits,'progress_m':result['progress_m']}
        return {'accepted':True,'snapped_position':result['point'],'bearing':result['bearing'],'off_route_distance_m':result['distance_m'],'remaining_distance_m':result['remaining_m'],'remaining_duration_s':route['estimatedDuration']*result['remaining_m']/max(1,result['total_m']),'maneuver':upcoming,'distance_to_maneuver_m':to_maneuver,'reroute':reroute,'arrived':arrival_hits>=3,'context':context,'data':self.metadata()}
    def match(self,body):
        trace=body.get('points') or []
        if not isinstance(trace,list) or not 1<=len(trace)<=64:raise ValueError('Trace needs 1..64 points')
        evaluation=None
        if body.get('route_id'):
            planned=self.cached(body['route_id'])
            if body.get('profile',planned['profile'])!=planned['profile']:raise ValueError('Matching profile differs from planned route')
            body={**body,'profile':planned['profile']}
            evaluation=self.progress({**trace[-1],'route_id':body['route_id'],'context':body.get('context') or {}})
            if not evaluation['accepted']:return evaluation
        profile,costing=self.profile(body);accepted=[];accuracies=[];last_time=None;last_accepted_index=-1
        for index,value in enumerate(trace):
            point=self.point(value);accuracy=value.get('accuracy')
            if accuracy is not None and (not isinstance(accuracy,(float,int)) or not math.isfinite(accuracy) or accuracy>50 or accuracy<0):continue
            stamp=value.get('timestamp');speed=value.get('speed');heading=value.get('heading')
            if stamp is not None:
                if not isinstance(stamp,(float,int)) or not math.isfinite(stamp) or stamp<=0:continue
                if last_time is not None:
                    if stamp<=last_time:continue
                    if stamp-last_time>15:accepted=[];accuracies=[]
                    elif accepted and haversine([accepted[-1]['lon'],accepted[-1]['lat']],[point['lon'],point['lat']])/(stamp-last_time)>60:continue
                point['time']=stamp;last_time=stamp
            moving=isinstance(speed,(float,int)) and math.isfinite(speed) and speed>=2
            if moving and isinstance(heading,(float,int)) and math.isfinite(heading):point['heading']=heading%360
            accepted.append(point);last_accepted_index=index
            if accuracy is not None:accuracies.append(accuracy)
        if not accepted:return {'accepted':False,'reason':'no_accurate_fixes','reroute':False,'context':body.get('context') or {}}
        if last_accepted_index!=len(trace)-1:return {'accepted':False,'reason':'invalid_latest_fix','reroute':False,'context':body.get('context') or {}}
        uncertainty=sorted(accuracies)[len(accuracies)//2] if accuracies else 5
        base={'accepted':True,'data':self.metadata(),'matching_available':True}
        if evaluation:base.update(evaluation)
        try:
            if len(accepted)<2:
                nearest=self.nearest({**accepted[-1],'profile':profile});waypoints=nearest['waypoints']
                if not waypoints:raise ValueError('No candidate road')
                return {**base,'matching_method':'nearest_initialization','snapped_position':waypoints[0]['location'],'reroute':base.get('reroute',False),'context':base.get('context',body.get('context') or {})}
            result=self._call('trace_route',{'shape':accepted,'shape_match':'map_snap','costing':costing,'trace_options':{'gps_accuracy':max(1,uncertainty),'search_radius':max(20,min(100,uncertainty*2))},'directions_options':{'units':'kilometers'}})
            if result.get('alternates'):raise ValueError('Discontinuous trace cannot supply a trusted latest road position')
            route=self._convert(result['trip'],profile,remember=False)
            latest=[accepted[-1]['lon'],accepted[-1]['lat']]
            if haversine(latest,route['geometry']['coordinates'][-1])>max(35,uncertainty*2):raise ValueError('Matched endpoint is too far from latest fix')
            return {**base,'matching_method':'Valhalla HMM','matchings':[route],'snapped_position':route['geometry']['coordinates'][-1]}
        except Exception:
            if not evaluation:raise
            # Accurate raw evidence can still trigger reroute when no graph match exists.
            # Never label raw GPS as a road snap or hide off-route evidence with route projection.
            return {**base,'matching_method':'raw_fix_unmatched','matching_available':False,'snapped_position':None,'display_position':[float(trace[-1]['lon']),float(trace[-1]['lat'])]}
