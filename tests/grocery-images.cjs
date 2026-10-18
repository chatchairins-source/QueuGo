'use strict';
const assert=require('node:assert/strict'),fs=require('fs'),os=require('os'),path=require('path'),crypto=require('crypto'),{JSDOM}=require('jsdom'),N=require('../catalog/normalize.js'),I=require('../ops/grocery/collect-image-candidates.cjs');
let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
eq(I.imageUrl(undefined,'https://example.test/a'),null);eq(I.imageUrl('javascript:alert(1)','https://example.test/a'),null);eq(I.imageUrl('http://example.test/photo.png','https://example.test/a'),null);eq(I.imageUrl('/photo.png','https://example.test/a'),'https://example.test/photo.png');
const row=N.normalize({brand:'สุรีย์',product_name_th:'น้ำจิ้มไก่',size_value:300,size_unit:'g',pack_size:1,sales_unit:'ชิ้น',category:'ซอส',source_url:'https://www.sahapat.co.th/product/foodproduct/Suree',source_type:'distributor',retrieved_at:'2026-10-15T00:00:00Z'});
const html='<div class="item"><img src="/300.png"><p>น้ำจิ้มไก่ 300 กรัม</p></div><div class="item"><img src="/690.png"><p>น้ำจิ้มไก่ 690 กรัม</p></div><div class="item"><p>น้ำจิ้มไก่ 300 กรัม</p></div>';
const d=new JSDOM(html),assets=I.spc(d.window.document,row.source_url);eq(assets.length,2);const matched=I.match(row,assets);eq(matched[0].match,'label_size_exact');eq(matched[1].match==='label_size_exact',false);eq(matched[0].rights_status,'unverified');eq(matched[0].rights_evidence,null);
eq(I.match({...row,pack_size:6},assets).some(x=>x.match==='label_size_exact'),false);eq(I.match({...row,variant:'สูตรเผ็ด'},assets).some(x=>x.match==='label_size_exact'),false);
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'queuego-image-test-'));try{fs.writeFileSync(path.join(temp,crypto.createHash('sha256').update(row.source_url).digest('hex').slice(0,20)+'.html'),html);const result=I.collect([row],temp);eq(result.statistics.exact_candidates,1);eq(result.statistics.approved,0);eq(result.items[0].production_approved,false);const retail=I.collect([{...row,source_type:'retailer'}],temp);eq(retail.items[0].source_status,'retailer_excluded');eq(retail.items[0].candidates.length,0);const missing=I.collect([{...row,source_url:'https://manufacturer.example/missing'}],temp);eq(missing.statistics.missing,1);}finally{d.window.close();fs.rmSync(temp,{recursive:true,force:true})}
const staged=require('../catalog/image-candidates-20261015.json');eq(staged.items.length,414);eq(new Set(staged.items.map(x=>x.normalized_key)).size,414);eq(staged.items.some(x=>x.production_approved),false);eq(staged.items.flatMap(x=>x.candidates).some(x=>x.rights_status!=='unverified'||x.rights_evidence!==null),false);

const rightsMigration=fs.readFileSync('supabase/migrations/20261018034000_grocery_approved_image_projection.sql','utf8');
eq(rightsMigration.includes("case when m.image_status='approved' then nullif(btrim(m.image),'') else null end as image"),true);
eq(rightsMigration.includes("coalesce(nullif(btrim(m.image),''), nullif(btrim(m.image_source),'')) as image"),false);
const merchantUi=fs.readFileSync('merchant/grocery-catalog.js','utf8');
eq(merchantUi.includes('QG_GROCERY_PREVIEW_IMAGES?.[r.id]'),false);
eq(merchantUi.includes("display_name:r.title_th,image:null"),true);
console.log(JSON.stringify({checks,failures:0,scope:'image identity, rights-gated projection and no unapproved source hotlink; no Production images written'}));
