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
assert(style.includes('VectorSource(SOURCE_ID, tileJsonUrl)'),'Vector TileJSON endpoint must be runtime-configurable and not tied to one provider');
assert(!style.includes('vector.openstreetmap.org'),'OSMF public vector endpoint must not be hardcoded into the renderer');
assert(view.includes('MapLibre.getInstance(context.applicationContext)'),'MapLibre must initialize before MapView creation');
assert(view.includes('mapViewIdentity()')&&view.includes('mapIdentity()'),'Module must expose renderer identity for future lifecycle retention tests');
assert(view.includes('readyMap.uiSettings.isAttributionEnabled = true'),'OpenStreetMap attribution control must stay enabled');

console.log('native rider MapLibre OSM module contract passed');
