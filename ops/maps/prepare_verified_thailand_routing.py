"""Recover only routing data from the checksummed QueueGo field-test release."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
from zipfile import ZipFile


REFERENCE_SHA256 = '43abce348f63beb0295921ae66806cb1e3851129d66f4eb3f4c5728342b9b549'
SOURCE_MANIFEST = Path(__file__).with_name('sources') / 'thailand-261104.manifest.json'
ROUTING_FILES = {'manifest.json', 'tiles.tar', 'valhalla.json'}


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify(root):
    manifest = json.loads((root / 'manifest.json').read_text())
    if manifest['source'] != json.loads(SOURCE_MANIFEST.read_text()):
        raise ValueError('Routing source does not match the pinned Thailand snapshot')
    if (manifest['format_version'], manifest['engine'], manifest['engine_version'],
            manifest['profile'], manifest['licence']) != (1, 'valhalla', '3.9.1', 'motorcycle', 'ODbL-1.0'):
        raise ValueError('Unexpected Thailand routing package format')
    for filename, key in [('tiles.tar', 'graph'), ('valhalla.json', 'config')]:
        file = root / filename
        entry = manifest[key]
        if not 0 < entry['bytes'] <= 2**31 or file.stat().st_size != entry['bytes'] or sha256(file) != entry['sha256']:
            raise ValueError(f'Routing package size or checksum mismatch: {filename}')
    print(f"Verified Thailand graph {manifest['graph']['sha256']}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('assets', type=Path)
    parser.add_argument('--reference-apk', type=Path)
    args = parser.parse_args()
    root = args.assets / 'routing'
    if args.reference_apk is None:
        verify(root)
        return
    if sha256(args.reference_apk) != REFERENCE_SHA256:
        raise ValueError('Reference APK checksum mismatch')
    args.assets.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.assets) as temp:
        staging = Path(temp)
        with ZipFile(args.reference_apk) as apk:
            for name in sorted(ROUTING_FILES):
                with apk.open(f'assets/routing/{name}') as source, (staging / name).open('wb') as dest:
                    shutil.copyfileobj(source, dest, 1024 * 1024)
        verify(staging)
        if root.exists():
            raise FileExistsError(root)
        staging.rename(root)


if __name__ == '__main__':
    main()
