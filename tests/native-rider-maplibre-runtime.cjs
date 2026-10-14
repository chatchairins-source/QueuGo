const fs=require('fs');
const assert=require('assert');

const view=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoMapLibreOsmView.kt','utf8');
const activity=fs.readFileSync('native-android/rider-maplibre-osm/src/debug/java/com/queuego/ridermaplibreosm/MapLibreOsmRuntimeActivity.kt','utf8');
const runtime=fs.readFileSync('native-android/rider-maplibre-osm/src/androidTest/java/com/queuego/ridermaplibreosm/MapLibreOsmRuntimeTest.kt','utf8');
const runner=fs.readFileSync('native-android/qa/run-rider-maplibre-osm-runtime.py','utf8');
const workflow=fs.readFileSync('.github/workflows/build-native-rider-pilot.yml','utf8');

assert(view.includes('fun onCreate(savedInstanceState: Bundle? = null)')&&
  view.includes('mapView.onCreate(savedInstanceState)')&&
  view.includes('mapView.onSaveInstanceState(outState)'),
  'MapLibre wrapper must forward the full Android MapView lifecycle instead of hardcoding onCreate(null)');
assert(view.includes('styleApplyCount += 1')&&view.includes('fun styleApplyCount(): Int'),
  'MapLibre runtime must expose style application count for no-reload assertions');
assert(runtime.includes('repeat(60)')&&
  runtime.includes('GPS-like camera refresh recreated MapView')&&
  runtime.includes('Tab return recreated MapLibreMap')&&
  runtime.includes('Resume reloaded style'),
  'MapLibre runtime must prove 60 refreshes, tab return and resume keep the same renderer/style');
assert(activity.includes('MapLibreOsmConfig.localPmTiles(')&&
  activity.includes('assets.open("queuego-runtime.pmtiles")')&&
  activity.includes('File(filesDir, "queuego-runtime.pmtiles")')&&
  !activity.includes('http://')&&!activity.includes('https://'),
  'MapLibre runtime must copy an APK test archive into app storage and open it with pmtiles://file:// without external network');
assert(view.includes('queryRenderedFeatures(viewport, layerId)')&&
  runtime.includes('Local PMTiles residential road was not decoded and rendered')&&
  runtime.includes('renderedResidentialRoadCount()'),
  'MapLibre runtime must prove a residential road feature is decoded and rendered from the local PMTiles archive');
assert(runner.includes(':rider-maplibre-osm:connectedDebugAndroidTest')&&
  runner.includes('QUEUEGO_MAPLIBRE_OSM_RUNTIME_PASS'),
  'MapLibre runtime runner must execute the module connected Android test');
assert(workflow.includes('maplibre-osm-runtime:')&&
  workflow.includes("startsWith(github.ref, 'refs/heads/work-native-rider-maplibre-')"),
  'CI must run the isolated MapLibre runtime gate on MapLibre working branches');

console.log('native Rider MapLibre runtime contract passed');
