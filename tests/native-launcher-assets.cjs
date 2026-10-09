const fs=require('fs');
const path=require('path');
const assert=require('assert');
const roles=['customer','merchant','rider'];
const root=path.join(__dirname,'..','native-android');
let checks=0;
function check(condition,message){checks++;assert.ok(condition,message);}
let sharedPaths;
for(const role of roles){
 const res=path.join(root,role,'src/main/res');
 const manifest=fs.readFileSync(path.join(root,role,'src/main/AndroidManifest.xml'),'utf8');
 check(manifest.includes('android:icon="@mipmap/ic_queuego_launcher"'),role+' adaptive manifest');
 check(manifest.includes('android:roundIcon="@mipmap/ic_queuego_launcher"'),role+' round manifest');
 check(!fs.existsSync(path.join(res,'drawable/ic_queuego_launcher.xml')),role+' old icon removed');
 const adaptive=fs.readFileSync(path.join(res,'mipmap-anydpi-v26/ic_queuego_launcher.xml'),'utf8');
 check(adaptive.includes('<adaptive-icon') && adaptive.includes('@drawable/ic_queuego_foreground') && adaptive.includes('@drawable/ic_queuego_background'),role+' separate adaptive layers');
 const vector=fs.readFileSync(path.join(res,'drawable/ic_queuego_foreground.xml'),'utf8');
 const paths=[...vector.matchAll(/android:pathData="([^"]+)"/g)].map(x=>x[1]);
 check(paths.length>=4,role+' Q and service symbol');
 if(sharedPaths)check(JSON.stringify(paths.slice(0,2))===JSON.stringify(sharedPaths),role+' same Q geometry');
 else sharedPaths=paths.slice(0,2);
 check(vector.includes('#EF3340')&&!vector.includes('<text'),role+' brand color and no app text');
 for(const [density,size] of Object.entries({mdpi:48,hdpi:72,xhdpi:96,xxhdpi:144,xxxhdpi:192,store:512})){
  const file=density==='store'?path.join(root,'branding',`${role}-play-store-512.png`):path.join(res,`mipmap-${density}`,'ic_queuego_launcher.png');
  const bytes=fs.readFileSync(file);
  check(bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])),role+' '+density+' PNG');
  check(bytes.readUInt32BE(16)===size && bytes.readUInt32BE(20)===size,role+' '+density+' dimensions');
 }
}
const metrics=JSON.parse(fs.readFileSync(path.join(root,'branding/geometry-check.json'),'utf8'));
for(const role of roles)check(metrics.metrics[role].maxRadiusDp<=33,role+' adaptive safe circle');
check(metrics.roleWeightSpreadPercent<=10,'symbol coverage consistency');
check(fs.existsSync(path.join(root,'branding/family-preview.png')),'reviewable family preview');
console.log(JSON.stringify({checks,failures:0,scope:'native launcher assets, density sizes, shared Q geometry and recorded raster safe-area metrics; physical launcher rendering remains pending'}));
