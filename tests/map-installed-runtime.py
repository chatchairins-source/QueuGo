#!/usr/bin/env python3
"""Actual package entrypoint smoke; no routing/GPS transport fixtures."""
import argparse,json,subprocess,sys,time,urllib.request
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--store',required=True);p.add_argument('--assets-root',required=True);p.add_argument('--output',required=True);a=p.parse_args();root=Path(__file__).resolve().parents[1];store=Path(a.store);before={p.name for p in store.iterdir()}
server=subprocess.Popen([sys.executable,str(root/'queuego-map-engine/start_installed.py'),'--store',a.store,'--web-root',str(root),'--assets-root',a.assets_root,'--port','8767'],stdout=subprocess.DEVNULL)
try:
    for _ in range(200):
        if server.poll() is not None:raise RuntimeError('Installed service failed startup')
        try:
            health=json.load(urllib.request.urlopen('http://127.0.0.1:8767/map-api/health',timeout=1));break
        except OSError:time.sleep(.1)
    else:raise TimeoutError('Installed service startup timeout')
    request=urllib.request.Request('http://127.0.0.1:8767/map-api/route',data=json.dumps({'points':[{'lat':14.9918756,'lon':103.0994795},{'lat':15.0088319,'lon':103.1300059}]}).encode(),headers={'Content-Type':'application/json'})
    route=json.load(urllib.request.urlopen(request,timeout=15))['routes'][0];assert route['distance']>1000
    with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8767/map-api/vector.pmtiles',headers={'Range':'bytes=0-126'})) as response:
        assert response.status==206 and response.read()[:7]==b'PMTiles'
    assert before=={p.name for p in store.iterdir()},'Runtime changed package-store files'
    report={'checks':3,'failures':0,'route_distance_m':route['distance'],'dataset':health['dataset_sha256'],'package_store_unchanged':True,'transient_config_outside_package':True,'docker_image_tested':False};Path(a.output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
finally:server.terminate();server.wait()
