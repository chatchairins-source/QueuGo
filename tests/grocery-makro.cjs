'use strict';
const fs=require('fs'),assert=require('assert');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const sync=fs.readFileSync('ops/grocery/sync-makro-dry-branded.cjs','utf8');
const importer=fs.readFileSync('ops/grocery/import-makro-source-catalog.cjs','utf8');
const sql=fs.readFileSync('supabase/migrations/20261015165000_makro_grocery_source_catalog.sql','utf8');
const merchant=fs.readFileSync('merchant/grocery-catalog.js','utf8');
const workflow=fs.readFileSync('.github/workflows/makro-public-catalog-probe.yml','utf8');
const coverage=JSON.parse(fs.readFileSync('catalog/makro-store-codes-20261015.json','utf8'));

ok(sync.includes('/search/api/v1/indexes/products/search'),'sync uses Makro public search endpoint');
ok(sync.includes('TARGET_COUNT=10000'),'sync is locked to exact 10k');
ok(sync.includes("{root:'dry-grocery'"),'food source begins with Makro dry-grocery');
ok(sync.includes("{root:'health-beauty/personal-care'"),'supplemental source is branded personal care, not fresh/electrical');
ok(sync.includes('storeCodes:STORE_CODES'),'sync uses public Makro store coverage');
ok(Array.isArray(coverage.store_codes)&&coverage.store_codes.length>=100,'public store coverage snapshot is populated');
ok(sync.includes('validBrand'),'sync rejects unbranded rows');
for(const token of ['eggs?','frozen','tofu','fresh','chilled','fresh-market','market-fresh','electronics?','electrical','appliances?','alcohol','beer','wine','liquor','tobacco','cigarettes?']){
  ok(sync.includes(token),'sync excludes '+token);
}
ok(sync.includes("storage_type::(?:chilled|frozen|refrigerated)"),'sync rejects chilled/frozen storage');
ok(sync.includes('eligible.length<TARGET_COUNT'),'sync fails closed below 10k instead of widening unsafe scope');
ok(sync.includes("outPath=process.argv[2]||'artifacts/makro-catalog/makro-grocery-branded.json'"),'generated 10k snapshot lives in CI artifact path');
ok(sync.includes("if(bytes>15*1024*1024)"),'snapshot size is bounded to avoid repo/web bloat');
ok(!sync.includes('image_urls:'),'generated snapshot keeps one preview image only');
ok(!sync.includes('product_attributes:'),'generated snapshot omits bulky unused product attributes');

ok(sql.includes('create table if not exists public.grocery_source_products'),'source catalog table exists');
ok(sql.includes("source_category in ('dry-grocery','health-beauty/personal-care')"),'database allows only approved grocery source roots');
ok(sql.includes("deepest_category=source_category"),'database binds deepest category to approved source root');
ok(!sql.includes('create extension if not exists http'),'database does not crawl Makro from Production');
ok(!sql.includes('qg_internal_makro_source_sync_page'),'no hidden Production crawler remains');
ok(!/\nend\n\$\$;/.test(sql),'PL/pgSQL function bodies must terminate END with a semicolon');
ok(!/^\s*\$;\s*$/m.test(sql),'SQL migration must not contain single-dollar function terminators');
ok((sql.match(/\bas \$\$/g)||[]).length===(sql.match(/^\$\$;\s*$/gm)||[]).length,'every dollar-quoted function body is closed with $;');
ok(sql.includes('qg_grocery_source_search'),'Merchant source search RPC exists');
ok(sql.includes('qg_grocery_source_add_to_shop'),'Merchant add-from-source RPC exists');
ok(sql.includes('qg_admin_grocery_source_import'),'admin batch source import exists');
ok(sql.includes('qg_admin_grocery_source_finalize'),'atomic 10k source activation exists');
ok(sql.includes("p_target<>10000"),'finalize requires exact 10k snapshot');
ok(sql.includes("'catalog_preview_image',s.image_url"),'Makro image is stored as Merchant preview metadata');
ok(sql.includes("p_shop_id,v_name,'',p_store_price,null,v_category"),'new shop product does not promote preview URL into products.image');
ok(sql.includes("'catalog_source_category',s.source_category"),'shop product keeps real Makro source root');

ok(importer.includes("process.argv.includes('--apply')"),'source importer is dry-run unless explicitly applied');
ok(importer.includes('QG_CATALOG_ADMIN_TOKEN'),'source importer requires admin token for writes');
ok(importer.includes('data.items.length!==10000'),'source importer requires exactly 10k rows');
ok(importer.includes("ALLOWED_ROOTS=new Set(['dry-grocery','health-beauty/personal-care'])"),'importer rejects any unapproved source root');
ok(importer.includes('qg_admin_grocery_source_finalize'),'source importer finalizes only after all batches');

ok(merchant.includes("qg_grocery_source_search"),'Merchant add mode searches source catalog');
ok(merchant.includes("qg_grocery_source_add_to_shop"),'Merchant adds only selected source product');
ok(merchant.includes('ค้นหาสินค้า Makro: ชื่อ ยี่ห้อ หรือบาร์โค้ด'),'Merchant exposes 10k name/brand/barcode search');
ok(merchant.includes('image:r.image_url'),'Merchant source search renders Makro preview image');

ok(workflow.includes('sync-makro-dry-branded.cjs'),'CI builds Makro source snapshot');
ok(workflow.includes('artifacts/makro-catalog/makro-grocery-branded.json'),'CI uploads generated snapshot as artifact');
ok(!workflow.includes('git push origin'),'CI does not commit regenerated 10k data into source tree');
ok(!workflow.includes('probe-makro-browser.cjs'),'CI no longer depends on flaky browser capture');

const {exclusionReason}=require('../ops/grocery/sync-makro-dry-branded.cjs');
for(const doc of [
 {categories:['dry-grocery/flour/flour'],title:'แป้งวาฟเฟิลแช่แข็ง'},
 {categories:['dry-grocery/dairy-products/cheese'],title:'ชีสแช่เย็น'},
 {categories:['dry-grocery/dessert/frozen-desserts'],title:'วาฟเฟิล'},
 {categories:['dry-grocery'],deepestCategory:'dry-grocery/frozen-desserts',title:'วาฟเฟิล'},
 {categories:['dry-grocery'],title:'FROZEN dough'},
 {categories:['dry-grocery'],title:'Refrigerated cheese'},
 {categories:['dry-grocery'],productAttributes:{categoricalAttributes:['storage_type::chilled']}}
])ok(!!exclusionReason(doc,'dry-grocery'),'cold product rejected even under dry category');
ok(exclusionReason({categories:['dry-grocery/instant-meals'],title:'มาม่า บะหมี่กึ่งสำเร็จรูป'},'dry-grocery')===null,'normal dry branded product remains eligible');
console.log(JSON.stringify({checks,failures:0,scope:'exact 10k branded non-fresh Makro grocery source; search then add; no fresh market/electrical; no Production mutation'}));
