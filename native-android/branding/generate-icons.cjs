// One geometry source for preview, Android vectors and exported launcher artwork.
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const red = '#EF3340'; // Production Customer --red / native QgRed.
const ring = 'M81,51 A28,28 0,1 1,25 51 A28,28 0,1 1,81 51 Z M73,51 A20,20 0,1 0,33 51 A20,20 0,1 0,73 51 Z';
const tail = 'M54,59 H61 C64,59 66,60 68,63 L77,72 C79,74 78,76 75,76 H71 C68,76 66,75 64,73 L52,62 C51,60 52,59 54,59 Z';
const roles = {
  customer: [
    {d:'M46,39 V46 M42,39 V45 C42,48 44,49 46,49 C48,49 50,48 50,45 V39 M46,49 V58', stroke:2.2},
    {d:'M59,38 C55.5,38 54,41 54,44 C54,47 56,49 59,49 C62,49 64,47 64,44 C64,41 62.5,38 59,38 Z', fill:true},
    {d:'M59,48 V58', stroke:2.2}
  ],
  merchant: [
    {d:'M43,46 V56 C43,57 44,58 45,58 H61 C62,58 63,57 63,56 V46 M51,58 V50 H57 V58', stroke:1.7},
    {d:'M42,44 L44.5,39 H61.5 L64,44 V46 C64,49 60,50 58.5,47 C57,50 53.5,50 53,47 C52.5,50 49,50 47.5,47 C46,50 42,49 42,46 Z', stroke:1.7},
    {d:'M48,39 L47.5,44 M58,39 L58.5,44 M53,39 V44', stroke:1.5}
  ],
  rider: [
    {d:'M61,52 A8,8 0,1 1,45 52 A8,8 0,1 1,61 52', stroke:2.3},
    {d:'M56,52 A3,3 0,1 1,50 52 A3,3 0,1 1,56 52', stroke:2.0},
    {d:'M45,48 C41,44 45,42 45,38 C48,39 49,41 49,43 C51,40 51,38 54,35 C54,39 59,40 59,44 C56,41 55,42 53,44 C50,44 47,45 45,48 Z', fill:true}
  ]
};
function paths(role) {
  return [{d:ring,fill:true,even:true},{d:tail,fill:true},...roles[role]];
}
function svgPaths(role) {
  return paths(role).map(p=>`<path d="${p.d}" fill="${p.fill?red:'none'}"${p.even?' fill-rule="evenodd"':''}${p.stroke?` stroke="${red}" stroke-width="${p.stroke}" stroke-linecap="round" stroke-linejoin="round"`:''}/>`).join('');
}
function svg(role, background=true) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="${background?'18 18 72 72':'0 0 108 108'}">${background?'<rect width="108" height="108" fill="#FFFFFF"/>':''}${svgPaths(role)}</svg>`;
}
for(const role of Object.keys(roles)) {
  fs.writeFileSync(path.join(__dirname, `${role}-foreground.svg`),svg(role,false));
  fs.writeFileSync(path.join(__dirname, `${role}-play-store.svg`),svg(role));
}
// Preview is reviewed BEFORE --integrate changes any Android resource.
let preview = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630"><rect width="1200" height="630" fill="#F7F7F8"/><text x="60" y="52" font-family="sans-serif" font-size="22" fill="#17191D">QueueGo · launcher family / native vector preview</text>`;
Object.keys(roles).forEach((role,i)=>{
 const x=80+i*380;
 preview+=`<rect x="${x}" y="90" width="280" height="280" rx="62" fill="white"/><svg x="${x}" y="90" width="280" height="280" viewBox="18 18 72 72">${svgPaths(role)}</svg><text x="${x+140}" y="410" text-anchor="middle" font-family="sans-serif" font-size="22" fill="#17191D">${role[0].toUpperCase()+role.slice(1)}</text>`;
 [48,32,24].forEach((size,j)=>{preview+=`<rect x="${x+j*96}" y="452" width="${size}" height="${size}" rx="${size*.22}" fill="white"/><svg x="${x+j*96}" y="452" width="${size}" height="${size}" viewBox="18 18 72 72">${svgPaths(role)}</svg>`;});
 // Adaptive circle mask with the launcher's 72/108 center crop.
 preview+=`<defs><clipPath id="mask${i}"><circle cx="${x+140}" cy="559" r="36"/></clipPath></defs><g clip-path="url(#mask${i})"><rect x="${x+104}" y="523" width="72" height="72" fill="white"/><svg x="${x+104}" y="523" width="72" height="72" viewBox="18 18 72 72">${svgPaths(role)}</svg></g>`;
});
preview+='</svg>';fs.writeFileSync(path.join(__dirname,'family-preview.svg'),preview);
if(process.argv.includes('--integrate')) {
 for(const role of Object.keys(roles)) {
  const res=path.join(root,role,'src/main/res');
  const xmlPaths=paths(role).map(p=>`    <path android:pathData="${p.d}" android:fillColor="${p.fill?red:'#00000000'}"${p.even?' android:fillType="evenOdd"':''}${p.stroke?` android:strokeColor="${red}" android:strokeWidth="${p.stroke}" android:strokeLineCap="round" android:strokeLineJoin="round"`:''}/>`).join('\n');
  const vector=`<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n${xmlPaths}\n</vector>\n`;
  fs.writeFileSync(path.join(res,'drawable/ic_queuego_foreground.xml'),vector);
  fs.writeFileSync(path.join(res,'drawable/ic_queuego_background.xml'),'<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="#FFFFFF"/></shape>\n');
  fs.mkdirSync(path.join(res,'mipmap-anydpi-v26'),{recursive:true});
  fs.writeFileSync(path.join(res,'mipmap-anydpi-v26/ic_queuego_launcher.xml'),'<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/ic_queuego_background"/><foreground android:drawable="@drawable/ic_queuego_foreground"/></adaptive-icon>\n');
  if (fs.existsSync(path.join(res,'drawable/ic_queuego_launcher.xml'))) fs.unlinkSync(path.join(res,'drawable/ic_queuego_launcher.xml'));
  const manifest=path.join(root,role,'src/main/AndroidManifest.xml');
  fs.writeFileSync(manifest,fs.readFileSync(manifest,'utf8').replaceAll('@drawable/ic_queuego_launcher','@mipmap/ic_queuego_launcher'));
 }
}
