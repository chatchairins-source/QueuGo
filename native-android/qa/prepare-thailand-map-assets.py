"""Validate a real nationwide Shortbread archive and stage build-time APK assets.

This checks archive structure/declared coverage, not actual road completeness.
The separate offline renderer/device gate must still pass.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import struct


def inspect_archive(path):
    size = path.stat().st_size
    with path.open('rb') as stream:
        header = stream.read(127)
        if len(header) != 127 or header[:8] != b'PMTiles\x03':
            raise ValueError('Expected a PMTiles v3 archive')
        root_offset, root_size, metadata_offset, metadata_size, leaf_offset, leaf_size, tile_offset, tile_size = struct.unpack_from('<8Q', header, 8)
        for offset, length in ((root_offset, root_size), (metadata_offset, metadata_size), (leaf_offset, leaf_size), (tile_offset, tile_size)):
            if length and (offset < 127 or offset + length > size):
                raise ValueError('Archive section extends outside the file')
        if not root_size or not tile_size or metadata_size > 16 * 1024 * 1024:
            raise ValueError('Empty or unreasonable archive section')
        west, south, east, north = (v / 1e7 for v in struct.unpack_from('<4i', header, 102))
        if west > 97.5 or south > 5.8 or east < 105.5 or north < 20.3:
            raise ValueError('Declared bounds do not cover Thailand; regional archives are forbidden')
        if header[99] != 1 or header[100] > 5 or header[101] < 14:
            raise ValueError('Expected MVT with country overview and street-level zoom 14')
        stream.seek(metadata_offset)
        metadata = stream.read(metadata_size)
        if header[97] == 2:
            metadata = gzip.decompress(metadata)
        elif header[97] != 1:
            raise ValueError('Unsupported metadata compression')
        metadata = json.loads(metadata)
        layers = {layer['id'] for layer in metadata.get('vector_layers', [])}
        if not {'streets', 'water_polygons', 'buildings'}.issubset(layers):
            raise ValueError('Archive is not compatible with the existing Shortbread style')
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {'sha256': digest, 'bytes': size, 'bounds': [west, south, east, north],
            'minzoom': header[100], 'maxzoom': header[101], 'layers': sorted(layers)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('assets_root', type=Path)
    parser.add_argument('--expected-sha256', required=True)
    parser.add_argument('--source-url', required=True)
    parser.add_argument('--source-date', required=True)
    args = parser.parse_args()
    result = inspect_archive(args.archive)
    if result['sha256'] != args.expected_sha256.lower():
        raise SystemExit('Map SHA-256 mismatch')
    result.update(source_url=args.source_url, source_date=args.source_date,
                  license='ODbL-1.0', attribution='© OpenStreetMap contributors',
                  license_url='https://www.openstreetmap.org/copyright',
                  rendering_test='pending', routing_data=False)
    target = args.assets_root / 'maps'
    target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.archive, target / 'thailand-shortbread.pmtiles')
    (target / 'thailand-shortbread.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
