const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.join(__dirname,'..');
const readJson=p=>JSON.parse(fs.readFileSync(path.join(root,p),'utf8'));
const read=p=>fs.readFileSync(path.join(root,p),'utf8');

const forbiddenFacts=['price','delivery_price','cost','cost_price','stock','stock_quantity','barcode','gtin'];
const allowed=new Set([
  'ผักสด','เห็ด','ผลไม้','เนื้อหมู','เนื้อวัว','ไก่ / เป็ด',
  'ปลา','กุ้ง','ปู','หอย','ปลาหมึก','อาหารทะเล','ไข่',
  'เต้าหู้ / เส้นสด / ลูกชิ้น','ของสดพร้อมปรุง','อาหารแช่เย็น / แช่แข็ง',
  'พริกแกง / เครื่องแกง','เครื่องปรุง / ซอส','ข้าวสาร / ธัญพืช','ของแห้ง',
  'อาหารปรุงสำเร็จ','ขนม / ของหวาน','เครื่องดื่ม','ของใช้ในครัวเรือน',
  'ดอกไม้ / ของไหว้','อื่น ๆ'
]);
const allSlugs=new Set();
function verifySourceItem(item,label){
  assert.match(item.slug,/^[a-z0-9][a-z0-9-]+[a-z0-9]$/,label+' slug');
  assert.ok(!allSlugs.has(item.slug),'duplicate catalog slug '+item.slug); allSlugs.add(item.slug);
  assert.ok(String(item.name_th||'').trim().length>=2,label+' name');
  assert.ok(allowed.has(item.category),'unsupported category '+item.category);
  assert.match(String(item.source_url||''),/^https:\/\//,label+' source URL');
  assert.ok(['Makro PRO',"Lotus's",'กรมการค้าภายใน กระทรวงพาณิชย์','ตลาดไท'].includes(item.source_name),label+' source name '+item.source_name);
  assert.match(String(item.retrieved_on||''),/^\d{4}-\d{2}-\d{2}$/,label+' retrieved date');
  for(const forbidden of forbiddenFacts){
    assert.equal(Object.prototype.hasOwnProperty.call(item,forbidden),false,label+' must not import/invent '+forbidden);
  }
}

const master=readJson('catalog/thai-market-master.json');
assert.equal(master.version,1);
assert.ok(Array.isArray(master.items)&&master.items.length>=25,'market master starter must contain sourced real items');
for(const item of master.items){
  verifySourceItem(item,'starter');
  assert.equal(item.image,null,'starter images must stay null until approved rights evidence exists');
}

const retail=readJson('catalog/market-fresh-retail-sources-20261019.json');
assert.ok(Array.isArray(retail.items)&&retail.items.length>=30,'Makro/Lotus fresh expansion must be substantial');
for(const item of retail.items) verifySourceItem(item,'fresh retailer fact');
for(const required of ['เห็ด','เนื้อวัว','เนื้อหมู']){
  assert.ok(retail.items.some(x=>x.category===required),'fresh expansion must include '+required);
}
assert.ok(retail.items.some(x=>/เป็ด/.test(x.name_th)),'fresh expansion must include duck');
assert.ok(retail.items.some(x=>/ไก่/.test(x.name_th)),'fresh expansion must include chicken');

const phase2=readJson('catalog/market-seafood-fruit-veg-sources-20261019.json');
assert.ok(Array.isArray(phase2.items)&&phase2.items.length>=60,'phase 2 market expansion must be substantial');
for(const item of phase2.items) verifySourceItem(item,'phase 2 source fact');
for(const required of ['ปลา','กุ้ง','ปู','หอย','ปลาหมึก','ผลไม้','ผักสด']){
  assert.ok(phase2.items.some(x=>x.category===required),'phase 2 must cover '+required);
}
for(const requiredName of ['ปลานิล','ปลาทับทิม','ปลาดุก','ปลาช่อน','ปลากะพง','ปลาอินทรี','ปลาทู','ปลาเนื้ออ่อน','ปลาหมอ','กุ้ง','กุ้งแชบ๊วย','ปู','ปูทะเล','ปูนิ่ม','หอย','หอยนางรม','หอยหวาน','หอยเชลล์','ปลาหมึก','มะม่วง','แตงโม','ส้ม','แอปเปิล','ฝรั่ง','มะละกอ','สับปะรด','องุ่น','ลิ้นจี่','ลำไย','เงาะ','ทุเรียน','มังคุด','ผักชี','ขึ้นฉ่าย','ดอกกะหล่ำ','บร็อกโคลี','ถั่วฝักยาว','ฟักทอง','ขิง','ข่า','ตะไคร้','ใบมะกรูด','พริก','มะเขือ','มะนาว']){
  assert.ok(phase2.items.some(x=>x.name_th.includes(requiredName)),'phase 2 missing common-market item '+requiredName);
}

const migration=read('supabase/migrations/20261019114500_market_master_catalog.sql');
const expansion=read('supabase/migrations/20261019122500_market_fresh_makro_lotus_expansion.sql');
const imageReview=read('supabase/migrations/20261019132000_market_image_source_review.sql');
const phase2Migration=read('supabase/migrations/20261019140000_market_seafood_fruit_veg_expansion.sql');
const phase2GapFill=read('supabase/migrations/20261019143000_market_phase2_gap_fill.sql');
const phase2ChaebowMudCrab=read('supabase/migrations/20261019150000_market_phase2_chaebow_mudcrab.sql');
const imageSourceGapFill=read('supabase/migrations/20261019151500_market_image_source_gapfill.sql');
const openLicenseImages=read('supabase/migrations/20261019154500_market_open_license_images.sql');
const openLicenseImagesBatch2=read('supabase/migrations/20261019160000_market_open_license_images_batch2.sql');
const openLicenseImagesBatch3=read('supabase/migrations/20261019161500_market_open_license_images_batch3.sql');
const openLicenseImagesBatch4=read('supabase/migrations/20261019163000_market_open_license_images_batch4.sql');
const openLicenseImagesBatch5=read('supabase/migrations/20261019164500_market_open_license_images_batch5.sql');
const openLicenseImagesBatch6=read('supabase/migrations/20261019170000_market_open_license_images_batch6.sql');
const openLicenseImagesBatch7=read('supabase/migrations/20261019171500_market_open_license_images_batch7.sql');
const openLicenseImagesBatch8=read('supabase/migrations/20261019173000_market_open_license_images_batch8.sql');
const openLicenseImagesBatch9=read('supabase/migrations/20261019174500_market_open_license_images_batch9.sql');
const openLicenseImagesBatch10=read('supabase/migrations/20261019180000_market_open_license_images_batch10.sql');
const openLicenseImagesBatch11=read('supabase/migrations/20261019181500_market_open_license_images_batch11.sql');
const openLicenseImagesBatch12=read('supabase/migrations/20261019183000_market_open_license_images_batch12.sql');
const openLicenseImagesBatch13=read('supabase/migrations/20261019184500_market_open_license_images_batch13.sql');
const openLicenseImagesBatch14=read('supabase/migrations/20261019190000_market_open_license_images_batch14.sql');
const openLicenseImagesBatch15=read('supabase/migrations/20261019191500_market_open_license_images_batch15.sql');
const openLicenseImagesBatch16=read('supabase/migrations/20261019193000_market_open_license_images_batch16.sql');
const openLicenseImagesBatch17=read('supabase/migrations/20261019194500_market_open_license_images_batch17.sql');
assert.ok(expansion.includes("'เห็ด'"),'fresh expansion must add mushroom category');
assert.ok(expansion.includes('Makro PRO')&&expansion.includes("Lotus''s"),'fresh expansion must retain both public sources');
for(const cat of ["'กุ้ง'","'ปู'","'หอย'","'ปลาหมึก'"]){
  assert.ok(phase2Migration.includes(cat),'phase 2 migration missing category '+cat);
}
assert.ok(phase2Migration.includes("image,null,'pending'")||phase2Migration.includes("image,image_status,image_source"),'phase 2 images must remain pending review');
assert.ok(phase2Migration.includes('Retailer pages/images remain review references only'),'phase 2 must document retailer-image safety');
assert.ok(phase2GapFill.includes('talaadthai-climbing-perch-small'),'phase 2 gap fill must retain sourced climbing-perch identity');
assert.ok(phase2GapFill.includes('makro-soft-shell-crab-1kg'),'phase 2 gap fill must retain sourced soft-shell crab');
assert.ok(phase2ChaebowMudCrab.includes('lotus-shrimp-chaebow-25-30kg'),'phase 2 must retain sourced chaebow shrimp');
assert.ok(phase2ChaebowMudCrab.includes('talaadthai-mud-crab-medium'),'phase 2 must retain sourced mud crab');
assert.ok(imageReview.includes('has_image_reference'),'catalog RPC must expose image-review state');
assert.ok(imageReview.includes("image_status='pending'"),'review candidates must stay pending');
for(const needle of [
  'create table public.market_master_products',
  'create unique index market_products_shop_master_unique',
  'primary key(shop_id,request_id)',
  'request ID body mismatch',
  'alter table public.market_master_products enable row level security',
  'create table market_private.catalog_add_receipts',
  'create or replace function public.qg_market_master_categories()',
  'create or replace function public.qg_market_catalog_page(',
  'create or replace function public.qg_market_catalog_add(',
  'pg_advisory_xact_lock',
  'grant execute on function public.qg_market_catalog_add',
  'revoke all on function public.qg_market_catalog_add'
]) assert.ok(migration.includes(needle),'missing migration contract: '+needle);

const images=readJson('catalog/market-image-candidates-20261019.json');
assert.equal(images.items.length,allSlugs.size,'every catalog source item must have one image-review record');
const imageSlugs=new Set();
for(const row of images.items){
  assert.ok(!imageSlugs.has(row.slug),'duplicate image-review row '+row.slug); imageSlugs.add(row.slug);
  assert.ok(allSlugs.has(row.slug),'image-review row has unknown catalog slug '+row.slug);
  assert.match(String(row.source_page_url||''),/^https:\/\//,'image source page '+row.slug);
  assert.ok(['rights_pending','source_page_found','family_reference_only','approved'].includes(row.status),'invalid image review state '+row.slug);
  if(row.status==='approved'){
    assert.match(String(row.candidate_image_url||''),/^https:\/\/upload\.wikimedia\.org\//,'approved open-license image '+row.slug);
    assert.ok(String(row.rights_evidence||'').length>10,'approved image must retain attribution/license evidence '+row.slug);
    assert.match(row.source_page_url,/^https:\/\/commons\.wikimedia\.org\/wiki\/File:/,'approved image must retain Commons source page '+row.slug);
  }else{
    assert.equal(row.rights_evidence,null,'unapproved image must not claim rights evidence '+row.slug);
    if(row.status==='rights_pending') assert.match(String(row.candidate_image_url||''),/^https:\/\//,'direct image candidate '+row.slug);
    if(row.status==='source_page_found'||row.status==='family_reference_only') assert.equal(row.candidate_image_url,null,'non-direct image state must not carry a direct image candidate '+row.slug);
  }
}
for(const slug of allSlugs) assert.ok(imageSlugs.has(slug),'missing image-review state for '+slug);
assert.ok(images.items.some(x=>x.status==='rights_pending'),'must retain verified retailer candidates under rights review');
assert.ok(images.items.filter(x=>x.status==='approved').length>=88,'must retain approved open-license catalog images');
assert.equal(images.items.filter(x=>x.status==='source_page_found').length,0,'every current catalog item must have a direct image candidate or approved image');
assert.equal(images.items.filter(x=>x.status==='family_reference_only').length,0,'all current catalog items must have concrete image evidence');
for(const slug of ['pork-lean','egg-size-2-10','fresh-tofu-skin','textured-vegetable-protein','ginkgo-nut','dried-squid-medium']){
  assert.ok(imageSourceGapFill.includes("'"+slug+"'"),'image source gap-fill migration missing '+slug);
}
assert.ok(openLicenseImages.includes("image like 'https://upload.wikimedia.org/%'"),'open-license migration must keep Wikimedia rights gate');
assert.ok(openLicenseImages.includes("image_source like 'https://commons.wikimedia.org/wiki/File:%'"),'open-license migration must keep Commons source-page gate');
for(const slug of ['dried-lily-flower','dried-squid-medium','talaadthai-climbing-perch-small','talaadthai-shrimp-chaebow-medium','talaadthai-shrimp-chaebow-large','talaadthai-mud-crab-small','talaadthai-mud-crab-medium']){
  assert.ok(openLicenseImages.includes("'"+slug+"'"),'open-license migration missing '+slug);
}
for(const slug of ['chinese-cabbage','makro-chinese-cabbage-400-600g','holy-basil-leaves','large-cavendish-banana']){
  assert.ok(openLicenseImagesBatch2.includes("'"+slug+"'"),'open-license batch 2 missing '+slug);
}
for(const slug of ['spring-onion','makro-cucumber-500g','water-spinach']){
  assert.ok(openLicenseImagesBatch3.includes("'"+slug+"'"),'open-license batch 3 missing '+slug);
}
for(const slug of ['makro-bird-chili-300g','makro-thai-eggplant-300g','makro-kaffir-lime-leaf-150g']){
  assert.ok(openLicenseImagesBatch4.includes("'"+slug+"'"),'open-license batch 4 missing '+slug);
}
for(const slug of ['makro-old-galangal-1kg','makro-broccoli-10kg','makro-old-ginger-1kg']){
  assert.ok(openLicenseImagesBatch5.includes("'"+slug+"'"),'open-license batch 5 missing '+slug);
}
for(const slug of ['makro-fuji-apple-10','makro-longan-1kg','makro-guava-1kg','makro-mangosteen-basket-3kg']){
  assert.ok(openLicenseImagesBatch6.includes("'"+slug+"'"),'open-license batch 6 missing '+slug);
}
for(const slug of ['lotus-ripe-namdokmai-mango-kg','makro-red-lady-papaya-1kg','makro-monthong-durian-2-3kg','makro-shine-muscat-500g']){
  assert.ok(openLicenseImagesBatch7.includes("'"+slug+"'"),'open-license batch 7 missing '+slug);
}
for(const slug of ['makro-import-carrot-1kg','thai-basil','makro-coriander-300g','makro-cauliflower-1kg','makro-onion-1kg']){
  assert.ok(openLicenseImagesBatch8.includes("'"+slug+"'"),'open-license batch 8 missing '+slug);
}
for(const slug of ['cabbage','makro-cabbage-500-700g','pork-belly','makro-chicken-drumstick-1kg','egg-size-2-10','egg-size-3-10','makro-enoki-1kg','lotus-enoki-500g','dried-shiitake-small','dried-shiitake-large','makro-tomato-1kg','makro-long-bean-500g','lotus-mackerel-kg']){
  assert.ok(openLicenseImagesBatch9.includes("'"+slug+"'"),'open-license batch 9 missing '+slug);
}
for(const slug of ['choy-sum','makro-choy-sum-500g','makro-peeled-garlic-1kg','lotus-hom-banana-bunch','makro-kinnaree-watermelon-4kg','makro-honey-murcott-orange-1kg','lotus-hom-suwan-pineapple','makro-red-dragon-fruit-1kg']){
  assert.ok(openLicenseImagesBatch10.includes("'"+slug+"'"),'open-license batch 10 missing '+slug);
}
for(const slug of ['lotus-fresh-shiitake-100g','makro-white-shimeji-1kg','lotus-beef-shank','makro-pork-loin-1kg','makro-lemongrass-1kg']){
  assert.ok(openLicenseImagesBatch11.includes("'"+slug+"'"),'open-license batch 11 missing '+slug);
}
for(const slug of ['makro-tilapia-900g-1kg','lotus-snakehead-whole','makro-river-prawn-small-1kg','lotus-seabass-whole-4-6kg','lotus-cockle-kg']){
  assert.ok(openLicenseImagesBatch12.includes("'"+slug+"'"),'open-license batch 12 missing '+slug);
}
for(const slug of ['makro-pork-shoulder-1kg','lotus-beef-rib','lotus-beef-hip']){
  assert.ok(openLicenseImagesBatch13.includes("'"+slug+"'"),'open-license batch 13 missing '+slug);
}
for(const slug of ['kale-mixed','makro-kale-500g','makro-celery-300g','makro-large-pumpkin-half-kg','makro-key-lime-10','lotus-emperor-lychee-kg','makro-golden-rambutan-2-5kg','makro-sunlady-cantaloupe-1kg']){
  assert.ok(openLicenseImagesBatch14.includes("'"+slug+"'"),'open-license batch 14 missing '+slug);
}
for(const slug of ['makro-king-oyster-500g','lotus-king-mackerel-3-5kg','makro-white-shrimp-l-1kg','makro-squid-rings-1kg','makro-squid-tentacles-1kg','makro-blue-crab-large-1kg']){
  assert.ok(openLicenseImagesBatch15.includes("'"+slug+"'"),'open-license batch 15 missing '+slug);
}
for(const slug of ['makro-chicken-leg-quarter-1kg','makro-catfish-cut-1kg','makro-soft-shell-crab-1kg']){
  assert.ok(openLicenseImagesBatch16.includes("'"+slug+"'"),'open-license batch 16 missing '+slug);
}
for(const slug of ['dried-bamboo-shoot','fresh-tofu-skin','textured-vegetable-protein','ginkgo-nut']){
  assert.ok(openLicenseImagesBatch17.includes("'"+slug+"'"),'open-license batch 17 missing '+slug);
}

const catalog=read('merchant/market-catalog.js');
for(const needle of ['qg_market_catalog_page','qg_market_catalog_add','effective_gp_rate','เพิ่มสินค้าจากแคตตาล็อก','เพิ่มสินค้าเข้าร้าน']){
  assert.ok(catalog.includes(needle),'missing Merchant market catalog behavior: '+needle);
}
assert.ok(catalog.includes('qgMarketCatalogSetKg(1)')&&catalog.includes('qgMarketCatalogSetKg(2)')&&catalog.includes('qgMarketCatalogSetKg(3)'),'catalog must offer 1/2/3 kg presets');
assert.ok(catalog.includes('p_request_id:crypto.randomUUID()'),'Merchant add flow must use a durable request ID');
assert.ok(catalog.includes("pendingKey(state.shop)"),'Merchant add flow must retain pending request recovery');
assert.ok(catalog.includes('พบรูปอ้างอิงแล้ว · รอตรวจสิทธิ์ก่อนเผยแพร่'),'Merchant must show rights-pending image status');
assert.ok(catalog.includes('กำลังหารูปที่ตรวจสอบแหล่งที่มาได้'),'Merchant must show missing-image review status');
assert.ok(catalog.includes('qgMarketCatalogSetCategory')&&catalog.includes('market-catalog-chip'),'Merchant must expose category chips without duplicate filter state');
assert.ok(!catalog.includes('src="${E(r.image_source)}"'),'pending external image_source must never render as approved art');

const marketCss=read('merchant/market.css');
for(const needle of ['market-catalog-card','market-catalog-image','market-catalog-chips','object-fit:contain']){
  assert.ok(marketCss.includes(needle),'Market catalog responsive CSS missing '+needle);
}

const market=read('merchant/market.js');
assert.ok(market.includes("navigate('market-catalog')"));
assert.ok(market.includes('＋ เพิ่มสินค้าเอง'));
assert.ok(market.includes('ราคาหน้าร้าน / ชุด (บาท)'));
assert.ok(market.includes('ราคาเดลิเวอรี่'));
for(const category of ['เห็ด','กุ้ง','ปู','หอย','ปลาหมึก']){
  assert.ok(market.includes("'"+category+"'"),'Merchant category selector missing '+category);
}
const merchant=read('merchant/index.html');
assert.ok(merchant.includes("case'market-catalog'"));
assert.ok(merchant.includes('market-catalog.js?v=20261019-market-image-flow3'));
assert.ok(merchant.includes("'market-stock','market-catalog'"),'pending merchants must be able to prepare catalog/stock');

console.log(JSON.stringify({
  scope:'Market Master Catalog source/image/category/weight/pending-merchant contracts',
  starter:master.items.length,
  firstFresh:retail.items.length,
  phase2:phase2.items.length,
  totalCatalog:allSlugs.size,
  imageReview:images.counts
}));
