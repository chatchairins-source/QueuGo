const fs=require('fs'),assert=require('assert'),crypto=require('crypto');
const c=JSON.parse(fs.readFileSync('data/maps/buriram-places.json','utf8'));let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
ok(c.licence==='ODbL-1.0');ok(c.attribution==='© OpenStreetMap contributors');ok(c.source_timestamp==='2026-10-15T20:21:00Z');ok(c.records.length>=3000);
const ids=new Set(),types=new Set(),categories=new Set();const [w,s,e,n]=c.bounds;
for(const p of c.records){
 ok(/^osm:(node|way):[0-9]+$/.test(p.id));ok(!ids.has(p.id));ids.add(p.id);types.add(p.id.split(':')[1]);categories.add(p.category);
 ok(p.name.trim().length>0);ok(Number.isFinite(p.lat)&&Number.isFinite(p.lng)&&p.lat>=s&&p.lat<=n&&p.lng>=w&&p.lng<=e);
 ok(p.verified===false&&p.orderable===false);ok(p.source_url==='https://www.openstreetmap.org/'+p.id.split(':').slice(1).join('/'));
 ok(p.phone===null||typeof p.phone==='string');ok(p.opening_hours===null||typeof p.opening_hours==='string');
}
ok(types.has('node')&&types.has('way'));for(const x of ['food','grocery','laundry','fuel','hotel','health'])ok(categories.has(x));
console.log(JSON.stringify({checks,failures:0,records:c.records.length,sha256:crypto.createHash('sha256').update(fs.readFileSync('data/maps/buriram-places.json')).digest('hex'),scope:'real pinned OSM records, bounded coverage, exact identity, provenance and no fabricated commerce eligibility'}));
