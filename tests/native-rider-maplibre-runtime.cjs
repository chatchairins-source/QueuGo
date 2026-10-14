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
assert(view.includes('addOnDidFinishRenderingMapListener')&&
  view.includes('queryRenderedFeatures')&&view.includes('fun renderedFeatureCount'),
  'MapLibre runtime must expose a fully-rendered signal and rendered-feature query for real PMTiles evidence');
assert(runtime.includes('repeat(60)')&&
  runtime.includes('GPS-like camera refresh recreated MapView')&&
  runtime.includes('Tab return recreated MapLibreMap')&&
  runtime.includes('Resume reloaded style'),
  'MapLibre runtime must prove 60 refreshes, tab return and resume keep the same renderer/style');
assert(activity.includes('asset://queuego-runtime-tilejson.json'),
  'MapLibre runtime must keep an APK-local fallback vector source and avoid external-network flakiness');
assert(activity.includes('sawaijee-shortbread.pmtiles')&&
  activity.includes('QueueGoPmTilesArchive.requireValidVectorV3(localFile)')&&
  activity.includes('MapLibreOsmConfig.localPmTiles(')&&
  activity.includes('14.9058')&&activity.includes('103.14682'),
  'When real SawaiJee PMTiles is injected, runtime must materialize and open the local archive at the pilot village');
assert(runtime.includes('realPmTilesAssetRendersVillageRoadsWhenInjected')&&
  runtime.includes('renderedVillageRoadFeatureCount() > 0')&&
  runtime.includes('mapFullyRendered.await'),
  'Real PMTiles runtime gate must prove village roads render from the local archive');
assert(runner.includes(':rider-maplibre-osm:connectedDebugAndroidTest')&&
  runner.includes('QUEUEGO_MAPLIBRE_OSM_RUNTIME_PASS'),
  'MapLibre runtime runner must execute the module connected Android test');
assert(workflow.includes('maplibre-osm-runtime:')&&
  workflow.includes("startsWith(github.ref, 'refs/heads/work-native-rider-maplibre-')"),
  'CI must run the isolated MapLibre runtime gate on MapLibre working branches');

console.log('native Rider MapLibre runtime contract passed');
