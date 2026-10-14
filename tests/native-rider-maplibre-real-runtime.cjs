const fs=require('fs');
const assert=require('assert');

const workflow=fs.readFileSync('.github/workflows/build-sawaijee-maplibre-runtime.yml','utf8');
const verifier=fs.readFileSync('native-android/qa/verify-maplibre-real-runtime-result.py','utf8');

assert(workflow.includes('work-native-rider-maplibre-sawaijee-real-runtime-20261014'),
  'Real PMTiles emulator gate must stay isolated from Rider production branches');
assert(workflow.includes('build-buriram-shortbread-pmtiles.sh')&&
  workflow.includes('verify-pmtiles-village.py'),
  'Real runtime gate must generate and validate SawaiJee OSM PMTiles before Android testing');
assert(workflow.includes('src/debug/assets/sawaijee-shortbread.pmtiles')&&
  workflow.includes('cmp "${QG_MAP_DATA_DIR}/${QG_PM_TILES_OUTPUT_NAME}"'),
  'Verified PMTiles must be injected only into debug assets and byte-compared before runtime');
assert(workflow.includes('run-rider-maplibre-osm-runtime.py')&&
  workflow.includes('verify-maplibre-real-runtime-result.py'),
  'Real runtime gate must execute MapLibre instrumentation and prove the non-skipped assertion');
assert(workflow.includes('14.9058')&&workflow.includes('103.14682'),
  'Real runtime gate must remain anchored on the SawaiJee pilot point');
assert(!workflow.includes('src/main/assets/sawaijee-shortbread.pmtiles'),
  'Real PMTiles smoke archive must never be bundled into main Rider assets');

assert(verifier.includes('realPmTilesAssetRendersVillageRoadsWhenInjected'),
  'Result verifier must target the real PMTiles village-road instrumentation test');
assert(verifier.includes('case.find("skipped")')&&
  verifier.includes('Real PMTiles runtime test was skipped'),
  'Result verifier must fail if the real-data instrumentation test silently skips');
assert(verifier.includes('QUEUEGO_SAWAIJEE_REAL_PMTILES_RUNTIME_EXECUTED'),
  'Successful real-data runtime verification must emit a deterministic evidence marker');

console.log('native Rider MapLibre real PMTiles runtime gate contract passed');
