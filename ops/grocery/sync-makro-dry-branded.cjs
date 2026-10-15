#!/usr/bin/env node
'use strict';

const fs=require('fs'),path=require('path'),crypto=require('crypto');
const endpoint='https://search.maknet.siammakro.cloud/search/api/v1/indexes/products/search';
const outPath=process.argv[2]||'catalog/makro-dry-branded-20261015.json';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const RETRIES=4,CONCURRENCY=4,TARGET_COUNT=10000,ROOT='dry-grocery';
const STORE_CODES=JSON.parse(fs.readFileSync('catalog/makro-store-codes-20261015.json','utf8')).store_codes;

function validBrand(v){
  const x=String(v||'').trim();
  if(!x)return false;
  return !/^(?:-|n\/?a|na|none|no\s*brand|unbranded|ไม่มีแบรนด์|ไม่มียี่ห้อ|ไม่ระบุ)$/i.test(x);
}
function allowedShelfStable(doc){
  const cats=(doc.categories||[]).map(x=>String(x).toLowerCase());
  if(!cats.some(x=>x===ROOT||x.startsWith(ROOT+'/')))return false;
  const joined=cats.join('|');
  // User scope: dry branded grocery only. Never market/fresh/frozen/chilled food.
  if(/(?:^|\/)(?:eggs?|frozen|tofu|fresh(?:-|\/|$)|chilled|produce|meat|seafood|vegetable|fruit|alcohol|beer|wine|liquor|spirits?|tobacco|cigarettes?)(?:\/|$)/i.test(joined))return false;
  if(doc.isWeightedFresh===true)return false;
  const attrs=doc.productAttributes?.categoricalAttributes||[];
  if(attrs.some(x=>/^storage_type::(?:chilled|frozen|refrigerated)$/i.test(String(x))))return false;
  return true;
}
function compact(doc,retrievedAt){
  const image=(doc.images||[]).find(x=>typeof x==='string'&&/^https:\/\/images\.mango-prod\.siammakro\.cloud\//.test(x))||null;
  return {
    source_product_id:String(doc.productId||doc.id||''),
    sku:doc.sku||null,
    barcode:doc.itemBarCode||doc.itemBarCodes?.[0]||null,
    brand:String(doc.brand||'').trim(),
    title_th:String(doc.title||doc.searchTitle||'').trim(),
    title_en:doc.titleEn||null,
    deepest_category:doc.deepestCategory||ROOT,
    image_url:image,
    source_category:ROOT,
    source_url:'https://www.makro.pro/th/c/dry-grocery',
    retrieved_at:retrievedAt
  };
}
function body(page,size){
  return {
    q:'*',
    size,
    filters:{
      category:[ROOT],
      storeCodes:STORE_CODES,
      isSalesCustomer:false,
      countryCode:'TH',
      lang:'th',
      allowRestrictedProduct:true,
      allowAlcohol:true
    },
    sortBy:'RELEVANCE',
    autocorrect:true,
    page,
    autocategorise:true,
    includeCurations:true,
    source:'web'
  };
}
async function fetchPage(page,size){
  for(let attempt=1;attempt<=RETRIES;attempt++){
    try{
      const r=await fetch(endpoint,{
        method:'POST',
        headers:{
          'content-type':'application/json',
          'accept':'application/json',
          'accept-language':'th-TH,th;q=0.9',
          'x-device-platform':'ios',
          'referer':'https://www.makro.pro/'
        },
        body:JSON.stringify(body(page,size)),
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
  const retrievedAt=new Date().toISOString();
  let size=20,first=null;
  for(const candidate of [200,120,100,50,20]){
    const j=await fetchPage(1,candidate);
    if(j.hits.length>0){size=j.hits.length===candidate?candidate:j.hits.length;first=j;break}
  }
  if(!first)throw Error('Cannot fetch first Makro dry-grocery page');
  const expected=Number(first.found)||0,pages=Math.ceil(expected/size);
  if(expected<10000)throw Error('Makro dry-grocery source below 10k: '+expected);
  console.log(JSON.stringify({endpoint,root:ROOT,expected,size,pages}));

  const docs=new Map();
  const add=j=>{for(const h of j.hits||[]){const d=h?.document,id=String(d?.productId||d?.id||'');if(id)docs.set(id,d)}};
  add(first);
  let next=2,done=1;
  async function worker(){
    while(true){
      const page=next++;
      if(page>pages)return;
      const j=await fetchPage(page,size);
      add(j);done++;
      if(done%10===0||done===pages)console.log(JSON.stringify({progress:done,pages,unique:docs.size,pageFound:j.found}));
      await sleep(70);
    }
  }
  await Promise.all(Array.from({length:Math.min(CONCURRENCY,Math.max(1,pages-1))},worker));

  const excluded={missingBrand:0,notShelfStable:0,missingTitle:0},eligible=[];
  for(const d of docs.values()){
    if(!validBrand(d.brand)){excluded.missingBrand++;continue}
    if(!allowedShelfStable(d)){excluded.notShelfStable++;continue}
    if(!String(d.title||d.searchTitle||'').trim()){excluded.missingTitle++;continue}
    eligible.push(compact(d,retrievedAt));
  }
  eligible.sort((a,b)=>a.brand.localeCompare(b.brand,'th')||a.title_th.localeCompare(b.title_th,'th')||a.source_product_id.localeCompare(b.source_product_id));
  if(eligible.length<TARGET_COUNT)throw Error('Eligible branded shelf-stable dry-grocery below target: '+eligible.length);

  const items=eligible.slice(0,TARGET_COUNT);
  const payload={
    schema_version:3,
    source:'Makro PRO public dry-grocery search API',
    source_url:'https://www.makro.pro/th/c/dry-grocery',
    retrieved_at:retrievedAt,
    source_found:expected,
    unique_source_products:docs.size,
    eligible_count:eligible.length,
    target_count:TARGET_COUNT,
    count:items.length,
    filters:{
      root:'dry-grocery',
      require_brand:true,
      exclude_fresh:true,
      exclude_market_fresh:true,
      exclude_electronics:true,
      exclude_beverages_root:true,
      excluded_category_tokens:['eggs','frozen','tofu','fresh','chilled','produce','meat','seafood','vegetable','fruit','alcohol','beer','wine','liquor','spirit','tobacco','cigarette'],
      excluded_storage_types:['chilled','frozen','refrigerated']
    },
    excluded,
    items
  };
  fs.mkdirSync(path.dirname(outPath),{recursive:true});
  // Compact JSON keeps the 10k source catalog small; Merchant still gets one preview image per item.
  fs.writeFileSync(outPath,JSON.stringify(payload)+'\n');
  const bytes=fs.statSync(outPath).size;
  const digest=crypto.createHash('sha256').update(fs.readFileSync(outPath)).digest('hex');
  console.log(JSON.stringify({written:outPath,count:items.length,eligible_count:eligible.length,excluded,bytes,sha256:digest}));
}
main().catch(e=>{console.error(e.stack||e);process.exit(1)});
