#!/usr/bin/env python3
"""Verify actual package and rollback metadata; does not manufacture map data."""
import sys,json,argparse,shutil,tempfile,tarfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'ops/maps'))
from offline_package import checksum,verify,install,activate,resolve
p=argparse.ArgumentParser();p.add_argument('--archive',required=True);p.add_argument('--sha256',required=True);p.add_argument('--store',required=True);p.add_argument('--output',required=True);a=p.parse_args()
checks=[]
def check(name,condition):
    assert condition,name;checks.append(name)
version=install(a.archive,a.store,a.sha256);activate(a.store,version);old=(Path(a.store)/'active.json').read_bytes()
try:install(a.archive,a.store,'0'*64);raise AssertionError('accepted corrupt archive')
except ValueError:check('corrupt archive rejected',True)
check('failed install preserves active pointer',(Path(a.store)/'active.json').read_bytes()==old)
root=Path(a.store)/version;manifest=verify(root);check('all component checksums',bool(manifest['files']))
# A second version of SAME real dataset exercises rollback; explicitly no fresh-data claim.
with tempfile.TemporaryDirectory(dir=a.store) as temporary:
    replica=Path(temporary)/'buriram-map-v1-rollback-check';shutil.copytree(root,replica,copy_function=lambda x,y:__import__('os').link(x,y))
    # Manifest must be independent from hard-linked immutable files.
    (replica/'manifest.json').unlink();second={**manifest,'id':replica.name};(replica/'manifest.json').write_text(json.dumps(second))
    destination=Path(a.store)/replica.name;shutil.move(str(replica),destination)
    activated=activate(a.store,destination.name);check('atomic new version activation',activated['previous']==version)
    rolled=activate(a.store,activated['previous']);check('rollback to verified previous',rolled['current']==version)
    shutil.rmtree(destination)
paths=resolve(a.store);sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'queuego-map-engine'));from core import Engine
engine=Engine(paths['database'],paths['config']);route=engine.route({'points':[{'lat':14.9918756,'lon':103.0994795},{'lat':15.0088319,'lon':103.1300059}]})['routes'][0]
check('route after offline install and rollback',route['distance']>1000)
report={'archive_sha256':a.sha256,'archive_bytes':Path(a.archive).stat().st_size,'uncompressed_bytes':manifest['uncompressed_bytes'],'checks':checks,'route_distance_m':route['distance'],'dataset_sha256':engine.metadata()['dataset_sha256'],'android_runtime_included':False,'public_download_published':False,'rollback_fixture':'two package version identifiers using identical actual OSM data; no second dataset freshness claim'}
Path(a.output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({'checks':len(checks),'failures':0}))
