"""Recover verified rendering DATA from an immutable predecessor artifact.

Only maps/*.pmtiles and its provenance are copied. Every application is built
from the current checkout. Rendering and routing snapshots are recorded separately.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import zipfile


def checksum(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for data in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(data)
    return digest.hexdigest()


def validate(root, expected_zip_sha, expected_source_url):
    maps = root / 'maps'
    metadata = json.loads((maps / 'thailand-shortbread.json').read_text())
    assert metadata['source_url'] == expected_source_url, 'Unexpected rendering snapshot'
    spec = importlib.util.spec_from_file_location('country_assets',
        Path(__file__).with_name('prepare-thailand-map-assets.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    actual = module.inspect_archive(maps / 'thailand-shortbread.pmtiles')
    assert actual['sha256'] == metadata['sha256'] and actual['bytes'] == metadata['bytes']
    recovery = json.loads((maps / 'thailand-shortbread-recovery.json').read_text())
    assert recovery['reference_zip_sha256'] == expected_zip_sha
    print(json.dumps({'rendering_source': expected_source_url, 'archive': actual,
                      'routing_snapshot': 'separately checked Thailand261104 graph',
                      'application_source': 'current checkout; predecessor code is not copied'}))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--reference-zip')
    parser.add_argument('--expected-zip-sha256', required=True)
    parser.add_argument('--expected-source-url', required=True)
    parser.add_argument('--assets-root', required=True)
    args = parser.parse_args()
    root = Path(args.assets_root).resolve()
    if args.reference_zip:
        archive = Path(args.reference_zip)
        assert checksum(archive) == args.expected_zip_sha256, 'Reference artifact checksum mismatch'
        root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='.country-reference-', dir=root.parent) as temporary:
            apk = Path(temporary) / 'reference-customer.apk'
            with zipfile.ZipFile(archive) as source:
                candidates = [name for name in source.namelist() if name.endswith('/customer-debug.apk') or name == 'customer-debug.apk']
                assert len(candidates) == 1, 'Expected exactly one reference APK'
                with source.open(candidates[0]) as input_file, apk.open('wb') as output_file:
                    shutil.copyfileobj(input_file, output_file, 1024 * 1024)
            maps = root / 'maps'
            maps.mkdir(exist_ok=True)
            with zipfile.ZipFile(apk) as source:
                for name in ('thailand-shortbread.pmtiles', 'thailand-shortbread.json'):
                    with source.open('assets/maps/' + name) as input_file, (maps / name).open('wb') as output_file:
                        shutil.copyfileobj(input_file, output_file, 1024 * 1024)
            (maps / 'thailand-shortbread-recovery.json').write_text(json.dumps({
                'reference_run': 41029740374, 'reference_artifact': 12965729022,
                'reference_zip_sha256': args.expected_zip_sha256,
                'reference_apk_sha256': checksum(apk),
                'data_only': True, 'rendering_source_url': args.expected_source_url,
                'reason': 'Fresh coastline upstream timed out; reuse pinned verified national rendering data',
                'routing_source_url': 'https://download.geofabrik.de/asia/thailand-261104.osm.pbf',
            }, indent=2) + '\n')
    validate(root, args.expected_zip_sha256, args.expected_source_url)


if __name__ == '__main__':
    main()
