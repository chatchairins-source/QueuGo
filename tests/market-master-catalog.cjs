const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.join(__dirname,'..');

const master=JSON.parse(fs.readFileSync(path.join(root,'catalog/thai-market-master.json'),'utf8'));
assert.equal(master.version,1);
assert.ok(Array.isArray(master.items)&&master.items.length>=25,'market master starter must contain sourced real items');
const allowed=new Set(['ผักสด','เห็ด','ผลไม้','เนื้อหมู','เนื้อวัว','ไก่ / เป็ด','ปลา','อาหารทะเล','ไข่','เต้าหู้ / เส้นสด / ลูกชิ้น','ของสดพร้อมปรุง','อาหารแช่เย็น / แช่แข็ง','พริกแกง / เครื่องแกง','เครื่องปรุง / ซอส','ข้าวสาร / ธัญพืช','ของแห้ง','อาหารปรุงสำเร็จ','ขนม / ของหวาน','เครื่องดื่ม','ของใช้ในครัวเรือน','ดอกไม้ / ของไหว้','อื่น ๆ']);
const slugs=new Set();
for(const item of master.items){
  assert.match(item.slug,/^[a-z0-9][a-z0-9-]+[a-z0-9]$/);
  assert.ok(!slugs.has(item.slug),'duplicate market master slug '+item.slug); slugs.add(item.slug);
  assert.ok(String(item.name_th||'').trim().length>=2);
  assert.ok(allowed.has(item.category),'unsupported category '+item.category);
  assert.match(String(item.source_url||''),/^https:\/\//);
  assert.ok(String(item.source_name||'').trim().length>=2);
  assert.match(String(item.retrieved_on||''),/^\d{4}-\d{2}-\d{2}$/);
  for(const forbidden of ['price','delivery_price','cost','cost_price','stock','stock_quantity','barcode','gtin']) {
    assert.equal(Object.prototype.hasOwnProperty.call(item,forbidden),false,'master must not invent '+forbidden);
  }
  assert.equal(item.image,null,'starter images must stay null until approved rights evidence exists');
}
const retail=JSON.parse(fs.readFileSync(path.join(root,'catalog/market-fresh-retail-sources-20261019.json'),'utf8'));
assert.ok(Array.isArray(retail.items)&&retail.items.length>=30,'Makro/Lotus fresh expansion must be substantial');
for(const item of retail.items){
  assert.ok(allowed.has(item.category),'unsupported retail category '+item.category);
  assert.match(String(item.source_url||''),/^https:\/\//);
  assert.ok(['Makro PRO',"Lotus's"].includes(item.source_name),'unexpected fresh source '+item.source_name);
  for(const forbidden of ['price','delivery_price','cost','cost_price','stock','stock_quantity','barcode','gtin']) {
    assert.equal(Object.prototype.hasOwnProperty.call(item,forbidden),false,'retail facts must not import '+forbidden);
  }
}
assert.ok(retail.items.some(x=>x.category==='เห็ด'),'fresh expansion must include mushrooms');
assert.ok(retail.items.some(x=>x.category==='เนื้อวัว'),'fresh expansion must include beef');
assert.ok(retail.items.some(x=>/เป็ด/.test(x.name_th)),'fresh expansion must include duck');
assert.ok(retail.items.some(x=>/ไก่/.test(x.name_th)),'fresh expansion must include chicken');
assert.ok(retail.items.some(x=>x.category==='เนื้อหมู'),'fresh expansion must include pork');

const migration=fs.readFileSync(path.join(root,'supabase/migrations/20261019114500_market_master_catalog.sql'),'utf8');
const expansion=fs.readFileSync(path.join(root,'supabase/migrations/20261019122500_market_fresh_makro_lotus_expansion.sql'),'utf8');
assert.ok(expansion.includes("'เห็ด'"),'fresh expansion must add mushroom category');
assert.ok(expansion.includes('Makro PRO')&&expansion.includes("Lotus''s"),'fresh expansion must retain both public sources');
for(const needle of [
  'create table public.market_master_products',
  'alter table public.market_master_products enable row level security',
  'create table market_private.catalog_add_receipts',
  'create or replace function public.qg_market_master_categories()',
  'create or replace function public.qg_market_catalog_page(',
  'create or replace function public.qg_market_catalog_add(',
  'pg_advisory_xact_lock',
  'grant execute on function public.qg_market_catalog_add',
  'revoke all on function public.qg_market_catalog_add'
]) assert.ok(migration.includes(needle),'missing migration contract: '+needle);

const catalog=fs.readFileSync(path.join(root,'merchant/market-catalog.js'),'utf8');
for(const needle of ['qg_market_catalog_page','qg_market_catalog_add','effective_gp_rate','เพิ่มสินค้าจากแคตตาล็อก','เพิ่มสินค้าเข้าร้าน']) {
  assert.ok(catalog.includes(needle),'missing Merchant market catalog behavior: '+needle);
}
assert.ok(catalog.includes('qgMarketCatalogSetKg(1)')&&catalog.includes('qgMarketCatalogSetKg(2)')&&catalog.includes('qgMarketCatalogSetKg(3)'),'catalog must offer 1/2/3 kg presets');
const market=fs.readFileSync(path.join(root,'merchant/market.js'),'utf8');
assert.ok(market.includes("navigate('market-catalog')"));
assert.ok(market.includes('＋ เพิ่มสินค้าเอง'));
assert.ok(market.includes('ราคาหน้าร้าน / ชุด (บาท)'));
assert.ok(market.includes('ราคาเดลิเวอรี่'));
const merchant=fs.readFileSync(path.join(root,'merchant/index.html'),'utf8');
assert.ok(merchant.includes("case'market-catalog'"));
assert.ok(merchant.includes('market-catalog.js?v=20261019-market-catalog1'));
assert.ok(merchant.includes("'market-stock','market-catalog'"),'pending merchants must be able to prepare catalog/stock');
console.log('market-master-catalog: PASS',master.items.length,'starter +',retail.items.length,'Makro/Lotus fresh facts');
