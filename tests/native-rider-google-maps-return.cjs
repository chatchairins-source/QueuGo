const assert=require('assert');
const fs=require('fs');

const manifest=fs.readFileSync('native-android/rider/src/main/AndroidManifest.xml','utf8');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const service=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderReturnService.kt','utf8');
const main=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/MainActivity.kt','utf8');

assert(manifest.includes('<package android:name="com.google.android.apps.maps" />'),
  'Rider must declare Google Maps package visibility');
assert(app.includes('setPackage(GOOGLE_MAPS_PACKAGE)')&&app.includes('"google.navigation:q="'),
  'Rider navigation must open the installed Google Maps app explicitly');
assert(!app.includes('https://www.google.com/maps/dir/'),
  'Rider must not fall back to browser-based Google Maps');
assert(app.includes('"ต้องติดตั้ง Google Maps ก่อนเปิดรับงาน"')&&app.includes('openGoogleMapsInstallPage(context)'),
  'Rider must require Google Maps before going online');
assert(app.includes('enabled && !Settings.canDrawOverlays(context)')&&app.includes('configureReturnControl()'),
  'Rider must require the cross-app return control before going online');
assert(app.includes('RiderReturnService.start(context)'),
  'Opening Google Maps must start the QueueGo return overlay service');
assert(!app.includes('RiderNavigationController()')&&!app.includes('RiderNavigationSpeaker(context)')&&!app.includes('if (navigationState.active)'),
  'Rider Home must not run the retired in-app navigation engine or turn-by-turn panel');
assert(service.includes('TYPE_APPLICATION_OVERLAY')&&service.includes('text = "Q"')&&service.includes('MainActivity::class.java'),
  'Rider must expose a draggable Q overlay that returns to QueueGo');
assert(main.includes('override fun onResume()')&&main.includes('RiderReturnService.stop(this)'),
  'Returning to QueueGo must remove the floating overlay');

console.log('Native Rider Google Maps-only return overlay contracts: PASS');
