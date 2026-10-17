#!/usr/bin/env python3
"""Immutable regional packages: verify every file, atomic activation and explicit rollback.
The package contains data, not the Android routing runtime. No delta format is claimed.
"""
import argparse,hashlib,json,os,re,shutil,tarfile,tempfile,urllib.request
from pathlib import Path

def checksum(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()
def safe_name(name):
    if not re.fullmatch(r'[a-z0-9][a-z0-9._-]{0,95}',name):raise ValueError('Invalid package version')
    return name
def verify(root):
    root=Path(root);manifest=json.loads((root/'manifest.json').read_text());safe_name(manifest['id'])
    if manifest['format_version']!=1 or manifest['engine']!={'name':'valhalla','version':'3.9.1'}:raise ValueError('Unsupported package/engine version')
    declared=set()
    for entry in manifest['files']:
        path=Path(entry['path']);target=root/path
        if path.is_absolute() or '..' in path.parts or target.is_symlink() or not target.resolve().is_relative_to(root.resolve()):raise ValueError('Unsafe package path')
        if entry['path'] in declared:raise ValueError('Duplicate package file')
        declared.add(entry['path'])
        if target.stat().st_size!=entry['bytes'] or checksum(target)!=entry['sha256']:raise ValueError('Package checksum mismatch: '+entry['path'])
    actual={str(p.relative_to(root)) for p in root.rglob('*') if p.is_file() and p.name!='manifest.json'}
    if actual!=declared:raise ValueError('Undeclared or missing package files')
    for required in ('roads.sqlite','vector.pmtiles','valhalla.json'):
        if required not in declared:raise ValueError('Incomplete offline navigation package')
    if not any(p.startswith('tiles/') and p.endswith('.gph') for p in declared):raise ValueError('Routing tiles missing')
    return manifest

def build(database,graph,vector,destination,version):
    destination=Path(destination);safe_name(version)
    if destination.exists():raise ValueError('Build destination already exists; use a new version')
    import sqlite3
    with sqlite3.connect(database) as db:dataset=json.loads(db.execute("SELECT value FROM metadata WHERE key='dataset'").fetchone()[0])
    graph_report=json.loads((Path(graph)/'build-report.json').read_text())
    if graph_report['dataset_sha256']!=dataset['extract_sha256']:raise ValueError('Graph/database snapshot mismatch')
    if checksum(vector)!='30699573ed2068d3c7e4393ad477dbe8febf68f453f27ae88437f5c652a3ddfc':raise ValueError('Unrecognized vector snapshot; review source manifest before packaging')
    destination.mkdir(parents=True)
    try:
        shutil.copy2(database,destination/'roads.sqlite');shutil.copy2(vector,destination/'vector.pmtiles')
        shutil.copytree(Path(graph)/'tiles',destination/'tiles')
        # Build-time admins are not needed by the routing actor. Tiles contain admin metadata.
        config=json.loads((Path(graph)/'valhalla.json').read_text())
        config['mjolnir']['tile_dir']='tiles';config['mjolnir']['tile_extract']='';config['mjolnir']['admin']=''
        (destination/'valhalla.json').write_text(json.dumps(config,indent=2)+'\n')
        import sqlite3
        with sqlite3.connect(database) as db:dataset=json.loads(db.execute("SELECT value FROM metadata WHERE key='dataset'").fetchone()[0])
        files=[{'path':str(p.relative_to(destination)),'bytes':p.stat().st_size,'sha256':checksum(p)} for p in sorted(destination.rglob('*')) if p.is_file()]
        manifest={'format_version':1,'id':version,'region':'Buriram padded operating envelope','bounds':dataset['bounds'],'engine':{'name':'valhalla','version':'3.9.1'},'dataset':dataset,'components':{'vector':'buriram-shortbread-20261016','roadgraph':'buriram-roadgraph-20261015','routing':'buriram-valhalla-3.9.1-20261015'},'vector_source':'https://github.com/chatchairins-source/QueuGo/releases/tag/rider-buriram-offline-20261016','licence':'ODbL-1.0','attribution':'© OpenStreetMap contributors','source_offer':'https://www.openstreetmap.org/copyright','files':files,'uncompressed_bytes':sum(f['bytes'] for f in files),'delta_updates':False,'android_runtime_included':False}
        (destination/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');verify(destination)
        archive=destination.with_suffix('.tar.gz')
        with tarfile.open(archive,'w:gz') as out:out.add(destination,arcname=version)
        (Path(str(archive)+'.sha256')).write_text(checksum(archive)+'  '+archive.name+'\n')
        return {'archive':str(archive),'sha256':checksum(archive),'bytes':archive.stat().st_size,'uncompressed_bytes':manifest['uncompressed_bytes']}
    except Exception:
        shutil.rmtree(destination,ignore_errors=True);raise

def install(archive,store,expected):
    if not re.fullmatch('[a-f0-9]{64}',expected) or checksum(archive)!=expected:raise ValueError('Archive checksum mismatch')
    store=Path(store);store.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.install-',dir=store) as temporary:
        stage=Path(temporary)
        with tarfile.open(archive,'r:gz') as source:
            members=source.getmembers();total=sum(m.size for m in members)
            if total>shutil.disk_usage(store).free or total>4*1024**3 or len(members)>100000:raise ValueError('Insufficient disk or package size limit')
            for member in members:
                target=stage/member.name
                if not target.resolve().is_relative_to(stage.resolve()) or not (member.isdir() or member.isfile()):raise ValueError('Unsafe archive entry')
            source.extractall(stage,filter='data')
        roots=list(stage.iterdir())
        if len(roots)!=1 or not roots[0].is_dir():raise ValueError('Invalid package root')
        manifest=verify(roots[0]);destination=store/manifest['id']
        if destination.exists():
            existing=verify(destination)
            if existing!=manifest:raise ValueError('Immutable package version conflict')
        else:os.replace(roots[0],destination)
    return manifest['id']

def activate(store,version):
    store=Path(store);version=safe_name(version);verify(store/version)
    pointer=store/'active.json';old=json.loads(pointer.read_text()) if pointer.exists() else {}
    value={'current':version,'previous':old.get('current') if old.get('current')!=version else old.get('previous')}
    temporary=store/'.active.tmp'
    with temporary.open('w') as out:json.dump(value,out);out.flush();os.fsync(out.fileno())
    os.replace(temporary,pointer)
    descriptor=os.open(store,os.O_RDONLY)
    try:os.fsync(descriptor)
    finally:os.close(descriptor)
    return value

def resolve(store,runtime_directory=None):
    store=Path(store);version=safe_name(json.loads((store/'active.json').read_text())['current']);root=store/version;verify(root)
    config=json.loads((root/'valhalla.json').read_text());config['mjolnir']['tile_dir']=str((root/'tiles').resolve())
    runtime=Path(runtime_directory or store)/('runtime-'+version+'.json');runtime.parent.mkdir(parents=True,exist_ok=True);runtime.write_text(json.dumps(config))
    return {'database':str(root/'roads.sqlite'),'config':str(runtime),'vector':str(root/'vector.pmtiles')}

def main():
    p=argparse.ArgumentParser();sub=p.add_subparsers(dest='action',required=True)
    b=sub.add_parser('build');b.add_argument('--database',required=True);b.add_argument('--graph',required=True);b.add_argument('--vector',required=True);b.add_argument('--destination',required=True);b.add_argument('--version',required=True)
    for action in ('activate','rollback','resolve'):
        q=sub.add_parser(action);q.add_argument('--store',required=True)
        if action=='activate':q.add_argument('--version',required=True)
    i=sub.add_parser('install');i.add_argument('--archive',required=True);i.add_argument('--sha256',required=True);i.add_argument('--store',required=True)
    d=sub.add_parser('download');d.add_argument('--url',required=True);d.add_argument('--sha256',required=True);d.add_argument('--store',required=True)
    a=p.parse_args();values=vars(a);action=values.pop('action')
    if action=='build':result=build(**values)
    elif action=='install':result=install(a.archive,a.store,a.sha256)
    elif action=='activate':result=activate(a.store,a.version)
    elif action=='resolve':result=resolve(a.store)
    elif action=='rollback':
        previous=json.loads((Path(a.store)/'active.json').read_text()).get('previous')
        if not previous:raise ValueError('No verified previous version')
        result=activate(a.store,previous)
    else:
        if not a.url.startswith('https://'):raise ValueError('HTTPS required')
        with tempfile.TemporaryDirectory() as t:
            file=Path(t)/'package.tar.gz'
            with urllib.request.urlopen(a.url,timeout=60) as source,file.open('wb') as out:
                if not source.url.startswith('https://'):raise ValueError('Insecure redirect')
                downloaded=0
                while chunk:=source.read(1024*1024):
                    downloaded+=len(chunk)
                    if downloaded>2*1024**3:raise ValueError('Download size limit')
                    out.write(chunk)
            result=install(file,a.store,a.sha256)
    print(json.dumps(result))
if __name__=='__main__':main()
