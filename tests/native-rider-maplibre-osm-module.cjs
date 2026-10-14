const fs=require('fs');
const assert=require('assert');

const settings=fs.readFileSync('native-android/settings.gradle.kts','utf8');
const build=fs.readFileSync('native-android/rider-maplibre-osm/build.gradle.kts','utf8');
const style=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/ShortbreadVillageStyle.kt','utf8');
const view=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoMapLibreOsmView.kt','utf8');
const riderBuild=fs.readFileSync('native-android/rider/build.gradle.kts','utf8');

assert(settings.includes('":rider-maplibre-osm"'),'MapLibre OSM module must be registered in native settings');
assert(build.includes('org.maplibre.gl:android-sdk-opengl:13.5.3'),'MapLibre OSM module must pin the stable OpenGL MapLibre artifact');
assert(!riderBuild.includes('project(":rider-maplibre-osm")'),'MapLibre OSM module must remain isolated from Rider Home until its runtime/performance gate passes');

for(const kind of ['residential','unclassified','living_street','service']){
  assert(style.includes('"'+kind+'"'),'Shortbread village style must include '+kind);
}
assert(style.includes('SOURCE_LAYER_STREETS = "streets"'),'OSM Shortbread style must render the streets source layer');
assert(style.includes('SOURCE_LAYER_BUILDINGS = "buildings"'),'OSM Shortbread style must retain village building context');
assert(style.includes('VectorSource(SOURCE_ID, sourceUri)'),'Vector TileJSON endpoint must be runtime-configurable and not tied to one provider');
assert(!style.includes('vector.openstreetmap.org'),'OSMF public vector endpoint must not be hardcoded into the renderer');
assert(view.includes('MapLibre.getInstance(context.applicationContext)'),'MapLibre must initialize before MapView creation');
const config=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/MapLibreOsmConfig.kt','utf8');
const downloader=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoPmTilesDownloader.kt','utf8');
const worker=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoPmTilesDownloadWorker.kt','utf8');
assert(config.includes('pmtiles://file://')&&config.includes('localPmTiles('),'Offline MapLibre source must use a local PMTiles file URI');
assert(downloader.includes('header("Range", "bytes=$existingBytes-")')&&downloader.includes('StandardCopyOption.ATOMIC_MOVE'),'PMTiles downloader must resume partial archives and atomically publish completed files');
assert(downloader.includes('PROGRESS_MIN_BYTES = 512L * 1024L')&&
  downloader.includes('PROGRESS_MIN_INTERVAL_NANOS = 500_000_000L')&&
  downloader.includes('shouldReportPmTilesProgress('),
  'PMTiles progress must be throttled so WorkManager is not updated for every small network buffer');
assert(worker.includes('CoroutineWorker')&&worker.includes('NetworkType.CONNECTED')&&worker.includes('setProgressAsync('),'PMTiles download must run as resumable background work with non-blocking progress reporting');
assert(!downloader.includes('vector.openstreetmap.org'),'Offline archive downloader must never bulk-download from OSMF tile infrastructure');
assert(!style.includes('SOURCE_LAYER_LAND'),'Village style must not depend on a non-standard Shortbread land source-layer');
assert(view.includes('mapViewIdentity()')&&view.includes('mapIdentity()'),'Module must expose renderer identity for future lifecycle retention tests');
assert(view.includes('readyMap.uiSettings.isAttributionEnabled = true'),'OpenStreetMap attribution control must stay enabled');

console.log('native rider MapLibre OSM module contract passed');
