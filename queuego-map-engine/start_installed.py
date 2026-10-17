#!/usr/bin/env python3
"""Serve a verified active package read-only; transient config stays outside the package."""
import argparse,sys,tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'ops/maps'))
from offline_package import resolve
from server import main
p=argparse.ArgumentParser();p.add_argument('--store',required=True);p.add_argument('--web-root',required=True);p.add_argument('--assets-root',required=True);p.add_argument('--host',default='127.0.0.1');p.add_argument('--port',default='8765');a=p.parse_args()
with tempfile.TemporaryDirectory(prefix='queuego-map-runtime-') as runtime:
    files=resolve(a.store,runtime)
    sys.argv=['queuego-map-server','--database',files['database'],'--config',files['config'],'--vector',files['vector'],'--web-root',a.web_root,'--assets-root',a.assets_root,'--host',a.host,'--port',a.port]
    main()
