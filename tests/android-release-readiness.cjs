const fs=require('fs');
const assert=require('assert');

const read=p=>fs.readFileSync(p,'utf8');
const workflow=read('.github/workflows/build-queuego-apks.yml');
const customer=read('index.html');
const merchant=read('merchant/index.html');
const rider=read('rider/index.html');
const printer=read('merchant/printer-v1.js');
const rls=read('QueueGo-Pre-Android-Security-Hardening.sql');
const all=[customer,merchant,rider,printer,read('admin/index.html'),read('role-realtime.js'),read('table-order.js')].join('\n');

const checks=[];
const ok=(name,value)=>{checks.push([name,Boolean(value)]);assert.ok(value,name)};

ok('Play target API 36',/targetSdkVersion.*36/.test(workflow));
ok('compile API 36',/compileSdkVersion.*36/.test(workflow));
ok('release builds AAB',/bundleRelease/.test(workflow)&&/\.aab/.test(workflow));
ok('release builds APK',/assembleRelease/.test(workflow)&&/\.apk/.test(workflow));
ok('release signing enforced',/QG_ANDROID_KEYSTORE_B64/.test(workflow)&&/signingConfig signingConfigs\.release/.test(workflow));
ok('native Android back handler',/OnBackPressedCallback/.test(workflow)&&/getWebView\(\)\.goBack\(\)/.test(workflow));
ok('no camera permission',!/android\.permission\.CAMERA/.test(workflow));
ok('no background location permission',!/android\.permission\.ACCESS_BACKGROUND_LOCATION/.test(workflow));
ok('no broad storage permission',!/android\.permission\.(READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|READ_MEDIA_IMAGES)/.test(workflow));
ok('fine/coarse location declared',/ACCESS_FINE_LOCATION/.test(workflow)&&/ACCESS_COARSE_LOCATION/.test(workflow));
ok('customer persists session',/localStorage/.test(customer));
ok('merchant persists session',/localStorage/.test(merchant));
ok('rider persists session',/localStorage/.test(rider));
ok('customer geolocation',/navigator\.geolocation/.test(customer));
ok('merchant geolocation',/navigator\.geolocation/.test(merchant));
ok('rider geolocation',/navigator\.geolocation/.test(rider));
ok('merchant file chooser present',/type=["']file["']/.test(merchant));
ok('rider file chooser present',/type=["']file["']/.test(rider));
ok('Android printer avoids unsupported Web Bluetooth USB',/nativeAndroid\(\)&&m!=='bridge'/.test(printer));
ok('print bridge URL restricted',/Print Bridge ต้องใช้ HTTPS/.test(printer)&&/192\.168\./.test(printer));
ok('network timeout protection exists',/AbortSignal\.timeout/.test(all));
ok('offline handling exists',/navigator\.onLine|addEventListener\(['"]offline/.test(all));
ok('no service role secret in client',!/service_role|sb_secret_/i.test(all));
ok('Rider profile read is scoped',/alter policy rider_profile_select[\s\S]*user_id = public\.get_my_user_id\(\)[\s\S]*get_my_role\(\) = 'admin'/.test(rls));
ok('Shop profile read excludes inactive public profiles',/alter policy shop_profiles_authenticated_select[\s\S]*status = 'active'/.test(rls));

console.log(JSON.stringify({checks:checks.length,failures:0,scope:'static Android release readiness; physical device/printer and Play upload remain external certification'},null,2));
