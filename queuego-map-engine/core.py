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


class Engine:
    def __init__(self,database,config):
        import valhalla
        self.actor=valhalla.Actor(str(config))
        self.version=valhalla.__version__
        self.database=str(Path(database).resolve())
        with self.db() as db:self.dataset=json.loads(db.execute("SELECT value FROM metadata WHERE key='dataset'").fetchone()[0])
        self.lock=threading.RLock();self.cache=OrderedDict()
    @contextmanager
    def db(self):
        connection=sqlite3.connect('file:'+self.database+'?mode=ro',uri=True);connection.row_factory=sqlite3.Row
        try:yield connection
        finally:connection.close()
    def metadata(self):
        return {'api_version':1,'engine':'QueueGo/Valhalla','engine_version':self.version,'dataset_sha256':self.dataset['extract_sha256'],'source_timestamp':self.dataset['source_replication_timestamp'],'bounds':self.dataset['bounds'],'attribution':'© OpenStreetMap contributors','licence':'ODbL-1.0','live_traffic':False,'historical_speed_feed':False}
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
            attributes=self._call('trace_attributes',{'encoded_polyline':leg['shape'],'shape_match':'edge_walk','costing':PROFILES[profile]})
            trace_edges.extend(attributes.get('edges',[]))
        signal_count=sum(bool(e.get('end_node',{}).get('traffic_signal')) for e in trace_edges)
        rough_m=sum(e.get('length',0)*1000 for e in trace_edges if e.get('surface') in ('dirt','gravel','impassable') or e.get('unpaved'))
        base=float(trip['summary']['time']);distance=float(trip['summary']['length'])*1000
        ways=list(dict.fromkeys(e['way_id'] for e in trace_edges if e.get('way_id')))
        # These are explicit planning penalties, not measured historical traffic or a safety guarantee.
        estimated=base+signal_count*EXPECTED_SIGNAL_DELAY_SECONDS
        route={'distance':distance,'duration':base,'estimatedDuration':estimated,'geometry':{'type':'LineString','coordinates':coordinates},'legs':legs,'profile':profile,'signalCount':signal_count,'restrictionCount':0,'osm_way_ids':ways,'cost_components':{'engine_seconds':base,'signal_expected_delay_seconds':signal_count*EXPECTED_SIGNAL_DELAY_SECONDS,'rough_surface_metres':rough_m,'reliability_penalty_seconds':rough_m/10,'historical_samples':0},'data':self.metadata()}
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
        return {'code':'Ok','durations':durations,'distances':distances,'profile':profile,'data':self.metadata()}
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
        upcoming=next((step for step in steps if step['end_shape_index']>result['shape_index']),steps[-1] if steps else None)
        context={'last_timestamp':now,'last_raw':current,'off_route_hits':hits,'last_reroute_at':last_reroute,'arrival_hits':arrival_hits,'progress_m':result['progress_m']}
        return {'accepted':True,'snapped_position':result['point'],'bearing':result['bearing'],'off_route_distance_m':result['distance_m'],'remaining_distance_m':result['remaining_m'],'remaining_duration_s':route['estimatedDuration']*result['remaining_m']/max(1,result['total_m']),'maneuver':upcoming,'distance_to_maneuver_m':sum(haversine(a,b) for a,b in zip(line[result['shape_index']:upcoming['end_shape_index']+1],line[result['shape_index']+1:upcoming['end_shape_index']+1])) if upcoming else 0,'reroute':reroute,'arrived':arrival_hits>=3,'context':context,'data':self.metadata()}
    def match(self,body):
        trace=body.get('points') or []
        if not isinstance(trace,list) or not 1<=len(trace)<=64:raise ValueError('Trace needs 1..64 points')
        profile,costing=self.profile(body);accepted=[]
        for value in trace:
            point=self.point(value)
            accuracy=value.get('accuracy')
            if accuracy is not None and (not isinstance(accuracy,(float,int)) or not math.isfinite(accuracy) or accuracy>50 or accuracy<0):continue
            if value.get('heading') is not None and isinstance(value['heading'],(float,int)) and math.isfinite(value['heading']):point['heading']=value['heading']%360
            accepted.append(point)
        if not accepted:return {'accepted':False,'reason':'no_accurate_fixes','reroute':False,'context':body.get('context') or {}}
        if len(accepted)<2:
            nearest=self.nearest({**accepted[-1],'profile':profile});waypoints=nearest['waypoints']
            return {'accepted':bool(waypoints),'matching_method':'nearest_initialization','snapped_position':waypoints[0]['location'] if waypoints else None,'reroute':False,'context':body.get('context') or {},'data':self.metadata()}
        result=self._call('trace_route',{'shape':accepted,'shape_match':'map_snap','costing':costing,'directions_options':{'units':'kilometers'}})
        route=self._convert(result['trip'],profile,remember=False)
        response={'accepted':True,'matching_method':'Valhalla HMM','matchings':[route],'snapped_position':route['geometry']['coordinates'][-1],'data':self.metadata()}
        if body.get('route_id'):
            latest=trace[-1]
            evaluation=self.progress({**latest,'route_id':body['route_id'],'context':body.get('context') or {}})
            # Progress/off-route is evaluated against the accurate raw fix; a bad route snap
            # must not erase genuine repeated off-route evidence. Display uses matched road.
            response.update(evaluation)
            if evaluation.get('accepted'):response['snapped_position']=route['geometry']['coordinates'][-1]
            else:response.pop('snapped_position',None)
        return response
