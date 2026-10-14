const fs=require('fs');
const assert=require('assert');

const settings=fs.readFileSync('native-android/settings.gradle.kts','utf8');
const build=fs.readFileSync('native-android/rider-maplibre-osm/build.gradle.kts','utf8');
const style=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/ShortbreadVillageStyle.kt','utf8');
const view=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoMapLibreOsmView.kt','utf8');
const riderBuild=fs.readFileSync('native-android/rider/build.gradle.kts','utf8');
const offlinePackage=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/OfflineMapPackage.kt','utf8');
const offlineWorker=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/OfflinePmTilesDownloadWorker.kt','utf8');
const offlineManager=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoOfflineMapManager.kt','utf8');

assert(settings.includes('":rider-maplibre-osm"'),'MapLibre OSM module must be registered in native settings');
assert(build.includes('org.maplibre.gl:android-sdk-opengl:13.5.3'),'MapLibre OSM module must pin the stable OpenGL MapLibre artifact');
assert(!riderBuild.includes('project(":rider-maplibre-osm")'),'MapLibre OSM module must remain isolated from Rider Home until its runtime/performance gate passes');

for(const kind of ['residential','unclassified','living_street','service']){
  assert(style.includes('"'+kind+'"'),'Shortbread village style must include '+kind);
}
assert(style.includes('SOURCE_LAYER_STREETS = "streets"'),'OSM Shortbread style must render the streets source layer');
assert(style.includes('SOURCE_LAYER_BUILDINGS = "buildings"'),'OSM Shortbread style must retain village building context');
assert(style.includes('VectorSource(SOURCE_ID, vectorSourceUri)'),'Vector source URI must be runtime-configurable for remote TileJSON and local PMTiles');
assert(!style.includes('vector.openstreetmap.org'),'OSMF public vector endpoint must not be hardcoded into the renderer');
assert(build.includes('androidx.work:work-runtime-ktx:2.12.0'),'Offline map downloads must use pinned WorkManager 2.12.0');
assert(offlinePackage.includes('"pmtiles://file://" + finalFile(context).absolutePath'),'Installed archives must be exposed as local PMTiles sources');
assert(offlineWorker.includes('header("Range", "bytes=" + existingBytes + "-")')&&
  offlineWorker.includes('DownloadWriteMode.APPEND')&&
  offlineWorker.includes('FileOutputStream(partial, append)')&&
  offlineWorker.includes('setProgress(progressData(downloaded, total))'),
  'PMTiles worker must resume with HTTP Range and publish background progress');
assert(offlineManager.includes('ExistingWorkPolicy.KEEP')&&offlineManager.includes('NetworkType.CONNECTED'),
  'Offline package manager must de-duplicate downloads and wait for network connectivity');
assert(view.includes('MapLibre.getInstance(context.applicationContext)'),'MapLibre must initialize before MapView creation');
assert(view.includes('mapViewIdentity()')&&view.includes('mapIdentity()'),'Module must expose renderer identity for lifecycle retention tests');
assert(view.includes('renderedVillageRoadFeatureCount()')&&view.includes('queryRenderedFeatures'),'Runtime gate must query actual rendered village-road features, not only inspect static style declarations');
assert(view.includes('readyMap.uiSettings.isAttributionEnabled = true'),'OpenStreetMap attribution control must stay enabled');

console.log('native rider MapLibre OSM module contract passed');
