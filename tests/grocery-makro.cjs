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
ok(sync.includes("TARGET_COUNT=10000,ROOT='dry-grocery'"),'sync is locked to exact 10k dry-grocery');
ok(sync.includes('storeCodes:STORE_CODES'),'sync uses public Makro store coverage');
ok(Array.isArray(coverage.store_codes)&&coverage.store_codes.length>=100,'public store coverage snapshot is populated');
ok(coverage.category==='dry-grocery','coverage snapshot is dry-grocery only');
ok(!sync.includes("ROOT='beverages'")&&!sync.includes("ROOTS=['dry-grocery','beverages']"),'sync does not pad with beverages root');
ok(sync.includes('validBrand'),'sync rejects unbranded rows');
for(const token of ['eggs?','frozen','tofu','fresh','chilled','alcohol','beer','wine','liquor','tobacco','cigarettes?']){
  ok(sync.includes(token),'sync excludes '+token);
}
ok(sync.includes("storage_type::(?:chilled|frozen|refrigerated)"),'sync rejects chilled/frozen storage');
ok(!sync.includes("|meat|seafood|vegetable|fruit|"),'sync does not wrongly discard dried/canned fruit, vegetable, meat or seafood');
ok(sync.includes('source_rank:sourceRank'),'sync preserves deterministic source rank');
ok(sync.includes('eligible.length<TARGET_COUNT'),'sync fails closed below 10k instead of widening scope');
ok(!sync.includes('image_urls:'),'generated snapshot keeps one preview image only');
ok(!sync.includes('product_attributes:'),'generated snapshot omits bulky unused product attributes');

ok(sql.includes('create table if not exists public.grocery_source_products'),'source catalog table exists');
ok(sql.includes("check (source='makro-pro')"),'source catalog is Makro-only');
ok(sql.includes("deepest_category='dry-grocery'"),'database enforces dry-grocery');
ok(!sql.includes("source_category in ('dry-grocery','beverages')"),'database does not allow beverages root');
ok(!sql.includes('create extension if not exists http'),'database does not crawl Makro from Production');
ok(!sql.includes('qg_internal_makro_source_sync_page'),'no hidden Production crawler remains');
ok(sql.includes('qg_grocery_source_search'),'Merchant source search RPC exists');
ok(sql.includes('qg_grocery_source_add_to_shop'),'Merchant add-from-source RPC exists');
ok(sql.includes('qg_admin_grocery_source_import'),'admin batch source import exists');
ok(sql.includes('qg_admin_grocery_source_finalize'),'atomic 10k source activation exists');
ok(sql.includes("p_target<>10000"),'finalize requires exact 10k snapshot');
ok(sql.includes("'catalog_preview_image',s.image_url"),'Makro image is stored as Merchant preview metadata');
ok(sql.includes("p_shop_id,v_name,'',p_store_price,null,v_category"),'new shop product does not promote preview URL into products.image');

ok(importer.includes("process.argv.includes('--apply')"),'source importer is dry-run unless explicitly applied');
ok(importer.includes('QG_CATALOG_ADMIN_TOKEN'),'source importer requires admin token for writes');
ok(importer.includes('data.items.length!==10000'),'source importer requires exactly 10k rows');
ok(importer.includes('qg_admin_grocery_source_finalize'),'source importer finalizes only after all batches');

ok(merchant.includes("qg_grocery_source_search"),'Merchant add mode searches source catalog');
ok(merchant.includes("qg_grocery_source_add_to_shop"),'Merchant adds only selected source product');
ok(merchant.includes('ค้นหาชื่อสินค้า ยี่ห้อ หรือบาร์โค้ด'),'Merchant exposes name/brand/barcode search');
ok(merchant.includes('catalog_preview_image'),'Merchant product views can show preview fallback');

ok(workflow.includes('sync-makro-dry-branded.cjs'),'CI builds Makro source snapshot');
ok(workflow.includes('git rebase "origin/${GITHUB_REF_NAME}"'),'generated catalog commit preserves successor HEAD');
ok(!workflow.includes('probe-makro-browser.cjs'),'CI no longer depends on flaky browser capture');

console.log(JSON.stringify({checks,failures:0,scope:'10k branded dry Makro source catalog; search then add; no Production mutation'}));
