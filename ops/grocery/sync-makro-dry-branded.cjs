#!/usr/bin/env node
'use strict';

const fs=require('fs'),path=require('path'),crypto=require('crypto');
const requestsPath=process.argv[2]||'artifacts/makro-browser-probe/requests.json';
const outPath=process.argv[3]||'catalog/makro-dry-branded-20261015.json';
const endpointNeedle='/search/api/v1/indexes/products/search';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const RETRIES=4,CONCURRENCY=4,TARGET_COUNT=10000;
const ROOTS=['dry-grocery'];

function validBrand(v){
  const x=String(v||'').trim();
  if(!x)return false;
  return !/^(?:-|n\/?a|na|none|no\s*brand|unbranded|ไม่มีแบรนด์|ไม่มียี่ห้อ|ไม่ระบุ)$/i.test(x);
}
function allowedShelfStable(doc,root){
  const cats=(doc.categories||[]).map(x=>String(x).toLowerCase());
  if(!cats.some(x=>x===root||x.startsWith(root+'/')))return false;
  const joined=cats.join('|');
  if(/(?:^|\/)(?:eggs?|frozen|tofu|fresh(?:-|\/|$)|chilled|produce|meat|seafood|vegetable|fruit)(?:\/|$)/i.test(joined))return false;
  if(doc.isWeightedFresh===true)return false;
  const attrs=doc.productAttributes?.categoricalAttributes||[];
  if(attrs.some(x=>/^storage_type::(?:chilled|frozen|refrigerated)$/i.test(String(x))))return false;
  return true;
}
function compact(doc,retrievedAt,root){
  const images=(doc.images||[]).filter(x=>typeof x==='string'&&/^https:\/\/images\.mango-prod\.siammakro\.cloud\//.test(x));
  return {
    source:'makro-pro',
    source_category:root,
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
    unit_size:doc.unitSize||null,
    unit_type:doc.unitType||null,
    unit_factor:doc.unitFactor??null,
    packaging_weight:doc.packagingWeight??null,
    source_url:'https://www.makro.pro/th/c/'+root,
    retrieved_at:retrievedAt
  };
}
async function fetchPage(url,template,root,page,size){
  const body={
    ...template,
    q:'*',page,size,
    filters:{...template.filters,category:[root]},
    autocorrect:false,autocategorise:false,includeCurations:false,source:'web'
  };
  for(let attempt=1;attempt<=RETRIES;attempt++){
    try{
      const r=await fetch(url,{
        method:'POST',
        headers:{'content-type':'application/json','referer':'https://www.makro.pro/','user-agent':'QueueGoCatalogSync/1.1'},
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
async function fetchRoot(live,template,root,retrievedAt,remaining){
  let size=20,first=null;
  for(const candidate of [200,120,100,50,20]){
    const j=await fetchPage(live.url,template,root,1,candidate);
    if(j.hits.length>0){size=j.hits.length===candidate?candidate:j.hits.length;first=j;break}
  }
  if(!first)return {root,sourceFound:0,items:[],excluded:{missingBrand:0,notShelfStable:0,missingTitle:0}};
  const expected=Number(first.found)||0;
  const pages=Math.ceil(expected/size);
  console.log(JSON.stringify({root,expected,size,pages,remaining}));

  const docs=new Map();
  function add(j){
    for(const h of j.hits||[]){
      const d=h?.document,id=String(d?.productId||d?.id||'');
      if(id)docs.set(id,d);
    }
  }
  add(first);
  let next=2,done=1;
  async function worker(){
    while(true){
      const page=next++;
      if(page>pages)return;
      const j=await fetchPage(live.url,template,root,page,size);
      add(j);done++;
      if(done%10===0||done===pages)console.log(JSON.stringify({root,progress:done,pages,unique:docs.size,pageFound:j.found}));
      await sleep(70);
    }
  }
  await Promise.all(Array.from({length:Math.min(CONCURRENCY,Math.max(1,pages-1))},worker));

  const excluded={missingBrand:0,notShelfStable:0,missingTitle:0};
  const items=[];
  for(const d of docs.values()){
    if(!validBrand(d.brand)){excluded.missingBrand++;continue}
    if(!allowedShelfStable(d,root)){excluded.notShelfStable++;continue}
    if(!String(d.title||d.searchTitle||'').trim()){excluded.missingTitle++;continue}
    items.push(compact(d,retrievedAt,root));
  }
  // Preserve Makro result order where possible by using relevance rank from source pages;
  // stable Thai sort only makes generated output deterministic after selection.
  items.sort((a,b)=>a.brand.localeCompare(b.brand,'th')||a.title_th.localeCompare(b.title_th,'th')||a.source_product_id.localeCompare(b.source_product_id));
  return {root,sourceFound:expected,items:items.slice(0,Math.max(0,remaining)),eligibleCount:items.length,excluded};
}
async function main(){
  if(!fs.existsSync(requestsPath))throw Error('Browser probe request log missing: '+requestsPath);
  const requests=JSON.parse(fs.readFileSync(requestsPath,'utf8'));
  const live=requests.find(r=>r.method==='POST'&&r.url.includes(endpointNeedle)&&r.postData&&/"page"\s*:\s*2/.test(r.postData))
    ||requests.find(r=>r.method==='POST'&&r.url.includes(endpointNeedle)&&r.postData);
  if(!live)throw Error('Makro search request template not captured');
  const template=JSON.parse(live.postData);
  if(!Array.isArray(template?.filters?.category))throw Error('Captured Makro request lacks category filter');
  const retrievedAt=new Date().toISOString();

  const byRoot=[],selected=[],seen=new Set();
  for(const root of ROOTS){
    if(selected.length>=TARGET_COUNT)break;
    const result=await fetchRoot(live,template,root,retrievedAt,TARGET_COUNT-selected.length);
    byRoot.push({
      root,
      source_found:result.sourceFound,
      eligible_count:result.eligibleCount,
      selected_count:result.items.length,
      excluded:result.excluded
    });
    for(const item of result.items){
      const key=item.source_product_id;
      if(!key||seen.has(key))continue;
      seen.add(key);selected.push(item);
      if(selected.length>=TARGET_COUNT)break;
    }
  }
  if(selected.length<TARGET_COUNT)throw Error('Eligible branded shelf-stable catalog below target: '+selected.length);

  selected.sort((a,b)=>a.source_category.localeCompare(b.source_category)||a.brand.localeCompare(b.brand,'th')||a.title_th.localeCompare(b.title_th,'th')||a.source_product_id.localeCompare(b.source_product_id));
  const payload={
    schema_version:2,
    source:'Makro PRO public branded shelf-stable catalog',
    retrieved_at:retrievedAt,
    target_count:TARGET_COUNT,
    count:selected.length,
    roots_priority:ROOTS,
    scope_note:'Makro dry-grocery only; no beverages, fresh market, or electronics',
    filters:{
      require_brand:true,
      exclude_fresh:true,
      exclude_electronics:true,
      exclude_market_fresh:true,
      exclude_alcohol_top_level:true,
      excluded_category_tokens:['eggs','frozen','tofu','fresh','chilled','produce','meat','seafood','vegetable','fruit'],
      excluded_storage_types:['chilled','frozen','refrigerated']
    },
    source_summary:byRoot,
    items:selected
  };
  fs.mkdirSync(path.dirname(outPath),{recursive:true});
  fs.writeFileSync(outPath,JSON.stringify(payload,null,2)+'\n');
  const digest=crypto.createHash('sha256').update(fs.readFileSync(outPath)).digest('hex');
  console.log(JSON.stringify({written:outPath,count:selected.length,source_summary:byRoot,sha256:digest}));
}
main().catch(e=>{console.error(e.stack||e);process.exit(1)});
