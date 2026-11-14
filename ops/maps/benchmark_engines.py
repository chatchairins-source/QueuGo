#!/usr/bin/env python3
"""Measured self-hosted engine comparison on identical real OSM inputs. No public routing APIs."""
import argparse
import json
import math
from pathlib import Path
import statistics
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

POINTS=[{'lat':14.9918756,'lon':103.0994795,'osm_node_id':11593547727}, {'lat':15.0088319,'lon':103.1300059,'osm_node_id':1661819325}, {'lat':14.9688448,'lon':103.1193315,'osm_node_id':12712892020}]


def http(url, data=None, content_type='application/json'):
    payload = None if data is None else (json.dumps(data).encode() if not isinstance(data,str) else data.encode())
    request=urllib.request.Request(url, data=payload, headers={'Content-Type':content_type})
    with urllib.request.urlopen(request,timeout=30) as response: return json.load(response)


def memory(pid):
    # Host /proc may refer to a DIFFERENT process with the same namespace PID.
    # Never report those numbers. wait4 supplies the actual child peak at exit.
    return {'live_rss':'not sampled; isolated PID namespace'}


def measure(action, repetitions=30):
    response=action()
    for _ in range(4): action()
    samples=[]
    for _ in range(repetitions):
        started=time.perf_counter(); response=action();samples.append((time.perf_counter()-started)*1000)
    samples.sort()
    return {'requests':repetitions,'median_ms':round(statistics.median(samples),3),'p95_ms':round(samples[math.ceil(.95*len(samples))-1],3),'min_ms':round(samples[0],3),'max_ms':round(samples[-1],3)},response


def probe(action):
    try:
        value=action();return {'status':'supported','response_keys':list(value)[:12] if isinstance(value,dict) else ['list']}
    except Exception as error:return {'status':'not_available_or_failed','error':str(error)}


