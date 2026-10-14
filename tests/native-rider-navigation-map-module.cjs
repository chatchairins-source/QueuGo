const assert=require('assert');
const fs=require('fs');

const settings=fs.readFileSync('native-android/settings.gradle.kts','utf8');
const riderBuild=fs.readFileSync('native-android/rider/build.gradle.kts','utf8');
const riderApp=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const moduleBuild=fs.readFileSync('native-android/rider-navigation-map/build.gradle.kts','utf8');
const map=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt','utf8');
const route=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/LongdoNavigationRouteClient.kt','utf8');
const cameraPolicy=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationCameraPolicy.kt','utf8');
const guidance=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationGuidance.kt','utf8');
const coordinator=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationRouteCoordinator.kt','utf8');
const models=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationMapModels.kt','utf8');
const workflow=fs.readFileSync('.github/workflows/build-native-rider-pilot.yml','utf8');
const runtimeTest=fs.readFileSync('native-android/rider-navigation-map/src/androidTest/java/com/queuego/ridernavigationmap/NavigationMapRuntimeTest.kt','utf8');
const runtimeActivity=fs.readFileSync('native-android/rider-navigation-map/src/debug/java/com/queuego/ridernavigationmap/NavigationMapRuntimeActivity.kt','utf8');
const runtimeRunner=fs.readFileSync('native-android/qa/run-rider-navigation-map-runtime.py','utf8');

assert(settings.includes('":rider-navigation-map"'),
  'Standalone Rider navigation map module must be registered');
assert(moduleBuild.includes('id("com.android.library")')&&moduleBuild.includes('implementation(project(":shared"))'),
  'Navigation map must stay an Android library reusing the shared Longdo SDK');
assert(moduleBuild.includes('com.google.android.gms:play-services-location:21.3.0'),
  'Standalone Longdo runtime must include the Google Play Services location classes required by MapLocationManager');
const integration=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderNavigationIntegration.kt','utf8');
const locationService=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt','utf8');
const riderManifest=fs.readFileSync('native-android/rider/src/main/AndroidManifest.xml','utf8');
assert(riderBuild.includes('implementation(project(":rider-navigation-map"))')&&
  riderApp.includes('QueueGoRiderNavigationMap('),
  'Runtime-gated navigation map must be integrated into the Rider APK through the QueueGo adapter');
assert(locationService.includes('NAVIGATION_LOCATION_INTERVAL_MS = 1_000L')&&
  locationService.includes('LocationRequest.Builder(')&&
  locationService.includes('Priority.PRIORITY_HIGH_ACCURACY')&&
  locationService.includes('MutableStateFlow<NavigationMapFix?>')&&
  integration.includes('RiderNavigationLocationStore.fix.collectAsStateWithLifecycle()')&&
  integration.includes('NavigationRouteCoordinator')&&
  integration.includes('Channel.CONFLATED')&&
  integration.includes('riderNavigationStops'),
  'Rider navigation must use one foreground-owned 1-second fused GPS stream and feed its latest fix into the sequential coordinator');
assert(riderManifest.includes('android.permission.FOREGROUND_SERVICE_LOCATION')&&
  riderManifest.includes('android:name=".RiderNavigationLocationService"')&&
  riderManifest.includes('android:foregroundServiceType="location"')&&
  !riderManifest.includes('android.permission.ACCESS_BACKGROUND_LOCATION')&&
  locationService.includes('ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION')&&
  locationService.includes('START_NOT_STICKY'),
  'Navigation location must use the Android location foreground-service contract without requesting broad background-location permission');
assert(integration.includes('Lifecycle.Event.ON_RESUME && navigationActive')&&
  integration.includes('RiderNavigationLocationService.start(context)')&&
  integration.includes('RiderNavigationLocationService.stop(context)')&&
  integration.includes('do not stop an active navigation service when Compose unmounts')&&
  !integration.includes('Lifecycle.Event.ON_PAUSE -> locationEngine.stop()')&&
  !integration.includes('RiderNavigationLocationEngine'),
  'Navigation GPS must start while the Activity is visible, survive pause/tab unmount, and stop only when navigation is no longer active');
assert(map.includes('val holder = remember(stateKey, lifecycle)')&&map.includes('AndroidView(')&&map.includes('factory =')&&map.includes('update ='),
  'MapView owner must survive normal Compose recomposition and lifecycle-safe tab remounts while only updating overlays');
