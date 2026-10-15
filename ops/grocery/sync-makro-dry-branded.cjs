#!/usr/bin/env node
'use strict';

const fs=require('fs'),path=require('path'),crypto=require('crypto');
const requestsPath=process.argv[2]||'artifacts/makro-browser-probe/requests.json';
const outPath=process.argv[3]||'catalog/makro-dry-branded-20261015.json';
const endpointNeedle='/search/api/v1/indexes/products/search';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const RETRIES=4,CONCURRENCY=4;

function validBrand(v){
  const x=String(v||'').trim();
  if(!x)return false;
  return !/^(?:-|n\/?a|na|none|no\s*brand|unbranded|ไม่มีแบรนด์|ไม่มียี่ห้อ|ไม่ระบุ)$/i.test(x);
}
function dryOnly(doc){
  const cats=(doc.categories||[]).map(x=>String(x).toLowerCase());
  if(!cats.some(x=>x==='dry-grocery'||x.startsWith('dry-grocery/')))return false;
  const joined=cats.join('|');
  if(/(?:^|\/)(?:eggs?|frozen|tofu|dairy-products|fresh(?:-|\/|$)|chilled|produce|meat|seafood|vegetable|fruit)(?:\/|$)/i.test(joined))return false;
  if(doc.isWeightedFresh===true)return false;
  return true;
}
function compact(doc,retrievedAt){
  const images=(doc.images||[]).filter(x=>typeof x==='string'&&/^https:\/\/images\.mango-prod\.siammakro\.cloud\//.test(x));
  return {
    source:'makro-pro',
    source_category:'dry-grocery',
    source_product_id:String(doc.productId||doc.id||''),
    sku:doc.sku||null,
    barcode:doc.itemBarCode||doc.itemBarCodes?.[0]||null,
    brand:String(doc.brand||'').trim(),
    brand_en:doc.brandEn||null,
    title_th:String(doc.title||doc.searchTitle||'').trim(),
    title_en:doc.titleEn||null,
    categories:Array.isArray(doc.categories)?doc.categories:[],
    category_ids:Array.isArray(doc.categoryIds)?doc.categoryIds:[],
    deepest_category:doc.deepestCategory||null,
    image_url:images[0]||null,
    image_urls:images.slice(0,5),
    unit_size:doc.unitSize||null,
    unit_type:doc.unitType||null,
    unit_factor:doc.unitFactor??null,
    packaging_weight:doc.packagingWeight??null,
    product_attributes:doc.productAttributes||null,
    source_url:'https://www.makro.pro/th/c/dry-grocery',
    retrieved_at:retrievedAt
  };
}
async function fetchPage(url,template,page,size){
  const body={...template,page,size};
  for(let attempt=1;attempt<=RETRIES;attempt++){
    try{
      const r=await fetch(url,{
        method:'POST',
        headers:{'content-type':'application/json','referer':'https://www.makro.pro/','user-agent':'QueueGoCatalogSync/1.0'},
        body:JSON.stringify(body),
        signal:AbortSignal.timeout(30000)
      });
      if(!r.ok)throw Error('HTTP '+r.status+' '+(await r.text()).slice(0,300));
      const j=await r.json();
      if(!Array.isArray(j.hits))throw Error('missing hits');
      return j;
    }catch(e){
      if(attempt===RETRIES)throw e;
      await sleep(500*attempt);
    }
  }
}
async function main(){
  if(!fs.existsSync(requestsPath))throw Error('Browser probe request log missing: '+requestsPath);
  const requests=JSON.parse(fs.readFileSync(requestsPath,'utf8'));
  const live=requests.find(r=>r.method==='POST'&&r.url.includes(endpointNeedle)&&r.postData&&/"page"\s*:\s*2/.test(r.postData))
    ||requests.find(r=>r.method==='POST'&&r.url.includes(endpointNeedle)&&r.postData);
  if(!live)throw Error('Makro search request template not captured');
  const template=JSON.parse(live.postData);
  if(!Array.isArray(template?.filters?.category)||!template.filters.category.includes('dry-grocery'))throw Error('Captured request is not dry-grocery');
  template.q='*';template.autocorrect=false;template.autocategorise=false;template.includeCurations=false;template.source='web';
  const retrievedAt=new Date().toISOString();

  let size=20,first=null;
  for(const candidate of [200,100,50,20]){
    const j=await fetchPage(live.url,template,1,candidate);
    if(j.hits.length>0){size=j.hits.length===candidate?candidate:j.hits.length;first=j;break}
  }
  if(!first)throw Error('Cannot fetch first Makro search page');
  const expected=Number(first.found)||0;
  if(expected<5000)throw Error('Makro dry-grocery result unexpectedly small: '+expected);
  const pages=Math.ceil(expected/size);
  console.log(JSON.stringify({endpoint:live.url,expected,size,pages}));

  const docs=new Map();
  function add(j){
    for(const h of j.hits||[]){
      const d=h?.document;
      const id=String(d?.productId||d?.id||'');
      if(id)docs.set(id,d);
    }
  }
  add(first);
  let next=2,done=1;
  async function worker(){
    while(true){
      const page=next++;
      if(page>pages)return;
      const j=await fetchPage(live.url,template,page,size);
      add(j);done++;
      if(done%10===0||done===pages)console.log(JSON.stringify({progress:done,pages,unique:docs.size,pageFound:j.found}));
      await sleep(80);
    }
  }
  await Promise.all(Array.from({length:Math.min(CONCURRENCY,Math.max(1,pages-1))},worker));

  // One reconciliation pass if search pages changed while syncing.
  if(docs.size<Math.floor(expected*0.985)){
    console.log(JSON.stringify({reconcile:true,unique:docs.size,expected}));
    for(let page=1;page<=pages;page++){
      add(await fetchPage(live.url,template,page,size));
      if(page%20===0)console.log(JSON.stringify({reconcilePage:page,unique:docs.size}));
      await sleep(60);
    }
  }

  const raw=[...docs.values()];
  const excluded={missingBrand:0,notDry:0,missingTitle:0};
  const items=[];
  for(const d of raw){
    if(!validBrand(d.brand)){excluded.missingBrand++;continue}
    if(!dryOnly(d)){excluded.notDry++;continue}
    if(!String(d.title||d.searchTitle||'').trim()){excluded.missingTitle++;continue}
    items.push(compact(d,retrievedAt));
  }
  items.sort((a,b)=>a.brand.localeCompare(b.brand,'th')||a.title_th.localeCompare(b.title_th,'th')||a.source_product_id.localeCompare(b.source_product_id));
  if(items.length<8000)throw Error('Filtered branded dry catalog unexpectedly small: '+items.length);

  const payload={
    schema_version:1,
    source:'Makro PRO public dry-grocery search',
    source_url:'https://www.makro.pro/th/c/dry-grocery',
    retrieved_at:retrievedAt,
    source_found:expected,
    unique_source_products:raw.length,
    filters:{
      require_brand:true,
      include_root:'dry-grocery',
      exclude_category_tokens:['eggs','frozen','tofu','dairy-products','fresh','chilled','produce','meat','seafood','vegetable','fruit'],
      exclude_electronics:true,
      exclude_market_fresh:true
    },
    excluded,
    count:items.length,
    items
  };
  fs.mkdirSync(path.dirname(outPath),{recursive:true});
  fs.writeFileSync(outPath,JSON.stringify(payload,null,2)+'\n');
  const digest=crypto.createHash('sha256').update(fs.readFileSync(outPath)).digest('hex');
  console.log(JSON.stringify({written:outPath,count:items.length,excluded,sha256:digest}));
}
main().catch(e=>{console.error(e.stack||e);process.exit(1)});
