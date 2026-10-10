const fs=require('fs');
const path=require('path');
const assert=require('assert/strict');

const ROOT='native-android';
const SOURCE_ROOTS=[
  'native-android/customer/src/main',
  'native-android/merchant/src/main',
  'native-android/rider/src/main',
  'native-android/shared/src/main',
];
const ROOT_FILES=[
  'native-android/build.gradle.kts',
  'native-android/customer/build.gradle.kts',
  'native-android/merchant/build.gradle.kts',
  'native-android/rider/build.gradle.kts',
  'native-android/settings.gradle.kts',
  'native-android/gradle.properties',
];

function walk(dir,out=[]){
  for(const entry of fs.readdirSync(dir,{withFileTypes:true})){
    const p=path.join(dir,entry.name);
    if(entry.isDirectory()) walk(p,out);
    else if(/\.(kt|kts|xml|properties|json)$/i.test(entry.name)) out.push(p);
  }
  return out;
}

const files=[...SOURCE_ROOTS.flatMap(p=>walk(p)),...ROOT_FILES.filter(fs.existsSync)];
assert.ok(files.length>20,'Native network safety scan unexpectedly found too few source files');

const forbidden=[
  {label:'localhost endpoint',re:/\blocalhost\b/i},
  {label:'loopback endpoint',re:/127\.0\.0\.1|\[::1\]/i},
  {label:'wildcard dev endpoint',re:/\b0\.0\.0\.0\b/},
  {label:'Supabase secret key prefix',re:/\bsb_secret_/i},
  {label:'service-role credential reference',re:/\b(?:supabase[_-]?)?service[_-]?role\b/i},
  {label:'service-role environment reference',re:/SUPABASE_SERVICE_ROLE/i},
  {label:'cleartext traffic enabled',re:/usesCleartextTraffic\s*=\s*"true"/i},
  {label:'debuggable main application',re:/android:debuggable\s*=\s*"true"/i},
];

for(const file of files){
  let source=fs.readFileSync(file,'utf8');
  // Android's XML namespace is an identifier, not a network endpoint.
  source=source.replaceAll('http://schemas.android.com/apk/res/android','ANDROID_XML_NAMESPACE');
  if(/http:\/\//i.test(source)){
    throw new Error(`Native runtime source contains cleartext URL: ${file}`);
  }
  for(const rule of forbidden){
    if(rule.re.test(source)) throw new Error(`Native runtime source contains ${rule.label}: ${file}`);
  }
}

for(const role of ['customer','merchant','rider']){
  const manifest=`native-android/${role}/src/main/AndroidManifest.xml`;
  const source=fs.readFileSync(manifest,'utf8');
  assert.match(source,/android:usesCleartextTraffic="false"/,`${role} must explicitly disable cleartext traffic`);
}

const riskyPermissions=[
  'android.permission.ACCESS_BACKGROUND_LOCATION',
  'android.permission.READ_EXTERNAL_STORAGE',
  'android.permission.WRITE_EXTERNAL_STORAGE',
  'android.permission.READ_MEDIA_IMAGES',
  'android.permission.CAMERA',
];
for(const role of ['customer','merchant','rider']){
  const manifest=fs.readFileSync(`native-android/${role}/src/main/AndroidManifest.xml`,'utf8');
  for(const permission of riskyPermissions){
    assert.ok(!manifest.includes(permission),`${role} must not request unapproved permission ${permission}`);
  }
}

console.log(JSON.stringify({
  checks:files.length+6+riskyPermissions.length*3,
  failures:0,
  scope:'Native runtime source dev-endpoint, credential, cleartext and unapproved-permission regression'
}));
