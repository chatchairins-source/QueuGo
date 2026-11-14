const fs=require('fs');
const cp=require('child_process');
const assert=require('assert');

const script='native-android/qa/build-buriram-shortbread-pmtiles.sh';
const src=fs.readFileSync(script,'utf8');

cp.execFileSync('bash',['-n',script],{stdio:'pipe'});

assert(src.includes('PLANETILER_VERSION="${PLANETILER_VERSION:-0.10.2}"'),
  'Buriram PMTiles build must pin Planetiler 0.10.2 by default');
assert(src.includes('ghcr.io/onthegomap/planetiler:${PLANETILER_VERSION}'),
  'Buriram PMTiles build must use the official pinned Planetiler image');
assert(src.includes('shortbread'),'Planetiler build must use the Shortbread profile');
assert(src.includes('https://download.geofabrik.de/asia/thailand-latest.osm.pbf'),
  'Planetiler must use Thailand raw OSM PBF from Geofabrik');
assert(src.includes('102.369,14.109,103.592,15.819'),
  'Planetiler must clip the default archive to the padded Buriram province bounds');
assert(src.includes('--output=${OUTPUT_PATH_IN_CONTAINER}')&&src.includes('.pmtiles'),
  'Planetiler output must be a PMTiles archive');
assert(src.includes('head -c 7')&&src.includes('"PMTiles"'),
  'Build must verify the PMTiles magic header');
assert(src.includes('sha256sum'),'Build must emit SHA-256 integrity evidence');
assert(src.includes('QG_PLANETILER_DRY_RUN'),
  'Planetiler build must support deterministic dry-run verification');
assert(!src.includes('vector.openstreetmap.org'),
  'Planetiler build must never scrape OpenStreetMap Foundation vector tiles');

const env={
  ...process.env,
  QG_PLANETILER_DRY_RUN:'1',
  QG_MAP_DATA_DIR:fs.mkdtempSync('/tmp/queuego-planetiler-')
};
try{
  const out=cp.execFileSync('bash',[script],{env,encoding:'utf8'});
  assert(out.includes('ghcr.io/onthegomap/planetiler:0.10.2'),'dry-run must pin Planetiler 0.10.2');
  assert(out.includes('shortbread'),'dry-run must execute the Shortbread task');
  assert(out.includes('thailand-latest.osm.pbf'),'dry-run must preserve the Thailand PBF input');
  assert(out.includes('buriram-shortbread.pmtiles'),'dry-run must preserve the Buriram PMTiles output');
} finally {
  fs.rmSync(env.QG_MAP_DATA_DIR,{recursive:true,force:true});
}

console.log('native Rider MapLibre Planetiler build contract passed');
