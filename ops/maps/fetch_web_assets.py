#!/usr/bin/env python3
"""Download pinned, checksummed map runtime assets once before hosting locally/offline."""
import argparse,json,hashlib,urllib.request
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('destination');a=p.parse_args();root=Path(a.destination);root.mkdir(parents=True,exist_ok=True)
manifest=json.loads(Path(__file__).with_name('web-assets.json').read_text())
for entry in manifest['files']:
    path=root/entry['path'];path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256']:continue
    data=urllib.request.urlopen(entry['url'],timeout=60).read()
    if hashlib.sha256(data).hexdigest()!=entry['sha256']:raise ValueError('Asset checksum mismatch: '+entry['path'])
    path.write_bytes(data)
print(json.dumps({'files':len(manifest['files']),'destination':str(root)}))