assert(map.includes('Deliberately keep lastRiderFix, map, pins and route layers')&&
  !map.includes('latestDeviceLocation = null')&&!map.includes('clearPin()'),
  'Refresh/resume must not clear the existing map or rebuild every marker');
assert(!map.includes('LaunchedEffect(state)')&&
  map.includes('state.stops == stopSource')&&map.includes('state.routes == routeSource')&&
  !map.includes('state.stops === stopSource')&&!map.includes('state.routes === routeSource')&&
  !map.includes('routes.hashCode()'),
  'GPS/order refresh must not render twice or rebuild structurally-equivalent route geometry');
assert(map.includes('stopSource = null')&&map.includes('routeSource = null')&&
  map.includes('if (!pushed) renderSucceeded = false')&&map.includes('runCatching { activeMap.removeLine(oldLine) }.isFailure')&&
  map.includes('nextSegments += requireNotNull(oldSegment)')&&
  map.includes('routeSource = if (succeeded) state.routes else null'),
  'Transient Longdo pin/line failures must remain retryable instead of caching a partial frame');
assert(map.includes('fun attachMap(nextMap: com.longdo.api.Map): Boolean')&&
  map.includes('if (map === nextMap) return false')&&
  map.includes('val newSdkMap = attachMap(nextMap)')&&
  map.includes('if (!newSdkMap) return false')&&
  map.includes('never reset base/camera or force the visible map to assemble again'),
  'Repeated Longdo onMapCreated callbacks for the same SDK map must not reinitialize base/camera');
assert(map.includes('RiderNavigationMapRegistry')&&
  map.includes('holder.container?.takeIf { it.context === context }')&&
  map.includes('holder.retainAcrossVisibilityLoss()')&&
  runtimeActivity.includes('hideMapForAnotherTab')&&runtimeActivity.includes('showMapAfterReturningHome')&&
  runtimeActivity.includes('hasMapView')&&runtimeTest.includes('awaitMapPresence')&&
  runtimeTest.includes('expected = false')&&runtimeTest.includes('expected = true')&&
  runtimeTest.includes('Rider tab return recreated MapGLSurfaceView')&&
  runtimeTest.includes('Rider tab return recreated Longdo map')&&
  runtimeTest.includes('Healthy tab remount should not invoke the continuity bridge')&&
  runtimeTest.includes('manualPanSuspendsFollowButTapDoesNotReportGesture')&&
  runtimeTest.includes('routeProgressMutatesExistingLongdoLineWithoutRecreatingIt')&&
  runtimeActivity.includes('dispatchDragOnMap')&&runtimeActivity.includes('userGestureCount')&&
  runtimeActivity.includes('shortenActiveRouteFixture')&&runtimeActivity.includes('routeLineIdentities'),
  'Leaving Home for another Rider tab must actually unmount/remount the map and reuse the same map objects without leaking an old Activity context');
assert(map.includes('activeDestroyObserver')&&
  map.includes('Keep the lifecycle observer registered while Home is unmounted')&&
  map.includes('remember(stateKey, lifecycle)')&&
  runtimeActivity.includes('mapViewUsesActivityContext')&&
  runtimeTest.includes('retainedMapIsDisposedWhenActivityDiesOffHome')&&
  runtimeTest.includes('destroyed Activity'),
  'A retained off-Home map must be destroyed with its Activity and recreated with the new Activity context');
assert(map.includes('fun retainAcrossVisibilityLoss()')&&
  map.includes('Capture a frozen frame quietly for emergency continuity')&&
  map.includes('Lifecycle.Event.ON_PAUSE -> holder.retainAcrossVisibilityLoss()')&&
  map.includes('holder.retainAcrossVisibilityLoss()')&&
  map.includes('fun shutdownSdk()')&&
  map.includes('activeView.onPause()')&&
  map.indexOf('activeView.onPause()')===map.lastIndexOf('activeView.onPause()')&&
  !map.includes('view?.onPause()')&&!map.includes('createdView.onPause()')&&
  !map.includes('activeView.onResume()')&&
  map.includes('Calling it after ordinary background/foreground transitions can replace renderer.map'),
  'Ordinary tab/background visibility loss must not call Longdo onPause/onResume; SDK shutdown is reserved for real surface disposal');
