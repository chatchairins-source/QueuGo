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
  'pork-lean':'หมูเนื้อแดง',
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
const STOP=new Set(['คละ','ขนาด','ใหญ่','เล็ก','กลาง','ไซส์','สด','ทั้งตัว','แพ็ค','แพ็ก','ประมาณ','กก','กรัม','ตัว','ชิ้น','เบอร์','ละ','ผัก','เนื้อ','ขาว','หอม','เกรด']);
const NEGATIVE=['น้ำยาปรับผ้านุ่ม','น้ํายาปรับผ้านุ่ม','ผงซักฟอก','โต๊ะ','เก้าอี้','เฟอร์นิเจอร์','แชมพู','สบู่','กระดาษชำระ','ทิชชู่','เตียง','ชั้นวาง'];
const segmenter=new Intl.Segmenter('th',{granularity:'word'});
function clean(v){
  return String(v||'').normalize('NFC').toLowerCase()
    .replace(/\([^)]*\)/g,' ')
    .replace(/\b(?:kg|g|ml|l|xl|xxl|s|m|l)\b/gi,' ')
    .replace(/[0-9.,/%×x+-]+/g,' ')
    .replace(/ซีพี|เอโร่|โลตัส|makro|aro/gi,' ')
    .replace(/[^\p{L}\p{N}]+/gu,' ')
    .replace(/\s+/g,' ')
    .trim();
}
function tokens(v){
  const out=[];
  for(const part of segmenter.segment(clean(v))){
    const t=part.segment.trim();
    if(part.isWordLike&&t.length>=2)out.push(t);
  }
  return out;
}
function distinctive(v){return tokens(v).filter(t=>!STOP.has(t));}
function compact(v){return clean(v).replace(/\s+/g,'');}
function queryFor(item){
  return ALIASES[item.slug]||clean(item.name_th);
}
function imageOf(doc){
  return (doc?.images||[]).find(x=>typeof x==='string'&&/^https:\/\/images\.mango-prod\.siammakro\.cloud\//.test(x))||null;
}
function categoryFits(item,title){
  const t=compact(title),cat=String(item.category||'');
  const hints={
    'เนื้อหมู':['หมู','สุกร'],'เนื้อวัว':['วัว','โคขุน','โค'],
    'ไก่ / เป็ด':['ไก่','เป็ด'],'ปลา':['ปลา'],'กุ้ง':['กุ้ง'],'ปู':['ปู'],
    'หอย':['หอย'],'ปลาหมึก':['หมึก'],
    'ผักสด':['คะน้า','กวางตุ้ง','กะหล่ำ','แตงกวา','มะเขือ','ต้นหอม','หอมใหญ่','กระเทียม','แครอท','กะเพรา','โหระพา','ผักกาด','ผักชี','ขิง','ตะไคร้','มะกรูด','มะนาว'],
    'ผลไม้':['กล้วย','มะม่วง','แตงโม','ทุเรียน','ฝรั่ง','มะละกอ','มังคุด','ลำไย','ลิ้นจี่','เงาะ','แก้วมังกร','แคนตาลูป','แอปเปิล'],
    'เห็ด':['เห็ด'],'ไข่':['ไข่'],
    'เต้าหู้ / เส้นสด / ลูกชิ้น':['เต้าหู้','เส้น','ลูกชิ้น'],
    'ของแห้ง':['แห้ง','หมี่','โปรตีนเกษตร','แปะก๊วย','ดอกไม้จีน']
  }[cat]||[];
  return !hints.length||hints.some(h=>t.includes(compact(h)));
}
function score(item,doc){
  const q=compact(queryFor(item)),title=compact(doc?.title||doc?.searchTitle||'');
  if(q.length<3||title.length<3)return 0;
  if(NEGATIVE.some(x=>title.includes(compact(x))))return -1;
  if(!categoryFits(item,title))return 0;
  if(title===q)return 2;
  if(title.includes(q))return 1.5;
  if(q.length>=6&&q.includes(title)&&title.length/q.length>=.8)return 1.25;
  return 0;
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
        const accepted=!!best&&best.score>=1.25;
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
