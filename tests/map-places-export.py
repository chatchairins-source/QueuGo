#!/usr/bin/env python3
"""Compare a real CI rebuild with the shipped OSM snapshot, no substituted source data."""
import json,sys
from pathlib import Path
actual,expected=[json.loads(Path(x).read_text()) for x in sys.argv[1:]]
for key in ('records','source_timestamp','bounds','licence','attribution'):
 assert actual[key]==expected[key],f'Catalog reproducibility mismatch: {key}'
print(json.dumps({'checks':5,'failures':0,'records':len(actual['records']),'scope':'actual pinned CI PBF rebuild matches shipped Web/Android places'}))