assert(map.includes('MotionEvent.ACTION_MOVE')&&
  map.includes('MotionEvent.ACTION_POINTER_DOWN')&&
  map.includes('ViewConfiguration.get(context).scaledTouchSlop')&&
  map.includes('currentUserGesture.value()')&&
  map.includes('holder.view?.setOnTouchListener(null)')&&
  integration.includes('var followRider by remember(stateKey) { mutableStateOf(true) }')&&
  integration.includes('followRider = followRider')&&
  integration.includes('onUserGesture = { followRider = false }')&&
  integration.includes('followRider = true'),
  'Manual drag or pinch must suspend navigation follow until the Rider explicitly recenters');

assert(map.includes('setPreserveEGLContextOnPause(true)'),
  'Navigation map must preserve the GL context across background/resume when Android allows it');
assert(map.includes('announceRendererReplacement')&&
  map.includes('fun configureMap(')&&
  map.includes('adoptSilentRendererReplacement')&&
  map.includes('watchRendererIdentity')&&
  map.includes('RENDERER_WATCH_MAX_ATTEMPTS')&&
  map.includes('Longdo can replace renderer.map after ON_RESUME returns')&&
  map.includes('configureMap(activeView.context, rendererMap)'),
  'Renderer-map identity changes must be bridged and watched after resume so delayed silent Longdo replacements are proactively adopted');
assert(map.includes('PixelCopy.request')&&map.includes('snapshotOverlay')&&
  map.includes('showContinuityBridge')&&map.includes('settleContinuityBridge')&&
  map.includes('pendingBridgeWithoutSnapshot')&&
  map.includes('Count the bridge request at renderer-swap detection time')&&
  map.includes('The bridge is shown only when attachMap() detects a real map')&&
  models.includes('snapshotBridgeCount: Int = 0')&&models.includes('snapshotVisible: Boolean = false'),
  'When Android recreates the Longdo renderer, a captured SurfaceView frame must hide visible map reassembly');
assert(map.includes('nextMap.setDoTileAnimation(false)')&&map.includes('nextMap.setDoPreShowCache(true)')&&
  map.includes('setDoPreDrawCache(true)')&&map.includes('lastKnownCamera'),
  'Longdo replacement maps must prefer cached tiles and disable tile-appearance animation');
assert(guidance.includes('class NavigationProgressTracker')&&
  guidance.includes('requiredOffRouteSamples: Int = 2')&&
  guidance.includes('rerouteCooldownMillis: Long = 8_000L')&&
  guidance.includes('shouldReroute')&&guidance.includes('arrived')&&
  guidance.includes('navigationNextInstruction')&&guidance.includes('navigationTurnPhrase'),
  'Navigation guidance must expose progress/ETA, reliable off-route reroute, arrival and turn instructions without Rider business-state coupling');
assert(route.includes('interface NavigationRouteProvider')&&
  route.includes('class LongdoNavigationRouteClient')&&
  route.includes(': NavigationRouteProvider'),
  'Longdo routing must be injectable behind a provider contract so the standalone coordinator is testable without Production calls');
assert(coordinator.includes('class NavigationRouteCoordinator')&&
  coordinator.includes('progressTracker.update')&&
  coordinator.includes('guidance.shouldReroute')&&
  coordinator.includes('preservedFuture')&&
  coordinator.includes('segment.toStopId != activeStop.id'),
  'Route coordinator must reroute only the active leg, preserve future stops and stay independent of Rider business state');
assert(coordinator.includes('ROUTE_PROGRESS_RENDER_STEP_METERS = 10.0')&&
  coordinator.includes('visibleProgressMeters')&&
  coordinator.includes('maxOf(visibleProgressMeters, match.progressMeters)')&&
  coordinator.includes('trimNavigationRouteSegment')&&
  coordinator.includes('guidance.weakGps || guidance.offRoute'),
  'Active route must shrink monotonically behind the Rider without rebuilding geometry on every GPS tick or trusting unreliable fixes');
assert(route.includes('NavigationRouteInstruction')&&
  route.includes('turnCode = properties.optInt("turn", 4)')&&
  route.includes('roadName = properties.optString("name").trim()'),
  'Longdo route parsing must preserve maneuver instructions and road names for in-app guidance');
assert(cameraPolicy.includes('navigationCameraDecision')&&
  cameraPolicy.includes('speedMetersPerSecond >= 18f')&&
  cameraPolicy.includes('smoothNavigationBearing')&&
  cameraPolicy.includes('navigationPointAtDistance')&&
  map.includes('activeMap.setLocation(')&&
  map.includes('true\n                )')&&
  map.includes('activeMap.setZoom(decision.zoom, true)')&&
  map.includes('minimumDeltaDegrees = 2.5f')&&
  map.includes('CAMERA_ZOOM_MIN_INTERVAL_MS = 1_800L')&&
  map.includes('decision.bearingDegrees?.let { activeMap.rotate(it, true) }'),
  'Navigation camera must animate position/zoom, suppress micro-wobble and look ahead on the matched road');
