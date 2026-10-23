#!/usr/bin/env python3
"""Serve a verified active package read-only; transient config stays outside the package."""
import argparse,os,sys,tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'ops/maps'))
from offline_package import resolve
from server import main

p=argparse.ArgumentParser()
p.add_argument('--store',required=True)
p.add_argument('--web-root',required=True)
p.add_argument('--assets-root',required=True)
p.add_argument('--host',default=os.environ.get('HOST','127.0.0.1'))
p.add_argument('--port',default=os.environ.get('PORT','8765'))
p.add_argument('--allowed-origin',action='append',default=[])
a=p.parse_args()

origins=[]
for value in [*a.allowed_origin,*os.environ.get('QUEUEGO_MAP_ALLOWED_ORIGINS','').split(',')]:
    value=value.strip()
    if value and value not in origins:origins.append(value)

with tempfile.TemporaryDirectory(prefix='queuego-map-runtime-') as runtime:
    files=resolve(a.store,runtime)
    sys.argv=['queuego-map-server','--database',files['database'],'--config',files['config'],'--vector',files['vector'],'--web-root',a.web_root,'--assets-root',a.assets_root,'--host',a.host,'--port',str(a.port)]
    for origin in origins:sys.argv.extend(['--allowed-origin',origin])
    main()
