const fs=require('fs');
const assert=require('assert');

const settings=fs.readFileSync('native-android/settings.gradle.kts','utf8');
const build=fs.readFileSync('native-android/rider-maplibre-osm/build.gradle.kts','utf8');
const style=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/ShortbreadVillageStyle.kt','utf8');
const view=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoMapLibreOsmView.kt','utf8');
const localRoads=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoLocalRoads.kt','utf8');
const localRoadFeed=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoLocalRoadFeed.kt','utf8');
const localRoadRouting=fs.readFileSync('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/QueueGoLocalRoadRoutingPolicy.kt','utf8');
const riderBuild=fs.readFileSync('native-android/rider/build.gradle.kts','utf8');

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

assert(localRoads.includes('enum class QueueGoLocalRoadSource')&&
  localRoads.includes('OPENSTREETMAP_VERIFIED')&&
  localRoads.includes('PUBLIC_DATA')&&
  localRoads.includes('RIDER_GPS_TRACE_VERIFIED')&&
  localRoads.includes('MERCHANT_CONFIRMED')&&
  localRoads.includes('FIELD_SURVEY')&&
  !localRoads.toLowerCase().includes('grab'),
  'QueueGo Local Roads provenance must exclude competitor/private geometry sources');
assert(localRoads.includes('val renderable: Boolean')&&
  localRoads.includes('verified && access != QueueGoLocalRoadAccess.PRIVATE_RESTRICTED')&&
  localRoads.includes('GeoJsonSource(SOURCE_ID, features)')&&
  localRoads.includes('existing.setGeoJson(features)'),
  'QueueGo Local Roads must render only verified non-private geometry and update GeoJSON in place');
assert(view.includes('fun setLocalRoads(')&&
  view.includes('QueueGoLocalRoadOverlay.apply(style, localRoads)'),
  'MapLibre Rider map must accept verified local-road updates without reloading the base style');

assert(localRoadFeed.includes('schemaVersion: Int')&&
  localRoadFeed.includes('revision: Long')&&
  localRoadFeed.includes('QueueGoLocalRoadFeedDecision')&&
  localRoadFeed.includes('REJECT_STALE')&&
  localRoadFeed.includes('QUEUEGO_LOCAL_ROAD_MAX_TOTAL_POINTS')&&
  localRoadFeed.includes('queueGoLocalRoadSourceReferenceAllowed'),
  'Local Roads ingestion must be versioned, bounded, idempotent and fail closed on stale/provenance-mismatched snapshots');
assert(localRoadFeed.includes('queuego-trace:')&&
  !localRoadFeed.toLowerCase().includes('rider_id')&&
  !localRoadFeed.toLowerCase().includes('grab:'),
  'Verified trace provenance must reference QueueGo aggregate trace evidence without Rider identity or competitor references');

assert(localRoadRouting.includes('UNVERIFIED')&&
  localRoadRouting.includes('PRIVATE_RESTRICTED')&&
  localRoadRouting.includes('VEHICLE_NOT_ALLOWED')&&
  localRoadRouting.includes('DESTINATION_ACCESS_ONLY')&&
  localRoadRouting.includes('ONE_WAY_REVERSE')&&
  localRoadRouting.includes('QueueGoRoutingVehicle.MOTORCYCLE')&&
  localRoadRouting.includes('QueueGoRoadTraversalUse.DESTINATION_ACCESS'),
  'Local Roads routing eligibility must fail closed for unverified/private/wrong-vehicle/through-service/reverse-one-way traversal');
assert(!localRoadRouting.includes('shortestPath')&&
  !localRoadRouting.includes('NavigationRouteCoordinator')&&
  !localRoadRouting.includes('LongdoNavigationRouteClient'),
  'Local Roads eligibility policy must not silently replace the Production routing engine');

console.log('native rider MapLibre OSM module contract passed');
