#!/usr/bin/env node
'use strict';

const fs=require('fs'),path=require('path');
const endpoint='https://search.maknet.siammakro.cloud/search/api/v1/indexes/products/search';
const storeCodes=JSON.parse(fs.readFileSync('catalog/makro-store-codes-20261015.json','utf8')).store_codes;
const sourceFiles=[
  'catalog/thai-market-master.json',
  'catalog/market-fresh-retail-sources-20261019.json',
  'catalog/market-seafood-fruit-veg-sources-20261019.json'
];
const outPath=process.argv[2]||'artifacts/market-makro-exact/market-makro-exact-source-images.json';
const items=sourceFiles.flatMap(file=>JSON.parse(fs.readFileSync(file,'utf8')).items||[])
  .filter(x=>x.source_name==='Makro PRO'&&/makro\.pro\/(?:th\/)?p\//.test(String(x.source_url||'')));
const extractId=url=>{
  const leaf=decodeURIComponent(String(url||'').split('?')[0].split('/').filter(Boolean).at(-1)||'');
  const m=leaf.match(/(?:^|-)(\d{8,})$/);
  return m?m[1]:null;
};
const imageOf=doc=>(doc?.images||[]).find(x=>typeof x==='string'&&/^https:\/\/images\.mango-prod\.siammakro\.cloud\//.test(x))||null;
async function search(q){
  const body={q:String(q),size:30,filters:{storeCodes,isSalesCustomer:false,countryCode:'TH',lang:'th',allowRestrictedProduct:false,allowAlcohol:false},sortBy:'RELEVANCE',autocorrect:false,page:1,autocategorise:false,includeCurations:false,source:'web'};
  const r=await fetch(endpoint,{method:'POST',headers:{'content-type':'application/json','accept':'application/json','accept-language':'th-TH','x-device-platform':'ios','referer':'https://www.makro.pro/'},body:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
  if(!r.ok)throw Error('HTTP '+r.status+' '+(await r.text()).slice(0,200));
  const j=await r.json();return (j.hits||[]).map(x=>x.document).filter(Boolean);
}
(async()=>{
  const rows=[];
  for(const item of items){
    const sourceProductId=extractId(item.source_url);
    let exact=null,error=null;
    try{
      if(sourceProductId){
        const docs=await search(sourceProductId);
        exact=docs.find(d=>String(d.productId||d.id||'')===sourceProductId)||null;
      }
    }catch(e){error=String(e.message||e)}
    rows.push({
      slug:item.slug,name_th:item.name_th,category:item.category,source_url:item.source_url,
      source_product_id:sourceProductId,
      status:exact&&imageOf(exact)?'exact':'missing',
      makro_title:exact?.title||exact?.searchTitle||null,
      makro_brand:exact?.brand||null,
      image_url:exact?imageOf(exact):null,
      error
    });
    await new Promise(r=>setTimeout(r,100));
  }
  const payload={generated_at:new Date().toISOString(),count:rows.length,exact:rows.filter(x=>x.status==='exact').length,missing:rows.filter(x=>x.status!=='exact').length,items:rows};
  fs.mkdirSync(path.dirname(outPath),{recursive:true});fs.writeFileSync(outPath,JSON.stringify(payload,null,2)+'\n');
  console.log(JSON.stringify({written:outPath,count:payload.count,exact:payload.exact,missing:payload.missing}));
})().catch(e=>{console.error(e.stack||e);process.exit(1)});
