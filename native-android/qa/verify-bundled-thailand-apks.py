"""Verify role-specific QueueGo Map packaging in actual Android APKs.

Customer and Merchant stream QueueGo Map online and must not contain nationwide
archives. Rider alone carries the pinned, uncompressed Thailand PMTiles archive.
Every APK must match the exact build source SHA.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-sha', required=True)
parser.add_argument('apks', type=Path, nargs='+')
args = parser.parse_args()
reports = []
seen_roles = set()
expected_assets = {'assets/maps/thailand-shortbread.pmtiles', 'assets/maps/thailand-shortbread.json'}
recovery_asset = 'assets/maps/thailand-shortbread-recovery.json'
recovery_source = 'https://download.geofabrik.de/asia/thailand-261029.osm.pbf'
recovery_zip_sha = 'a2a6985556e6ba26ef4509ba3605266ae6aa1d541bc6d772b076eac1f7a0e9b0'
map_digests = set()

for apk in args.apks:
    role = apk.name.split('-', 1)[0].lower()
    if role not in {'customer', 'merchant', 'rider'} or role in seen_roles:
        raise SystemExit(f'{apk.name}: unknown or duplicate app role')
    seen_roles.add(role)
    recovery_verified = False
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        map_names = [name for name in names if name.startswith('assets/maps/') and not name.endswith('/')]
        if role == 'rider':
            allowed_assets = expected_assets | ({recovery_asset} if recovery_asset in map_names else set())
            if set(map_names) != allowed_assets or len(map_names) != len(allowed_assets):
                raise SystemExit(f'{apk.name}: missing, duplicate or unexpected Rider map assets')
            entry = archive.getinfo('assets/maps/thailand-shortbread.pmtiles')
            if entry.compress_type != zipfile.ZIP_STORED:
                raise SystemExit(f'{apk.name}: Rider PMTiles must be stored without ZIP compression')
            manifest = json.loads(archive.read('assets/maps/thailand-shortbread.json'))
            with archive.open(entry) as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            if digest != manifest['sha256'] or entry.file_size != manifest['bytes']:
                raise SystemExit(f'{apk.name}: Rider map integrity mismatch')
            if recovery_asset in map_names:
                provenance = json.loads(archive.read(recovery_asset))
                valid = (isinstance(provenance, dict) and
                         provenance.get('reference_run') == 41029740374 and
                         provenance.get('reference_artifact') == 12965729022 and
                         provenance.get('reference_zip_sha256') == recovery_zip_sha and
                         provenance.get('data_only') is True and
                         provenance.get('rendering_source_url') == recovery_source and
                         manifest.get('source_url') == recovery_source)
                apk_sha = provenance.get('reference_apk_sha256') if isinstance(provenance, dict) else None
                if not valid or not isinstance(apk_sha, str) or not re.fullmatch(r'[0-9a-f]{64}', apk_sha):
                    raise SystemExit(f'{apk.name}: invalid Rider map recovery provenance')
                recovery_verified = True
            map_digests.add(digest)
            map_bytes = entry.file_size
            map_compression = 'stored'
        else:
            if map_names:
                raise SystemExit(f'{apk.name}: {role} must use online QueueGo Map without bundled nationwide map assets')
            digest = None
            map_bytes = 0
            map_compression = None
        if not any(args.source_sha.encode() in archive.read(name) for name in names if name.endswith('.dex')):
            raise SystemExit(f'{apk.name}: expected source SHA is absent from DEX')
    with apk.open('rb') as stream:
        apk_digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    reports.append({'role': role, 'apk': apk.name, 'bytes': apk.stat().st_size,
                    'sha256': apk_digest, 'map_sha256': digest,
                    'map_bytes': map_bytes, 'map_zip_compression': map_compression,
                    'map_recovery_provenance_verified': recovery_verified,
                    'map_mode': 'bundled_offline' if role == 'rider' else 'queuego_online',
                    'source_sha': args.source_sha,
                    'runtime_map_rendering': 'requires separate device evidence'})

if seen_roles != {'customer', 'merchant', 'rider'}:
    raise SystemExit(f'Expected Customer, Merchant and Rider APKs, got {sorted(seen_roles)}')
if len(map_digests) != 1:
    raise SystemExit('Expected exactly one verified Rider nationwide map archive')
print(json.dumps(reports, indent=2))
