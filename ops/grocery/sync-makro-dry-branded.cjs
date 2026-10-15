#!/usr/bin/env node
'use strict';

const fs=require('fs'),path=require('path'),crypto=require('crypto');
const endpoint='https://search.maknet.siammakro.cloud/search/api/v1/indexes/products/search';
const outPath=process.argv[2]||'artifacts/makro-catalog/makro-grocery-branded.json';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const RETRIES=4,CONCURRENCY=4,TARGET_COUNT=10000;
const STORE_CODES=JSON.parse(fs.readFileSync('catalog/makro-store-codes-20261015.json','utf8')).store_codes;
const SOURCES=[
  {root:'dry-grocery',url:'https://www.makro.pro/th/c/dry-grocery',priority:0},
  {root:'health-beauty/personal-care',url:'https://www.makro.pro/th/c/health-beauty/personal-care',priority:1}
];

function scalar(v,max=200){
  if(v===null||v===undefined)return null;
  if(typeof v!=='string'&&typeof v!=='number'&&typeof v!=='boolean')return null;
  const s=String(v).trim();
  return s?s.slice(0,max):null;
}
function validBrand(v){
  const x=String(v||'').trim();
  if(!x)return false;
  return !/^(?:-|n\/?a|na|none|no\s*brand|unbranded|ไม่มีแบรนด์|ไม่มียี่ห้อ|ไม่ระบุ)$/i.test(x);
}
function exclusionReason(doc,root){
  const cats=(doc.categories||[]).map(x=>String(x).toLowerCase());
  if(!cats.some(x=>x===root||x.startsWith(root+'/')))return 'outside-root';
  const joined=cats.join('|');
  const categoryMatch=joined.match(/(?:^|\/)(eggs?|frozen|tofu|fresh(?:-|\/|$)|chilled|fresh-market|market-fresh|electronics?|electrical|appliances?|alcohol|beer|wine|liquor|spirits?|tobacco|cigarettes?)(?:\/|$)/i);
  if(categoryMatch)return 'category:'+String(categoryMatch[1]).toLowerCase();
  if(doc.isWeightedFresh===true)return 'weighted-fresh';
  const attrs=doc.productAttributes?.categoricalAttributes||[];
  const storage=attrs.map(String).find(x=>/^storage_type::(?:chilled|frozen|refrigerated)$/i.test(x));
  if(storage)return 'storage:'+storage.toLowerCase();
  return null;
}
function compact(doc,retrievedAt,source,sourceRank){
  const image=(doc.images||[]).find(x=>typeof x==='string'&&/^https:\/\/images\.mango-prod\.siammakro\.cloud\//.test(x))||null;
  const factor=Number(doc.unitFactor);
  return {
    source_product_id:String(doc.productId||doc.id||''),
    source_rank:source.priority*1000000+sourceRank,
    source_category:source.root,
    source_url:source.url,
    sku:scalar(doc.sku,100),
    barcode:scalar(doc.itemBarCode||doc.itemBarCodes?.[0],64),
    brand:String(doc.brand||'').trim().slice(0,250),
    title_th:String(doc.title||doc.searchTitle||'').trim().slice(0,500),
    title_en:scalar(doc.titleEn,500),
    deepest_category:scalar(doc.deepestCategory,250)||source.root,
    image_url:image,
    unit_size:scalar(doc.unitSize,100),
    unit_type:scalar(doc.unitType,100),
    unit_factor:Number.isFinite(factor)?factor:null,
    retrieved_at:retrievedAt
  };
}
function body(root,page,size){
  return {
    q:'*',
    size,
    filters:{
      category:[root],
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
async function fetchPage(root,page,size){
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
        body:JSON.stringify(body(root,page,size)),
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
async function fetchSource(source){
  let size=20,first=null;
  for(const candidate of [200,120,100,50,20]){
    const j=await fetchPage(source.root,1,candidate);
    if(j.hits.length>0){size=j.hits.length===candidate?candidate:j.hits.length;first=j;break}
  }
  if(!first)throw Error('Cannot fetch Makro category '+source.root);
  const expected=Number(first.found)||0,pages=Math.ceil(expected/size);
  console.log(JSON.stringify({endpoint,root:source.root,expected,size,pages}));

  const docs=new Map();
  const add=j=>{
    const page=Number(j.page)||1;
    (j.hits||[]).forEach((h,i)=>{
      const d=h?.document,id=String(d?.productId||d?.id||'');
      if(!id)return;
      const rank=(page-1)*size+i;
      const prev=docs.get(id);
      if(!prev||rank<prev.rank)docs.set(id,{doc:d,rank});
    });
  };
  add(first);
  let next=2,done=1;
  async function worker(){
    while(true){
      const page=next++;
      if(page>pages)return;
      const j=await fetchPage(source.root,page,size);
      add(j);done++;
      if(done%10===0||done===pages)console.log(JSON.stringify({root:source.root,progress:done,pages,unique:docs.size,pageFound:j.found}));
      await sleep(70);
    }
  }
  await Promise.all(Array.from({length:Math.min(CONCURRENCY,Math.max(1,pages-1))},worker));
  return {expected,docs};
}
async function main(){
  const retrievedAt=new Date().toISOString();
  const globallySeen=new Set(),eligible=[],summaries=[];
  for(const source of SOURCES){
    const {expected,docs}=await fetchSource(source);
    const excluded={duplicate:0,missingBrand:0,restricted:0,missingTitle:0};
    const reasons={};
    for(const entry of docs.values()){
      const d=entry.doc,id=String(d?.productId||d?.id||'');
      if(globallySeen.has(id)){excluded.duplicate++;continue}
      globallySeen.add(id);
      if(!validBrand(d.brand)){excluded.missingBrand++;continue}
      const reason=exclusionReason(d,source.root);
      if(reason){excluded.restricted++;reasons[reason]=(reasons[reason]||0)+1;continue}
      if(!String(d.title||d.searchTitle||'').trim()){excluded.missingTitle++;continue}
      eligible.push({...compact(d,retrievedAt,source,entry.rank),source_priority:source.priority});
    }
    summaries.push({root:source.root,source_found:expected,unique:docs.size,excluded,reasons});
  }

  eligible.sort((a,b)=>a.source_priority-b.source_priority||a.source_rank-b.source_rank||a.source_product_id.localeCompare(b.source_product_id));
  console.log('FILTER_SUMMARY',JSON.stringify({eligible:eligible.length,sources:summaries}));
  if(eligible.length<TARGET_COUNT)throw Error('Eligible branded non-fresh grocery source below target: '+eligible.length);

  const items=eligible.slice(0,TARGET_COUNT).map(({source_priority,...x})=>x);
  const payload={
    schema_version:4,
    source:'Makro PRO public search API',
    retrieved_at:retrievedAt,
    source_found:summaries.reduce((n,x)=>n+x.source_found,0),
    eligible_count:eligible.length,
    target_count:TARGET_COUNT,
    count:items.length,
    filters:{
      roots:SOURCES.map(x=>x.root),
      require_brand:true,
      exclude_fresh:true,
      exclude_market_fresh:true,
      exclude_electronics:true,
      excluded_category_tokens:['eggs','frozen','tofu','fresh','chilled','fresh-market','market-fresh','electronics','electrical','appliance','alcohol','beer','wine','liquor','spirit','tobacco','cigarette'],
      excluded_storage_types:['chilled','frozen','refrigerated']
    },
    sources:summaries,
    items
  };
  fs.mkdirSync(path.dirname(outPath),{recursive:true});
  fs.writeFileSync(outPath,JSON.stringify(payload)+'\n');
  const bytes=fs.statSync(outPath).size;
  const digest=crypto.createHash('sha256').update(fs.readFileSync(outPath)).digest('hex');
  if(bytes>15*1024*1024)throw Error('Makro snapshot unexpectedly large: '+bytes);
  console.log(JSON.stringify({written:outPath,count:items.length,eligible_count:eligible.length,bytes,sha256:digest}));
}
main().catch(e=>{console.error(e.stack||e);process.exit(1)});
