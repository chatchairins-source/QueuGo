const fs=require('fs'),assert=require('assert');
const matrix=JSON.parse(fs.readFileSync('native-android/qa/native-screen-coverage-20261010.json','utf8'));
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(matrix.physical_visual_parity==='OPEN','source coverage matrix must never certify rendered/physical visual parity');
ok(/not rendered or physical visual parity certification/i.test(matrix.scope),'matrix scope must remain source-only');
ok(/API\/store\/model\/validation-only files are intentionally excluded/i.test(matrix.audit_note||''),'coverage matrix must preserve the reviewed non-UI exclusion boundary');

const minimum={customer:18,merchant:17,rider:14};
const requiredSurfaces={
  customer:['tracking_map_component'],
  merchant:['app_shell'],
  rider:['chat_image_component']
};
for(const [role,surfaces] of Object.entries(matrix.roles)){
  ok(Array.isArray(surfaces)&&surfaces.length>=minimum[role],role+' source coverage surface count regressed');
  const seen=new Set();
  for(const entry of surfaces){
    const [surface,path,kind='screen',requiredToken]=entry;
    ok(typeof surface==='string'&&surface.length>0,role+' surface name missing');
    ok(!seen.has(surface),role+' duplicate surface: '+surface); seen.add(surface);
    ok(kind==='screen'||kind==='helper',role+' unsupported coverage kind: '+surface);
    ok(typeof path==='string'&&path.startsWith('native-android/'+role+'/src/main/java/'),role+' surface path outside Native role: '+surface);
    ok(fs.existsSync(path),role+' source surface missing: '+surface+' -> '+path);
    const source=fs.readFileSync(path,'utf8');
    ok(source.length>300,role+' source surface is unexpectedly empty/thin: '+surface);
    if(kind==='screen') ok(source.includes('@Composable'),role+' UI surface lost Compose implementation: '+surface);
    else {
      ok(typeof requiredToken==='string'&&requiredToken.length>0,role+' helper coverage requires a source token: '+surface);
      ok(source.includes(requiredToken),role+' helper coverage token missing: '+surface+' -> '+requiredToken);
    }
  }
  for(const required of requiredSurfaces[role]||[]) ok(seen.has(required),role+' required UI surface missing from coverage matrix: '+required);
}

const riderLogin=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderLoginScreen.kt','utf8');
ok(riderLogin.includes('onRegister: () -> Unit'),'Rider registration entry must remain on the Native login surface');
ok(!/ACTION_VIEW|startActivity\s*\(/.test(riderLogin),'Rider registration must not regress to external-browser signup');

console.log(JSON.stringify({checks,failures:0,scope:'Native Customer/Merchant/Rider source-surface coverage; visual parity remains OPEN'}));
