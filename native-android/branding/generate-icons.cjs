// Approved red/white family. One geometry source for Android, SVG and PNG exports.
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const wordmarks = require('./wordmarks.json');
const red = '#EF1717';
// Thick circular Q, short low tail. Foreground remains inside adaptive safe circle.
const ring = 'M76,45 A22,22 0,1 1,32 45 A22,22 0,1 1,76 45 Z M67,45 A13,13 0,1 0,41 45 A13,13 0,1 0,67 45 Z';
const tail = 'M66,57 C67,56 69,56 70,57 L73,60 C75,62 75,64 73,65 C72,66 70,66 69,65 L66,62 C65,61 65,59 66,57 Z';
const symbols = {
  customer: [
    {d:'M45,59 L47,43 Q47,42 49,42 H59 Q61,42 61,44 L63,59',stroke:1.8},
    {d:'M50,42 V40 A4,4 0,0 1,58 40 V42',stroke:1.6},
    {d:'M50,44 A0.9,0.9 0,1 0,50 45.8 A0.9,0.9 0,1 0,50 44 Z M58,44 A0.9,0.9 0,1 0,58 45.8 A0.9,0.9 0,1 0,58 44 Z',fill:true}
  ],
  merchant: [
    {d:'M46,47 H48 V59 H46 Z M60,47 H62 V59 H60 Z',fill:true},
    {d:'M47,38 H51 L50,44 C49,47 44,46 45,43 Z M52,38 H56 L56.5,43 C57,47 51,47 51.5,43 Z M57,38 H61 L63,43 C64,46 59,47 58,44 Z',fill:true}
  ],
  rider: [
    {d:'M43,59 C44,49 51,44 62,41 L61,38 L65,42 L62,47 L62.5,44 C53,47 48,52 47,59 Z',fill:true},
    {d:'M51,57 L54,57 L55,54 L52.5,54 Z M53.5,52 L56,52 L58,49 L55.5,49 Z',fill:true}
  ]
};
function paths(role) { return [{d:ring,fill:true,even:true},{d:tail,fill:true},...symbols[role],{d:wordmarks[role].path,fill:true}]; }
function svgPaths(role) { return paths(role).map(p=>`<path d="${p.d}" fill="${p.fill?'#FFFFFF':'none'}"${p.even?' fill-rule="evenodd"':''}${p.stroke?` stroke="#FFFFFF" stroke-width="${p.stroke}" stroke-linecap="round" stroke-linejoin="round"`:''}/>`).join(''); }
function svg(role,background=true) { return `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="${background?'18 18 72 72':'0 0 108 108'}">${background?`<rect x="18" y="18" width="72" height="72" fill="${red}"/>`:''}${svgPaths(role)}</svg>`; }
for(const role of Object.keys(symbols)) {
 fs.writeFileSync(path.join(__dirname,`${role}-foreground.svg`),svg(role,false));
 fs.writeFileSync(path.join(__dirname,`${role}-play-store.svg`),svg(role));
 if(!process.argv.includes('--integrate'))continue;
 const res=path.join(root,role,'src/main/res');
 const vectorPaths=paths(role).map(p=>`    <path android:pathData="${p.d}" android:fillColor="${p.fill?'#FFFFFFFF':'#00000000'}"${p.even?' android:fillType="evenOdd"':''}${p.stroke?` android:strokeColor="#FFFFFFFF" android:strokeWidth="${p.stroke}" android:strokeLineCap="round" android:strokeLineJoin="round"`:''}/>`).join('\n');
 const vector=`<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n${vectorPaths}\n</vector>\n`;
 fs.writeFileSync(path.join(res,'drawable/ic_queuego_foreground.xml'),vector);
 fs.writeFileSync(path.join(res,'drawable/ic_queuego_monochrome.xml'),vector);
 fs.writeFileSync(path.join(res,'drawable/ic_queuego_background.xml'),`<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="${red}"/></shape>\n`);
 for(const version of [26,33]) {
  fs.mkdirSync(path.join(res,`mipmap-anydpi-v${version}`),{recursive:true});
  fs.writeFileSync(path.join(res,`mipmap-anydpi-v${version}/ic_queuego_launcher.xml`),`<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/ic_queuego_background"/><foreground android:drawable="@drawable/ic_queuego_foreground"/>${version===33?'<monochrome android:drawable="@drawable/ic_queuego_monochrome"/>':''}</adaptive-icon>\n`);
 }
 const manifest=path.join(root,role,'src/main/AndroidManifest.xml');
 fs.writeFileSync(manifest,fs.readFileSync(manifest,'utf8').replace(/android:label="[^"]*"/,`android:label="${wordmarks[role].name}"`));
}
let preview='<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="540" viewBox="0 0 1200 540"><rect width="1200" height="540" fill="#F6F3F0"/>';
Object.keys(symbols).forEach((role,i)=>{
 const x=70+i*380;
 preview+=`<defs><clipPath id="tile${i}"><rect x="${x}" y="80" width="300" height="300" rx="65"/></clipPath></defs><g clip-path="url(#tile${i})"><svg x="${x}" y="80" width="300" height="300" viewBox="18 18 72 72"><rect x="18" y="18" width="72" height="72" fill="${red}"/>${svgPaths(role)}</svg></g><text x="${x+150}" y="435" text-anchor="middle" font-family="DejaVu Sans,sans-serif" font-weight="bold" font-size="30" fill="#202020">${wordmarks[role].name}</text>`;
});
fs.writeFileSync(path.join(__dirname,'family-preview.svg'),preview+'</svg>');