assert(map.includes('ACTIVE_ROUTE_COLOR')&&map.includes('FUTURE_ROUTE_COLOR')&&
  map.includes('setWidth(if (segment.style == NavigationRouteStyle.ACTIVE) 8 else 6)'),
  'Current route must be dark red and later route legs visibly faded');
assert(map.includes('oldLine.setLocation(segment.points.map')&&
  map.includes('renderedRouteSegments.zip(routeLines)')&&
  map.includes('Topology/style changed or an in-place mutation failed'),
  'Route progress must mutate existing Longdo line geometry in place and rebuild only on topology/style changes');
assert(map.includes('RIDER_MARKER_MAX_ANIMATION_MS = 850L')&&
  map.includes('ValueAnimator.ofFloat(0f, 1f)')&&
  map.includes('riderMarkerAnimator?.cancel()')&&
  map.includes('existing.getMovingLocation()')&&
  map.includes('riderMarkerAnimationMillis(lastRiderFix ?: previousTarget, fix)')&&
  !map.includes('RIDER_MARKER_ANIMATION_STEPS = 18')&&
  !map.includes('existing.setLocation(\n                        location,\n                        true,'),
  'Rider marker smoothing must use one cancellable Android animator shorter than the GPS cadence and never stack Longdo animations');
assert(map.includes('PixelCopy is deliberately not scheduled on every GPS tick')&&
  !map.includes('publishDiagnostics()\n        scheduleSnapshotRefresh()'),
  'Live GPS rendering must not schedule PixelCopy snapshots on every navigation update');
assert(models.includes('resolvedActiveStopId')&&models.includes('styledRemainingRoutes'),
  'Module must support numbered multi-stop progression independently of RiderJob');
assert(route.includes('private val apiKey: String')&&route.includes('RouteService/geojson/route')&&
  !route.includes('347649ca13f49db0d8a599ae35f8de2b'),
  'Routing key must be injected and never hard-coded into the standalone module');
assert(workflow.includes(':rider-navigation-map:testDebugUnitTest'),
  'Native Pilot must compile and unit-test the standalone map module');
assert(models.includes('renderPassCount: Int = 0')&&
  map.includes('renderPassCount += 1')&&
  map.includes('state update that never reached the map'),
  'Diagnostics must expose applied render passes without forcing an overlay rebuild');
assert(runtimeTest.includes('awaitAppliedRender')&&runtimeTest.includes('awaitStopProgression')&&
  runtimeTest.includes('awaitRendererReplacementBridge')&&
  runtimeTest.includes('Longdo renderer replacement callback did not announce continuity bridge')&&
  runtimeTest.includes('bounded GPS retries')&&
  runtimeTest.includes('GPS refresh recreated MapGLSurfaceView')&&
  runtimeTest.includes('GPS refresh rebuilt unchanged route layers')&&
  runtimeTest.includes('Healthy tab remount should not invoke the continuity bridge')&&
  runtimeTest.includes('Healthy background/resume should not invoke the continuity bridge')&&
  runtimeTest.includes('Longdo map replacement was not recorded')&&
  runtimeTest.includes('Equivalent backend refresh rebuilt unchanged route layers')&&
  runtimeTest.includes('Equivalent backend refresh changed SDK map creation count')&&
  runtimeTest.includes('Equivalent backend refresh changed map replacement count')&&
  runtimeTest.includes('Equivalent backend refresh invoked continuity bridge')&&
  runtimeActivity.includes('refreshEquivalentOrderSnapshot'),
  'Android runtime test must prove GPS/backend refresh stays in place and renderer replacement is visually bridged instead of exposed');
assert(runtimeActivity.includes('there are no QueueGo accounts, jobs, orders or Production writes')&&
  runtimeActivity.includes('refreshRiderFix')&&runtimeActivity.includes('advanceToSecondStop'),
  'Runtime host must remain isolated from QueueGo business data while exercising navigation state');
assert(workflow.includes('navigation-map-runtime:')&&
  workflow.includes('run-rider-navigation-map-runtime.py')&&
  runtimeRunner.includes(':rider-navigation-map:connectedDebugAndroidTest'),
  'Standalone navigation map must have a dedicated Android runtime gate');

console.log('Standalone Rider navigation map module contracts: PASS');