def benchmark(root, osrm_tools, java, jar, output):
    root=Path(root).resolve(); osrm_tools=Path(osrm_tools).resolve(); processes=[];reports=[]
    import valhalla
    config=json.loads((root/'valhalla-th/valhalla.json').read_text());config['httpd']['service']['listen']='tcp://127.0.0.1:8002'
    (root/'valhalla-th/server.json').write_text(json.dumps(config))
    commands={
        'valhalla':[__import__('sys').executable,str(Path(__file__).resolve().parent/'benchmark_valhalla_http.py'),str(root/'valhalla-th/server.json')],
        'osrm':[str(osrm_tools/'lib/ld-musl-x86_64.so.1'),'--library-path',str(osrm_tools/'lib')+':'+str(osrm_tools/'usr/lib'),str(osrm_tools/'usr/local/bin/osrm-routed'),'--algorithm','mld','--threads','2','--ip','127.0.0.1',str(root/'osrm/buriram.osrm')],
        'graphhopper':[java,'-XX:ActiveProcessorCount=2','-Xmx3g','-jar',jar,'server',str(root/'graphhopper/graphhopper.yml')]
    }
    coordinate=';'.join(f'{p["lon"]},{p["lat"]}' for p in POINTS[:2])
    plain=[{k:p[k] for k in ('lat','lon')} for p in POINTS]
    query=urllib.parse.urlencode([('point',f'{p["lat"]},{p["lon"]}') for p in plain[:2]]+[('profile','car'),('points_encoded','false')])
    actions={
        'valhalla':lambda:http('http://127.0.0.1:8002/route',{'locations':plain[:2],'costing':'auto','directions_options':{'units':'kilometers'}}),
        'osrm':lambda:http('http://127.0.0.1:5000/route/v1/driving/'+coordinate+'?overview=full&geometries=geojson&steps=true'),
        'graphhopper':lambda:http('http://127.0.0.1:8989/route?'+query)
    }
    try:
        for engine in ('valhalla','osrm','graphhopper'):
            directory=root/('valhalla-th' if engine=='valhalla' else engine)
            log=(directory/'server.log').open('w')
            process=subprocess.Popen(commands[engine],stdout=log,stderr=subprocess.STDOUT,cwd=directory)
            processes.append((process,log))
            deadline=time.monotonic()+90
            while True:
                if process.poll() is not None: raise RuntimeError(engine+' server stopped: '+(directory/'server.log').read_text()[-5000:])
                try: response=actions[engine]();break
                except Exception:
                    if time.monotonic()>deadline:raise
                    time.sleep(.25)
            latency,response=measure(actions[engine])
            if engine=='valhalla': summary={'distance_m':response['trip']['summary']['length']*1000,'duration_s':response['trip']['summary']['time']}
            elif engine=='osrm':summary={k:response['routes'][0][v] for k,v in [('distance_m','distance'),('duration_s','duration')]}
            else:summary={'distance_m':response['paths'][0]['distance'],'duration_s':response['paths'][0]['time']/1000}
            assert summary['distance_m']>100 and summary['duration_s']>0, summary
            report={'engine':engine,'build':json.loads((directory/'build-report.json').read_text()),'route_http_warm':latency,'route_result':summary,'server_memory':memory(process.pid),'process_command':commands[engine]}
            if engine=='valhalla':
                report['native_matrix']=probe(lambda:http('http://127.0.0.1:8002/sources_to_targets',{'sources':plain,'targets':plain,'costing':'auto'}))
                report['alternatives']=probe(lambda:http('http://127.0.0.1:8002/route',{'locations':plain[:2],'costing':'auto','alternates':2}))
                report['motorcycle']=probe(lambda:http('http://127.0.0.1:8002/route',{'locations':plain[:2],'costing':'motorcycle'}))
                trip=actions[engine]()['trip']
                report['map_matching']=probe(lambda:http('http://127.0.0.1:8002/trace_route',{'encoded_polyline':trip['legs'][0]['shape'],'shape_match':'map_snap','costing':'auto'}))
            elif engine=='osrm':
                report['native_matrix']=probe(lambda:http('http://127.0.0.1:5000/table/v1/driving/'+coordinate+'?annotations=distance,duration'))
                report['alternatives']=probe(lambda:http('http://127.0.0.1:5000/route/v1/driving/'+coordinate+'?alternatives=true'))
                line=response['routes'][0]['geometry']['coordinates']; trace=line[::max(1,len(line)//25)]
                trace_coordinate=';'.join(f'{p[0]},{p[1]}' for p in trace)
                report['map_matching']=probe(lambda:http('http://127.0.0.1:5000/match/v1/driving/'+trace_coordinate+'?radiuses='+';'.join('15' for _ in trace)))
                report['motorcycle']={'status':'requires_separate_reviewed_Lua_profile_and_graph','tested_profile':'car, Thailand left-hand driving; do not relabel as motorcycle'}
            else:
                report['native_matrix']=probe(lambda:http('http://127.0.0.1:8989/matrix?'+query))
                report['alternatives']=probe(lambda:http('http://127.0.0.1:8989/route?'+query+'&algorithm=alternative_route'))
                report['motorcycle']=probe(lambda:http('http://127.0.0.1:8989/route?'+query.replace('profile=car','profile=motorcycle')))
                line=response['paths'][0]['points']['coordinates'];trace=line[::max(1,len(line)//25)]
                gpx='<gpx version="1.1" creator="QueueGo real OSM route-derived benchmark"><trk><trkseg>'+''.join(f'<trkpt lat="{p[1]}" lon="{p[0]}"/>' for p in trace)+'</trkseg></trk></gpx>'
                report['map_matching']=probe(lambda:http('http://127.0.0.1:8989/match?profile=car&type=json',gpx,'application/gpx+xml'))
            reports.append(report)
            process.terminate()
            import os
            _,status,usage=os.wait4(process.pid,0)
            process.returncode=os.waitstatus_to_exitcode(status)
            report['server_memory']['peak_rss_kib']=usage.ru_maxrss
            report['server_memory']['measurement']='wait4 child process resource usage at shutdown'
            log.close()
            print(engine,latency,summary,flush=True)
    finally:
        for process,log in processes:
            if process.poll() is None:process.terminate();process.wait(timeout=15)
            if not log.closed:log.close()
    result={'protocol_version':1,'dataset_sha256':reports[0]['build']['dataset_sha256'],'real_osm_node_queries':POINTS,'engines':reports,'method':'Same host and extract; Valhalla official Actor behind Python HTTP (native ZeroMQ cannot enumerate interfaces in this restricted host); OSRM and GraphHopper native HTTP. Sequential HTTP route requests, 5 warmups, 30 timed queries; each engine uses 2 threads/active CPUs. No public routing endpoint. Latency is not a Thailand-wide or Android benchmark.','matching_trace_provenance':'Derived from each engine real-OSM route geometry, not claimed as recorded physical GPS.'}
    Path(output).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'output':str(output),'engines':len(reports)}))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--root',required=True);p.add_argument('--osrm-tools',required=True);p.add_argument('--java',required=True);p.add_argument('--graphhopper-jar',required=True);p.add_argument('--output',required=True);a=p.parse_args();benchmark(a.root,a.osrm_tools,a.java,a.graphhopper_jar,a.output)
