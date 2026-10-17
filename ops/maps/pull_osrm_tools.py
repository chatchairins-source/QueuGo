#!/usr/bin/env python3
"""Fetch the pinned official OSRM OCI image without requiring a Docker daemon.
Only engine binaries, profiles, libraries and licence notices are extracted.
No execution of image entrypoints and no untrusted paths/links outside target.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import tarfile
import requests

REPOSITORY = 'project-osrm/osrm-backend'
TAG = 'v6.0.0'
ACCEPT = ','.join(['application/vnd.oci.image.index.v1+json', 'application/vnd.docker.distribution.manifest.list.v2+json', 'application/vnd.oci.image.manifest.v1+json', 'application/vnd.docker.distribution.manifest.v2+json'])


def pull(target):
    root = Path(target).resolve(); root.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    token = session.get('https://ghcr.io/token', params={'service': 'ghcr.io', 'scope': f'repository:{REPOSITORY}:pull'}, timeout=60)
    token.raise_for_status()
    session.headers.update({'Authorization': 'Bearer ' + token.json()['token'], 'Accept': ACCEPT})
    def manifest(ref):
        r = session.get(f'https://ghcr.io/v2/{REPOSITORY}/manifests/{ref}', timeout=60); r.raise_for_status()
        return r.json(), r.headers.get('Docker-Content-Digest')
    document, index_digest = manifest(TAG)
    if 'manifests' in document:
        digest = next(m['digest'] for m in document['manifests'] if m['platform'].get('os') == 'linux' and m['platform'].get('architecture') == 'amd64')
        document, digest = manifest(digest)
    else: digest = index_digest
    for index, layer in enumerate(document['layers']):
        cache = root.parent / (layer['digest'].replace(':', '-') + '.tar.gz')
        if not cache.exists():
            with session.get(f"https://ghcr.io/v2/{REPOSITORY}/blobs/{layer['digest']}", stream=True, timeout=120) as response:
                response.raise_for_status()
                with cache.open('wb') as out:
                    for block in response.iter_content(1024 * 1024): out.write(block)
        assert hashlib.sha256(cache.read_bytes()).hexdigest() == layer['digest'].split(':')[1], 'OCI blob digest mismatch'
        with tarfile.open(cache) as archive:
            for member in archive:
                name = PurePosixPath(member.name)
                if name.is_absolute() or '..' in name.parts: continue
                text = str(name)
                if not any(text.startswith(p) for p in ('usr/local/', 'usr/lib/', 'lib/', 'lib64/', 'opt/', 'usr/share/doc/osrm')): continue
                destination = root.joinpath(*name.parts)
                if member.isfile():
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    if destination.is_symlink(): destination.unlink()
                    with archive.extractfile(member) as source, destination.open('wb') as output:
                        import shutil
                        shutil.copyfileobj(source, output)
                    destination.chmod(member.mode & 0o777)
                elif member.issym():
                    link = member.linkname
                    pointed = (root / link.lstrip('/')) if link.startswith('/') else destination.parent / link
                    pointed = pointed.resolve()
                    if not pointed.is_relative_to(root): continue
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    if destination.exists() or destination.is_symlink(): destination.unlink()
                    destination.symlink_to(os.path.relpath(pointed, destination.parent))
        print(f'OSRM OCI layer {index+1}/{len(document["layers"])} verified', flush=True)
    metadata = {'image': f'ghcr.io/{REPOSITORY}:{TAG}', 'index_digest': index_digest, 'platform_digest': digest, 'licence': 'BSD-2-Clause'}
    (root / 'image-manifest.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print(json.dumps(metadata))

if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('target'); args = p.parse_args(); pull(args.target)
