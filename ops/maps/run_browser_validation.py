#!/usr/bin/env python3
"""Keep the real service and browser in one runner/network namespace."""
import argparse,subprocess,sys,time,urllib.request
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--database',required=True);p.add_argument('--config',required=True);p.add_argument('--vector',required=True);p.add_argument('--assets-root',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[2]
server=subprocess.Popen([sys.executable,str(root/'queuego-map-engine/server.py'),'--database',a.database,'--config',a.config,'--vector',a.vector,'--assets-root',a.assets_root,'--port','8766'],cwd=root,stdout=subprocess.DEVNULL)
try:
    for _ in range(100):
        if server.poll() is not None:raise RuntimeError('Map service failed to start')
        try:urllib.request.urlopen('http://127.0.0.1:8766/map-api/health',timeout=1);break
        except OSError:time.sleep(.1)
    else:raise TimeoutError('Map service did not become ready')
    raise SystemExit(subprocess.run(['node','tests/map-engine-browser.cjs'],cwd=root).returncode)
finally:server.terminate();server.wait()
