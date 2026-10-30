"""Fail closed on missing, compressed, altered or duplicate bundled map assets."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-sha', required=True)
parser.add_argument('apks', type=Path, nargs='+')
args = parser.parse_args()
reports = []
map_digests = set()
for apk in args.apks:
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        map_names = [name for name in names if name.startswith('assets/maps/') and not name.endswith('/')]
        expected = {'assets/maps/thailand-shortbread.pmtiles', 'assets/maps/thailand-shortbread.json'}
        if set(map_names) != expected or len(map_names) != 2:
            raise SystemExit(f'{apk.name}: unexpected, missing or duplicate map assets')
        entry = archive.getinfo('assets/maps/thailand-shortbread.pmtiles')
        if entry.compress_type != zipfile.ZIP_STORED:
            raise SystemExit(f'{apk.name}: PMTiles must be stored without ZIP compression')
        manifest = json.loads(archive.read('assets/maps/thailand-shortbread.json'))
        with archive.open(entry) as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if digest != manifest['sha256'] or entry.file_size != manifest['bytes']:
            raise SystemExit(f'{apk.name}: map integrity mismatch')
        if not any(args.source_sha.encode() in archive.read(name) for name in names if name.endswith('.dex')):
            raise SystemExit(f'{apk.name}: expected source SHA is absent from DEX')
        map_digests.add(digest)
    with apk.open('rb') as stream:
        apk_digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    reports.append({'apk': apk.name, 'bytes': apk.stat().st_size, 'sha256': apk_digest,
                    'map_sha256': digest, 'map_bytes': entry.file_size, 'map_zip_compression': 'stored',
                    'source_sha': args.source_sha, 'runtime_offline_rendering': 'requires separate evidence'})
if len(map_digests) != 1:
    raise SystemExit('Apps contain different map archives')
print(json.dumps(reports, indent=2))
