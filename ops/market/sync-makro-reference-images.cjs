#!/usr/bin/env node
'use strict';

const fs=require('fs'),path=require('path');
const endpoint='https://search.maknet.siammakro.cloud/search/api/v1/indexes/products/search';
const storeCodes=JSON.parse(fs.readFileSync('catalog/makro-store-codes-20261015.json','utf8')).store_codes;
const manifest=JSON.parse(fs.readFileSync('catalog/market-image-candidates-20261019.json','utf8'));
const outPath=process.argv[2]||'artifacts/market-makro-images/market-makro-reference-images.json';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const CONCURRENCY=4,RETRIES=3;

const ALIASES={
  'pork-tenderloin-dressed':'สันในหมู',
  'pork-lean':'เนื้อหมู',
  'whole-duck':'เป็ดสดทั้งตัว',
  'egg-size-2-10':'ไข่ไก่ เบอร์ 2',
  'egg-size-3-10':'ไข่ไก่ เบอร์ 3',
  'dried-lily-flower':'ดอกไม้จีนแห้ง',
  'textured-vegetable-protein':'โปรตีนเกษตร',
  'ginkgo-nut':'แปะก๊วย',
  'dried-squid-medium':'ปลาหมึกแห้ง',
  'large-cavendish-banana':'กล้วยหอม',
  'native-chicken':'ไก่บ้าน'
};
const STOP=new Set(['คละ','ขนาด','ใหญ่','เล็ก','กลาง','ไซส์','สด','ทั้งตัว','แพ็ค','แพ็ก','ประมาณ','กก','กรัม','ตัว','ชิ้น','เบอร์','ละ']);
function clean(v){
  return String(v||'').toLowerCase()
    .replace(/\([^)]*\)/g,' ')
    .replace(/\b(?:kg|g|ml|l|xl|xxl|s|m|l)\b/gi,' ')
    .replace(/[0-9.,/%×x+-]+/g,' ')
    .replace(/ซีพี|เอโร่|โลตัส|makro|aro/gi,' ')
    .replace(/[^\p{L}\p{N}]+/gu,' ')
    .trim();
}
function tokens(v){
  return clean(v).split(/\s+/).filter(t=>t.length>=2&&!STOP.has(t));
}
function queryFor(item){
  return ALIASES[item.slug]||clean(item.name_th);
}
function imageOf(doc){
  return (doc?.images||[]).find(x=>typeof x==='string'&&/^https:\/\/images\.mango-prod\.siammakro\.cloud\//.test(x))||null;
}
function score(item,doc){
  const a=new Set(tokens(queryFor(item))),b=new Set(tokens(doc?.title||doc?.searchTitle||''));
  if(!a.size||!b.size)return 0;
  let hit=0; for(const t of a) if([...b].some(x=>x.includes(t)||t.includes(x))) hit++;
  let s=hit/a.size;
  const title=clean(doc?.title||doc?.searchTitle||'');
  const q=clean(queryFor(item));
  if(title===q)s+=.5;
  else if(title.includes(q)||q.includes(title))s+=.25;
  const cat=String(item.category||'');
  const categoryHints={
    'เนื้อหมู':['หมู','สุกร'],'เนื้อวัว':['วัว','โค','เนื้อ'],'ไก่ / เป็ด':['ไก่','เป็ด'],
    'ปลา':['ปลา'],'กุ้ง':['กุ้ง'],'ปู':['ปู'],'หอย':['หอย'],'ปลาหมึก':['หมึก'],
    'ผักสด':['ผัก','คะน้า','กวางตุ้ง','กะหล่ำ','แตง','มะเขือ','หอม','กระเทียม','แครอท','กะเพรา','โหระพา'],
    'ผลไม้':['กล้วย','มะม่วง','แตงโม','ทุเรียน','ฝรั่ง','มะละกอ','มังคุด','ลำไย','ลิ้นจี่','เงาะ','แก้วมังกร','แคนตาลูป'],
    'เห็ด':['เห็ด'],'ไข่':['ไข่']
  }[cat]||[];
  if(categoryHints.some(h=>title.includes(h)))s+=.15;
  return Math.round(s*1000)/1000;
}
async function search(q){
  const body={q,size:20,filters:{storeCodes,isSalesCustomer:false,countryCode:'TH',lang:'th',allowRestrictedProduct:false,allowAlcohol:false},sortBy:'RELEVANCE',autocorrect:true,page:1,autocategorise:true,includeCurations:true,source:'web'};
  for(let attempt=1;attempt<=RETRIES;attempt++){
    try{
      const r=await fetch(endpoint,{method:'POST',headers:{'content-type':'application/json','accept':'application/json','accept-language':'th-TH,th;q=0.9','x-device-platform':'ios','referer':'https://www.makro.pro/'},body:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
      if(!r.ok)throw Error('HTTP '+r.status+' '+(await r.text()).slice(0,200));
      const j=await r.json(); return Array.isArray(j.hits)?j.hits.map(h=>h.document).filter(Boolean):[];
    }catch(e){if(attempt===RETRIES)throw e;await sleep(500*attempt);}
  }
}
(async()=>{
  const queue=manifest.items.slice(),results=[],failures=[];
  let next=0;
  async function worker(){
    while(true){
      const i=next++; if(i>=queue.length)return;
      const item=queue[i],q=queryFor(item);
      try{
        const docs=await search(q);
        const ranked=docs.map(doc=>({doc,score:score(item,doc),image:imageOf(doc)})).filter(x=>x.image).sort((a,b)=>b.score-a.score);
        const best=ranked[0]||null;
        const accepted=!!best&&best.score>=0.6;
        results[i]={
          slug:item.slug,name_th:item.name_th,category:item.category,query:q,
          status:accepted?'matched':'review',
          score:best?.score||0,
          makro_product_id:best?.doc?.productId||null,
          makro_title:best?.doc?.title||best?.doc?.searchTitle||null,
          makro_brand:best?.doc?.brand||null,
          image_url:accepted?best.image:null,
          source_url:accepted&&best?.doc?.productId?'https://www.makro.pro/th/p/'+best.doc.productId:null,
          top_candidates:ranked.slice(0,3).map(x=>({score:x.score,product_id:x.doc?.productId||null,title:x.doc?.title||x.doc?.searchTitle||null,brand:x.doc?.brand||null,image_url:x.image}))
        };
      }catch(e){failures.push({slug:item.slug,error:String(e.message||e)});results[i]={slug:item.slug,name_th:item.name_th,category:item.category,query:q,status:'error',score:0,image_url:null,source_url:null,top_candidates:[]};}
      await sleep(80);
    }
  }
  await Promise.all(Array.from({length:CONCURRENCY},worker));
  const matched=results.filter(x=>x.status==='matched').length;
  const payload={schema_version:1,source:'Makro PRO public search API',generated_at:new Date().toISOString(),count:results.length,matched,review:results.length-matched,failures,items:results};
  fs.mkdirSync(path.dirname(outPath),{recursive:true});fs.writeFileSync(outPath,JSON.stringify(payload,null,2)+'\n');
  console.log(JSON.stringify({written:outPath,count:results.length,matched,review:results.length-matched,failures:failures.length}));
})().catch(e=>{console.error(e.stack||e);process.exit(1)});
