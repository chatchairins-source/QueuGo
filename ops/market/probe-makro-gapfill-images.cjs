#!/usr/bin/env node
'use strict';

const fs=require('fs'),path=require('path');
const endpoint='https://search.maknet.siammakro.cloud/search/api/v1/indexes/products/search';
const storeCodes=JSON.parse(fs.readFileSync('catalog/makro-store-codes-20261015.json','utf8')).store_codes;
const outPath=process.argv[2]||'artifacts/market-makro-gapfill/market-makro-gapfill-candidates.json';
const queries={
  'talaadthai-shrimp-chaebow-medium':'กุ้งแชบ๊วย',
  'talaadthai-shrimp-chaebow-large':'กุ้งแชบ๊วย',
  'lotus-shrimp-chaebow-25-30kg':'กุ้งแชบ๊วย',
  'native-chicken':'ไก่บ้าน',
  'lotus-duck-whole-xl':'ซีพี เป็ดอนามัย XL',
  'lotus-duck-whole-m':'ซีพี เป็ดอนามัย M',
  'dried-squid-medium':'ปลาหมึกแห้ง',
  'textured-vegetable-protein':'โปรตีนเกษตร',
  'mee-sua-400g':'หมี่ซั่ว',
  'dried-shiitake-small':'เห็ดหอมแห้งเล็ก',
  'dried-shiitake-large':'เห็ดหอมแห้งใหญ่',
  'fresh-tofu-skin':'ฟองเต้าหู้',
  'lotus-beef-rib':'ซี่โครงวัว',
  'lotus-beef-shank':'น่องวัว',
  'lotus-beef-hip':'สะโพกวัว',
  'lotus-beef-sirloin':'สันนอกวัว',
  'pork-lean':'เนื้อหมูหั่นแกง',
  'pork-belly':'สามชั้นหมูตัดเส้น',
  'lotus-seabass-whole-4-6kg':'ปลากะพงขาวทั้งตัว',
  'lotus-seabass-sliced':'ปลากะพงหั่นชิ้น',
  'lotus-snakehead-whole':'ปลาช่อน',
  'lotus-mackerel-kg':'ปลาทูสด',
  'makro-tilapia-900g-1kg':'ปลานิล 900 1000 กรัม',
  'lotus-soft-fish-kg':'ปลาเนื้ออ่อน',
  'talaadthai-climbing-perch-small':'ปลาหมอสด',
  'lotus-king-mackerel-3-5kg':'ปลาอินทรี',
  'lotus-peeled-squid-kg':'หมึกกล้วยลอกหนัง',
  'makro-squid-tentacles-1kg':'หนวดหมึก',
  'lotus-peeled-cuttlefish-kg':'หมึกกระดองลอกหนัง',
  'talaadthai-mud-crab-medium':'ปูทะเล',
  'talaadthai-mud-crab-small':'ปูทะเล',
  'lotus-cut-blue-crab-500g':'ปูม้าตัด',
  'lotus-hom-banana-bunch':'กล้วยหอม',
  'makro-red-dragon-fruit-1kg':'แก้วมังกรเนื้อแดง',
  'lotus-ripe-namdokmai-mango-kg':'มะม่วงน้ำดอกไม้',
  'makro-longan-1kg':'ลำไย 1 กก.',
  'lotus-emperor-lychee-kg':'ลิ้นจี่',
  'makro-honey-murcott-orange-1kg':'ส้มเมอร์คอต',
  'lotus-hom-suwan-pineapple':'สับปะรดหอมสุวรรณ',
  'makro-shine-muscat-500g':'องุ่นไชน์มัสแคท',
  'makro-fuji-apple-10':'แอปเปิลฟูจิ',
  'cabbage':'กะหล่ำปลี',
  'kale-mixed':'คะน้า',
  'water-spinach':'ผักบุ้งจีน',
  'lotus-cockle-kg':'หอยแครง',
  'lotus-small-clam':'หอยลายเล็ก',
  'lotus-sweet-snail-kg':'หอยหวาน',
  'makro-white-shimeji-1kg':'เห็ดชิเมจิขาว'
};
const imageOf=doc=>(doc?.images||[]).find(x=>typeof x==='string'&&/^https:\/\/images\.mango-prod\.siammakro\.cloud\//.test(x))||null;
async function search(q){
  const body={q,size:12,filters:{storeCodes,isSalesCustomer:false,countryCode:'TH',lang:'th',allowRestrictedProduct:false,allowAlcohol:false},sortBy:'RELEVANCE',autocorrect:true,page:1,autocategorise:true,includeCurations:true,source:'web'};
  const r=await fetch(endpoint,{method:'POST',headers:{'content-type':'application/json','accept':'application/json','accept-language':'th-TH,th;q=0.9','x-device-platform':'ios','referer':'https://www.makro.pro/'},body:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
  if(!r.ok)throw Error('HTTP '+r.status+' '+(await r.text()).slice(0,200));
  const j=await r.json();
  return (j.hits||[]).map(x=>x.document).filter(Boolean).map(d=>({
    product_id:String(d.productId||d.id||''),
    title:d.title||d.searchTitle||null,
    brand:d.brand||null,
    deepest_category:d.deepestCategory||null,
    image_url:imageOf(d)
  })).filter(x=>x.image_url);
}
(async()=>{
  const rows=[];
  for(const [slug,q] of Object.entries(queries)){
    let candidates=[],error=null;
    try{candidates=await search(q)}catch(e){error=String(e.message||e)}
    rows.push({slug,query:q,error,candidates:candidates.slice(0,8)});
    await new Promise(r=>setTimeout(r,90));
  }
  const payload={generated_at:new Date().toISOString(),count:rows.length,items:rows};
  fs.mkdirSync(path.dirname(outPath),{recursive:true});
  fs.writeFileSync(outPath,JSON.stringify(payload,null,2)+'\n');
  console.log(JSON.stringify({written:outPath,count:rows.length,with_candidates:rows.filter(x=>x.candidates.length).length,errors:rows.filter(x=>x.error).length}));
})().catch(e=>{console.error(e.stack||e);process.exit(1)});
