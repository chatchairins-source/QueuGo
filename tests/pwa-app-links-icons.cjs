const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),apps=fs.readFileSync(path.join(root,'apps.html'),'utf8');
const cases=[{html:'index.html',manifest:'manifest.webmanifest',icon:'assets/icons/queuego-customer.png',download:'QueueGo-Customer.html'},{html:'merchant/index.html',manifest:'merchant/manifest.webmanifest',icon:'assets/icons/queuego-merchant.png',download:'QueueGo-Shop.html'},{html:'rider/index.html',manifest:'rider/manifest.webmanifest',icon:'android-build/branding/queuego-approved-icon.webp',download:'QueueGo-Rider.html'}];
for(const item of cases){const html=fs.readFileSync(path.join(root,item.html),'utf8');assert.match(html,/rel=["']manifest["']/i);const m=JSON.parse(fs.readFileSync(path.join(root,item.manifest),'utf8'));const iconPath=path.relative(path.dirname(path.join(root,item.manifest)),path.join(root,item.icon)).split(path.sep).join('/');assert.ok(m.icons.some(i=>i.src===iconPath),item.html+' manifest icon mismatch');if(item.icon.endsWith('.png')&&!item.html.startsWith('rider'))assert.ok(html.includes('apple-touch-icon'),item.html+' iOS icon missing');assert.ok(fs.existsSync(path.join(root,item.icon)),item.icon+' missing');assert.ok(apps.includes('download="'+item.download+'"'),item.download+' link missing');}
for(const [filename, role] of [['QueueGo-Customer.html','ลูกค้า'],['QueueGo-Shop.html','ร้านค้า'],['QueueGo-Rider.html','ไรเดอร์']]){
  const link=apps.match(new RegExp('<a\\b[^>]*download="'+filename+'"[^>]*>[^<]*<\\/a>'));
  assert.ok(link,filename+' download link missing');
  assert.ok(link[0].includes('aria-label="ดาวน์โหลด HTML แอป'+role+'"'),filename+' download link must identify its app for screen readers');
}
assert.ok(apps.includes('href="./"')&&apps.includes('href="merchant/"')&&apps.includes('href="rider/"'),'all three launch links must exist');
assert.match(apps, /@media\(max-width:400px\)\{\.actions\{grid-template-columns:minmax\(0,1fr\)\}/, 'narrow phones must stack launcher actions');
assert.match(apps, /\.action:focus-visible\{outline:3px solid var\(--red\);outline-offset:3px\}/, 'keyboard focus must be visible on app actions');

// Small informational text must meet WCAG AA (4.5:1) against the lightest page background.
function luminance(hex){
  const channels=hex.match(/[0-9a-f]{2}/gi).map(part=>parseInt(part,16)/255);
  return channels.map(v=>v<=0.04045?v/12.92:((v+0.055)/1.055)**2.4)
    .reduce((sum,v,i)=>sum+v*[0.2126,0.7152,0.0722][i],0);
}
for(const selector of ['.note','footer']){
  const rule=apps.match(new RegExp('(?:\\\\.|^)'+selector.replace('.','\\\\.')+'\\\\{([^}]+)\\\\}'));
  assert.ok(rule,selector+' style rule missing');
  const color=rule[1].match(/color:(#[0-9a-f]{6})/i)?.[1];
  assert.ok(color,selector+' text color missing');
  const contrast=1.05/(luminance(color)+0.05);
  assert.ok(contrast>=4.5,selector+' must meet WCAG AA 4.5:1 contrast on white; got '+contrast.toFixed(2));
}
console.log('PASS: three app launch links, HTML downloads, manifests, icons and responsive accessibility');
