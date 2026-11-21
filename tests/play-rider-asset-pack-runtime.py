"""Exercise the real bundle verifier with disposable ZIPs, never release evidence."""
import importlib.util
import tempfile
from pathlib import Path
import zipfile

spec = importlib.util.spec_from_file_location('packs', 'native-android/qa/stage-play-rider-assets.py')
packs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packs)
# Source certification is outside this isolated payload-verifier test.
packs.check_source = lambda: None
with tempfile.TemporaryDirectory() as folder:
    root = Path(folder)
    packs.FILES = {}
    entries = {}
    for member in ('assets/maps/thailand-shortbread.pmtiles', 'assets/maps/thailand-shortbread.json', 'assets/routing/tiles.tar'):
        target = root / Path(member).name
        target.write_bytes(b'actual-test-payload')
        packs.FILES[member] = target
        pack = packs.MAP_NAME if '/maps/' in member else packs.ROUTE_NAME
        entries[pack + '/' + member] = target.read_bytes()
    for pack in (packs.MAP_NAME, packs.ROUTE_NAME):
        entries[pack + '/manifest/AndroidManifest.xml'] = b'fixture-manifest'
    def verify(extra=None, replace=None):
        payload = dict(entries)
        payload.update(extra or {})
        payload.update(replace or {})
        bundle = root / 'fixture.aab'
        with zipfile.ZipFile(bundle, 'w') as archive:
            for name, data in payload.items():
                archive.writestr(name, data)
        packs.verify_bundle(bundle)
    verify()
    for extra, replace in (
        ({packs.MAP_NAME + '/assets/maps/stale.pmtiles': b'unverified'}, None),
        ({'base/assets/maps/thailand-shortbread.json': b'stale-metadata'}, None),
        (None, {packs.ROUTE_NAME + '/assets/routing/tiles.tar': b'different-payload!!'}),
    ):
        try:
            verify(extra, replace)
        except ValueError:
            continue
        raise AssertionError('Unverified or duplicated payload was accepted')
print('Play pack runtime: valid payload accepted; extra, base duplicate and changed bytes rejected')
