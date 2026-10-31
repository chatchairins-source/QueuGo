"""Stage validated, freshly built Debug APKs; never reuse predecessor binaries."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys


def export(manifest_path, output):
    source_sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    reports = json.loads(manifest_path.read_text())
    roles = [row['role'] for row in reports]
    if sorted(roles) != ['customer', 'merchant', 'rider']:
        raise ValueError('Expected exactly three unique APK roles')
    # Validate every file before writing any deliverable.
    for row in reports:
        role = row['role']
        source = Path(f'native-android/{role}/build/outputs/apk/debug/{role}-debug.apk')
        if row['source_sha'] != source_sha or row['apk'] != source.name:
            raise ValueError(f'{role}: build source mismatch')
        with source.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if digest != row['sha256'] or source.stat().st_size != row['bytes']:
            raise ValueError(f'{role}: APK changed after validation')
    for row in reports:
        role = row['role']
        source = Path(f'native-android/{role}/build/outputs/apk/debug/{role}-debug.apk')
        directory = output / role
        directory.mkdir(parents=True, exist_ok=True)
        filename = f'QueueGo-{role.title()}-Test-{source_sha[:12]}.apk'
        shutil.copyfile(source, directory / filename)
        metadata = dict(row, apk=filename, build_type='debug', production_certified=False,
                        authenticated_e2e=False, physical_device=False)
        (directory / 'TESTED-SOURCE.json').write_text(json.dumps(metadata, indent=2) + '\n')
        (directory / 'SHA256SUMS').write_text(f"{row['sha256']}  {filename}\n")
        print(f"{role}: {row['bytes']} bytes SHA-256 {row['sha256']}")


if __name__ == '__main__':
    export(Path(sys.argv[1]), Path(sys.argv[2]))
