"""Stage validated, freshly built Debug APKs; never reuse predecessor binaries."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys


def export(manifest_path, output):
    source_sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    if len(source_sha) != 40 or any(c not in '0123456789abcdef' for c in source_sha):
        raise ValueError('APK export requires a valid 40-character Git source SHA')
    if any(part.is_symlink() for part in (manifest_path, *manifest_path.parents)) or not manifest_path.is_file():
        raise ValueError('APK manifest must be a regular file, not a symlink (including ancestors)')
    reports = json.loads(manifest_path.read_text())
    if not isinstance(reports, list) or not all(isinstance(row, dict) for row in reports):
        raise ValueError('APK manifest must be a list of role records')
    # Fail closed on malformed role metadata before any filesystem writes.
    if any(not isinstance(row.get('role'), str) for row in reports):
        raise ValueError('APK manifest role must be a string')
    if any(not isinstance(row.get('source_sha'), str) or not isinstance(row.get('apk'), str)
           for row in reports):
        raise ValueError('APK manifest requires string source_sha and apk fields')
    roles = [row['role'] for row in reports]
    if sorted(roles) != ['customer', 'merchant', 'rider']:
        raise ValueError('Expected exactly three unique APK roles')
    if any(parent.is_symlink() for parent in (output, *output.parents)):
        raise ValueError('Refusing symlinked APK export root or ancestor')
    if output.exists() and not output.is_dir():
        raise ValueError('APK export root must be a directory')
    # The root manifest is retained by the workflow. Never allow it to redirect
    # or disguise the evidence used to identify the exported APK set.
    retained_manifest = output / 'APK-MANIFEST.json'
    if retained_manifest.is_symlink() or (retained_manifest.exists() and not retained_manifest.is_file()):
        raise ValueError('APK export manifest evidence must be a regular file, not a symlink')
    for role in roles:
        directory = output / role
        if directory.is_symlink():
            raise ValueError(f'{role}: refusing symlinked artifact directory')
        if directory.exists() and not directory.is_dir():
            raise ValueError(f'{role}: artifact path must be a directory')
        signature = directory / 'APK-SIGNATURE.txt'
        if signature.is_symlink():
            raise ValueError(f'{role}: refusing symlinked signature evidence')
        if signature.exists() and not signature.is_file():
            raise ValueError(f'{role}: signature evidence must be a regular file')
        for evidence_name in ('TESTED-SOURCE.json', 'SHA256SUMS'):
            evidence = directory / evidence_name
            if evidence.is_symlink() or (evidence.exists() and not evidence.is_file()):
                raise ValueError(f'{role}: export metadata must be a regular file, not a symlink')
        if directory.exists() and any(p.name != 'APK-SIGNATURE.txt' for p in directory.iterdir()):
            raise ValueError(f'{role}: refusing to mix fresh APKs with existing role artifacts')
    # Validate every file before writing any deliverable.
    for row in reports:
        role = row['role']
        source = Path(f'native-android/{role}/build/outputs/apk/debug/{role}-debug.apk')
        if any(part.is_symlink() for part in (source, *source.parents)) or not source.is_file():
            raise ValueError(f'{role}: APK source must be a regular file, not a symlink (including ancestors)')
        if (not isinstance(row.get('sha256'), str) or len(row['sha256']) != 64
                or any(c not in '0123456789abcdef' for c in row['sha256'])
                or type(row.get('bytes')) is not int or row['bytes'] <= 0):
            raise ValueError(f'{role}: invalid APK integrity metadata')
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
        destination = directory / filename
        shutil.copyfile(source, destination)
        with destination.open('rb') as stream:
            copied_digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if copied_digest != row['sha256'] or destination.stat().st_size != row['bytes']:
            destination.unlink(missing_ok=True)
            raise ValueError(f'{role}: staged APK integrity mismatch')
        metadata = dict(row, apk=filename, build_type='debug', production_certified=False,
                        authenticated_e2e=False, physical_device=False)
        (directory / 'TESTED-SOURCE.json').write_text(json.dumps(metadata, indent=2) + '\n')
        (directory / 'SHA256SUMS').write_text(f"{row['sha256']}  {filename}\n")
        print(f"{role}: {row['bytes']} bytes SHA-256 {row['sha256']}")


if __name__ == '__main__':
    export(Path(sys.argv[1]), Path(sys.argv[2]))
