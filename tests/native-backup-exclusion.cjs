const fs=require('fs'),assert=require('assert');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
const read=p=>fs.readFileSync(p,'utf8');

const manifest=read('native-android/shared/src/main/AndroidManifest.xml');
ok(manifest.includes('android:allowBackup="false"'),'Native shared manifest must disable app-data backup');
ok(manifest.includes('android:fullBackupContent="@xml/queuego_backup_rules"'),'Native shared manifest must bind Android 11- backup exclusions');
ok(manifest.includes('android:dataExtractionRules="@xml/queuego_data_extraction_rules"'),'Native shared manifest must bind Android 12+ extraction exclusions');

for(const role of ['customer','merchant','rider']){
  const gradle=read(`native-android/${role}/build.gradle.kts`);
  ok(gradle.includes('implementation(project(":shared"))'),`${role} must consume shared backup policy resources/manifest`);
}

const legacy=read('native-android/shared/src/main/res/xml/queuego_backup_rules.xml');
const modern=read('native-android/shared/src/main/res/xml/queuego_data_extraction_rules.xml');
for(const domain of ['root','file','database','sharedpref','external']){
  const legacyNeedle=`<exclude domain="${domain}" path="." />`;
  ok(legacy.includes(legacyNeedle),`legacy backup rules must exclude ${domain}`);
  const modernCount=modern.split(legacyNeedle).length-1;
  ok(modernCount===2,`Android 12+ rules must exclude ${domain} from cloud backup and device transfer`);
}
ok(modern.includes('<cloud-backup>')&&modern.includes('<device-transfer>'),'Android 12+ extraction rules must cover cloud backup and D2D transfer');
ok(!legacy.includes('<include ')&&!modern.includes('<include '),'Native backup policy must not opt any app-data domain back in');

console.log(JSON.stringify({checks,failures:0,scope:'Native Customer/Merchant/Rider app-data backup and device-transfer exclusion'}));
