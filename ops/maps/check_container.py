#!/usr/bin/env python3
"""Build candidate runtime and route from actual mounted data without container internet."""
import argparse,json,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--store',required=True);p.add_argument('--output',required=True);p.add_argument('--tag',default='queuego-map:ci');a=p.parse_args()
subprocess.run(['docker','build','-f','queuego-map-engine/Dockerfile','-t',a.tag,'.'],check=True)
name='queuego-map-offline-ci'
subprocess.run(['docker','run','--detach','--name',name,'--network','none','--read-only','--memory','512m','--cpus','2','--tmpfs','/tmp:rw,noexec,nosuid,size=64m','--volume',str(Path(a.store).resolve())+':/data:ro',a.tag],check=True)
try:
    probe="import urllib.request; urllib.request.urlopen('http://127.0.0.1:8765/map-api/health',timeout=1)"
    for _ in range(100):
        result=subprocess.run(['docker','exec',name,'python','-c',probe],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        if result.returncode==0:break
        time.sleep(.2)
    else:
        subprocess.run(['docker','logs',name]);raise RuntimeError('Container service startup failed')
    code='''import urllib.request,json
base='http://127.0.0.1:8765/map-api/'
health=json.load(urllib.request.urlopen(base+'health'))
body={'points':[{'lat':14.9918756,'lon':103.0994795},{'lat':15.0088319,'lon':103.1300059}]}
request=urllib.request.Request(base+'route',data=json.dumps(body).encode(),headers={'Content-Type':'application/json'})
route=json.load(urllib.request.urlopen(request))['routes'][0]
assert route['distance']>1000
assert set(health['unresolved_turn_relations'])=={2148441,17320674}
assert set(health['source_direction_conflicts'])=={1538817}
assert health['quarantined_way_count']==5
print(json.dumps({'distance_m':route['distance'],'dataset_sha256':health['dataset_sha256'],'quarantined_way_count':health['quarantined_way_count']}))
'''
    result=subprocess.run(['docker','exec','-i',name,'python','-'],input=code,text=True,capture_output=True,check=True)
    report=json.loads(result.stdout)
    image=json.loads(subprocess.check_output(['docker','image','inspect',a.tag]))[0]
    report.update(network='none',read_only=True,memory_limit_bytes=512*1024**2,image_id=image['Id'],image_bytes=image['Size'],checks=3,failures=0)
    Path(a.output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
finally:subprocess.run(['docker','rm','--force',name],check=True)
