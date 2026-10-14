const fs=require('fs');
const assert=require('assert');

const settings=fs.readFileSync('native-android/settings.gradle.kts','utf8');
const build=fs.readFileSync('native-android/rider-maplibre-osm/build.gradle.kts','utf8');
const style=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/ShortbreadVillageStyle.kt','utf8');
const view=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoMapLibreOsmView.kt','utf8');
const riderBuild=fs.readFileSync('native-android/rider/build.gradle.kts','utf8');
const localRoadModels=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoLocalRoadModels.kt','utf8');
const localRoadGeoJson=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoLocalRoadGeoJson.kt','utf8');
const localRoadOverlay=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoLocalRoadOverlay.kt','utf8');
const localRoadPolicy=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoLocalRoadPolicy.kt','utf8');

assert(settings.includes('":rider-maplibre-osm"'),'MapLibre OSM module must be registered in native settings');
assert(build.includes('org.maplibre.gl:android-sdk-opengl:13.5.3'),'MapLibre OSM module must pin the stable OpenGL MapLibre artifact');
assert(!riderBuild.includes('project(":rider-maplibre-osm")'),'MapLibre OSM module must remain isolated from Rider Home until its runtime/performance gate passes');

for(const kind of ['residential','unclassified','living_street','service']){
  assert(style.includes('"'+kind+'"'),'Shortbread village style must include '+kind);
}
for(const kind of ['track','pedestrian','cycleway','footway','path']){
  assert(style.includes('"'+kind+'"'),'Shortbread village context must include '+kind);
}
assert(style.includes('lineDasharray(policy.dashPattern)'),
  'Non-standard village access paths must stay visually distinct from normal drivable streets');
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
assert(worker.includes('CoroutineWorker')&&worker.includes('NetworkType.CONNECTED')&&worker.includes('setProgressAsync('),'PMTiles download must run as resumable background work with non-blocking progress reporting');
assert(!downloader.includes('vector.openstreetmap.org'),'Offline archive downloader must never bulk-download from OSMF tile infrastructure');
assert(!style.includes('SOURCE_LAYER_LAND'),'Village style must not depend on a non-standard Shortbread land source-layer');
assert(view.includes('mapViewIdentity()')&&view.includes('mapIdentity()'),'Module must expose renderer identity for future lifecycle retention tests');
assert(view.includes('readyMap.uiSettings.isAttributionEnabled = true'),'OpenStreetMap attribution control must stay enabled');
assert(
  localRoadModels.includes('enum class QueueGoLocalRoadSource')&&
  localRoadModels.includes('OPENSTREETMAP')&&
  localRoadModels.includes('RIDER_TRACE_AGGREGATE')&&
  localRoadModels.includes('FIELD_SURVEY')&&
  localRoadModels.includes('sourceReference: String')&&
  localRoadModels.includes('verifiedAtEpochMs: Long?')&&
  !localRoadModels.includes('GRAB'),
  'QueueGo Local Roads must carry explicit provenance and must not model proprietary competitor data as an import source');

assert(
  localRoadModels.includes('verification == QueueGoLocalRoadVerification.VERIFIED')&&
  localRoadModels.includes('!privateAccess')&&
  localRoadPolicy.includes('eligibleForMotorcycleRouting')&&
  localRoadOverlay.includes('CANDIDATE_LAYER_ID')&&
  localRoadOverlay.includes('lineDasharray')&&
  localRoadOverlay.includes('GeoJsonSource'),
  'Candidate/private local roads may be visual context but only verified open roads can become motorcycle routing candidates');

assert(
  localRoadGeoJson.includes('"source_ref"')&&
  localRoadGeoJson.includes('"verification"')&&
  !localRoadGeoJson.includes('rider_id')&&
  !localRoadGeoJson.includes('order_id')&&
  view.includes('fun setLocalRoads(')&&
  view.includes('QueueGoLocalRoadOverlay.installOrUpdate')&&
  view.includes('localRoadApplyCount'),
  'Local-road overlay must be privacy-safe and update the existing MapLibre style without map recreation');


console.log('native rider MapLibre OSM module contract passed');
