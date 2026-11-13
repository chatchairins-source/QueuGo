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
console.log('PASS: three app launch links, HTML downloads, manifests, icons and responsive accessibility');
