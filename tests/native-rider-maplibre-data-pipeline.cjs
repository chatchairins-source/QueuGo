const fs=require('fs');
const assert=require('assert');
const {execFileSync}=require('child_process');

const scriptPath='ops/maps/build-buriram-shortbread-pmtiles.sh';
const script=fs.readFileSync(scriptPath,'utf8');

execFileSync('bash',['-n',scriptPath],{stdio:'pipe'});
const plan=execFileSync(scriptPath,['--print-plan'],{
  encoding:'utf8',
  env:{
    ...process.env,
    QUEUEGO_MAP_WORK_DIR:'/tmp/queuego-map-plan-work',
    QUEUEGO_MAP_OUTPUT_DIR:'/tmp/queuego-map-plan-output'
  }
});
assert(plan.includes('planetiler_version=0.10.2')&&
  plan.includes('bounds=102.369,14.109,103.592,15.819')&&
  plan.includes('schema=shortbread'),
  'Buriram PMTiles pipeline print-plan must remain executable and deterministic');

assert(script.includes('PLANETILER_VERSION="${PLANETILER_VERSION:-0.10.2}"'),
  'Buriram tile build must pin Planetiler 0.10.2 by default');
assert(script.includes('https://download.geofabrik.de/asia/thailand-latest.osm.pbf'),
  'Buriram tile build must use the real Thailand OSM PBF source');
assert(script.includes('BURIRAM_BOUNDS="${BURIRAM_BOUNDS:-102.369,14.109,103.592,15.819}"'),
  'Buriram tile build must use the province bounding box by default');
assert(script.includes('planetiler.jar.sha256')&&script.includes('sha256sum'),
  'Planetiler binary must be SHA-256 verified before execution');
assert(script.includes('"${OSM_URL}.md5"')&&script.includes('md5sum'),
  'Thailand OSM PBF must be checked against the Geofabrik MD5');
assert(script.includes('--continue-at -'),
  'Thailand OSM source download must resume after network interruption');
assert(script.includes('-jar "${PLANETILER_JAR}" shortbread')&&
  script.includes('--output="${OUTPUT_PM_TILES}"')&&
  script.includes('--bounds="${BURIRAM_BOUNDS}"'),
  'Planetiler must generate a bounded Shortbread PMTiles archive');
assert(script.includes('"sha256": digest.hexdigest()')&&
  script.includes('"attribution": "© OpenStreetMap contributors"'),
  'Generated package manifest must expose integrity and OSM attribution');
assert(script.includes('--print-plan'),
  'Tile pipeline must support a network-free build-plan inspection mode');

console.log('native Rider MapLibre Buriram data-pipeline contract passed');
