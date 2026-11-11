"""Assemble an audited SDK dependency from complete source-built native libraries.

Official 13.7.1 Java classes/resources and the other ABIs remain byte-identical.
This never edits ELF bytes; the two replacements are complete compiled libraries.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zipfile

VERSION = '13.7.1-qg4625.1'
SOURCE = '4cf121e515d8d065b57cd555a0b231ea27ff0e5d'
BUILDER = '5859c94d69d3b67f26638b3a75847f63bc42a0d8'
PATCH = '9d3ff4ac5d6ae1c6e74b0f5dead516d9e32d540b6ed7f5d8cb63fd2b76bc8af0'
BASELINE_AAR = '4a6762e878ae8f392a27d89138c19196a519121d1449f824e542058147a8a072'
BASELINE_POM = '56349b4aae6f886c5a2befeaeb375e28c91cd457791555fd3a5851692d897de9'
ZIP_HASHES = {
    'x86_64': 'd26a429791c6e1b08ff0e738f225fd990f767e42646519b62c90ee62791c3b8a',
    'arm64-v8a': '81c79af1e06cd29a32c1a54cf5042a31c1ad4ecc6cb9f57eac9f8e2d277da63d',
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def native_library(archive, abi):
    assert digest(archive.read_bytes()) == ZIP_HASHES[abi], 'SDK artifact checksum mismatch'
    with zipfile.ZipFile(archive) as bundle:
        provenance = json.loads(bundle.read('provenance.json'))
        assert provenance['source_commit'] == SOURCE and provenance['builder_commit'] == BUILDER
        assert provenance['patch_sha256'] == PATCH and provenance['abi'] == abi
        aar = bundle.read('maplibre-opengl-13.7.1-uniform4625.aar')
        assert digest(aar) == provenance['aar_sha256'] and len(aar) == provenance['aar_bytes']
    name = f'jni/{abi}/libmaplibre.so'
    with zipfile.ZipFile(io.BytesIO(aar)) as package:
        libraries = [n for n in package.namelist() if n.startswith('jni/') and n.endswith('.so')]
        assert libraries == [name], libraries
        data = package.read(name)
    assert data[:6] == b'\x7fELF\x02\x01', 'Expected a little-endian 64-bit ELF'
    assert struct.unpack_from('<H', data, 18)[0] == {'x86_64': 62, 'arm64-v8a': 183}[abi]
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    assert size >= 56 and 0 < count < 128 and offset + size * count <= len(data)
    loads = [struct.unpack_from('<IIQQQQQQ', data, offset + i * size) for i in range(count)]
    loads = [p for p in loads if p[0] == 1]
    assert loads and all(p[-1] >= 16384 for p in loads), 'SDK is not 16KiB aligned'
    return name, data, provenance


def prepare(args):
    baseline = Path(args.baseline_aar).read_bytes()
    pom = Path(args.baseline_pom).read_bytes()
    assert digest(baseline) == BASELINE_AAR and digest(pom) == BASELINE_POM
    replacements, components = {}, {}
    for abi, archive in [('x86_64', args.x86_zip), ('arm64-v8a', args.arm64_zip)]:
        name, data, provenance = native_library(Path(archive), abi)
        replacements[name] = data
        components[abi] = dict(provenance, native_sha256=digest(data), native_bytes=len(data))
    root = Path(args.repository).resolve()
    target = root / 'org/maplibre/gl/android-sdk-opengl' / VERSION
    target.mkdir(parents=True, exist_ok=False)
    aar = target / f'android-sdk-opengl-{VERSION}.aar'
    with zipfile.ZipFile(io.BytesIO(baseline)) as original, zipfile.ZipFile(aar, 'w') as output:
        assert set(replacements).issubset(original.namelist())
        for entry in original.infolist():
            data = replacements.get(entry.filename, original.read(entry.filename))
            output.writestr(entry, data)
    with zipfile.ZipFile(io.BytesIO(baseline)) as original, zipfile.ZipFile(aar) as assembled:
        assert original.namelist() == assembled.namelist()
        for name in original.namelist():
            if name not in replacements:
                assert assembled.read(name) == original.read(name), 'Baseline SDK content changed: ' + name
            else:
                assert digest(assembled.read(name)) != digest(original.read(name)), 'Native fix was not applied'
    namespace = 'http://maven.apache.org/POM/4.0.0'
    ET.register_namespace('', namespace)
    document = ET.fromstring(pom)
    assert document.findtext(f'{{{namespace}}}version') == '13.7.1'
    document.find(f'{{{namespace}}}version').text = VERSION
    pom_target = aar.with_suffix('.pom')
    pom_target.write_bytes(ET.tostring(document, encoding='utf-8', xml_declaration=True))
    for file in (aar, pom_target):
        Path(str(file) + '.sha256').write_text(digest(file.read_bytes()) + '\n')
    manifest = {'version': VERSION, 'source_commit': SOURCE, 'patch_sha256': PATCH,
                'builder_commit': BUILDER, 'builder_run': 42177808701,
                'baseline_aar_sha256': BASELINE_AAR, 'baseline_pom_sha256': BASELINE_POM,
                'aar_sha256': digest(aar.read_bytes()), 'components': components,
                'unchanged': 'Official Java classes/resources and unpatched 32-bit ABI libraries',
                'installed_rider_qa': False, 'physical_device_qa': False}
    (root / 'component.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'version': VERSION, 'aar_sha256': manifest['aar_sha256']}))


def verify(args):
    root = Path(args.repository)
    manifest = json.loads((root / 'component.json').read_text())
    assert manifest['version'] == VERSION and manifest['source_commit'] == SOURCE
    assert manifest['patch_sha256'] == PATCH and manifest['builder_commit'] == BUILDER
    assert set(manifest['components']) == set(ZIP_HASHES), 'component manifest must include both audited 64-bit ABIs'
    artifact = Path(args.apk if args.action == 'verify-apk' else args.aab)
    prefix = 'lib/' if args.action == 'verify-apk' else 'base/lib/'
    with zipfile.ZipFile(artifact) as package:
        for abi, component in manifest['components'].items():
            actual = package.read(f'{prefix}{abi}/libmaplibre.so')
            assert digest(actual) == component['native_sha256'], 'Artifact does not contain source-built SDK: ' + abi
    print(json.dumps({'artifact': str(artifact), 'source_built_sdk_abis': sorted(manifest['components'])}))


parser = argparse.ArgumentParser(description=__doc__)
sub = parser.add_subparsers(dest='action', required=True)
p = sub.add_parser('prepare')
for name in ('baseline-aar', 'baseline-pom', 'x86-zip', 'arm64-zip', 'repository'):
    p.add_argument('--' + name, required=True)
v = sub.add_parser('verify-apk')
v.add_argument('--repository', required=True)
v.add_argument('--apk', required=True)
v = sub.add_parser('verify-aab')
v.add_argument('--repository', required=True)
v.add_argument('--aab', required=True)
args = parser.parse_args()
prepare(args) if args.action == 'prepare' else verify(args)
