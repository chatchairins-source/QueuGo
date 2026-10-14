const fs=require('fs');
const assert=require('assert');

const workflow=fs.readFileSync('.github/workflows/build-sawaijee-osm-map.yml','utf8');
const verifier=fs.readFileSync('native-android/qa/verify-pmtiles-village.py','utf8');

assert(workflow.includes('work-native-rider-maplibre-sawaijee-realdata-20261014'),
  'Real-data workflow must stay isolated from production Rider branches');
assert(workflow.includes('QG_MAP_DATA_DIR=$RUNNER_TEMP/queuego-sawaijee-map')&&
  !workflow.includes('QG_MAP_DATA_DIR: ${{ runner.temp }}'),
  'Runner temp path must be exported after runner startup instead of using unavailable job-level runner context');
assert(workflow.includes('103.115,14.870,103.180,14.945'),
  'Real-data workflow must clip the smoke archive around SawaiJee');
assert(workflow.includes("QG_SAWAIJEE_LAT: '14.9058'")&&workflow.includes("QG_SAWAIJEE_LON: '103.14682'"),
  'Real-data gate must verify the SawaiJee pilot point');
assert(workflow.includes('build-buriram-shortbread-pmtiles.sh')&&
  workflow.includes('sawaijee-shortbread.pmtiles'),
  'SawaiJee archive must be built through the pinned Planetiler Shortbread pipeline');
assert(workflow.includes('compression-level: 0')&&workflow.includes('retention-days: 7'),
  'Already-compressed PMTiles evidence should upload without recompression and expire automatically');

assert(verifier.includes('HEADER_SIZE = 127')&&verifier.includes('MAGIC = b"PMTiles"'),
  'Verifier must enforce the PMTiles v3 fixed header');
assert(verifier.includes('TILE_TYPE_MVT = 1')&&verifier.includes('required-maxzoom'),
  'Verifier must require MVT vector data and navigation-grade max zoom');
assert(verifier.includes('root_offset = u64(header, 8)')&&
  verifier.includes('metadata_offset = u64(header, 24)')&&
  verifier.includes('tile_data_offset = u64(header, 56)'),
  'Verifier must reject archives with out-of-range root, metadata or tile-data sections');
assert(verifier.includes('target_inside_bounds')&&
  verifier.includes('min_lat <= args.target_lat <= max_lat')&&
  verifier.includes('min_lon <= args.target_lon <= max_lon'),
  'Verifier must prove the pilot village point is inside the generated archive');

console.log('native Rider MapLibre SawaiJee real-data gate contract passed');
